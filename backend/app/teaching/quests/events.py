"""Apply verified task events and store idempotent event receipts."""

from datetime import datetime, timezone

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.course_content.access import authorize_chapter, authorize_classroom
from app.models import LearningEvent, NodeState, PlayerQuest, StoryDefinition, User, WorldFlag
from app.teaching.quests.access import authorize_quest
from app.teaching.quests.contracts import EventInput
from app.teaching.quests.evidence import validate_evidence
from app.teaching.quests.graph import node_statuses
from app.teaching.quests.state import ensure_quests, world_payload


def apply_quest_event(db, user, body: EventInput):
    """Validate and stage progress in the caller's transaction; return whether duplicated."""
    authorize_classroom(db, user, body.classroom_id)
    if user.role != "student":
        raise HTTPException(403, "教师预览不能写入学生成绩")
    definition = authorize_quest(db, user, body.classroom_id, body.quest_id)
    existing = db.scalar(
        select(LearningEvent).where(
            LearningEvent.user_id == user.id, LearningEvent.classroom_id == body.classroom_id, LearningEvent.event_id == body.event_id
        )
    )
    if existing:
        original = db.get(PlayerQuest, existing.player_quest_id)
        if existing.node_id != body.node_id or original.quest_id != body.quest_id or existing.evidence != body.evidence:
            raise HTTPException(409, "幂等键已用于另一事件")
        return True
    instance = db.scalar(
        select(PlayerQuest)
        .where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == body.classroom_id, PlayerQuest.quest_id == body.quest_id)
        .with_for_update()
    )
    if not instance:
        raise HTTPException(404, "任务尚未开放")
    authorize_chapter(db, user, body.classroom_id, definition.chapter_id)
    states = {state.node_id: state for state in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))}
    node = next((n for n in definition.definition["nodes"] if n["id"] == body.node_id), None)
    if not node:
        raise HTTPException(404, "任务节点不存在")
    statuses = node_statuses(definition.definition, states)
    if statuses[body.node_id] == "complete":
        return True
    if statuses[body.node_id] != "ready":
        raise HTTPException(409, "任务前置尚未完成或不属于所选分支")
    if node["type"] in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"}:
        marker = body.evidence.get("dialogue_id")
        stories = db.scalars(
            select(StoryDefinition).where(
                StoryDefinition.quest_id == instance.quest_id, StoryDefinition.course_instance_id == definition.course_instance_id
            )
        )
        verified = any(
            marker == f"story:{story.id}:{node['id']}" and any(item.get("checkpoint") == node["id"] for item in story.content["nodes"])
            for story in stories
        )
        if not verified or not db.get(WorldFlag, (user.id, body.classroom_id, f"dialogue:completed:{node['target']}:{marker}")):
            raise HTTPException(422, "请先确认当前版本的指定流程对白")
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
            evidence=body.evidence,
        )
    )
    statuses = node_statuses(definition.definition, states)
    required = [n for n in definition.definition["nodes"] if not n.get("optional", False)]
    if all(statuses[n["id"]] in {"complete", "skipped"} for n in required):
        instance.status = "complete"
        instance.completed_at = datetime.now(timezone.utc)
        for effect in definition.definition.get("effects", []):
            if effect["type"] == "unlock_building" or effect.get("key") == "library_unlocked":
                from app.teaching.world.state import unlock_building

                unlock_building(db, user, body.classroom_id, effect.get("building_id", "library"))
                continue
            if not db.get(WorldFlag, (user.id, body.classroom_id, effect["key"])):
                db.add(WorldFlag(user_id=user.id, classroom_id=body.classroom_id, key=effect["key"]))
    return False


def event(body: EventInput, db: Session, user: User):
    authorize_classroom(db, user, body.classroom_id)
    if user.role != "student":
        raise HTTPException(403, "教师预览不能写入学生成绩")
    ensure_quests(db, user, body.classroom_id)
    duplicate = apply_quest_event(db, user, body)
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
    return {"ok": True, "duplicate": duplicate, "world": world_payload(db, user, body.classroom_id)}
