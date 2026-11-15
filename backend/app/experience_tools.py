"""Self-scoped experience navigation, stage positioning and durable checkpoints.

No model dispatch and no grading evidence is created by these operations.
"""
import hashlib
import json
from datetime import datetime
from types import SimpleNamespace
from typing import Annotated, Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session

from .auth import current_user, preview_classroom
from .config import dev_tools_enabled
from .course_scope import course_clause, instance_id, lock_course
from .courses import authorize_activity, authorize_chapter, authorize_material, chapter_available
from .db import get_db
from .dev_tools import PENDING_SUBMISSIONS
from .integration_models import AssistantCall
from .models import (Activity, ActivityProgress, ActivityQuestion, BuildingUnlock, Chapter, ChapterRelease, CourseInstance,
                     ExperienceCheckpoint, Material, MaterialChapter, Membership, NodeState, Npc,
                     PlayerPosition, PlayerQuest, Question, QuestDefinition, StoryDefinition, StorySession,
                     Submission, TownPreviewStudent, User, WorldFlag)
from .quests import node_statuses, validate_definition
from .world_config import load_world_config, validate_point

router = APIRouter(prefix="/experience", tags=["experience tools"])
Db = Annotated[Session, Depends(get_db)]
Actor = Annotated[User, Depends(current_user)]


def guard(db, user, classroom_id, request, *, mutate=False):
    if request.url.path.startswith("/api/dev/") and not dev_tools_enabled():
        raise HTTPException(404, "开发工具未启用")
    lock_course(db, classroom_id, exclusive=mutate)
    actor = db.scalar(select(User).where(User.id == user.id).with_for_update()) if mutate else user
    if not actor or actor.role != "student":
        raise HTTPException(403, "仅允许操作自己的体验记录")
    preview = db.get(TownPreviewStudent, user.id)
    if request.url.path.startswith("/api/teacher-town/"):
        if not preview or preview_classroom(db, preview).id != classroom_id:
            raise HTTPException(403, "只能操作本人当前班级的教师体验存档")
    elif not dev_tools_enabled() or not db.get(Membership, (user.id, classroom_id)):
        raise HTTPException(403, "开发工具仅对开发环境中已加入的班级开放")
    instance_id(db, classroom_id)
    if mutate:
        if db.scalar(select(Submission.id).where(Submission.user_id == user.id, Submission.classroom_id == classroom_id,
                                                Submission.status.in_(PENDING_SUBMISSIONS)).with_for_update().limit(1)):
            raise HTTPException(409, "仍有未完成或结果待确认的判题提交")
        if db.scalar(select(AssistantCall.id).where(AssistantCall.user_id == user.id, AssistantCall.classroom_id == classroom_id,
                                                   AssistantCall.status.in_(["running", "cancel_requested"])).limit(1)):
            raise HTTPException(409, "助教仍在回复，请结束后再操作")


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, default=str).encode()).hexdigest()


def definition_hash(db, classroom_id):
    # Include current definitions, not only authoring revision: imports and media replacements also invalidate saves.
    result = {"world": load_world_config()}
    for model in (Chapter, Material, Activity, Question, QuestDefinition, StoryDefinition, Npc):
        result[model.__tablename__] = sorted([dict((c.name, getattr(row, c.name)) for c in model.__table__.columns)
            for row in db.scalars(select(model).where(course_clause(db, model, classroom_id)))], key=lambda row: str(row.get("id", row.get("key"))))
    result["releases"] = [dict((c.name, getattr(row, c.name)) for c in ChapterRelease.__table__.columns)
                           for row in db.scalars(select(ChapterRelease).where(ChapterRelease.classroom_id == classroom_id).order_by(ChapterRelease.chapter_id))]
    result["revision"] = db.get(CourseInstance, instance_id(db, classroom_id)).revision
    result["activity_questions"] = [(row.activity_id, row.question_id, row.order_index) for row in db.scalars(
        select(ActivityQuestion).join(Activity).where(course_clause(db, Activity, classroom_id)).order_by(ActivityQuestion.activity_id, ActivityQuestion.order_index))]
    return digest(result)


STATE_FIELDS = {
    WorldFlag: ("key",), BuildingUnlock: ("course_instance_id", "building_id"),
    PlayerPosition: ("map_key", "tile_x", "tile_y", "direction"),
    ActivityProgress: ("activity_id", "page_no", "visited_pages", "completed", "media_progress"),
    StorySession: ("story_id", "npc_key", "interaction_id", "anchor", "node_id", "status", "pending_activity_id", "return_mapping"),
}


def own_rows(db, model, user, classroom_id):
    return list(db.scalars(select(model).where(model.user_id == user.id, model.classroom_id == classroom_id)))


def snapshot(db, user, classroom_id):
    data = {}
    for model, fields in STATE_FIELDS.items():
        rows = own_rows(db, model, user, classroom_id)
        if model is StorySession:
            rows = [row for row in rows if row.status in {"active", "waiting"}]
        data[model.__tablename__] = sorted([{field: getattr(row, field) for field in fields} for row in rows], key=lambda row: digest(row))
    data["quests"] = []
    for quest in sorted(own_rows(db, PlayerQuest, user, classroom_id), key=lambda row: row.quest_id):
        states = db.scalars(select(NodeState).where(NodeState.player_quest_id == quest.id).order_by(NodeState.node_id))
        data["quests"].append({"quest_id": quest.quest_id, "status": quest.status,
            "completed_at": quest.completed_at.isoformat() if quest.completed_at else None,
            "states": [{k: getattr(row, k) for k in ("node_id", "status", "choice", "evidence")} for row in states]})
    return data


def state_token(db, user, classroom_id):
    return digest([definition_hash(db, classroom_id), snapshot(db, user, classroom_id)])


class Scope(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int = Field(gt=0)


class ChapterInput(Scope):
    chapter_id: int = Field(gt=0)


@router.post("/chapter")
def switch_chapter(body: ChapterInput, request: Request, db: Db, user: Actor):
    guard(db, user, body.classroom_id, request, mutate=True)
    chapter = db.scalar(select(Chapter).where(Chapter.id == body.chapter_id, course_clause(db, Chapter, body.classroom_id)))
    release = db.get(ChapterRelease, (body.classroom_id, body.chapter_id))
    from .auth import as_utc
    from .models import now
    if not chapter or not release or release.status != "published" or (release.opens_at and as_utc(release.opens_at) > now()):
        raise HTTPException(403, "章节尚未发布或未到开放时间，不能定位")
    # Only this development actor bypasses prerequisite gates; the classroom release is unchanged.
    for flag in own_rows(db, WorldFlag, user, body.classroom_id):
        if flag.key.startswith("dev:chapter:"):
            db.delete(flag)
    db.flush()
    db.add(WorldFlag(user_id=user.id, classroom_id=body.classroom_id, key=f"dev:chapter:{chapter.id}"))
    db.commit()
    return {"ok": True}


@router.get("")
def overview(classroom_id: int, request: Request, db: Db, user: Actor):
    guard(db, user, classroom_id, request)
    chapters = []
    for chapter in db.scalars(select(Chapter).where(course_clause(db, Chapter, classroom_id)).order_by(Chapter.order_index)):
        release = db.get(ChapterRelease, (classroom_id, chapter.id))
        chapters.append({"id": chapter.id, "title": chapter.title, "available": chapter_available(db, user, classroom_id, chapter.id),
                         "status": release.status if release else "unpublished"})
    materials = [{"id": row.id, "title": row.title, "kind": row.kind, "status": row.status,
                  "pages": max(1, len((row.content or {}).get("pages", []))),
                  "chapters": list(db.scalars(select(MaterialChapter.chapter_id).where(MaterialChapter.material_id == row.id)))}
                 for row in db.scalars(select(Material).where(course_clause(db, Material, classroom_id)))]
    from .content_relations import activity_questions
    activities = [{"id": row.id, "chapter_id": row.chapter_id, "title": row.title, "kind": row.kind,
                   "published": row.content.get("published") is not False, "questions": len(activity_questions(db, row))}
                  for row in db.scalars(select(Activity).where(course_clause(db, Activity, classroom_id)))]
    quests = []
    for definition in db.scalars(select(QuestDefinition).where(course_clause(db, QuestDefinition, classroom_id))):
        instance = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id == definition.id))
        states = {row.node_id: row for row in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))} if instance else {}
        statuses = node_statuses(definition.definition, states)
        quests.append({"id": definition.id, "title": definition.title, "chapter_id": definition.chapter_id,
                       "nodes": [{**node, "status": statuses[node["id"]]} for node in definition.definition["nodes"]]})
    current_hash = definition_hash(db, classroom_id)
    checkpoints = [{"id": row.id, "name": row.name, "created_at": row.created_at.isoformat(), "valid": row.definition_hash == current_hash}
                   for row in db.scalars(select(ExperienceCheckpoint).where(ExperienceCheckpoint.user_id == user.id,
                        ExperienceCheckpoint.classroom_id == classroom_id).order_by(ExperienceCheckpoint.created_at.desc()))]
    return {"chapters": chapters, "materials": materials, "activities": activities, "quests": quests, "checkpoints": checkpoints}


class OpenInput(Scope):
    kind: Literal["material", "activity"]
    target: str = Field(min_length=1, max_length=100)
    page_no: int = Field(default=1, ge=1)
    question_no: int = Field(default=1, ge=1)
    video_time: float = Field(default=0, ge=0, le=86400, allow_inf_nan=False)


@router.post("/open")
def open_content(body: OpenInput, request: Request, db: Db, user: Actor):
    guard(db, user, body.classroom_id, request)
    if body.kind == "material":
        if not body.target.isdigit():
            raise HTTPException(422, "资料编号无效")
        material = authorize_material(db, user, body.classroom_id, int(body.target))
        if material.status != "ready":
            raise HTTPException(409, "资料尚未就绪")
        if body.page_no > max(1, len(material.content.get("pages", []))):
            raise HTTPException(422, "页码超出资料范围")
        return {"workspace": {"kind": "material", "material_id": material.id, "page_no": body.page_no, "video_time": body.video_time}}
    activity = authorize_activity(db, user, body.classroom_id, body.target)
    from .content_relations import activity_questions
    kind = "lesson" if activity.kind in {"lesson", "reading"} else "practice"
    if kind == "practice" and body.question_no > len(activity_questions(db, activity)):
        raise HTTPException(422, "题号超出活动范围")
    return {"workspace": {"kind": kind, "activity_id": activity.id, "page_no": body.page_no,
                           "question_no": body.question_no, "video_time": body.video_time}}


class StageInput(Scope):
    quest_id: str = Field(max_length=100)
    node_id: str = Field(max_length=100)
    choices: dict[str, str] = Field(default_factory=dict, max_length=100)
    any_dependencies: dict[str, str] = Field(default_factory=dict, max_length=100)
    expected: str | None = Field(default=None, max_length=64)


def stage_plan(definition, target_id, choices, any_dependencies):
    try:
        validate_definition(definition)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    nodes = {node["id"]: node for node in definition["nodes"]}
    if target_id not in nodes:
        raise HTTPException(404, "任务阶段不存在")
    for key, value in choices.items():
        if key not in nodes or value not in [choice["id"] for choice in nodes[key].get("choices", [])]:
            raise HTTPException(422, "分支选择无效")
    for key, value in any_dependencies.items():
        if key not in nodes or nodes[key].get("aggregation") != "ANY" or value not in nodes[key].get("dependencies", []):
            raise HTTPException(422, "ANY 前置选择无效")
    selected, states, visiting = dict(choices), {}, set()

    def visit(key, complete=True):
        node = nodes[key]
        if key in visiting:
            raise HTTPException(422, "任务依赖存在循环")
        condition = node.get("condition")
        if condition:
            parent, choice = condition["node"], condition["choice"]
            if parent in selected and selected[parent] != choice:
                if key == target_id:
                    raise HTTPException(422, "选择的分支无法到达目标阶段")
                return
            selected[parent] = choice
            visit(parent)
        if key in states:
            return
        visiting.add(key)
        deps = node.get("dependencies", [])
        if node.get("aggregation", "ALL") == "ANY" and deps:
            chosen = any_dependencies.get(key)
            if len(deps) > 1 and not chosen:
                raise HTTPException(422, f"请选择“{node['title']}”采用的 ANY 前置")
            deps = [chosen or deps[0]]
        for dep in deps:
            visit(dep)
        visiting.remove(key)
        if complete:
            if node.get("choices") and key not in selected:
                raise HTTPException(422, f"请选择“{node['title']}”的分支")
            states[key] = SimpleNamespace(choice=selected.get(key))

    visit(target_id, complete=False)
    statuses = node_statuses(definition, states)
    if statuses[target_id] != "ready":
        raise HTTPException(422, "当前前置与分支无法定位到这个阶段")
    return [{"node_id": key, "title": nodes[key]["title"], "choice": state.choice} for key, state in states.items()]


def prepare_stage(body, db, user):
    definition = db.scalar(select(QuestDefinition).where(QuestDefinition.id == body.quest_id, course_clause(db, QuestDefinition, body.classroom_id)))
    if not definition:
        raise HTTPException(404, "本班任务不存在")
    authorize_chapter(db, user, body.classroom_id, definition.chapter_id)
    return definition, stage_plan(definition.definition, body.node_id, body.choices, body.any_dependencies)


@router.post("/stage/preview")
def preview_stage(body: StageInput, request: Request, db: Db, user: Actor):
    guard(db, user, body.classroom_id, request)
    definition, plan = prepare_stage(body, db, user)
    return {"expected": state_token(db, user, body.classroom_id), "completed": plan,
            "target": next(node["title"] for node in definition.definition["nodes"] if node["id"] == body.node_id)}


def end_stories(db, user, classroom_id):
    for row in own_rows(db, StorySession, user, classroom_id):
        if row.status in {"active", "waiting"}:
            row.status = "ended"
            row.revision += 1
    db.flush()


def sync_stage_effects(db, user, classroom_id, definition, completed):
    """Rebuild this task's world effects, retaining effects required by other tasks."""
    touched, desired = set(), set()
    def effect_key(effect):
        if effect["type"] == "unlock_building" or effect.get("key") == "library_unlocked":
            return ("building", effect.get("building_id", "library"))
        return ("flag", effect["key"])
    for other in db.scalars(select(QuestDefinition).where(course_clause(db, QuestDefinition, classroom_id))):
        instance = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id == other.id))
        done = set(db.scalars(select(NodeState.node_id).where(NodeState.player_quest_id == instance.id))) if instance else set()
        for effect in other.definition.get("effects", []):
            key = effect_key(effect)
            if other.id == definition.id:
                touched.add(key)
            elif instance and instance.status == "complete":
                desired.add(key)
        for story in db.scalars(select(StoryDefinition).where(StoryDefinition.quest_id == other.id, course_clause(db, StoryDefinition, classroom_id))):
            for node_id, effects in story.content.get("checkpoint_effects", {}).items():
                for effect in effects:
                    key = effect_key(effect)
                    if other.id == definition.id:
                        touched.add(key)
                    if node_id in (completed if other.id == definition.id else done):
                        desired.add(key)
    for kind, key in touched:
        model = BuildingUnlock if kind == "building" else WorldFlag
        pk = (user.id, classroom_id, instance_id(db, classroom_id), key) if kind == "building" else (user.id, classroom_id, key)
        row = db.get(model, pk)
        if (kind, key) not in desired and row:
            db.delete(row)
        elif (kind, key) in desired and not row:
            if kind == "building":
                from .world_authoring import unlock_building
                unlock_building(db, user, classroom_id, key)
            else:
                db.add(WorldFlag(user_id=user.id, classroom_id=classroom_id, key=key))
    for node in definition.definition["nodes"]:
        if node["id"] in completed:
            continue
        if node.get("dialogue_id"):
            row = db.get(WorldFlag, (user.id, classroom_id, f"dialogue:completed:{node['target']}:{node['dialogue_id']}"))
            if row:
                db.delete(row)
        if node.get("activity_id"):
            row = db.get(ActivityProgress, (user.id, classroom_id, node["activity_id"]))
            if row:
                db.delete(row)


@router.post("/stage")
def locate_stage(body: StageInput, request: Request, db: Db, user: Actor):
    guard(db, user, body.classroom_id, request, mutate=True)
    if body.expected != state_token(db, user, body.classroom_id):
        raise HTTPException(409, "体验状态或课程已变化，请重新预览")
    definition, plan = prepare_stage(body, db, user)
    quest = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == body.classroom_id, PlayerQuest.quest_id == definition.id))
    if not quest:
        quest = PlayerQuest(user_id=user.id, classroom_id=body.classroom_id, quest_id=definition.id)
        db.add(quest)
        db.flush()
    db.execute(delete(NodeState).where(NodeState.player_quest_id == quest.id))
    quest.status, quest.completed_at = "active", None
    for item in plan:
        db.add(NodeState(player_quest_id=quest.id, node_id=item["node_id"], choice=item["choice"], evidence={"source": "development_positioning"}))
    sync_stage_effects(db, user, body.classroom_id, definition, {item["node_id"] for item in plan})
    end_stories(db, user, body.classroom_id)
    # Do not leave the actor inside a building which the earlier stage has locked.
    spawn = load_world_config()["default_spawn"]
    validate_point(spawn["map_key"], spawn["tile_x"], spawn["tile_y"])
    db.execute(delete(PlayerPosition).where(PlayerPosition.user_id == user.id, PlayerPosition.classroom_id == body.classroom_id))
    db.add(PlayerPosition(user_id=user.id, classroom_id=body.classroom_id, **spawn))
    db.commit()
    return {"ok": True, "user_id": user.id, "classroom_id": body.classroom_id}


class SaveInput(Scope):
    name: str = Field(min_length=1, max_length=80)
    client_state: dict = Field(default_factory=dict)
    request_id: UUID | None = None


@router.post("/checkpoints")
def save_checkpoint(body: SaveInput, request: Request, db: Db, user: Actor):
    guard(db, user, body.classroom_id, request, mutate=True)
    if not body.name.strip() or len(json.dumps(body.client_state)) > 262144:
        raise HTTPException(422, "名称不能为空，本机阅读和草稿状态不能超过 256KB")
    if body.request_id:
        existing = db.get(ExperienceCheckpoint, str(body.request_id))
        if existing:
            if existing.user_id != user.id or existing.classroom_id != body.classroom_id or existing.name != body.name.strip():
                raise HTTPException(409, "保存请求已使用，请刷新后重试")
            return {"id": existing.id}
    count = db.scalar(select(func.count()).select_from(ExperienceCheckpoint).where(ExperienceCheckpoint.user_id == user.id,
        ExperienceCheckpoint.classroom_id == body.classroom_id))
    if count >= 20:
        raise HTTPException(409, "每班最多保存 20 个检查点，请先删除不需要的记录")
    row = ExperienceCheckpoint(user_id=user.id, classroom_id=body.classroom_id, name=body.name.strip(),
        definition_hash=definition_hash(db, body.classroom_id), snapshot={**snapshot(db, user, body.classroom_id), "client_state": body.client_state})
    if body.request_id:
        row.id = str(body.request_id)
    db.add(row)
    db.commit()
    return {"id": row.id}


def checkpoint(db, user, classroom_id, checkpoint_id):
    row = db.get(ExperienceCheckpoint, checkpoint_id)
    if not row or row.user_id != user.id or row.classroom_id != classroom_id:
        raise HTTPException(404, "检查点不存在")
    return row


class RestoreInput(Scope):
    expected: str = Field(min_length=64, max_length=64)


@router.get("/checkpoints/{checkpoint_id}/preview")
def preview_restore(checkpoint_id: str, classroom_id: int, request: Request, db: Db, user: Actor):
    guard(db, user, classroom_id, request)
    row = checkpoint(db, user, classroom_id, checkpoint_id)
    if row.definition_hash != definition_hash(db, classroom_id):
        raise HTTPException(409, "课程定义或发布状态已变化，此检查点已失效")
    return {"expected": state_token(db, user, classroom_id), "name": row.name, "quests": len(row.snapshot["quests"])}


@router.post("/checkpoints/{checkpoint_id}/restore")
def restore_checkpoint(checkpoint_id: str, body: RestoreInput, request: Request, db: Db, user: Actor):
    guard(db, user, body.classroom_id, request, mutate=True)
    row = checkpoint(db, user, body.classroom_id, checkpoint_id)
    if row.definition_hash != definition_hash(db, body.classroom_id) or body.expected != state_token(db, user, body.classroom_id):
        raise HTTPException(409, "检查点已失效或体验状态变化，请重新预览")
    data, owner = row.snapshot, {"user_id": user.id, "classroom_id": body.classroom_id}
    for saved in data[PlayerPosition.__tablename__]:
        validate_point(saved["map_key"], saved["tile_x"], saved["tile_y"])
    end_stories(db, user, body.classroom_id)
    for model, fields in STATE_FIELDS.items():
        if model is not StorySession:
            db.execute(delete(model).where(model.user_id == user.id, model.classroom_id == body.classroom_id))
        for saved in data[model.__tablename__]:
            db.add(model(**owner, **{key: saved[key] for key in fields}))
    saved_quests = {q["quest_id"]: q for q in data["quests"]}
    quests = {q.quest_id: q for q in own_rows(db, PlayerQuest, user, body.classroom_id)}
    for key in saved_quests.keys() | quests.keys():
        quest = quests.get(key)
        if not quest:
            quest = PlayerQuest(**owner, quest_id=key)
            db.add(quest)
            db.flush()
        saved = saved_quests.get(key, {"status": "active", "completed_at": None, "states": []})
        quest.status = saved["status"]
        quest.completed_at = datetime.fromisoformat(saved["completed_at"]) if saved["completed_at"] else None
        db.execute(delete(NodeState).where(NodeState.player_quest_id == quest.id))
        for state in saved["states"]:
            db.add(NodeState(player_quest_id=quest.id, **state))
    db.commit()
    return {"ok": True, **owner, "client_state": data.get("client_state", {})}


@router.delete("/checkpoints/{checkpoint_id}")
def delete_checkpoint(checkpoint_id: str, classroom_id: int, request: Request, db: Db, user: Actor):
    guard(db, user, classroom_id, request, mutate=True)
    db.delete(checkpoint(db, user, classroom_id, checkpoint_id))
    db.commit()
    return {"ok": True}


@router.post("/spawn")
def return_to_spawn(body: Scope, request: Request, db: Db, user: Actor):
    guard(db, user, body.classroom_id, request, mutate=True)
    spawn = load_world_config()["default_spawn"]
    validate_point(spawn["map_key"], spawn["tile_x"], spawn["tile_y"])
    db.execute(delete(PlayerPosition).where(PlayerPosition.user_id == user.id, PlayerPosition.classroom_id == body.classroom_id))
    db.add(PlayerPosition(user_id=user.id, classroom_id=body.classroom_id, **spawn))
    end_stories(db, user, body.classroom_id)
    db.commit()
    return {"ok": True, "user_id": user.id, "classroom_id": body.classroom_id}
