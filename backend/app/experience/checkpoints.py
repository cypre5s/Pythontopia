"""Save and restore version-checked learner experience checkpoints."""

import json
from datetime import datetime

from fastapi import HTTPException, Request
from sqlalchemy import delete, func, select

from app.experience.access import guard
from app.experience.contracts import Actor, Db, RestoreInput, SaveInput
from app.experience.stages import end_stories
from app.experience.state import STATE_FIELDS, definition_hash, own_rows, snapshot, state_token
from app.models import ExperienceCheckpoint, NodeState, PlayerPosition, PlayerQuest, StorySession
from app.world_config import validate_point


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
    count = db.scalar(
        select(func.count())
        .select_from(ExperienceCheckpoint)
        .where(ExperienceCheckpoint.user_id == user.id, ExperienceCheckpoint.classroom_id == body.classroom_id)
    )
    if count >= 20:
        raise HTTPException(409, "每班最多保存 20 个检查点，请先删除不需要的记录")
    row = ExperienceCheckpoint(
        user_id=user.id,
        classroom_id=body.classroom_id,
        name=body.name.strip(),
        definition_hash=definition_hash(db, body.classroom_id),
        snapshot={**snapshot(db, user, body.classroom_id), "client_state": body.client_state},
    )
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


def preview_restore(checkpoint_id: str, classroom_id: int, request: Request, db: Db, user: Actor):
    guard(db, user, classroom_id, request)
    row = checkpoint(db, user, classroom_id, checkpoint_id)
    if row.definition_hash != definition_hash(db, classroom_id):
        raise HTTPException(409, "课程定义或发布状态已变化，此检查点已失效")
    return {"expected": state_token(db, user, classroom_id), "name": row.name, "quests": len(row.snapshot["quests"])}


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


def delete_checkpoint(checkpoint_id: str, classroom_id: int, request: Request, db: Db, user: Actor):
    guard(db, user, classroom_id, request, mutate=True)
    db.delete(checkpoint(db, user, classroom_id, checkpoint_id))
    db.commit()
    return {"ok": True}
