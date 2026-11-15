"""Execute authorized story commands with per-student locking and receipts."""

import hashlib
import json

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.course_content.access import authorize_classroom, chapter_available
from app.course_scope import belongs_to_course, course_clause, require_course_content
from app.models import LearningRecord, Npc, PlayerPosition, QuestDefinition, StoryDefinition, StorySession, User, WorldFlag
from app.teaching.conditions.runtime import condition_matches
from app.teaching.quests.access import quest_visible
from app.teaching.quests.contracts import EventInput
from app.teaching.quests.events import apply_quest_event
from app.teaching.quests.evidence import validate_evidence
from app.teaching.quests.state import ensure_quests
from app.teaching.story.contracts import AdvanceInput, InteractionInput, ReturnInput
from app.teaching.story.runtime import available_choices, select_flow, session_response, settle_session
from app.teaching.story.state import (
    available_activity,
    lock_student,
    open_session,
    quest_states,
    require_session_proximity,
    session_definition,
    story_nodes,
)
from app.teaching.world.proximity import check_near


def interact(body: InteractionInput, db: Session, user: User):
    classroom = authorize_classroom(db, user, body.classroom_id)
    lock_student(db, user, body.classroom_id)
    from app.teaching.world.runtime import npc_present, require_interaction

    if body.interaction_id:
        require_interaction(db, user, body.classroom_id, body.interaction_id)
    else:
        npc = db.get(Npc, body.npc_key)
        if not npc:
            raise HTTPException(404, "NPC不存在")
        require_course_content(db, npc, body.classroom_id)
        if not npc_present(db, user, body.classroom_id, npc):
            raise HTTPException(403, "当前NPC尚未出现")
        check_near(db, user, body.classroom_id, (npc.map_key, npc.tile_x, npc.tile_y))
    entry = f"interaction:{body.interaction_id}" if body.interaction_id else body.npc_key
    previous = open_session(db, user, body.classroom_id)
    abandoned = False
    if previous:
        previous_story = db.get(StoryDefinition, previous.story_id)
        previous_quest = db.get(QuestDefinition, previous_story.quest_id) if previous_story and previous_story.quest_id is not None else None
        if (
            not belongs_to_course(db, previous_story, body.classroom_id)
            or previous_story.quest_id is not None
            and not quest_visible(previous_quest, user, classroom, db)
            or (previous_story.chapter_id is not None and not chapter_available(db, user, body.classroom_id, previous_story.chapter_id))
        ):
            previous.status = "ended"
            previous.revision += 1
            previous.pending_activity_id = None
            previous.return_mapping = {}
            db.flush()
            previous = None
            abandoned = True
    if previous:
        if previous.npc_key == body.npc_key and previous.interaction_id == body.interaction_id:
            return session_response(db, user, previous)
        raise HTTPException(409, "请先结束当前流程，再打开另一交互")
    targets = {body.npc_key} if body.npc_key else {body.interaction_id}
    if body.interaction_id:
        object_id = require_interaction(db, user, body.classroom_id, body.interaction_id)["target"].get("object_id")
        if object_id:
            targets.add(object_id)
    selected = select_flow(
        db,
        user,
        body.classroom_id,
        entry,
        db.scalars(select(StoryDefinition).where(course_clause(db, StoryDefinition, body.classroom_id))),
        targets=targets,
    )
    if not selected:
        if abandoned:
            db.commit()
        raise HTTPException(404, "此NPC尚未绑定当前演出")
    story, instance = selected
    if story.quest_id is not None:
        ensure_quests(db, user, body.classroom_id, commit=False)
        lock_student(db, user, body.classroom_id)
    root = next(route["node"] for route in story.content["entries"][entry] if condition_matches(db, user, body.classroom_id, story, route["when"]))
    session = StorySession(
        user_id=user.id,
        classroom_id=body.classroom_id,
        story_id=story.id,
        npc_key=body.npc_key,
        interaction_id=body.interaction_id,
        node_id=root,
        status="active",
        revision=0,
        receipts=[],
    )
    # Appearance/entry conditions may change as this flow supplies real evidence.
    # Retain the already-authorized physical interaction anchor while its current
    # chapter and course are reauthorized on every subsequent operation.
    from app.world_config import load_world_config

    position = db.get(PlayerPosition, (user.id, body.classroom_id))
    player = (
        {key: getattr(position, key) for key in ("map_key", "tile_x", "tile_y")}
        if position
        else {key: load_world_config()["default_spawn"][key] for key in ("map_key", "tile_x", "tile_y")}
    )
    if body.interaction_id:
        item = require_interaction(db, user, body.classroom_id, body.interaction_id)
        point = min(
            (point for point in item["points"] if point["map_key"] == player["map_key"]),
            key=lambda point: abs(point["tile_x"] - player["tile_x"]) + abs(point["tile_y"] - player["tile_y"]),
        )
        session.anchor = {**point, "radius": item["radius"]}
    else:
        session.anchor = {"map_key": npc.map_key, "tile_x": npc.tile_x, "tile_y": npc.tile_y, "radius": 5}
    db.add(session)
    db.flush()
    if body.interaction_id and story.content.get("mode", "task") != "ambient":
        from app.teaching.quests.interactions import record_interaction

        record_interaction(db, user, body.classroom_id, body.interaction_id, session)
    settle_session(db, user, session, story)
    db.commit()
    return session_response(db, user, session)


def current(classroom_id: int, db: Session, user: User):
    lock_student(db, user, classroom_id)
    session = open_session(db, user, classroom_id)
    return session_response(db, user, session) if session else None


def end_session(session_id: str, body: ReturnInput, db: Session, user: User):
    session, signature, duplicate = _request(db, user, session_id, body, "end")
    if not duplicate:
        session.status = "ended"
        session.pending_activity_id = None
        session.return_mapping = {}
        _receipt(session, body, signature)
        db.commit()
    return session_response(db, user, session)


def _request(db, user, session_id, body, kind):
    lock_student(db, user, body.classroom_id)
    session = db.get(StorySession, session_id)
    if not session or (session.user_id, session.classroom_id) != (user.id, body.classroom_id):
        raise HTTPException(404, "演出会话不存在")
    signature = hashlib.sha256(json.dumps({"kind": kind, **body.model_dump(exclude={"event_id"})}, sort_keys=True).encode()).hexdigest()
    previous = next((receipt for receipt in session.receipts if receipt["id"] == body.event_id), None)
    if previous:
        if previous["signature"] != signature:
            raise HTTPException(409, "幂等键已用于另一演出操作")
        return session, signature, True
    if session.revision != body.revision:
        raise HTTPException(409, "演出进度已变化，请恢复当前会话")
    if session.status == "ended":
        raise HTTPException(409, "演出会话已结束")
    if kind != "end":
        require_session_proximity(db, user, session)
    return session, signature, False


def _receipt(session, body, signature):
    session.revision += 1
    session.receipts = [*session.receipts[-31:], {"id": body.event_id, "signature": signature}]


def _goal_event(db, user, session, story, goal_id, evidence, event_id):
    child_id = hashlib.sha256(f"{session.id}:{event_id}:{goal_id}".encode()).hexdigest()
    apply_quest_event(
        db, user, EventInput(classroom_id=session.classroom_id, event_id=child_id, quest_id=story.quest_id, node_id=goal_id, evidence=evidence)
    )


def advance(session_id: str, body: AdvanceInput, db: Session, user: User):
    session, signature, duplicate = _request(db, user, session_id, body, "advance")
    if duplicate:
        return session_response(db, user, session)
    story, instance = session_definition(db, user, session)
    if session.status != "active":
        raise HTTPException(409, "请先从当前学习活动返回")
    node = story_nodes(story)[session.node_id]
    if node["type"] == "dialogue":
        rounds = node["text"].split("\n\n")
        round_index = session.return_mapping.get("dialogue_round", 0)
        if round_index < len(rounds) - 1:
            if body.choice:
                raise HTTPException(422, "请先读完当前对白")
            session.return_mapping = {"dialogue_round": round_index + 1}
            _receipt(session, body, signature)
            db.commit()
            return session_response(db, user, session)
    session.return_mapping = {}
    if node["type"] == "action" and node["action"] == "enter_building":
        from app.teaching.world.runtime import enter_building

        session.anchor = enter_building(db, user, session.classroom_id, node["building_id"])
    choices = available_choices(db, user, session, story, node)
    selected = next((choice for choice in choices if choice["id"] == body.choice), None)
    if choices and not selected or body.choice and not choices:
        raise HTTPException(422, "请选择当前对白选项")
    checkpoint = node.get("checkpoint")
    if checkpoint:
        if instance is None:
            raise HTTPException(409, "这段对白的任务目标已失效，请结束本次互动后联系老师")
        definition, _ = quest_states(db, instance)
        goal = next((item for item in definition["nodes"] if item["id"] == checkpoint), None)
        if goal is None or goal.get("target") != session.npc_key:
            raise HTTPException(409, "对白检查点与NPC不匹配")
        marker = f"story:{story.id}:{checkpoint}"
        if goal["type"] == "PLAY_DIALOGUE" and goal.get("dialogue_id", session.npc_key) != marker:
            raise HTTPException(409, "对白检查点标记不匹配")
        flag = f"dialogue:completed:{session.npc_key}:{marker}"
        if not db.get(WorldFlag, (user.id, session.classroom_id, flag)):
            db.add(WorldFlag(user_id=user.id, classroom_id=session.classroom_id, key=flag))
            db.flush()
        evidence = {"npc_key": session.npc_key, "dialogue_id": marker}
        if selected:
            evidence["choice"] = selected["id"]
        _goal_event(db, user, session, story, checkpoint, evidence, body.event_id)
        for effect in story.content.get("checkpoint_effects", {}).get(checkpoint, []):
            from app.teaching.world.state import unlock_building

            unlock_building(db, user, session.classroom_id, effect["building_id"])
    following = selected["next"] if selected else node.get("next")
    if following:
        session.node_id = following
        settle_session(db, user, session, story)
    else:
        session.status = "ended"
    _receipt(session, body, signature)
    db.commit()
    return session_response(db, user, session)


def return_activity(session_id: str, body: ReturnInput, db: Session, user: User):
    session, signature, duplicate = _request(db, user, session_id, body, "return")
    if duplicate:
        return session_response(db, user, session)
    story, instance = session_definition(db, user, session)
    if session.status != "waiting":
        raise HTTPException(409, "当前没有待返回的学习活动")
    node = story_nodes(story)[session.node_id]
    binding = story.content["bindings"][node["binding"]]
    activity = available_activity(db, user, session, binding)
    outcome, evidence = "suspended", {}
    definition, statuses = quest_states(db, instance) if instance else ({"nodes": []}, {})
    goal = next((item for item in definition["nodes"] if item["id"] == binding.get("goal_id")), None)
    if not activity:
        outcome = "unavailable"
    elif instance is None:
        from app.teaching.conditions.runtime import activity_completed

        outcome = "completed" if activity_completed(db, user, session.classroom_id, activity.id) else "suspended"
    elif statuses[goal["id"]] == "complete":
        outcome = "completed"
    elif statuses[goal["id"]] == "ready":
        if activity.kind in {"lesson", "reading"}:
            record = db.scalar(
                select(LearningRecord)
                .where(
                    LearningRecord.user_id == user.id, LearningRecord.classroom_id == session.classroom_id, LearningRecord.activity_id == activity.id
                )
                .order_by(LearningRecord.created_at.desc())
            )
            evidence = {"record_id": record.id} if record else {}
        try:
            validate_evidence(db, user, session.classroom_id, goal, evidence)
        except HTTPException as exc:
            if exc.status_code != 422:
                raise
        else:
            _goal_event(db, user, session, story, goal["id"], evidence, body.event_id)
            outcome = "completed"
    session.node_id = session.return_mapping[outcome]
    session.pending_activity_id = None
    session.return_mapping = {}
    settle_session(db, user, session, story)
    _receipt(session, body, signature)
    db.commit()
    return session_response(db, user, session)
