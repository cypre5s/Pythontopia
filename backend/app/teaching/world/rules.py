"""Pure world geometry, interaction anchors and completion predicates."""

import json
from collections import deque
from functools import lru_cache

from app.config import ROOT
from app.world_config import interaction_points, load_world_config, validate_point


def interior_buildings():
    return {b["id"]: b for area in load_world_config()["maps"].values() for b in area["buildings"] if b.get("portal_id")}


def completion_predicate(condition):
    """Every successful path must require positive, server-verified completion."""
    key, value = next(iter(condition.items()))
    if key == "all":
        return any(completion_predicate(child) for child in value)
    if key == "any":
        return all(completion_predicate(child) for child in value)
    return key == "activity_completed" or key in {"chapter", "quest", "goal"} and value.get("status") == "complete" or key == "goal_complete"


@lru_cache(maxsize=128)
def reachable_cells(map_key):
    config = load_world_config()
    area = config["maps"][map_key]
    path = ROOT / "frontend" / "public" / area["asset"].lstrip("/")
    raw = json.loads(path.read_text(encoding="utf-8"))
    collision = next((layer["data"] for layer in raw["layers"] if layer["name"] == "collision"), [])
    if len(collision) != area["width"] * area["height"]:
        raise ValueError("地图碰撞资源不完整")
    spawn = (
        config["default_spawn"]
        if config["default_spawn"]["map_key"] == map_key
        else next(p["to"] for p in config["portals"] if p["to"]["map_key"] == map_key)
    )
    visited, queue = set(), deque([(spawn["tile_x"], spawn["tile_y"])])
    while queue:
        x, y = queue.popleft()
        if (x, y) in visited:
            continue
        try:
            validate_point(map_key, x, y)
        except ValueError:
            continue
        if collision[y * area["width"] + x]:
            continue
        visited.add((x, y))
        queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    return frozenset(visited)


def interaction_points_for(item):
    target = item["target"]
    if "object_id" in target:
        points = interaction_points().get(target["object_id"])
        if points is None:
            raise ValueError("交互对象不存在")
        points = [points] if isinstance(points[0], str) else points
        return [{"map_key": m, "tile_x": x, "tile_y": y} for m, x, y in points if m == item["map_key"]]
    region = target["region"]
    return [
        {"map_key": item["map_key"], "tile_x": x, "tile_y": y}
        for x, y in sorted(reachable_cells(item["map_key"]))
        if region["x"] <= x < region["x"] + region["width"] and region["y"] <= y < region["y"] + region["height"]
    ]
