"""Course characters and bounded, authorized appearance resources."""
from functools import lru_cache
import json
from pathlib import Path

from .config import ROOT
from .course_exchange_models import CoursePackageState
from .world_config import DIRECTIONS, validate_point

CHARACTER_FIELDS = ("name", "portrait", "sprite", "persona", "placement", "appearance_condition")


@lru_cache
def builtin_assets():
    return json.loads((ROOT / "shared/world/characters.json").read_text(encoding="utf-8"))


def stored_assets(db, instance_id):
    state = db.get(CoursePackageState, instance_id)
    return state.configuration.get("_assets", []) if state else []


def asset_descriptor(reference, kind, assets, classroom_id=None):
    builtin = next((item for item in builtin_assets() if item["id"] == reference and item["kind"] == kind), None)
    if builtin:
        return dict(builtin)
    if not isinstance(reference, str) or not reference.startswith("asset:"):
        raise ValueError("角色素材不存在，请重新选择或上传")
    item = next((item for item in assets if item["id"] == reference[6:]), None)
    if not item or item.get("character_kind") != kind:
        raise ValueError("角色素材不存在或类型不匹配")
    result = {key: item[key] for key in ("sha256", "frame_width", "frame_height", "owner_npc_id") if key in item}
    result.update(id=reference, kind=kind, name=item.get("original_name", "自定义素材"))
    if classroom_id is not None:
        result["url"] = f"/api/classrooms/{classroom_id}/character-assets/{item['id']}?v={item['sha256']}"
    return result


def validate_characters(rows, assets):
    from .world_authoring import reachable_cells
    if not isinstance(rows, list) or len(rows) > 100:
        raise ValueError("教学角色最多100个")
    ids, positions, private_refs = set(), set(), set()
    from .authoring_diagnostics import located
    for index, row in enumerate(rows):
        with located("npcs", index, target={"kind": "npc", "id": row.get("id", "")} if isinstance(row, dict) else {"kind": "npc"}):
            if not isinstance(row, dict) or set(row) != {"id", *CHARACTER_FIELDS}:
                raise ValueError("角色字段不完整或包含旧格式字段")
            key = row["id"]
            if not isinstance(key, str) or not 1 <= len(key) <= 100 or key in ids:
                raise ValueError("角色ID无效或重复")
            ids.add(key)
            if not isinstance(row["name"], str) or not 1 <= len(row["name"].strip()) <= 80:
                raise ValueError("角色名称需要1至80字")
            if not isinstance(row["persona"], str) or len(row["persona"]) > 4000:
                raise ValueError("角色人设最多4000字")
            for kind in ("portrait", "sprite"):
                descriptor = asset_descriptor(row[kind], kind, assets)
                if row[kind].startswith("asset:"):
                    if row[kind] in private_refs or descriptor.get("owner_npc_id", key) != key:
                        raise ValueError("自定义素材只能由所属角色使用")
                    private_refs.add(row[kind])
            point = row["placement"]
            if point is None:
                continue
            if not isinstance(point, dict) or set(point) != {"map_key", "tile_x", "tile_y", "direction"} or point["direction"] not in DIRECTIONS:
                raise ValueError("角色位置或朝向无效")
            validate_point(point["map_key"], point["tile_x"], point["tile_y"])
            if (point["tile_x"], point["tile_y"]) not in reachable_cells(point["map_key"]):
                raise ValueError("角色位置无法从地图入口到达")
            position = (point["map_key"], point["tile_x"], point["tile_y"])
            if position in positions:
                raise ValueError("角色不能占据同一地图格")
            positions.add(position)


def presentation(db, classroom_id, npc):
    if npc is None:
        return None
    assets = stored_assets(db, npc.course_instance_id)
    portrait = asset_descriptor(npc.portrait, "portrait", assets, classroom_id)
    return {"key": npc.key, "name": npc.name, "portrait": portrait["url"],
            "sprite": asset_descriptor(npc.sprite, "sprite", assets, classroom_id)}


def validate_references(data):
    from .authoring_diagnostics import located
    npcs = {row["id"]: row for row in data["npc"]}

    def require(key, placed=False):
        if key not in npcs:
            raise ValueError("引用的教学角色不存在，请先更换角色引用")
        if placed and npcs[key]["placement"] is None:
            raise ValueError("地图交互或任务导航引用的角色尚未放置")

    for activity in data["activity"]:
        with located("activities", activity["id"], "npc_key", label=f"活动「{activity['title']}」"):
            if activity["npc_key"] is not None:
                require(activity["npc_key"])
    for quest in data["quest"]:
        for node in quest["definition"]["nodes"]:
            with located("quests", quest["id"], "definition", "nodes", node["id"],
                         target={"kind": "goal", "quest_id": quest["id"], "node_id": node["id"]}):
                if node.get("navigation_target"):
                    require(node["navigation_target"], placed=True)
                if node["type"] in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"}:
                    require(node["target"], placed=True)
    for flow in data["story"]:
        with located("flows", flow["id"], target={"kind": "flow", "flow_id": flow["id"]}):
            for entry in flow["content"]["entries"]:
                if not entry.startswith("interaction:"):
                    require(entry, placed=True)
            for node in flow["content"]["nodes"]:
                if node.get("speaker_npc_id"):
                    require(node["speaker_npc_id"])


def character_issues(db, instance, data):
    from sqlalchemy import select
    from .models import LearningEvent, Npc, StoryDefinition, StorySession, WorldFlag
    from .authoring_diagnostics import AuthoringIssue
    incoming = {row["id"]: row for row in data["npc"]}
    for npc in db.scalars(select(Npc).where(Npc.course_instance_id == instance.id)):
        updated = incoming.get(npc.stable_id)
        sessions = select(StorySession).where(StorySession.classroom_id == instance.classroom_id, StorySession.npc_key == npc.key)
        message = None
        if updated is None:
            historical = db.scalar(sessions.limit(1)) or db.scalar(select(WorldFlag).where(
                WorldFlag.classroom_id == instance.classroom_id, WorldFlag.key.contains(npc.key)).limit(1))
            if not historical:
                historical = db.scalar(select(LearningEvent.id).where(LearningEvent.classroom_id == instance.classroom_id,
                                       LearningEvent.evidence["npc_key"].as_string() == npc.key).limit(1))
            if not historical:
                spoken_flows = [story.id for story in db.scalars(select(StoryDefinition).where(StoryDefinition.course_instance_id == instance.id))
                                if any(node.get("speaker_npc_id") == npc.key for node in story.content["nodes"])]
                historical = db.scalar(select(StorySession.id).where(StorySession.classroom_id == instance.classroom_id,
                                       StorySession.story_id.in_(spoken_flows)).limit(1)) if spoken_flows else None
            if historical:
                message = "角色已有互动历史，无法删除；可以结束互动后撤回地图放置"
        elif updated["placement"] != npc.placement or updated["appearance_condition"] != npc.appearance_condition:
            if db.scalar(sessions.where(StorySession.status.in_(("active", "waiting"))).limit(1)):
                message = "角色仍有进行中或等待活动的互动，请结束互动后调整位置或出现条件"
        if message:
            yield AuthoringIssue(message, path=("npcs", npc.stable_id), target={"kind": "npc", "id": npc.stable_id},
                                 code="character_in_use", label=f"角色「{npc.name}」", suggestion="先处理引用或结束相关互动")


def world_character(db, classroom_id, npc):
    result = presentation(db, classroom_id, npc)
    result.update(stable_id=npc.stable_id, **npc.placement)
    return result


def validate_image(path: Path, kind, frame_width=16, frame_height=20):
    from PIL import Image, UnidentifiedImageError
    import warnings
    if kind not in {"portrait", "sprite"} or path.stat().st_size > 10 * 1024 * 1024:
        raise ValueError("角色图片类型无效或超过10MB")
    formats = ["PNG"] if kind == "sprite" else ["PNG", "JPEG", "WEBP"]
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(path, formats=formats) as picture:
                width, height = picture.size
                if width > 4096 or height > 4096 or width * height > 16_000_000 or getattr(picture, "n_frames", 1) != 1:
                    raise ValueError("角色图片须为静态图片，边长最多4096像素")
                media_type = Image.MIME[picture.format]
                picture.verify()
            with Image.open(path, formats=formats) as picture:
                picture.load()
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError, Image.DecompressionBombWarning) as exc:
        raise ValueError("图片无法完整解码或尺寸过大") from exc
    result = {"character_kind": kind, "width": width, "height": height, "media_type": media_type}
    if kind == "sprite":
        if any(type(value) is not int or not 8 <= value <= 256 for value in (frame_width, frame_height)):
            raise ValueError("像素角色帧宽高须为8至256像素")
        if (width, height) != (frame_width * 3, frame_height * 4):
            raise ValueError("像素角色须为三列四行，按下、左、右、上排列")
        result.update(frame_width=frame_width, frame_height=frame_height)
    return result
