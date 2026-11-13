"""Explicit, idempotent course import; service startup never imports course data."""

import json
import os
from pathlib import Path
from sqlalchemy import select

from fixture_content import load_content
from app.config import ROOT
from app.content_relations import (
    material_in_chapter, set_activity_questions, set_material_chapters,
    set_question_chapters, set_question_knowledge_points,
)
from app.db import SessionLocal
from app.models import (
    Activity, Chapter, CourseExample, KnowledgePoint, Material,
    Npc, QuestDefinition, Question, QuestionSecret,
)
from app.quests import validate_definition


def _shared(item, label):
    if item is not None and item.course_instance_id is not None:
        raise ValueError(f"{label}标识属于班级课程，请通过班级课程包导入更新")
    if item is not None and item.owner_id is not None:
        raise ValueError(f"{label}标识已被教师内容占用，拒绝覆盖")


def seed(db, source=None, *, content_root=None):
    """Import a chosen export and YAML package in one transaction.

    Source accepts the package directory containing json/, or that json/ directory.
    The repository package is only a convenience for explicit local demo imports.
    Existing learner evidence and uploaded material files are retained.
    """
    try:
        return _import_course(db, source, content_root=content_root)
    except Exception:
        db.rollback()
        raise


def _import_course(db, source=None, *, content_root=None):
    root = Path(source or os.getenv("REBORN_COURSE_ROOT", str(ROOT / "data/course/legacy/course-pack-43")))
    if (root / "json").is_dir():
        root = root / "json"

    def load(name):
        path = root / (name + ".json")
        if path.stat().st_size > 128 * 1024 * 1024:
            raise ValueError(f"课程来源文件过大：{name}")
        rows = json.loads(path.read_text(encoding="utf-8-sig"))
        if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
            raise ValueError(f"课程来源必须为对象列表：{name}")
        ids = [row.get("id") for row in rows]
        if any(type(key) is not int or key < 1 for key in ids) or len(ids) != len(set(ids)):
            raise ValueError(f"课程来源标识无效或重复：{name}")
        return rows

    chapters, docs, pages, kcs, examples, problems = [
        load(name) for name in ("course_chapter", "course_document", "course_page", "course_kc", "course_example", "course_problem")
    ]
    counts = dict(zip(("documents", "pages", "chapters", "knowledge_points", "examples", "problems"),
                      map(len, (docs, pages, chapters, kcs, examples, problems))))
    if not chapters:
        raise ValueError("课程包必须至少包含一个章节")
    content = load_content(Path(content_root)) if content_root is not None else load_content()
    from app.models import StoryDefinition

    # Legacy source IDs are never permission to overwrite live classroom rows.
    incoming = (
        (Chapter, [row["id"] for row in chapters]),
        (KnowledgePoint, [row["id"] for row in kcs]),
        (Material, [row["id"] for row in docs]),
        (CourseExample, [row["id"] for row in examples]),
        (Npc, [row["key"] for row in content["npcs"]]),
        (Activity, [row["id"] for row in content["activities"]]),
        (QuestDefinition, [row["id"] for row in content["quests"]]),
        (StoryDefinition, [row["id"] for row in content["stories"]]),
        (Question, [row["id"] for row in content["questions"]] + ["course-" + str(row["id"]) for row in problems]),
    )
    for model, keys in incoming:
        primary = model.key if model is Npc else model.id
        if keys and db.scalar(select(primary).where(model.course_instance_id.is_not(None), primary.in_(keys)).limit(1)) is not None:
            raise ValueError("旧课程来源标识与班级课程冲突；请使用班级课程包预览和导入，不能用seed修改班级定义")
    chapter_ids = {chapter["id"] for chapter in chapters}
    if any(spec["chapter_id"] not in chapter_ids for key in ("activities", "quests", "stories") for spec in content[key]):
        raise ValueError("教学配置引用了课程包之外的章节")
    # An explicit replacement retires only this package's shared definitions.
    # Teacher definitions and definitions in unrelated courses stay untouched.
    current_quests = {item["id"] for item in content["quests"]}
    current_activities = {item["id"] for item in content["activities"]}
    retired_quests = {item.id for item in db.scalars(select(QuestDefinition).where(
        QuestDefinition.owner_id.is_(None), QuestDefinition.chapter_id.in_(chapter_ids))) if item.id not in current_quests}
    current_stories = {item["id"] for item in content["stories"]}
    for story in db.scalars(select(StoryDefinition).where(StoryDefinition.chapter_id.in_(chapter_ids))):
        if story.quest_id in retired_quests or story.quest_id is None and story.id not in current_stories:
            db.delete(story)
    for item in db.scalars(select(QuestDefinition).where(QuestDefinition.id.in_(retired_quests))):
        db.delete(item)
    for activity in db.scalars(select(Activity).where(Activity.owner_id.is_(None), Activity.chapter_id.in_(chapter_ids))):
        if activity.id not in current_activities:
            activity.content = {key: value for key, value in activity.content.items() if key != "question_ids"} | {"published": False}
            activity.assistant_allowed = False
    for chapter in chapters:
        item = db.get(Chapter, chapter["id"])
        _shared(item, "章节")
        if not item:
            item = Chapter(id=chapter["id"])
            db.add(item)
        item.title, item.order_index = chapter["title"], chapter["order_index"]
        item.course_id = chapter.get("course_id", 43)
    db.flush()
    for kc in kcs:
        if kc["chapter_id"] not in chapter_ids:
            raise ValueError("知识点引用了不存在的章节")
        item = db.get(KnowledgePoint, kc["id"])
        _shared(item, "知识点")
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
        chapter = next((c for c in chapters if c["order_index"] == doc["sort_order"]), None)
        if not chapter:
            raise ValueError("课程资料引用了不存在的章节顺序")
        item = db.get(Material, doc["id"])
        _shared(item, "资料")
        if not item:
            item = Material(id=doc["id"], title=Path(doc["original_filename"]).stem, kind="text")
            db.add(item)
        # Imported/uploaded current files retain pages and publication metadata.
        if not item.storage_key:
            item.title = Path(doc["original_filename"]).stem
            item.kind, item.status = "text", "ready"
            item.content = {
                "notice": "原课件真实提取文本。原 PPT/PDF 和视频文件未随仓库提供，不代表完整幻灯片或视频。",
                "pages": [{"id": p["id"], "page_no": p["page_no"], "title": p["title"], "text": p["content_markdown"]}
                          for p in pages if p["document_id"] == doc["id"]],
            }
            set_material_chapters(db, item.id, [chapter["id"]])
    db.flush()
    code_to_kc = {kc["kc_code"]: kc["id"] for kc in kcs}
    problem_specs = {"course-" + str(problem["id"]): problem for problem in problems}
    question_specs = {question["id"]: question for question in content["questions"]}
    if question_specs.keys() & problem_specs.keys():
        raise ValueError("题目标识与课程导出题重复")
    assignments = {}
    question_chapters = {}
    activity_fields = ("title", "kind", "material_id", "npc_key", "assistant_allowed")
    for spec in content["activities"]:
        from app.learning_rules import validate_learning

        material = db.get(Material, spec.get("material_id")) if spec.get("material_id") else None
        if material and not material_in_chapter(db, material.id, spec["chapter_id"]):
            raise ValueError("学习资料必须关联活动章节")
        validate_learning(spec["content"], spec["kind"], material, resolve=True)
        question_ids = spec["content"].get("question_ids")
        if (not isinstance(question_ids, list) or any(not isinstance(key, str) for key in question_ids)
                or len(set(question_ids)) != len(question_ids)):
            raise ValueError(f"活动 {spec['id']} 必须固定非重复question_ids题集")
        if len(question_ids) > (100 if spec["kind"] == "assessment" else 5000):
            raise ValueError("章节测验最多100道题，其他活动最多5000道题")
        if set(question_ids) - (question_specs.keys() | problem_specs.keys()):
            raise ValueError(f"活动 {spec['id']} 引用了不存在的题目")
        mode = spec.get("knowledge_point_mode", "pages")
        point_ids = sorted(kc["id"] for kc in kcs if kc["chapter_id"] == spec["chapter_id"] and (
            mode == "chapter" or (mode == "pages" and set(kc.get("page_refs", [])) & set(spec.get("knowledge_point_pages", [])))
        ))
        if mode == "problem":
            point_ids = sorted({code_to_kc[problem_specs[key]["kc_code"]] for key in question_ids if key in problem_specs})
        values = {key: spec[key] for key in activity_fields}
        values["content"] = {key: value for key, value in spec["content"].items() if key != "question_ids"} | {"knowledge_point_ids": point_ids}
        item = db.get(Activity, spec["id"])
        _shared(item, "活动")
        if not item:
            item = Activity(id=spec["id"], chapter_id=spec["chapter_id"], **values)
            db.add(item)
        for key, value in values.items():
            setattr(item, key, value)
        item.chapter_id, item.owner_id = spec["chapter_id"], None
        assignments[item.id] = question_ids
        for key in question_ids:
            question_chapters.setdefault(key, set()).add(spec["chapter_id"])
    db.flush()
    # Only questions actually selected by current activities enter the demo bank.
    # Original exports and retired YAML questions remain external source material.
    for question_id, activity_chapters in question_chapters.items():
        question = db.get(Question, question_id)
        _shared(question, "题目")
        if not question:
            question = Question(id=question_id)
            db.add(question)
        question.owner_id, question.source_type, question.status = None, "imported", "ready"
        if question_id in question_specs:
            spec = question_specs[question_id]
            for key in ("title", "kind", "prompt", "options"):
                setattr(question, key, spec[key])
            question.samples, question.source = spec.get("samples", []), spec.get("source", {})
            question.source_type = spec.get("source_type", "imported")
            if question.source_type not in {"imported", "ai_generated"}:
                raise ValueError("题目来源只支持AI生成或导入")
            if question.source_type == "ai_generated" and not question.source.get("reviewed"):
                raise ValueError("AI生成题必须经审核后才能加入活动")
            associated_chapters = set(spec.get("chapter_ids", [spec["chapter_id"]] if "chapter_id" in spec else [])) | activity_chapters
            point_ids = spec.get("knowledge_point_ids", [])
            answer, hidden_cases = spec["answer"], spec.get("hidden_cases", [])
        else:
            spec = problem_specs[question_id]
            point_id = code_to_kc.get(spec["kc_code"])
            if point_id is None:
                raise ValueError("课程题引用了不存在的知识点")
            question.title, question.kind, question.prompt = spec["title"], "python", spec["statement_markdown"]
            question.options, question.samples = [], spec["samples"]
            question.source = {"kind": "course_export", "original_id": spec["id"], "reviewed": True}
            associated_chapters, point_ids = activity_chapters, [point_id]
            answer = None
            hidden_cases = [{"input": case["input"], "expected_output": case["expected_output"]}
                            for case in spec["test_cases"] if not case.get("is_sample")]
        if not associated_chapters <= chapter_ids:
            raise ValueError("题目引用了课程包之外的章节")
        db.flush()
        set_question_chapters(db, question_id, sorted(associated_chapters))
        set_question_knowledge_points(db, question_id, point_ids)
        secret = db.get(QuestionSecret, question_id)
        if not secret:
            secret = QuestionSecret(question_id=question_id)
            db.add(secret)
        secret.answer, secret.hidden_cases = answer, hidden_cases
    db.flush()
    for activity_id, ids in assignments.items():
        set_activity_questions(db, activity_id, ids)

    for spec in content["npcs"]:
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
        _shared(item, "任务")
        if not item:
            item = QuestDefinition(id=definition["id"])
            db.add(item)
        item.title, item.chapter_id, item.definition = definition["title"], definition["chapter_id"], definition
        item.owner_id = None
    db.flush()
    from app.story import validate_story

    for spec in content["stories"]:
        validate_story(spec["story"], db, spec["quest"], story_id=spec["id"])
        item = db.get(StoryDefinition, spec["id"])
        if not item:
            item = StoryDefinition(id=spec["id"])
            db.add(item)
        item.chapter_id, item.quest_id, item.content = spec["chapter_id"], spec["quest"]["id"] if spec["quest"] else None, spec["story"]
    # Definition changes may invalidate an open dialogue cursor; preserve the session
    # history and all learning data, and let the next interaction select a valid entry.
    from app.models import StorySession
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
