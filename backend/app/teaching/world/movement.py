"""Validate and persist player movement and portal transitions."""

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.course_content.access import authorize_classroom
from app.models import PlayerPosition, User
from app.teaching.quests.state import world_payload
from app.teaching.world.contracts import PositionInput, TransitionInput
from app.world_config import load_world_config


def update_position(body: PositionInput, db: Session, user: User):
    authorize_classroom(db, user, body.classroom_id)
    db.execute(select(User.id).where(User.id == user.id).with_for_update())
    position = db.get(PlayerPosition, (user.id, body.classroom_id))
    current_map = position.map_key if position else load_world_config()["default_spawn"]["map_key"]
    if body.map_key != current_map:
        raise HTTPException(409, "跨地图移动必须通过对应入口")
    if not position:
        position = PlayerPosition(user_id=user.id, classroom_id=body.classroom_id)
        db.add(position)
    for key in ("map_key", "tile_x", "tile_y", "direction"):
        setattr(position, key, getattr(body, key))
    db.commit()
    return {"map_key": position.map_key, "tile_x": position.tile_x, "tile_y": position.tile_y, "direction": position.direction}


def transition(body: TransitionInput, db: Session, user: User):
    authorize_classroom(db, user, body.classroom_id)
    db.execute(select(User.id).where(User.id == user.id).with_for_update())
    config = load_world_config()
    portal = next((item for item in config["portals"] if item["id"] == body.portal_id), None)
    if not portal:
        raise HTTPException(404, "地图入口不存在")
    position = db.get(PlayerPosition, (user.id, body.classroom_id))
    current = {"map_key": position.map_key, "tile_x": position.tile_x, "tile_y": position.tile_y} if position else config["default_spawn"]
    source = portal["from"]
    if (
        current["map_key"] != source["map_key"]
        or abs(current["tile_x"] - source["tile_x"]) + abs(current["tile_y"] - source["tile_y"]) > source["radius"]
    ):
        raise HTTPException(422, "请先到达对应地图入口附近")
    if portal.get("building_id"):
        from app.teaching.world.runtime import active_interactions, building_access

        if any(item["target"].get("object_id") in {portal["id"], portal["building_id"]} for item in active_interactions(db, user, body.classroom_id)):
            raise HTTPException(409, "请先完成建筑入口交互")
        access = building_access(db, user, body.classroom_id)[portal["building_id"]]
        if not access["can_enter"]:
            raise HTTPException(403, access["blocked_message"])
    if not position:
        position = PlayerPosition(user_id=user.id, classroom_id=body.classroom_id)
        db.add(position)
    for key, value in portal["to"].items():
        setattr(position, key, value)
    db.commit()
    payload = world_payload(db, user, body.classroom_id)
    return {"player": payload["player"], "world": payload}
