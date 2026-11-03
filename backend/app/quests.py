from typing import Annotated, Literal
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from .auth import current_user, require_teacher
from .courses import authorize_classroom, authorize_chapter, chapter_available
from .db import get_db
from .models import (
    Activity,
    ActivityVersion,
    Chapter,
    ChapterRelease,
    LearningEvent,
    LearningRecord,
    NodeState,
    Npc,
    PlayerPosition,
    PlayerQuest,
    QuestDefinition,
    Question,
    Submission,
    User,
    WorldFlag,
)

router = APIRouter(tags=["quests"])
NODE_TYPES = {
    "TALK_TO_NPC",
    "PLAY_DIALOGUE",
    "REACH_LOCATION",
    "INTERACT_OBJECT",
    "STUDY_LESSON",
    "COMPLETE_PRACTICE",
    "PASS_ASSESSMENT",
    "REPORT_BACK",
}
OBJECTS = {
    "library_shelf": ("library", 4, 4),
    "practice_plaza": ("town_hub", 38, 24),
    "library_door": ("town_hub", 8, 22),
    "town_square": ("town_hub", 22, 34),
}


def validate_definition(definition: dict, db: Session | None = None) -> dict:
    if not isinstance(definition, dict):
        raise ValueError("任务定义必须为对象")
    if set(definition) - {"id", "version", "title", "chapter_id", "nodes", "effects", "kind"}:
        raise ValueError("未知任务字段")
    if not isinstance(definition.get("id"), str) or not definition["id"].strip() or len(definition["id"]) > 100:
        raise ValueError("任务id必须为非空短字符串")
    if not isinstance(definition.get("title"), str) or not definition["title"].strip() or len(definition["title"]) > 200:
        raise ValueError("任务标题必须为非空短字符串")
    if type(definition.get("version")) is not int or definition["version"] < 1:
        raise ValueError("任务version必须为正整数")
    if type(definition.get("chapter_id")) is not int or definition["chapter_id"] < 1:
        raise ValueError("章节id必须为正整数")
    if definition.get("kind", "main") not in ("main", "side"):
        raise ValueError("任务kind只能为main或side")
    nodes = definition.get("nodes", [])
    if not isinstance(nodes, list) or not 1 <= len(nodes) <= 100 or any(not isinstance(node, dict) for node in nodes):
        raise ValueError("任务需要1至100个对象节点")
    ids = [node.get("id") for node in nodes]
    if any(not isinstance(id, str) or not id.strip() or len(id) > 100 for id in ids):
        raise ValueError("节点id必须为非空短字符串")
    if len(set(ids)) != len(ids):
        raise ValueError("节点id重复")
    mapping = {node["id"]: node for node in nodes}
    for node in nodes:
        if set(node) - {
            "id",
            "type",
            "title",
            "target",
            "dependencies",
            "aggregation",
            "optional",
            "activity_id",
            "activity_version",
            "condition",
            "choices",
            "dialogue_id",
        }:
            raise ValueError("未知节点字段")
        if (
            not isinstance(node.get("type"), str)
            or node["type"] not in NODE_TYPES
            or not isinstance(node.get("title"), str)
            or not node["title"].strip()
        ):
            raise ValueError("节点类型或标题无效")
        if "optional" in node and type(node["optional"]) is not bool:
            raise ValueError("optional必须为布尔值")
        for field in ("target", "activity_id", "dialogue_id"):
            if field in node and (not isinstance(node[field], str) or not node[field].strip() or len(node[field]) > 100):
                raise ValueError(f"{field}必须为非空短字符串")
        dependencies = node.get("dependencies", [])
        if not isinstance(dependencies, list) or any(not isinstance(dep, str) or not dep for dep in dependencies):
            raise ValueError("dependencies必须为节点id字符串列表")
        if len(set(dependencies)) != len(dependencies) or any(dep not in mapping for dep in dependencies):
            raise ValueError("悬空或重复依赖")
        if node.get("aggregation", "ALL") not in ("ALL", "ANY"):
            raise ValueError("聚合必须为ALL或ANY")
        choices = node.get("choices", [])
        if not isinstance(choices, list) or any(not isinstance(choice, dict) or set(choice) != {"id", "label"} for choice in choices):
            raise ValueError("choices必须为id/label对象列表")
        if any(
            not isinstance(choice["id"], str)
            or not choice["id"].strip()
            or len(choice["id"]) > 80
            or not isinstance(choice["label"], str)
            or not choice["label"].strip()
            or len(choice["label"]) > 200
            for choice in choices
        ):
            raise ValueError("选项id和label必须为非空短字符串")
        if len({choice["id"] for choice in choices}) != len(choices):
            raise ValueError("选择项id重复")
        condition = node.get("condition")
        if condition is not None:
            if (
                not isinstance(condition, dict)
                or set(condition) != {"node", "choice"}
                or any(not isinstance(condition[key], str) or not condition[key] for key in ("node", "choice"))
            ):
                raise ValueError("condition必须包含node和choice字符串")
            if condition["node"] not in mapping or condition["node"] not in dependencies:
                raise ValueError("分支条件必须引用直接前置节点")
            parent_choices = mapping[condition["node"]].get("choices", [])
            if not isinstance(parent_choices, list) or not any(
                isinstance(choice, dict) and choice.get("id") == condition["choice"] for choice in parent_choices
            ):
                raise ValueError("分支选项不存在")
        activity_version = node.get("activity_version", 1)
        if type(activity_version) is not int or activity_version < 1:
            raise ValueError("活动版本必须是正整数")
        if node["type"] in {"STUDY_LESSON", "COMPLETE_PRACTICE", "PASS_ASSESSMENT"}:
            if not node.get("activity_id"):
                raise ValueError("学习节点必须绑定活动")
            if db:
                activity = db.get(Activity, node["activity_id"])
                snapshot = db.get(ActivityVersion, (node["activity_id"], activity_version))
                if not activity or activity_version > activity.version or not snapshot:
                    raise ValueError("活动版本不存在")
                if snapshot.snapshot.get("content", {}).get("published") is False:
                    raise ValueError("活动版本尚未发布")
                expected = {"STUDY_LESSON": {"lesson", "reading"}, "COMPLETE_PRACTICE": {"practice"}, "PASS_ASSESSMENT": {"assessment"}}[node["type"]]
                if snapshot.snapshot.get("kind") not in expected:
                    raise ValueError("任务节点与教学活动版本类型不匹配")
        if node["type"] in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"}:
            if not node.get("target") or (db and not db.get(Npc, node["target"])):
                raise ValueError("NPC不存在")
        if node["type"] in {"REACH_LOCATION", "INTERACT_OBJECT"} and node.get("target") not in OBJECTS:
            raise ValueError("目标对象不存在")
    visiting, done = set(), set()

    def visit(node_id):
        if node_id in visiting:
            raise ValueError("任务节点存在循环")
        if node_id in done:
            return
        visiting.add(node_id)
        for dependency in mapping[node_id].get("dependencies", []):
            visit(dependency)
        visiting.remove(node_id)
        done.add(node_id)

    for node_id in mapping:
        visit(node_id)
    if db and not db.get(Chapter, definition["chapter_id"]):
        raise ValueError("章节不存在")
    effects = definition.get("effects", [])
    if not isinstance(effects, list):
        raise ValueError("effects必须为列表")
    for effect in effects:
        if (
            not isinstance(effect, dict)
            or set(effect) != {"type", "key"}
            or effect.get("type") != "set_world_flag"
            or not isinstance(effect.get("key"), str)
            or not effect["key"].strip()
            or len(effect["key"]) > 100
        ):
            raise ValueError("只允许明确的世界标记效果")
    return definition


def node_statuses(definition: dict, states: dict) -> dict:
    nodes = {node["id"]: node for node in definition["nodes"]}
    memo = {}

    def evaluate(node_id):
        if node_id in memo:
            return memo[node_id]
        if node_id in states:
            memo[node_id] = "complete"
            return "complete"
        node = nodes[node_id]
        condition = node.get("condition")
        if condition:
            parent = states.get(condition["node"])
            if parent and parent.choice != condition["choice"]:
                memo[node_id] = "skipped"
                return "skipped"
        deps = [evaluate(dep) for dep in node.get("dependencies", [])]
        if node.get("aggregation", "ALL") == "ANY" and deps:
            status = "ready" if "complete" in deps else "skipped" if all(x == "skipped" for x in deps) else "locked"
        else:
            if deps and all(status == "skipped" for status in deps):
                status = "skipped"
            elif all(status in {"complete", "skipped"} for status in deps):
                status = "ready"
            else:
                status = "locked"
        if condition and not states.get(condition["node"]):
            status = "locked"
        memo[node_id] = status
        return status

    return {node_id: evaluate(node_id) for node_id in nodes}


def ensure_quests(db, user, classroom_id):
    if user.role != "student":
        return
    from .models import Classroom

    classroom = db.get(Classroom, classroom_id)
    definitions = db.scalars(
        select(QuestDefinition)
        .where((QuestDefinition.owner_id.is_(None)) | (QuestDefinition.owner_id == classroom.teacher_id))
        .order_by(QuestDefinition.version.desc())
    ).all()
    selected = set()
    for definition in definitions:
        if definition.id in selected or not chapter_available(db, user, classroom_id, definition.chapter_id):
            continue
        release = db.get(ChapterRelease, (classroom_id, definition.chapter_id))
        if definition.version > release.version:
            continue
        selected.add(definition.id)
        existing = db.scalar(
            select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id == definition.id)
        )
        if not existing:
            db.add(PlayerQuest(user_id=user.id, classroom_id=classroom_id, quest_id=definition.id, quest_version=definition.version))
    try:
        db.commit()
    except IntegrityError:
        db.rollback()


def world_payload(db, user, classroom_id):
    classroom = authorize_classroom(db, user, classroom_id)
    ensure_quests(db, user, classroom_id)
    position = db.get(PlayerPosition, (user.id, classroom_id))
    player = (
        {"map_key": position.map_key, "tile_x": position.tile_x, "tile_y": position.tile_y, "direction": position.direction}
        if position
        else {"map_key": "town_hub", "tile_x": 22, "tile_y": 36, "direction": "down"}
    )
    quests = []
    for instance in db.scalars(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id)):
        definition = db.get(QuestDefinition, (instance.quest_id, instance.quest_version))
        states = {state.node_id: state for state in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))}
        statuses = node_statuses(definition.definition, states)
        quests.append(
            {
                "id": definition.id,
                "title": definition.title,
                "chapter_id": definition.chapter_id,
                "version": definition.version,
                "status": instance.status,
                "kind": definition.definition.get("kind", "main"),
                "nodes": [
                    {**node, "status": statuses[node["id"]], "choice": states[node["id"]].choice if node["id"] in states else None}
                    for node in definition.definition["nodes"]
                ],
            }
        )
    return {
        "player": player,
        "npcs": [
            {
                "key": npc.key,
                "name": npc.name,
                "map_key": npc.map_key,
                "tile_x": npc.tile_x,
                "tile_y": npc.tile_y,
                "portrait": npc.portrait,
                "dialogue": npc.dialogue,
            }
            for npc in db.scalars(select(Npc))
        ],
        "quests": quests,
        "chapters": [
            {
                "id": chapter.id,
                "title": chapter.title,
                "order_index": chapter.order_index,
                "available": chapter_available(db, user, classroom_id, chapter.id),
            }
            for chapter in db.scalars(select(Chapter).order_by(Chapter.order_index))
            if (chapter.owner_id in {None, classroom.teacher_id} or user.role == "admin")
            and (chapter.owner_id is None or user.role != "student" or chapter_available(db, user, classroom_id, chapter.id))
        ],
        "flags": list(db.scalars(select(WorldFlag.key).where(WorldFlag.user_id == user.id, WorldFlag.classroom_id == classroom_id))),
    }


@router.get("/world")
def world(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    return world_payload(db, user, classroom_id)


class PositionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    map_key: Literal["town_hub", "library"]
    tile_x: int = Field(ge=0, le=49)
    tile_y: int = Field(ge=0, le=39)
    direction: Literal["up", "down", "left", "right"] = "down"


@router.put("/world/position")
def update_position(body: PositionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    authorize_classroom(db, user, body.classroom_id)
    if body.map_key == "library":
        if body.tile_x > 17 or body.tile_y > 13:
            raise HTTPException(422, "室内坐标越界")
        if not db.get(WorldFlag, (user.id, body.classroom_id, "library_unlocked")):
            raise HTTPException(403, "图书馆尚未开放")
    position = db.get(PlayerPosition, (user.id, body.classroom_id))
    if not position:
        position = PlayerPosition(user_id=user.id, classroom_id=body.classroom_id)
        db.add(position)
    for key in ("map_key", "tile_x", "tile_y", "direction"):
        setattr(position, key, getattr(body, key))
    db.commit()
    return {"map_key": position.map_key, "tile_x": position.tile_x, "tile_y": position.tile_y, "direction": position.direction}


class EventEvidence(BaseModel):
    model_config = ConfigDict(extra="forbid")
    npc_key: str | None = Field(default=None, max_length=80)
    dialogue_id: str | None = Field(default=None, max_length=100)
    choice: str | None = Field(default=None, max_length=80)
    object_id: str | None = Field(default=None, max_length=100)
    map_key: str | None = Field(default=None, max_length=40)
    tile_x: int | None = Field(default=None, ge=0, le=49)
    tile_y: int | None = Field(default=None, ge=0, le=39)
    record_id: str | None = Field(default=None, max_length=100)
    submission_id: str | None = Field(default=None, max_length=100)


class EventInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    event_id: str = Field(min_length=8, max_length=100)
    quest_id: str = Field(min_length=1, max_length=100)
    node_id: str = Field(min_length=1, max_length=100)
    evidence: dict = Field(default_factory=dict)

    @field_validator("evidence")
    @classmethod
    def validate_payload(cls, value):
        return EventEvidence.model_validate(value).model_dump(exclude_none=True)


def check_near(db, user, classroom_id, target):
    position = db.get(PlayerPosition, (user.id, classroom_id))
    if not position or position.map_key != target[0] or abs(position.tile_x - target[1]) + abs(position.tile_y - target[2]) > 5:
        raise HTTPException(422, "请先到达任务人物或目标附近")


def validate_evidence(db, user, classroom_id, node, evidence):
    kind = node["type"]
    target = node.get("target")
    if kind in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"}:
        npc = db.get(Npc, target)
        if evidence.get("npc_key") != target:
            raise HTTPException(422, "交谈对象不匹配")
        check_near(db, user, classroom_id, (npc.map_key, npc.tile_x, npc.tile_y))
        if kind == "PLAY_DIALOGUE":
            root = node.get("dialogue_id", target)
            if evidence.get("dialogue_id") != root or not db.get(WorldFlag, (user.id, classroom_id, f"dialogue:completed:{target}:{root}")):
                raise HTTPException(422, "请先完成指定脚本对白")
    elif kind in {"REACH_LOCATION", "INTERACT_OBJECT"}:
        if kind == "INTERACT_OBJECT" and evidence.get("object_id") != target:
            raise HTTPException(422, "互动对象不匹配")
        check_near(db, user, classroom_id, OBJECTS[target])
    elif kind == "STUDY_LESSON":
        record = db.get(LearningRecord, evidence.get("record_id", ""))
        if not record or (record.user_id, record.classroom_id, record.activity_id, record.version) != (
            user.id,
            classroom_id,
            node["activity_id"],
            node.get("activity_version", 1),
        ):
            raise HTTPException(422, "缺少当前活动的真实阅读记录")
    elif kind in {"COMPLETE_PRACTICE", "PASS_ASSESSMENT"}:
        from .courses import authorize_activity

        activity = authorize_activity(db, user, classroom_id, node["activity_id"], node.get("activity_version", 1))
        questions = [
            q
            for q in db.scalars(select(Question).where(Question.activity_id == node["activity_id"]))
            if activity.content.get("question_ids") is None or q.id in activity.content["question_ids"]
        ]
        if not questions:
            raise HTTPException(422, "题组为空")
        accepted = {
            s.question_id
            for s in db.scalars(
                select(Submission).where(
                    Submission.user_id == user.id,
                    Submission.classroom_id == classroom_id,
                    Submission.activity_id == node["activity_id"],
                    Submission.mode == "submit",
                    Submission.status.in_(["accepted", "AC", "ac"]),
                )
            ).all()
        }
        if any(q.id not in accepted for q in questions):
            raise HTTPException(422, "请先通过此活动的全部正式练习")
        if evidence.get("submission_id"):
            submission = db.get(Submission, evidence["submission_id"])
            if (
                not submission
                or submission.user_id != user.id
                or submission.classroom_id != classroom_id
                or submission.activity_id != node["activity_id"]
            ):
                raise HTTPException(422, "提交证据不匹配")
    choices = node.get("choices", [])
    if choices and evidence.get("choice") not in {c["id"] for c in choices}:
        raise HTTPException(422, "请选择有效的剧情分支")


@router.post("/events")
def event(body: EventInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    authorize_classroom(db, user, body.classroom_id)
    if user.role != "student":
        raise HTTPException(403, "教师预览不能写入学生成绩")
    existing = db.scalar(
        select(LearningEvent).where(
            LearningEvent.user_id == user.id, LearningEvent.classroom_id == body.classroom_id, LearningEvent.event_id == body.event_id
        )
    )
    if existing:
        original = db.get(PlayerQuest, existing.player_quest_id)
        if existing.node_id != body.node_id or original.quest_id != body.quest_id or existing.evidence != body.evidence:
            raise HTTPException(409, "幂等键已用于另一事件")
        return {"ok": True, "duplicate": True, "world": world_payload(db, user, body.classroom_id)}
    ensure_quests(db, user, body.classroom_id)
    instance = db.scalar(
        select(PlayerQuest)
        .where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == body.classroom_id, PlayerQuest.quest_id == body.quest_id)
        .with_for_update()
    )
    if not instance:
        raise HTTPException(404, "任务尚未开放")
    definition = db.get(QuestDefinition, (instance.quest_id, instance.quest_version))
    authorize_chapter(db, user, body.classroom_id, definition.chapter_id)
    states = {state.node_id: state for state in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))}
    node = next((n for n in definition.definition["nodes"] if n["id"] == body.node_id), None)
    if not node:
        raise HTTPException(404, "任务节点不存在")
    statuses = node_statuses(definition.definition, states)
    if statuses[body.node_id] == "complete":
        return {"ok": True, "duplicate": True, "world": world_payload(db, user, body.classroom_id)}
    if statuses[body.node_id] != "ready":
        raise HTTPException(409, "任务前置尚未完成或不属于所选分支")
    validate_evidence(db, user, body.classroom_id, node, body.evidence)
    state = NodeState(player_quest_id=instance.id, node_id=node["id"], status="complete", choice=body.evidence.get("choice"), evidence=body.evidence)
    db.add(state)
    states[state.node_id] = state
    db.add(
        LearningEvent(
            event_id=body.event_id,
            user_id=user.id,
            classroom_id=body.classroom_id,
            player_quest_id=instance.id,
            node_id=node["id"],
            content_version=instance.quest_version,
            evidence=body.evidence,
        )
    )
    statuses = node_statuses(definition.definition, states)
    required = [n for n in definition.definition["nodes"] if not n.get("optional", False)]
    if all(statuses[n["id"]] in {"complete", "skipped"} for n in required):
        instance.status = "complete"
        instance.completed_at = datetime.now(timezone.utc)
        for effect in definition.definition.get("effects", []):
            if not db.get(WorldFlag, (user.id, body.classroom_id, effect["key"])):
                db.add(WorldFlag(user_id=user.id, classroom_id=body.classroom_id, key=effect["key"]))
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        same = db.scalar(
            select(LearningEvent).where(
                LearningEvent.user_id == user.id, LearningEvent.classroom_id == body.classroom_id, LearningEvent.event_id == body.event_id
            )
        )
        if not same:
            raise HTTPException(409, "任务状态已变化，请刷新重试")
        return {"ok": True, "duplicate": True, "world": world_payload(db, user, body.classroom_id)}
    return {"ok": True, "duplicate": False, "world": world_payload(db, user, body.classroom_id)}


class DefinitionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    definition: dict


@router.post("/teacher/quests/validate")
def validate_quest(body: DefinitionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    try:
        definition = validate_definition(body.definition, db)
        from .curriculum import authoring_chapter

        authoring_chapter(db, user, definition["chapter_id"])
        for node in definition["nodes"]:
            if node.get("activity_id"):
                activity = db.get(Activity, node["activity_id"])
                authoring_chapter(db, user, activity.chapter_id)
                if activity.owner_id and activity.owner_id != user.id and user.role != "admin":
                    raise HTTPException(403, "任务不能绑定其他教师的活动")
        return {"valid": True, "definition": definition}
    except (ValueError, KeyError, TypeError) as exc:
        raise HTTPException(422, str(exc))


@router.post("/teacher/quests", status_code=201)
def create_quest(body: DefinitionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    try:
        definition = validate_definition(body.definition, db)
    except (ValueError, KeyError, TypeError) as exc:
        raise HTTPException(422, str(exc))
    from .curriculum import authoring_chapter

    authoring_chapter(db, user, definition["chapter_id"])
    if db.get(QuestDefinition, (definition["id"], definition["version"])):
        raise HTTPException(409, "已发布版本不可覆盖，请使用新版本号")
    for node in definition["nodes"]:
        if node.get("activity_id"):
            activity = db.get(Activity, node["activity_id"])
            authoring_chapter(db, user, activity.chapter_id)
            if activity.owner_id and activity.owner_id != user.id and user.role != "admin":
                raise HTTPException(403, "任务不能绑定其他教师的活动")
    db.add(
        QuestDefinition(
            id=definition["id"],
            version=definition["version"],
            chapter_id=definition["chapter_id"],
            title=definition["title"],
            owner_id=user.id,
            definition=definition,
        )
    )
    db.commit()
    return definition


@router.get("/teacher/quests")
def list_quests(db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    return [
        q.definition
        for q in db.scalars(
            select(QuestDefinition)
            .where((QuestDefinition.owner_id.is_(None)) | (QuestDefinition.owner_id == user.id))
            .order_by(QuestDefinition.id, QuestDefinition.version)
        )
    ]
