"""Idempotent real course import and immutable chapter publication; no default accounts."""

import json
import os
from pathlib import Path

from .chapter_content import load_content
from .config import ROOT
from .db import SessionLocal
from .models import (
    Activity, ActivityVersion, Chapter, CourseExample, KnowledgePoint, Material, MaterialVersion,
    Npc, QuestDefinition, Question, QuestionSecret,
)
from .quests import validate_definition

# Historical callers inspect the original definitions; content lives only in YAML.
QUESTS = [quest for quest in load_content()["quests"] if quest["version"] == 1]


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
    for chapter in chapters:
        if not db.get(Chapter, chapter["id"]):
            db.add(Chapter(id=chapter["id"], title=chapter["title"], order_index=chapter["order_index"]))
    db.flush()
    for kc in kcs:
        if not db.get(KnowledgePoint, kc["id"]):
            db.add(KnowledgePoint(id=kc["id"], chapter_id=kc["chapter_id"], title=kc["kc_name"], content=kc))
    for example in examples:
        if not db.get(CourseExample, example["id"]):
            db.add(CourseExample(id=example["id"], content=example))
    for doc in docs:
        chapter = next(c for c in chapters if c["order_index"] == doc["sort_order"])
        if not db.get(Material, doc["id"]):
            db.add(Material(id=doc["id"], chapter_id=chapter["id"], title=chapter["title"], kind="text"))
            db.flush()
            db.add(MaterialVersion(material_id=doc["id"], version=1, status="ready", content={
                "notice": "原课件真实提取文本。原 PPT/PDF 和视频文件未随仓库提供，不代表完整幻灯片或视频。",
                "pages": [{"id": p["id"], "page_no": p["page_no"], "title": p["title"], "text": p["content_markdown"]}
                          for p in pages if p["document_id"] == doc["id"]],
            }))
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
            raise ValueError(f"活动 {spec['id']} v{spec['version']} 必须固定question_ids题集")
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
            item = Activity(id=spec["id"], chapter_id=spec["chapter_id"], version=spec["version"], **values)
            db.add(item)
            db.flush()
        snapshot = db.get(ActivityVersion, (spec["id"], spec["version"]))
        if not snapshot:
            db.add(ActivityVersion(activity_id=spec["id"], version=spec["version"], snapshot=values))
        elif snapshot.snapshot != values:
            raise ValueError(f"活动 {spec['id']} v{spec['version']} 已发布，修改内容必须增加版本")
        if item.version < spec["version"]:
            for key, value in values.items():
                setattr(item, key, value)
            item.version = spec["version"]
    db.flush()
    for question in content["questions"]:
        public = {key: question[key] for key in ("id", "chapter_id", "activity_id", "title", "kind", "prompt", "options", "source")}
        existing = db.get(Question, question["id"])
        if not existing:
            db.add(Question(**public, samples=[]))
            db.flush()
            db.add(QuestionSecret(question_id=question["id"], answer=question["answer"], hidden_cases=[]))
        else:
            secret = db.get(QuestionSecret, question["id"])
            if any(getattr(existing, key) != value for key, value in public.items()) or not secret or secret.answer != question["answer"]:
                raise ValueError(f"题目 {question['id']} 已发布，修订必须使用新题目ID并创建活动新版本")
    chapter_by_kc = {kc["kc_code"]: kc["chapter_id"] for kc in kcs}
    for problem in problems:
        reviewed = problem["id"] == reviewed_problem["id"]
        activity_id = "ch2-code" if reviewed else "archive-" + str(problem["id"])
        chapter_id = chapter_by_kc.get(problem["kc_code"], 180)
        if not reviewed and not db.get(Activity, activity_id):
            db.add(Activity(id=activity_id, chapter_id=chapter_id, title=problem["title"], kind="practice", assistant_allowed=False,
                            content={"published": False, "knowledge_point_ids": [code_to_kc[problem["kc_code"]]]}))
            db.flush()
        question_id = "course-" + str(problem["id"])
        if not db.get(Question, question_id):
            db.add(Question(id=question_id, chapter_id=chapter_id, activity_id=activity_id, title=problem["title"], kind="python",
                            prompt=problem["statement_markdown"], options=[], samples=problem["samples"],
                            source={"kind": "course_export", "original_id": problem["id"], "page_ids": [10771] if reviewed else [],
                                    "reviewed": reviewed}))
            db.flush()
            db.add(QuestionSecret(question_id=question_id, answer=None, hidden_cases=[
                {"input": case["input"], "expected_output": case["expected_output"]}
                for case in problem["test_cases"] if not case.get("is_sample")
            ]))
    from .dialogues import validate_dialogue

    for spec in content["npcs"]:
        validate_dialogue(spec["dialogue"])
        npc = db.get(Npc, spec["key"])
        if not npc:
            db.add(Npc(**spec))
        elif len(npc.dialogue) == 1 and npc.dialogue[0] == {key: spec["dialogue"][0][key] for key in ("id", "text")}:
            npc.dialogue = spec["dialogue"]
    db.flush()
    for definition in content["quests"]:
        validate_definition(definition, db)
        item = db.get(QuestDefinition, (definition["id"], definition["version"]))
        if not item:
            db.add(QuestDefinition(id=definition["id"], version=definition["version"], title=definition["title"],
                                   chapter_id=definition["chapter_id"], definition=definition))
        elif item.definition != definition:
            raise ValueError(f"任务 {definition['id']} v{definition['version']} 已发布，修改内容必须增加版本")
    db.flush()
    from .models import StoryDefinition
    from .story import validate_story

    for spec in content["stories"]:
        validate_story(spec["story"], db, spec["quest"], story_id=spec["id"], story_version=spec["version"])
        item = db.get(StoryDefinition, (spec["id"], spec["version"]))
        if not item:
            db.add(StoryDefinition(id=spec["id"], version=spec["version"], chapter_id=spec["chapter_id"],
                                   quest_id=spec["quest"]["id"], quest_version=spec["quest"]["version"], content=spec["story"]))
        elif item.content != spec["story"]:
            raise ValueError(f"演出 {spec['id']} v{spec['version']} 已发布，修改内容必须增加版本")
    db.commit()
    return counts


if __name__ == "__main__":
    with SessionLocal() as db:
        print(seed(db))
