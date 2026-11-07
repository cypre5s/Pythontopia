"""Idempotent real course import and current chapter configuration; no default accounts."""

import json
import os
from pathlib import Path
from sqlalchemy import select

from .chapter_content import load_content
from .config import ROOT
from .db import SessionLocal
from .models import (
    Activity, Chapter, CourseExample, KnowledgePoint, Material,
    Npc, QuestDefinition, Question, QuestionSecret,
)
from .quests import validate_definition

def seed(db, source=None):
    root = Path(source) if source else Path(os.getenv("REBORN_COURSE_ROOT", str(ROOT / "data/course-pack-43"))) / "json"

    def load(name):
        return json.loads((root / (name + ".json")).read_text(encoding="utf-8-sig"))

    chapters, docs, pages, kcs, examples, problems = [
        load(name) for name in ("course_chapter", "course_document", "course_page", "course_kc", "course_example", "course_problem")
    ]
    counts = dict(zip(("documents", "pages", "chapters", "knowledge_points", "examples", "problems"),
                      map(len, (docs, pages, chapters, kcs, examples, problems))))
    if list(counts.values()) != [7, 561, 7, 65, 46, 39]:
        raise ValueError(f"Unexpected export: {counts}")
    content = load_content()
    # YAML is the complete set of shared teaching tasks. Retire removed task
    # definitions without deleting any learner instance, event or grade.
    current_quests = {item["id"] for item in content["quests"]}
    current_activities = {item["id"] for item in content["activities"]}
    retired_quests = {item.id for item in db.scalars(select(QuestDefinition).where(QuestDefinition.owner_id.is_(None)))
                      if item.id not in current_quests}
    from .models import StoryDefinition

    for story in db.scalars(select(StoryDefinition).where(StoryDefinition.quest_id.in_(retired_quests))):
        db.delete(story)
    for item in db.scalars(select(QuestDefinition).where(QuestDefinition.id.in_(retired_quests))):
        db.delete(item)
    for activity in db.scalars(select(Activity).where(Activity.owner_id.is_(None))):
        if activity.id not in current_activities:
            activity.content = {**activity.content, "published": False}
            activity.assistant_allowed = False
    for chapter in chapters:
        item = db.get(Chapter, chapter["id"])
        if not item:
            item = Chapter(id=chapter["id"])
            db.add(item)
        item.title, item.order_index = chapter["title"], chapter["order_index"]
    db.flush()
    for kc in kcs:
        item = db.get(KnowledgePoint, kc["id"])
        if not item:
            item = KnowledgePoint(id=kc["id"])
            db.add(item)
        item.chapter_id, item.title, item.content = kc["chapter_id"], kc["kc_name"], kc
    for example in examples:
        item = db.get(CourseExample, example["id"])
        if not item:
            item = CourseExample(id=example["id"])
            db.add(item)
        item.content = example
    for doc in docs:
        chapter = next(c for c in chapters if c["order_index"] == doc["sort_order"])
        item = db.get(Material, doc["id"])
        if not item:
            item = Material(id=doc["id"], chapter_id=chapter["id"], title=Path(doc["original_filename"]).stem, kind="text")
            db.add(item)
        # Imported and uploaded current files retain their pages and publication metadata.
        if not item.storage_key:
            item.title, item.chapter_id = Path(doc["original_filename"]).stem, chapter["id"]
            item.kind, item.status = "text", "ready"
            item.content = {
                "notice": "原课件真实提取文本。原 PPT/PDF 和视频文件未随仓库提供，不代表完整幻灯片或视频。",
                "pages": [{"id": p["id"], "page_no": p["page_no"], "title": p["title"], "text": p["content_markdown"]}
                          for p in pages if p["document_id"] == doc["id"]],
            }
    db.flush()
    code_to_kc = {kc["kc_code"]: kc["id"] for kc in kcs}
    reviewed_problem = next(p for p in problems if p["id"] == 476)
    activity_fields = ("title", "kind", "material_id", "npc_key", "assistant_allowed", "content")
    for spec in content["activities"]:
        mode = spec.get("knowledge_point_mode", "pages")
        point_ids = sorted(kc["id"] for kc in kcs if kc["chapter_id"] == spec["chapter_id"] and (
            mode == "chapter" or (mode == "pages" and set(kc.get("page_refs", [])) & set(spec.get("knowledge_point_pages", [])))
        ))
        if mode == "problem":
            point_ids = [code_to_kc[reviewed_problem["kc_code"]]]
        question_ids = spec["content"].get("question_ids")
        if question_ids is None:
            raise ValueError(f"活动 {spec['id']} 必须固定question_ids题集")
        known_questions = {q["id"] for q in content["questions"] if q["activity_id"] == spec["id"]}
        if mode == "problem":
            known_questions.add("course-" + str(reviewed_problem["id"]))
        if not isinstance(question_ids, list) or any(not isinstance(key, str) or key not in known_questions for key in question_ids):
            raise ValueError(f"活动 {spec['id']} 引用了不存在或不属于它的题目")
        if len(set(question_ids)) != len(question_ids):
            raise ValueError(f"活动 {spec['id']} 题集含重复标识")
        values = {key: spec[key] for key in activity_fields if key != "content"}
        values["content"] = {**spec["content"], "question_ids": question_ids, "knowledge_point_ids": point_ids}
        item = db.get(Activity, spec["id"])
        if not item:
            item = Activity(id=spec["id"], chapter_id=spec["chapter_id"], **values)
            db.add(item)
            db.flush()
        for key, value in values.items():
            setattr(item, key, value)
        item.chapter_id = spec["chapter_id"]
        item.owner_id = None
    db.flush()
    for question in content["questions"]:
        # Removed teaching activities remain private question archives, retaining
        # stable question IDs and foreign keys of existing submissions.
        if not db.get(Activity, question["activity_id"]):
            db.add(Activity(id=question["activity_id"], chapter_id=question["chapter_id"], title="未发布的归档题目",
                            kind="practice", assistant_allowed=False, content={"published": False, "question_ids": []}))
            db.flush()
        public = {key: question[key] for key in ("id", "chapter_id", "activity_id", "title", "kind", "prompt", "options", "source")}
        existing = db.get(Question, question["id"])
        if not existing:
            db.add(Question(**public, samples=[]))
            db.flush()
            db.add(QuestionSecret(question_id=question["id"], answer=question["answer"], hidden_cases=[]))
        else:
            for key, value in public.items():
                setattr(existing, key, value)
            secret = db.get(QuestionSecret, question["id"])
            if not secret:
                secret = QuestionSecret(question_id=question["id"], hidden_cases=[])
                db.add(secret)
            secret.answer = question["answer"]
    chapter_by_kc = {kc["kc_code"]: kc["chapter_id"] for kc in kcs}
    for problem in problems:
        reviewed = problem["id"] == reviewed_problem["id"]
        activity_id = "archive-" + str(problem["id"])
        chapter_id = chapter_by_kc.get(problem["kc_code"], 180)
        if not db.get(Activity, activity_id):
            db.add(Activity(id=activity_id, chapter_id=chapter_id, title=problem["title"], kind="practice", assistant_allowed=False,
                            content={"published": False, "knowledge_point_ids": [code_to_kc[problem["kc_code"]]]}))
            db.flush()
        question_id = "course-" + str(problem["id"])
        question = db.get(Question, question_id)
        if not question:
            question = Question(id=question_id)
            db.add(question)
        question.chapter_id, question.activity_id = chapter_id, activity_id
        question.title, question.kind, question.prompt = problem["title"], "python", problem["statement_markdown"]
        question.options, question.samples = [], problem["samples"]
        question.source = {"kind": "course_export", "original_id": problem["id"], "page_ids": [10771] if reviewed else [], "reviewed": reviewed}
        db.flush()
        secret = db.get(QuestionSecret, question_id)
        if not secret:
            secret = QuestionSecret(question_id=question_id)
            db.add(secret)
        secret.answer = None
        secret.hidden_cases = [{"input": case["input"], "expected_output": case["expected_output"]}
                               for case in problem["test_cases"] if not case.get("is_sample")]
    from .dialogues import validate_dialogue

    for spec in content["npcs"]:
        validate_dialogue(spec["dialogue"])
        npc = db.get(Npc, spec["key"])
        if not npc:
            db.add(Npc(**spec))
        else:
            for key, value in spec.items():
                setattr(npc, key, value)
    db.flush()
    for definition in content["quests"]:
        validate_definition(definition, db)
        item = db.get(QuestDefinition, definition["id"])
        if not item:
            item = QuestDefinition(id=definition["id"])
            db.add(item)
        item.title, item.chapter_id, item.definition = definition["title"], definition["chapter_id"], definition
        item.owner_id = None
    db.flush()
    from .story import validate_story

    for spec in content["stories"]:
        validate_story(spec["story"], db, spec["quest"], story_id=spec["id"])
        item = db.get(StoryDefinition, spec["id"])
        if not item:
            item = StoryDefinition(id=spec["id"])
            db.add(item)
        item.chapter_id, item.quest_id, item.content = spec["chapter_id"], spec["quest"]["id"], spec["story"]
    # Definition changes may invalidate an open dialogue cursor; preserve the session
    # history and all learning data, and let the next interaction select a valid entry.
    from .models import StorySession
    for session in db.scalars(select(StorySession).where(StorySession.status.in_(["active", "waiting"]))):
        story = db.get(StoryDefinition, session.story_id)
        node = next((n for n in story.content["nodes"] if n["id"] == session.node_id), None) if story else None
        binding = story.content["bindings"].get(node.get("binding")) if node and node["type"] == "activity" else None
        valid = (node and ((session.status == "active" and node["type"] == "dialogue") or
                          (session.status == "waiting" and binding and binding["activity_id"] == session.pending_activity_id)))
        if not valid:
            session.status, session.pending_activity_id, session.return_mapping = "ended", None, {}
            session.revision += 1
        elif session.status == "waiting":
            session.return_mapping = node["returns"]
    db.commit()
    return counts


if __name__ == "__main__":
    with SessionLocal() as db:
        print(seed(db))
