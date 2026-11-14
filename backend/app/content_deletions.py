"""Two-stage, classroom-scoped retirement with impact review and retained evidence."""
from copy import deepcopy
from datetime import timedelta
from hashlib import sha256
import json
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from .auth import as_utc, require_teacher
from .db import Base, get_db
from .deletion_models import ContentDeletionPreview
from .models import (
    Activity, ActivityQuestion, Chapter, ChapterRelease, CourseInstance, KnowledgePoint,
    Material, MaterialChapter, Npc, PlayerQuest, Question, QuestionChapter, QuestionKnowledgePoint,
    QuestDefinition, StoryDefinition, StorySession, User, now,
)
from .teacher_scope import commit_content, require_scope, teacher_instance_id

router = APIRouter(prefix="/teacher/content-deletions", tags=["content-deletions"])
MODELS = {"chapter": Chapter, "material": Material, "question": Question, "activity": Activity,
          "knowledge_point": KnowledgePoint, "quest": QuestDefinition, "story": StoryDefinition}
LABELS = {"chapter": "章节", "material": "资料", "question": "题目", "activity": "活动",
          "knowledge_point": "知识点", "quest": "任务", "story": "教学流程"}
Kind = Literal["chapter", "material", "question", "activity"]
Teacher = Annotated[User, Depends(require_teacher)]
Database = Annotated[Session, Depends(get_db)]


class Target(BaseModel):
    model_config = ConfigDict(extra="forbid")
    kind: Kind
    id: str = Field(min_length=1, max_length=100)


class PreviewInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int = Field(gt=0)
    targets: list[Target] = Field(min_length=1, max_length=100)


class ConfirmInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int = Field(gt=0)
    confirmation: str = Field(min_length=1, max_length=200)
    confirmed: Literal[True]


def title(row):
    return row.content.get("title", row.stable_id or row.id) if isinstance(row, StoryDefinition) else row.title


def refs(value, context=None):
    """Only typed references; prose, node IDs and answers are never dependencies."""
    scalar = {"chapter_id": "chapter", "prerequisite_chapter_id": "chapter", "material_id": "material",
              "document_id": "material", "activity_id": "activity", "activity_completed": "activity",
              "question_id": "question", "knowledge_point_id": "knowledge_point", "quest_id": "quest", "story_id": "story"}
    lists = {"chapter_ids": "chapter", "material_ids": "material", "question_ids": "question", "knowledge_point_ids": "knowledge_point"}
    if isinstance(value, list):
        for child in value:
            yield from refs(child, context)
    elif isinstance(value, dict):
        for key, child in value.items():
            if key in scalar and isinstance(child, (str, int)):
                yield scalar[key], str(child)
            elif key in lists and isinstance(child, list):
                for item in child:
                    yield lists[key], str(item)
            elif key == "id" and context in {"chapter", "quest"}:
                yield context, str(child)
            elif key == "dialogue_id" and isinstance(child, str) and child.startswith("story:"):
                yield "story", child.split(":", 2)[1]
            else:
                yield from refs(child, key)


def plan_deletion(db, user, instance, targets):
    rows = {kind: {str(row.id): row for row in db.scalars(select(model).where(model.course_instance_id == instance.id))}
            for kind, model in MODELS.items()}
    retiring = {kind: {} for kind in MODELS}
    blockers, detach, preserved = [], [], []

    def add(kind, key, reason):
        key = str(key)
        if key in rows[kind] and key not in retiring[kind]:
            row = rows[kind][key]
            if user.role != "admin" and hasattr(row, "owner_id") and row.owner_id != user.id:
                blockers.append({"message": f"无权删除{LABELS[kind]}“{title(row)}”，请联系管理员", "kind": kind, "id": key})
            retiring[kind][key] = reason
            return True
        return False

    for target in targets:
        row = rows[target["kind"]].get(target["id"])
        require_scope(row, instance.id)
        if user.role != "admin" and row.owner_id != user.id:
            raise HTTPException(403, "只能删除自己管理的内容，共享课程由管理员维护")
        add(target["kind"], target["id"], "本次选择")

    selections = list(db.scalars(select(ActivityQuestion).where(ActivityQuestion.activity_id.in_([r.id for r in rows["activity"].values()]))))
    questions_by_activity = {key: {str(link.question_id) for link in selections if str(link.activity_id) == key} for key in rows["activity"]}

    def deleted_refs():
        return {(kind, value) for kind, removed in retiring.items() for key in removed
                for value in {key, str(rows[kind][key].stable_id)}}

    changed = True
    while changed:
        changed = False
        removed = deleted_refs()
        for kind in ("knowledge_point", "activity", "quest", "story"):
            for key, row in rows[kind].items():
                if str(row.chapter_id) in retiring["chapter"]:
                    changed |= add(kind, key, "所属章节将删除")
                if kind == "activity":
                    if str(row.material_id) in retiring["material"]:
                        changed |= add(kind, key, "依赖的资料将删除")
                    selected = questions_by_activity[key]
                    if selected and selected <= set(retiring["question"]):
                        changed |= add(kind, key, "删除所选题目后活动将为空")
                if kind in {"quest", "story"}:
                    source = row.definition if kind == "quest" else {"quest_id": row.quest_id, "content": row.content}
                    if set(refs(source)) & removed:
                        changed |= add(kind, key, "引用了本次删除的内容，整项移除以保留其他流程结构")

    removed = deleted_refs()
    # Conditions on retained world objects cannot be made true by deleting a prerequisite.
    from .course_exchange_models import CoursePackageState
    state = db.get(CoursePackageState, instance.id)
    config = deepcopy(state.configuration) if state else {}
    for key, value in config.get("buildings", {}).items():
        if set(refs(value)) & removed:
            blockers.append({"message": f"建筑“{key}”的开放条件引用了删除内容，请先调整", "kind": "building", "id": key})
    for item in config.get("world", {}).get("interactions", []):
        if set(refs(item)) & removed:
            blockers.append({"message": f"地图交互“{item.get('label', item['id'])}”引用了删除内容，请先调整", "kind": "interaction", "id": item["id"]})
    for npc in db.scalars(select(Npc).where(Npc.course_instance_id == instance.id)):
        if set(refs(npc.appearance_condition)) & removed:
            blockers.append({"message": f"角色“{npc.name}”的出现条件引用了删除内容，请先调整", "kind": "npc", "id": npc.key})
    for key in retiring["material"]:
        if rows["material"][key].status in {"queued", "processing"}:
            blockers.append({"message": f"资料“{title(rows['material'][key])}”仍在转换，请完成后重试", "kind": "material", "id": key})

    def unlink(kind, key, reason):
        row = rows[kind][str(key)]
        detach.append({"kind": kind, "id": str(key), "title": title(row), "reason": reason})

    for kind, model, source in (("material", MaterialChapter, "material_id"), ("question", QuestionChapter, "question_id")):
        for link in db.scalars(select(model).where(model.chapter_id.in_([int(key) for key in retiring["chapter"]]))):
            key = str(getattr(link, source))
            if key in rows[kind] and key not in retiring[kind]:
                unlink(kind, key, f"解除与章节“{title(rows['chapter'][str(link.chapter_id)])}”的关联；内容保留")
    for link in selections:
        if str(link.question_id) in retiring["question"] and str(link.activity_id) not in retiring["activity"]:
            unlink("activity", link.activity_id, f"移除题目“{title(rows['question'][str(link.question_id)])}”；已交试卷保持原题集和成绩")
    point_ids = {int(key) for key in retiring["knowledge_point"]}
    for key, row in rows["activity"].items():
        if key not in retiring["activity"] and point_ids & set(row.content.get("knowledge_point_ids", [])):
            unlink("activity", key, "解除已删除知识点的关联")
    for link in db.scalars(select(QuestionKnowledgePoint).where(QuestionKnowledgePoint.knowledge_point_id.in_(point_ids))):
        key = str(link.question_id)
        if key in rows["question"] and key not in retiring["question"]:
            unlink("question", key, "解除已删除知识点的关联")
    for key, row in rows["question"].items():
        if key not in retiring["question"] and str(row.source.get("material_id")) in retiring["material"]:
            unlink("question", key, "清除已删除资料的来源引用与页码；题目保留")
    releases = list(db.scalars(select(ChapterRelease).where(ChapterRelease.classroom_id == instance.classroom_id)))
    pause_releases = [r.chapter_id for r in releases if str(r.chapter_id) not in retiring["chapter"]
                      and str(r.prerequisite_chapter_id) in retiring["chapter"]]
    for key in pause_releases:
        unlink("chapter", key, "解除已删除的前置章节并暂停发布，待教师重新确认开放规则")

    # Validate the resulting current course, never splice arbitrary story edges.
    from .course_exchange import _snapshot
    from .world_authoring import validate_configuration
    from .authoring_diagnostics import AuthoringIssue
    try:
        data = _snapshot(db, instance)[0]
        stable = {kind: {rows[kind][key].stable_id or key for key in removed_keys} for kind, removed_keys in retiring.items()}
        for kind in MODELS:
            data[kind] = [r for r in data[kind] if r["id"] not in stable[kind]]
        for kind in ("material", "question"):
            for row in data[kind]:
                row["chapter_ids"] = [key for key in row["chapter_ids"] if key not in stable["chapter"]]
        for row in data["question"]:
            row["knowledge_point_ids"] = [key for key in row["knowledge_point_ids"] if key not in stable["knowledge_point"]]
            if row["source"].get("material_id") in stable["material"]:
                row["source"] = {k: v for k, v in row["source"].items() if k not in {"material_id", "page_no"}}
        for row in data["activity"]:
            row["question_ids"] = [key for key in row["question_ids"] if key not in stable["question"]]
            content = row["content"]
            content["knowledge_point_ids"] = [key for key in content.get("knowledge_point_ids", []) if key not in stable["knowledge_point"]]
        validate_configuration({k: v for k, v in config.items() if k in {"world", "buildings", "assistant", "editor"}}, data)
    except (ValueError, KeyError, TypeError, AttributeError) as exc:
        detail = exc.diagnostic() if isinstance(exc, AuthoringIssue) else {"message": str(exc)}
        blockers.append({"message": "删除后的课程需要修正：" + detail["message"], "kind": "configuration", "id": "",
                         "path": detail.get("path", ""), "target": detail.get("target", {})})

    # Retain full rows/files, including records referenced only through JSON snapshots.
    evidence, learners = {}, set()
    association_tables = {"activity_questions", "material_chapters", "question_chapters", "question_knowledge_points", "question_secrets", "chapter_releases"}
    model_tables = {model.__tablename__: kind for kind, model in MODELS.items()}
    for table in Base.metadata.sorted_tables:
        if table.name in association_tables or table.name in model_tables:
            continue
        conditions = []
        for foreign in table.foreign_keys:
            kind = model_tables.get(foreign.column.table.name)
            if kind and retiring[kind]:
                keys = [rows[kind][key].id for key in retiring[kind]]
                conditions.append(foreign.parent.in_(keys))
        if not conditions:
            continue
        from sqlalchemy import or_
        found = list(db.execute(select(table).where(or_(*conditions))).mappings())
        if found:
            evidence[table.name] = sorted([dict(row) for row in found], key=lambda r: json.dumps(r, sort_keys=True, default=str))
            learners.update(row["user_id"] for row in found if "user_id" in row)
            preserved.append({"kind": "history", "title": {"activity_progress": "活动进度", "learning_records": "学习记录",
                "submissions": "提交与评测记录", "assessment_attempts": "试卷与成绩", "assistant_sessions": "助教会话",
                "player_quests": "任务进度"}.get(table.name, "关联历史记录"), "count": len(found)})
    players = list(db.scalars(select(PlayerQuest).where(PlayerQuest.classroom_id == instance.classroom_id,
                                                       PlayerQuest.quest_id.in_(retiring["quest"]))))
    if players:
        evidence["player_quests"] = [{c.name: getattr(p, c.name) for c in PlayerQuest.__table__.columns} for p in players]
        learners.update(p.user_id for p in players)
        preserved.append({"kind": "history", "title": "任务进度", "count": len(players)})
        for table in Base.metadata.sorted_tables:
            if "player_quest_id" in table.c:
                evidence[table.name] = [dict(r) for r in db.execute(select(table).where(table.c.player_quest_id.in_([p.id for p in players]))).mappings()]
    sessions = list(db.scalars(select(StorySession).where(StorySession.classroom_id == instance.classroom_id).order_by(StorySession.id)))
    sessions = [r for r in sessions if r.story_id in retiring["story"] or r.pending_activity_id in retiring["activity"]]
    closing = [r.id for r in sessions if r.status in {"active", "waiting"}]
    if sessions:
        evidence["story_sessions"] = [{c.name: getattr(r, c.name) for c in StorySession.__table__.columns} for r in sessions]
        learners.update(r.user_id for r in sessions)
        preserved.append({"kind": "history", "title": "互动记录", "count": len(sessions)})
    retiring_rows = [{"kind": kind, "id": key, "title": title(rows[kind][key]), "reason": reason}
                     for kind, items in retiring.items() for key, reason in sorted(items.items())]
    # Root chapter deletion never expands to independent material/question bank entries.
    for item in detach:
        if item["kind"] in {"material", "question"}:
            preserved.append({"kind": item["kind"], "id": item["id"], "title": item["title"], "count": 1})
    for key in retiring["activity"]:
        activity = rows["activity"][key]
        for kind, keys in (("material", {str(activity.material_id)}), ("question", questions_by_activity[key])):
            for content_id in sorted(keys):
                if content_id in rows[kind] and content_id not in retiring[kind]:
                    preserved.append({"kind": kind, "id": content_id, "title": title(rows[kind][content_id]), "count": 1})
    public = {"remove": retiring_rows, "detach": detach, "preserve": list({json.dumps(x, sort_keys=True): x for x in preserved}.values()),
              "blockers": blockers, "affected_students": len(learners), "ending_sessions": len(closing),
              "history_policy": "学生提交、试卷、成绩、阅读及任务历史全部保留；删除内容退出当前教学，历史所需文件保留。"}
    # Hash actual evidence too: newly submitted work or session progress invalidates a stale preview.
    evidence = {key: sorted(value, key=lambda r: json.dumps(r, sort_keys=True, default=str)) for key, value in evidence.items()}
    fingerprint = sha256(json.dumps({"plan": public, "evidence": evidence}, sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest()
    return public, fingerprint, retiring, closing, pause_releases, rows


@router.post("/preview")
def preview_deletion(body: PreviewInput, user: Teacher, db: Database):
    instance_id = teacher_instance_id(db, user, body.classroom_id, exclusive=True)
    instance = db.get(CourseInstance, instance_id)
    targets = list({(t.kind, t.id): t.model_dump() for t in body.targets}.values())
    plan, fingerprint, *_ = plan_deletion(db, user, instance, targets)
    first = next(item for item in plan["remove"] if item["kind"] == targets[0]["kind"] and item["id"] == targets[0]["id"])
    confirmation = first["title"] if len(targets) == 1 else f"删除所选{len(targets)}项"
    preview = ContentDeletionPreview(teacher_id=user.id, classroom_id=body.classroom_id, course_instance_id=instance_id,
                                     revision=instance.revision, targets=targets, fingerprint=fingerprint, confirmation=confirmation)
    db.add(preview)
    db.commit()
    return {"id": preview.id, "revision": preview.revision, "confirmation": confirmation,
            "expires_at": as_utc(preview.created_at) + timedelta(minutes=15), **plan}


@router.post("/{preview_id}/confirm")
def confirm_deletion(preview_id: str, body: ConfirmInput, user: Teacher, db: Database):
    instance_id = teacher_instance_id(db, user, body.classroom_id, exclusive=True)
    preview = db.scalar(select(ContentDeletionPreview).where(ContentDeletionPreview.id == preview_id).with_for_update())
    if not preview or preview.teacher_id != user.id or preview.classroom_id != body.classroom_id or preview.course_instance_id != instance_id:
        raise HTTPException(404, "删除预览不存在")
    if body.confirmation != preview.confirmation:
        raise HTTPException(422, "确认文字不匹配")
    if preview.result is not None:
        return preview.result
    if now() - as_utc(preview.created_at) > timedelta(minutes=15):
        raise HTTPException(409, "删除预览已过期，请重新查看影响范围")
    instance = db.get(CourseInstance, instance_id)
    if instance.revision != preview.revision:
        raise HTTPException(409, "课程内容已变化，请重新查看影响范围")
    plan, fingerprint, retiring, closing, pauses, rows = plan_deletion(db, user, instance, preview.targets)
    if fingerprint != preview.fingerprint:
        raise HTTPException(409, "学生记录或删除影响已变化，请重新查看影响范围")
    if plan["blockers"]:
        raise HTTPException(409, "存在尚未处理的引用，请先修正后重新预览")
    at = now()
    for kind, items in retiring.items():
        for key in items:
            rows[kind][key].deleted_at = at
    chapter_ids = [int(k) for k in retiring["chapter"]]
    point_ids = [int(k) for k in retiring["knowledge_point"]]
    for model, column, keys in (
        (MaterialChapter, MaterialChapter.chapter_id, chapter_ids), (QuestionChapter, QuestionChapter.chapter_id, chapter_ids),
        (QuestionKnowledgePoint, QuestionKnowledgePoint.knowledge_point_id, point_ids),
        (ActivityQuestion, ActivityQuestion.question_id, list(retiring["question"])),
        (ActivityQuestion, ActivityQuestion.activity_id, list(retiring["activity"])),
    ):
        db.execute(delete(model).where(column.in_(keys)))
    for key, row in rows["activity"].items():
        if key not in retiring["activity"] and set(row.content.get("knowledge_point_ids", [])) & set(point_ids):
            row.content = {**row.content, "knowledge_point_ids": [p for p in row.content["knowledge_point_ids"] if p not in point_ids]}
    for key, row in rows["question"].items():
        if key not in retiring["question"] and str(row.source.get("material_id")) in retiring["material"]:
            row.source = {k: v for k, v in row.source.items() if k not in {"material_id", "page_no"}}
    for release in db.scalars(select(ChapterRelease).where(ChapterRelease.classroom_id == body.classroom_id)):
        if release.chapter_id in chapter_ids or release.chapter_id in pauses:
            release.status = "paused"
        if release.chapter_id in pauses:
            release.prerequisite_chapter_id = None
    for session in db.scalars(select(StorySession).where(StorySession.id.in_(closing))):
        session.status = "ended"
        session.revision += 1
        # Preserve node, pending activity, receipts and return mapping as historical evidence.
    preview.result = {"ok": True, "removed": len(plan["remove"]), "detached": len(plan["detach"]), "history_preserved": True}
    commit_content(db, instance_id)
    return preview.result
