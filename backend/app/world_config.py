"""Shared map geometry, interaction anchors and server-enforced portals."""

import json
from functools import lru_cache

from .config import ROOT

DIRECTIONS = {"up", "down", "left", "right"}
PORTRAITS = frozenset(f"/assets/pixel/portraits/galgame/{name}.png" for name in (
    "arena_guard", "class_president", "coach_q", "foreman_bug", "librarian_doc", "mayor_py",
    "mine_keeper", "professor", "sage_review", "student_a", "workshop_master",
))


def valid_portrait(value):
    return isinstance(value, str) and (value == "" or value in PORTRAITS)


def _string(value, limit=100):
    return isinstance(value, str) and 0 < len(value) <= limit and bool(value.strip())


def validate_point(map_key, tile_x, tile_y, config=None):
    config = config if config is not None else load_world_config()
    if not isinstance(map_key, str) or map_key not in config["maps"]:
        raise ValueError("地图不存在")
    area = config["maps"][map_key]
    if type(tile_x) is not int or type(tile_y) is not int or not (0 <= tile_x < area["width"] and 0 <= tile_y < area["height"]):
        raise ValueError("位置超出地图边界")
    walk = area.get("walk_bounds", {"x": 0, "y": 0, "width": area["width"], "height": area["height"]})
    if not (walk["x"] <= tile_x < walk["x"] + walk["width"] and walk["y"] <= tile_y < walk["y"] + walk["height"]):
        raise ValueError("位置超出可行走区域")
    return map_key, tile_x, tile_y


def _position(value, config, source=False):
    fields = {"map_key", "tile_x", "tile_y", "radius" if source else "direction"}
    if not isinstance(value, dict) or set(value) != fields:
        raise ValueError("地图位置字段无效")
    validate_point(value["map_key"], value["tile_x"], value["tile_y"], config)
    if source:
        if type(value["radius"]) is not int or not 0 <= value["radius"] <= 10:
            raise ValueError("传送入口距离无效")
    elif not isinstance(value["direction"], str) or value["direction"] not in DIRECTIONS:
        raise ValueError("地图朝向无效")


def interaction_anchors(interaction):
    return interaction.get("points", [{key: interaction.get(key) for key in ("map_key", "tile_x", "tile_y")}])


def validate_world_config(config):
    required = {"schema_version", "default_spawn", "maps", "interactions", "portals"}
    if not isinstance(config, dict) or required - set(config) or set(config) - required - {"portal_marker"}:
        raise ValueError("地图配置字段无效")
    if type(config["schema_version"]) is not int or config["schema_version"] != 1:
        raise ValueError("地图配置版本不受支持")
    maps, interactions, portals = config["maps"], config["interactions"], config["portals"]
    if not isinstance(maps, dict) or not 1 <= len(maps) <= 128:
        raise ValueError("地图配置需要有界地图列表")
    buildings, building_maps = {}, {}
    for map_key, area in maps.items():
        if not _string(map_key, 40) or not isinstance(area, dict) or set(area) - {"label", "width", "height", "renderer", "asset", "buildings", "walk_bounds", "render_bounds", "camera_mode"}:
            raise ValueError("无效地图定义")
        if not _string(area.get("label")) or area.get("renderer") != "tiled":
            raise ValueError("无效地图名称或渲染方式")
        if any(type(area.get(axis)) is not int or not 1 <= area[axis] <= 4096 for axis in ("width", "height")):
            raise ValueError("地图尺寸无效")
        for field in ("walk_bounds", "render_bounds"):
            bounds = area.get(field)
            if bounds is None:
                continue
            if not isinstance(bounds, dict) or set(bounds) != {"x", "y", "width", "height"} or any(type(value) is not int for value in bounds.values()):
                raise ValueError("地图可行走或渲染边界字段无效")
            if not 1 <= bounds["width"] <= 8192 or not 1 <= bounds["height"] <= 8192 or abs(bounds["x"]) > 4096 or abs(bounds["y"]) > 4096:
                raise ValueError("地图可行走或渲染边界尺寸无效")
            if field == "walk_bounds" and (bounds["x"] < 0 or bounds["y"] < 0 or bounds["x"] + bounds["width"] > area["width"] or bounds["y"] + bounds["height"] > area["height"]):
                raise ValueError("可行走区域必须位于逻辑地图内")
            if field == "render_bounds" and (bounds["x"] > 0 or bounds["y"] > 0 or bounds["x"] + bounds["width"] < area["width"] or bounds["y"] + bounds["height"] < area["height"]):
                raise ValueError("渲染边界必须覆盖逻辑地图")
        if "camera_mode" in area and (not isinstance(area["camera_mode"], str) or area["camera_mode"] not in {"centered", "bounded"}):
            raise ValueError("镜头模式无效")
        if "asset" in area and (not isinstance(area["asset"], str) or not area["asset"].startswith("/assets/maps/")
                                or ".." in area["asset"] or not area["asset"].endswith(".json")):
            raise ValueError("地图资源必须引用本地地图文件")
        if not area.get("asset"):
            raise ValueError("Tiled地图缺少资源文件")
        items = area.get("buildings")
        if not isinstance(items, list) or len(items) > 200:
            raise ValueError("无效建筑列表")
        for building in items:
            if not isinstance(building, dict) or set(building) - {"id", "object_name", "label", "purpose", "status", "anchor", "portal_id"}:
                raise ValueError("无效建筑定义")
            if any(not _string(building.get(key), 200) for key in ("id", "object_name", "label", "purpose")):
                raise ValueError("建筑标识或说明无效")
            if building["id"] in buildings or not isinstance(building.get("status"), str) or building["status"] not in {"open", "landmark", "closed"}:
                raise ValueError("建筑ID重复或状态无效")
            anchor = building.get("anchor")
            if not isinstance(anchor, dict) or set(anchor) != {"x", "y"}:
                raise ValueError("建筑锚点无效")
            validate_point(map_key, anchor["x"], anchor["y"], config)
            if building["status"] == "open" and not building.get("portal_id"):
                raise ValueError("开放建筑必须配置传送入口")
            buildings[building["id"]] = building
            building_maps[building["id"]] = map_key
    _position(config["default_spawn"], config)
    if not isinstance(interactions, dict) or len(interactions) > 500:
        raise ValueError("交互锚点列表无效")
    for key, anchor in interactions.items():
        if not _string(key) or not isinstance(anchor, dict) or set(anchor) - {"map_key", "tile_x", "tile_y", "label", "kind", "building_id", "points"}:
            raise ValueError("交互锚点字段无效")
        if not _string(anchor.get("label")) or not isinstance(anchor.get("kind"), str) or anchor["kind"] not in {"object", "building"}:
            raise ValueError("交互锚点名称或类型无效")
        points = interaction_anchors(anchor)
        if "points" in anchor and set(anchor) & {"map_key", "tile_x", "tile_y"}:
            raise ValueError("多点交互不能重复配置单点位置")
        if not isinstance(points, list) or not 1 <= len(points) <= 64:
            raise ValueError("交互锚点必须包含1至64个真实位置")
        unique = set()
        for point in points:
            if not isinstance(point, dict) or set(point) != {"map_key", "tile_x", "tile_y"}:
                raise ValueError("交互锚点位置字段无效")
            value = validate_point(point["map_key"], point["tile_x"], point["tile_y"], config)
            if value in unique:
                raise ValueError("交互锚点位置重复")
            unique.add(value)
        if key in buildings:
            building = buildings[key]
            if unique != {(building_maps[key], building["anchor"]["x"], building["anchor"]["y"])}:
                raise ValueError("建筑与同名交互锚点不一致")
        if "building_id" in anchor and (not _string(anchor["building_id"]) or anchor["building_id"] not in buildings):
            raise ValueError("交互锚点引用了不存在的建筑")
    if not isinstance(portals, list) or len(portals) > 200:
        raise ValueError("传送入口列表无效")
    portal_ids = set()
    for portal in portals:
        fields = {"id", "label", "action_label", "from", "to", "required_flags", "blocked_message", "building_id"}
        if not isinstance(portal, dict) or set(portal) - fields:
            raise ValueError("传送入口字段无效")
        if any(not _string(portal.get(key), 200) for key in ("id", "label", "action_label")):
            raise ValueError("传送入口标识或说明无效")
        if not isinstance(portal.get("blocked_message"), str) or len(portal["blocked_message"]) > 500:
            raise ValueError("传送入口阻止说明无效")
        if portal["id"] in portal_ids:
            raise ValueError("传送入口ID重复")
        portal_ids.add(portal["id"])
        _position(portal.get("from"), config, source=True)
        _position(portal.get("to"), config)
        if portal["id"] in interactions:
            points = interaction_anchors(interactions[portal["id"]])
            if len(points) != 1 or any(points[0][key] != portal["from"][key] for key in ("map_key", "tile_x", "tile_y")):
                raise ValueError("传送入口与同名交互锚点不一致")
        flags = portal.get("required_flags")
        if not isinstance(flags, list) or any(not _string(flag) for flag in flags) or len(set(flags)) != len(flags) or len(flags) > 30:
            raise ValueError("传送入口条件无效")
        if "building_id" in portal and (not _string(portal["building_id"]) or portal["building_id"] not in buildings):
            raise ValueError("传送入口引用了不存在的建筑")
        if "building_id" in portal:
            building = buildings[portal["building_id"]]
            source = portal["from"]
            expected = (building_maps[portal["building_id"]], building["anchor"]["x"], building["anchor"]["y"])
            if (source["map_key"], source["tile_x"], source["tile_y"]) != expected or building.get("portal_id") != portal["id"]:
                raise ValueError("建筑与传送入口的位置或反向引用不一致")
    if any("portal_id" in building and (not _string(building["portal_id"]) or building["portal_id"] not in portal_ids) for building in buildings.values()):
        raise ValueError("建筑引用了不存在的传送入口")
    portal_by_id = {portal["id"]: portal for portal in portals}
    if any("portal_id" in building and portal_by_id[building["portal_id"]].get("building_id") != building_id
           for building_id, building in buildings.items()):
        raise ValueError("建筑传送入口缺少一致的反向引用")
    marker = config.get("portal_marker")
    if marker is not None:
        if not isinstance(marker, dict) or set(marker) != {"asset", "width", "height"}:
            raise ValueError("入口提示素材字段无效")
        if not isinstance(marker["asset"], str) or not marker["asset"].startswith("/assets/pixel/") or ".." in marker["asset"] or not marker["asset"].endswith(".png"):
            raise ValueError("入口提示须引用本地素材")
        if any(type(marker[key]) is not int or not 1 <= marker[key] <= 32 for key in ("width", "height")):
            raise ValueError("入口提示尺寸无效")
    return config


@lru_cache(maxsize=1)
def load_world_config():
    path = ROOT / "shared" / "world" / "world.json"
    return validate_world_config(json.loads(path.read_text(encoding="utf-8-sig")))


def interaction_points():
    config = load_world_config()
    points = {building["id"]: (map_key, building["anchor"]["x"], building["anchor"]["y"])
              for map_key, area in config["maps"].items() for building in area["buildings"]}
    for key, anchor in config["interactions"].items():
        positions = [(point["map_key"], point["tile_x"], point["tile_y"]) for point in interaction_anchors(anchor)]
        points[key] = positions if "points" in anchor else positions[0]
    points.update({portal["id"]: (portal["from"]["map_key"], portal["from"]["tile_x"], portal["from"]["tile_y"]) for portal in config["portals"]})
    return points
