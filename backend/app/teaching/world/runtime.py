"""Evaluate course world availability and authorized interaction targets."""

from copy import deepcopy
from types import SimpleNamespace

from fastapi import HTTPException

from app.course_instances import classroom_course, content_mapping, remap_references
from app.models import PlayerPosition
from app.teaching.world.rules import interaction_points_for, interior_buildings
from app.teaching.world.state import configuration, unlocked_buildings
from app.world_config import interaction_points, load_world_config


def world_condition_matches(db, user, classroom_id, value):
    from app.teaching.conditions.runtime import condition_matches

    instance = classroom_course(db, classroom_id)
    maps = content_mapping(db, instance) if instance else None
    proxy = SimpleNamespace(course_instance_id=instance.id if instance else None, quest_id=None, content={"bindings": {}})
    try:
        return condition_matches(db, user, classroom_id, proxy, remap_references(value, maps) if maps else value)
    except (ValueError, KeyError):
        return False


def building_access(db, user, classroom_id):
    policies = configuration(db, classroom_id).get("buildings", {})
    unlocked = unlocked_buildings(db, user, classroom_id)
    result = {}
    for key in interior_buildings():
        policy = policies.get(
            key, {"enabled": classroom_course(db, classroom_id) is None, "default_unlocked": False, "blocked_message": "此课程尚未开放该建筑。"}
        )
        granted = (
            policy["default_unlocked"] or key in unlocked or "when" in policy and world_condition_matches(db, user, classroom_id, policy["when"])
        )
        result[key] = {
            "enabled": policy["enabled"],
            "unlocked": bool(granted),
            "can_enter": bool(policy["enabled"] and granted),
            "blocked_message": policy["blocked_message"],
        }
    return result


def npc_present(db, user, classroom_id, npc):
    return npc.placement is not None and world_condition_matches(db, user, classroom_id, npc.appearance_condition)


def active_interactions(db, user, classroom_id):
    result = []
    for item in configuration(db, classroom_id).get("world", {}).get("interactions", []):
        if world_condition_matches(db, user, classroom_id, item.get("when", {"always": True})):
            result.append({key: deepcopy(item[key]) for key in ("id", "label", "prompt", "trigger", "map_key", "target")})
            result[-1].update(points=interaction_points_for(item), radius=0 if item["trigger"] == "enter" else 2)
    return result


def require_interaction(db, user, classroom_id, interaction_id, *, near=True):
    from app.teaching.world.proximity import check_near

    item = next((item for item in active_interactions(db, user, classroom_id) if item["id"] == interaction_id), None)
    if not item:
        raise HTTPException(404, "当前地图交互不可用")
    if near:
        check_near(db, user, classroom_id, [(p["map_key"], p["tile_x"], p["tile_y"]) for p in item["points"]], radius=item["radius"])
    return item


def course_object_points(db, user, classroom_id, target):
    configured = [
        row
        for row in configuration(db, classroom_id).get("world", {}).get("interactions", [])
        if row["id"] == target or row["target"].get("object_id") == target
    ]
    item = next((row for row in active_interactions(db, user, classroom_id) if row["id"] == target or row["target"].get("object_id") == target), None)
    if item:
        return [(p["map_key"], p["tile_x"], p["tile_y"]) for p in item["points"]]
    if configured:
        return None
    return interaction_points().get(target)


def enter_building(db, user, classroom_id, building_id):
    from app.teaching.world.proximity import check_near

    access = building_access(db, user, classroom_id).get(building_id)
    if not access or not access["can_enter"]:
        raise HTTPException(403, access["blocked_message"] if access else "建筑不可进入")
    portal = next(p for p in load_world_config()["portals"] if p.get("building_id") == building_id)
    source = portal["from"]
    check_near(db, user, classroom_id, (source["map_key"], source["tile_x"], source["tile_y"]), radius=source["radius"])
    position = db.get(PlayerPosition, (user.id, classroom_id))
    if not position:
        position = PlayerPosition(user_id=user.id, classroom_id=classroom_id)
        db.add(position)
    for key, value in portal["to"].items():
        setattr(position, key, value)
    return {key: portal["to"][key] for key in ("map_key", "tile_x", "tile_y")}
