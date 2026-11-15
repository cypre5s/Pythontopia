"""Enforce player proximity to configured interaction points."""

from fastapi import HTTPException

from app.models import PlayerPosition
from app.world_config import load_world_config, validate_point


def check_near(db, user, classroom_id, target, radius=5):
    position = db.get(PlayerPosition, (user.id, classroom_id))
    points = [target] if isinstance(target[0], str) else target
    try:
        for point in points:
            validate_point(*point)
    except ValueError as exc:
        raise HTTPException(409, "任务目标位置与地图配置不一致") from exc
    current = (
        (position.map_key, position.tile_x, position.tile_y)
        if position
        else tuple(load_world_config()["default_spawn"][key] for key in ("map_key", "tile_x", "tile_y"))
    )
    if not any(current[0] == point[0] and abs(current[1] - point[1]) + abs(current[2] - point[2]) <= radius for point in points):
        raise HTTPException(422, "请先到达任务人物或目标附近")
