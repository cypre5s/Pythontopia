"""Typed world/flow authoring; geometry is owned by the program, never a course."""
from collections import deque
from copy import deepcopy
from functools import lru_cache
import json
from types import SimpleNamespace
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from .auth import require_teacher
from .authoring_diagnostics import INPUT_ERRORS, AuthoringIssue, located, node_label, row_target
from .authoring_protection import publication_plan
from .config import ROOT
from .course_exchange_models import CoursePackageState
from .course_instances import MODEL_NAMES, _json, classroom_course, content_mapping
from .course_scope import lock_course
from .db import get_db
from .models import (BuildingUnlock, Classroom, CourseInstance, Membership, PlayerPosition,
                     PlayerQuest, Npc, QuestDefinition, StoryDefinition, User)
from .teacher_scope import scoped_key, teacher_instance_id
from .world_config import interaction_points, load_world_config, validate_point

router = APIRouter(prefix="/teacher/classrooms/{classroom_id}/authoring", tags=["course-authoring"])


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
    spawn = config["default_spawn"] if config["default_spawn"]["map_key"] == map_key else next(p["to"] for p in config["portals"] if p["to"]["map_key"] == map_key)
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


def _short(value, maximum=100):
    return isinstance(value, str) and 0 < len(value) <= maximum and bool(value.strip())


def _object(value, allowed, required=()):
    if not isinstance(value, dict) or set(value) - set(allowed) or set(required) - set(value):
        raise ValueError("未知或缺失的课程世界字段")


def _flow_title(row):
    content = row.get("content")
    title = row.get("title") or (content.get("title") if isinstance(content, dict) else None)
    return title if isinstance(title, str) and title.strip() else row.get("id") or "未命名流程"


def _portable_story(content, maps):
    if not isinstance(content, dict):
        return content
    # Resolve each authoring reference in its own location before validating the
    # resulting runtime graph. This keeps a missing activity on its source card.
    result = {}
    nodes = content.get("nodes")
    for key, value in content.items():
        if key == "nodes" and isinstance(value, list):
            result[key] = []
            for index, node in enumerate(value):
                with located(key, index, target=row_target("node", node),
                             label=node_label(node, index) if isinstance(node, dict) else None):
                    result[key].append(_json(node, maps))
        elif key == "entries" and isinstance(value, dict):
            result[key] = {}
            for entry, routes in value.items():
                with located(key, entry, target={"kind": "entry", "id": entry}):
                    result[key].update(_json({key: {entry: routes}}, maps)[key])
        elif key == "bindings" and isinstance(value, dict):
            result[key] = {}
            for binding, detail in value.items():
                location = next(((i, node) for i, node in enumerate(nodes) if isinstance(node, dict)
                                 and node.get("binding") == binding), None) if isinstance(nodes, list) else None
                target = row_target("node", location[1]) if location else None
                label = node_label(location[1], location[0]) if location else None
                with located(key, binding, target=target, label=label,
                             suggestion="在学习活动节点的属性里重新选择本班可用活动。"):
                    result[key].update(_json({key: {binding: detail}}, maps)[key])
        else:
            with located(key):
                result.update(_json({key: value}, maps))
    return result


def _name_entry_issue(issue, names, stable_ids=None):
    if issue.target.get("kind") != "entry":
        return
    key = issue.target["id"]
    stable = (stable_ids or {}).get(key, key)
    issue.target["id"] = stable
    if issue.path[:2] == ("entries", key):
        issue.path = ("entries", stable, *issue.path[2:])
    if stable in names:
        issue.label = f"入口「{names[stable]}」"


def _points(item):
    target = item["target"]
    if "object_id" in target:
        points = interaction_points().get(target["object_id"])
        if points is None:
            raise ValueError("交互对象不存在")
        points = [points] if isinstance(points[0], str) else points
        return [{"map_key": m, "tile_x": x, "tile_y": y} for m, x, y in points if m == item["map_key"]]
    region = target["region"]
    return [{"map_key": item["map_key"], "tile_x": x, "tile_y": y}
        for x, y in sorted(reachable_cells(item["map_key"]))
        if region["x"] <= x < region["x"] + region["width"] and region["y"] <= y < region["y"] + region["height"]]


def validate_configuration(config, bundle_data):
    """Validate portable stable references, including all map shapes and entry conflicts."""
    from .story import validate_story
    from .story_conditions import validate_condition

    _object(config, {"world", "buildings", "assistant", "editor"})
    maps = {kind: {row["id"]: index + 1 if kind in {"chapter", "material", "knowledge_point", "example"} else row["id"]
                   for index, row in enumerate(rows)} for kind, rows in bundle_data.items()}
    converted = {}
    for kind, rows in bundle_data.items():
        if kind == "story":
            continue
        converted[kind] = {}
        for index, row in enumerate(rows):
            if kind == "quest":
                with located("quests", index, target=row_target("quest", row), label=f"任务「{row['title']}」"):
                    converted[kind][row["id"]] = _json(row, maps)
            else:
                converted[kind][row["id"]] = _json(row, maps)

    class Catalog:
        def get(self, model, key):
            kind = MODEL_NAMES[model]
            row = next((row for stable, row in converted[kind].items() if maps[kind][stable] == key), None)
            return SimpleNamespace(**row) if row else None

    catalog = Catalog()

    def condition(value, bindings=None, quest=None):
        validate_condition(_json(value, maps), bindings or {}, quest, catalog)

    world, buildings = config.get("world", {}), config.get("buildings", {})
    _object(world, {"interactions"})
    _object(buildings, interior_buildings())
    for building_id, policy in buildings.items():
        with located("configuration", "buildings", building_id, target={"kind": "building", "id": building_id},
                     label=f"建筑「{interior_buildings()[building_id]['label']}」"):
            _object(policy, {"enabled", "default_unlocked", "when", "blocked_message"}, {"enabled", "default_unlocked", "blocked_message"})
            if type(policy["enabled"]) is not bool or type(policy["default_unlocked"]) is not bool or not _short(policy["blocked_message"], 500):
                raise ValueError("建筑开放规则无效")
            if "when" in policy:
                condition(policy["when"])
    interactions = world.get("interactions", [])
    if not isinstance(interactions, list) or len(interactions) > 500:
        raise ValueError("交互入口最多500个")
    ids, targets, enter_cells = set(), set(), set()
    for index, item in enumerate(interactions):
        target = row_target("interaction", item)
        label = item.get("label") if isinstance(item, dict) else None
        with located("configuration", "world", "interactions", index, target=target, label=f"地图交互「{label or index + 1}」",
                     suggestion="检查所选地图、触发方式、对象或绘制区域。"):
            _object(item, {"id", "label", "prompt", "trigger", "map_key", "target", "when"}, {"id", "label", "prompt", "trigger", "map_key", "target"})
            if not _short(item["id"], 80) or item["id"] in ids or not _short(item["label"], 200) or not _short(item["prompt"], 500):
                raise ValueError("交互ID重复或名称/提示无效")
            ids.add(item["id"])
            if item["trigger"] not in {"click", "enter"} or item["map_key"] not in load_world_config()["maps"]:
                raise ValueError("交互触发方式或地图无效")
            target = item["target"]
            _object(target, {"object_id", "region"})
            if len(target) != 1:
                raise ValueError("交互需要一个对象或区域")
            if "object_id" in target and (not _short(target["object_id"]) or item["trigger"] == "enter"):
                raise ValueError("对象交互仅支持点击")
            if "region" in target:
                region = target["region"]
                _object(region, {"x", "y", "width", "height"}, {"x", "y", "width", "height"})
                if any(type(n) is not int for n in region.values()) or not 1 <= region["width"] <= 32 or not 1 <= region["height"] <= 32:
                    raise ValueError("区域大小无效")
                validate_point(item["map_key"], region["x"], region["y"])
                validate_point(item["map_key"], region["x"] + region["width"] - 1, region["y"] + region["height"] - 1)
            points = _points(item)
            if not points or any((p["tile_x"], p["tile_y"]) not in reachable_cells(p["map_key"]) for p in points):
                raise ValueError("交互必须存在从地图入口可达的位置")
            target_key = json.dumps([item["map_key"], target, item["trigger"]], sort_keys=True)
            if target_key in targets:
                raise ValueError("同一对象或区域的重复交互入口")
            targets.add(target_key)
            if item["trigger"] == "enter":
                cells = {(p["map_key"], p["tile_x"], p["tile_y"]) for p in points}
                if enter_cells & cells:
                    raise ValueError("进入触发区域不能重叠")
                enter_cells.update(cells)
            if "when" in item:
                condition(item["when"])
    for row in bundle_data["npc"]:
        with located("npcs", row["id"], "appearance_condition", target={"kind": "npc", "id": row["id"]}):
            condition(row["appearance_condition"])
    from .characters import validate_references
    validate_references(bundle_data)
    entry_names = {**{row["id"]: row["name"] for row in bundle_data["npc"]},
                   **{f"interaction:{item['id']}": item["label"] for item in interactions}}
    priorities = {}
    checkpoints = set()
    for index, row in enumerate(bundle_data["story"]):
        with located("flows", index, "content", target=row_target("flow", row, flow_id=row["id"]),
                     label=f"流程「{_flow_title(row)}」"):
            content = row["content"]
            quest = converted["quest"].get(row["quest_id"])
            try:
                runtime = _portable_story(content, maps)
                validate_story(runtime, quest_definition=quest["definition"] if quest else None, story_id=row["id"])
            except AuthoringIssue as issue:
                _name_entry_issue(issue, entry_names)
                raise
            if row["chapter_id"] is not None and row["chapter_id"] not in maps["chapter"] or row["quest_id"] is not None and quest is None:
                raise ValueError("流程引用了不存在的章节或任务")
            if quest and quest["chapter_id"] != maps["chapter"].get(row["chapter_id"]):
                raise ValueError("流程与任务章节不一致")
            if row["quest_id"] is None and content.get("mode") not in {"public", "ambient"}:
                raise ValueError("无任务流程必须声明公共业务或日常模式")
            if row["quest_id"] is not None and content.get("mode", "task") != "task":
                raise ValueError("任务流程必须声明task模式")
            checkpoints.update((row["quest_id"], node["checkpoint"], f"story:{row['id']}:{node['checkpoint']}")
                               for node in content["nodes"] if node.get("checkpoint"))

            def check_conditions(value):
                if isinstance(value, dict):
                    for key, child in value.items():
                        if key == "when":
                            validate_condition(child, runtime["bindings"], quest["definition"] if quest else None, catalog)
                        else:
                            check_conditions(child)
                elif isinstance(value, list):
                    for child in value:
                        check_conditions(child)
            if "when" in runtime:
                with located("when"):
                    check_conditions({"when": runtime["when"]})
            for node_index, node in enumerate(runtime["nodes"]):
                with located("nodes", node_index, target=row_target("node", node), label=node_label(node, node_index)):
                    check_conditions(node)
            for entry, routes in runtime["entries"].items():
                with located("entries", entry, target={"kind": "entry", "id": entry},
                             label=f"入口「{entry_names[entry]}」" if entry in entry_names else None):
                    check_conditions(routes)
            for binding in content["bindings"].values():
                if binding["activity_id"] not in maps["activity"]:
                    raise ValueError("流程活动不存在")
            for entry in content["entries"]:
                with located("entries", entry, target={"kind": "entry", "id": entry}):
                    if entry.startswith("interaction:"):
                        if entry.removeprefix("interaction:") not in ids:
                            raise ValueError("流程交互入口不存在")
                    elif entry not in maps["npc"]:
                        raise ValueError("流程角色不存在")
                    layer = 0 if row["quest_id"] is not None else 1 if row["chapter_id"] is not None else 2
                    key = (entry, layer, content.get("priority", 0))
                    if key in priorities:
                        raise AuthoringIssue(f"同一入口的优先级与流程「{priorities[key]}」重复。", code="entry_priority_conflict",
                                             suggestion="打开流程设置与入口，设置不同的入口优先级。")
                    priorities[key] = _flow_title(row)
    for quest_index, row in enumerate(bundle_data["quest"]):
        for goal_index, goal in enumerate(row["definition"]["nodes"]):
            marker = goal.get("dialogue_id")
            if goal.get("type") in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"} and not any(
                quest == row["id"] and node == goal["id"] and (goal["type"] != "PLAY_DIALOGUE" or checkpoint == marker)
                for quest, node, checkpoint in checkpoints
            ):
                raise AuthoringIssue(f"任务「{row['title']}」的目标「{goal['title']}」仍引用已移除的流程对白。",
                                     path=("quests", quest_index, "definition", "nodes", goal_index, "dialogue_id"),
                                     target=row_target("goal", goal, quest_id=row["id"]), code="missing_dialogue_checkpoint",
                                     suggestion="恢复原对白检查点，或同步调整这个任务目标后再保存。")
    from .course_assistant import validate_policy
    with located("configuration", "assistant", target={"kind": "assistant"}, label="助教教学角色"):
        validate_policy(config.get("assistant", {}), bundle_data)
    # Layouts are data only. The exchange parser already bounds size/depth; HTTP
    # authoring has the same explicit cap and never evaluates editor content.
    editor = config.get("editor", {})
    if not isinstance(editor, dict) or len(json.dumps(editor, ensure_ascii=False)) > 200_000:
        raise ValueError("编辑布局必须为有界对象")
    return config


def configuration(db, classroom_id):
    instance = classroom_course(db, classroom_id)
    state = db.get(CoursePackageState, instance.id) if instance else None
    return state.configuration if state else {}


def _condition(db, user, classroom_id, value):
    from .story_conditions import condition_matches
    instance = classroom_course(db, classroom_id)
    maps = content_mapping(db, instance) if instance else None
    proxy = SimpleNamespace(course_instance_id=instance.id if instance else None, quest_id=None, content={"bindings": {}})
    try:
        return condition_matches(db, user, classroom_id, proxy, _json(value, maps) if maps else value)
    except (ValueError, KeyError):
        return False


def unlocked_buildings(db, user, classroom_id):
    instance = classroom_course(db, classroom_id)
    ids = set(db.scalars(select(BuildingUnlock.building_id).where(BuildingUnlock.user_id == user.id,
        BuildingUnlock.classroom_id == classroom_id, BuildingUnlock.course_instance_id == instance.id))) if instance else set()
    return ids


def unlock_building(db, user, classroom_id, building_id):
    if building_id not in interior_buildings():
        raise HTTPException(409, "建筑没有室内")
    instance = classroom_course(db, classroom_id)
    if instance is None:
        raise HTTPException(409, "班级尚未导入课程")
    key = (user.id, classroom_id, instance.id, building_id)
    if not db.get(BuildingUnlock, key):
        db.add(BuildingUnlock(user_id=user.id, classroom_id=classroom_id, course_instance_id=instance.id, building_id=building_id))


def building_access(db, user, classroom_id):
    policies = configuration(db, classroom_id).get("buildings", {})
    unlocked = unlocked_buildings(db, user, classroom_id)
    result = {}
    for key in interior_buildings():
        policy = policies.get(key, {"enabled": classroom_course(db, classroom_id) is None, "default_unlocked": False, "blocked_message": "此课程尚未开放该建筑。"})
        granted = policy["default_unlocked"] or key in unlocked or "when" in policy and _condition(db, user, classroom_id, policy["when"])
        result[key] = {"enabled": policy["enabled"], "unlocked": bool(granted), "can_enter": bool(policy["enabled"] and granted),
                       "blocked_message": policy["blocked_message"]}
    return result


def npc_present(db, user, classroom_id, npc):
    return npc.placement is not None and _condition(db, user, classroom_id, npc.appearance_condition)


def active_interactions(db, user, classroom_id):
    result = []
    for item in configuration(db, classroom_id).get("world", {}).get("interactions", []):
        if _condition(db, user, classroom_id, item.get("when", {"always": True})):
            result.append({key: deepcopy(item[key]) for key in ("id", "label", "prompt", "trigger", "map_key", "target")})
            result[-1].update(points=_points(item), radius=0 if item["trigger"] == "enter" else 2)
    return result


def require_interaction(db, user, classroom_id, interaction_id, *, near=True):
    from .quests import check_near
    item = next((item for item in active_interactions(db, user, classroom_id) if item["id"] == interaction_id), None)
    if not item:
        raise HTTPException(404, "当前地图交互不可用")
    if near:
        check_near(db, user, classroom_id, [(p["map_key"], p["tile_x"], p["tile_y"]) for p in item["points"]], radius=item["radius"])
    return item


def course_object_points(db, user, classroom_id, target):
    configured = [row for row in configuration(db, classroom_id).get("world", {}).get("interactions", [])
                  if row["id"] == target or row["target"].get("object_id") == target]
    item = next((row for row in active_interactions(db, user, classroom_id)
                 if row["id"] == target or row["target"].get("object_id") == target), None)
    if item:
        return [(p["map_key"], p["tile_x"], p["tile_y"]) for p in item["points"]]
    if configured:
        return None
    return interaction_points().get(target)


def record_interaction(db, user, classroom_id, interaction_id, session):
    """An actual public interaction can satisfy an explicitly authored task goal."""
    from .courses import chapter_available
    from .quests import EventInput, apply_quest_event, ensure_quests, node_statuses, quest_visible
    from .models import NodeState
    from .course_scope import course_clause
    item = require_interaction(db, user, classroom_id, interaction_id)
    targets = {interaction_id, item["target"].get("object_id")}
    ensure_quests(db, user, classroom_id, commit=False)
    classroom = db.get(Classroom, classroom_id)
    for definition in db.scalars(select(QuestDefinition).where(course_clause(db, QuestDefinition, classroom_id))):
        if not quest_visible(definition, user, classroom, db) or not chapter_available(db, user, classroom_id, definition.chapter_id):
            continue
        player = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id == definition.id))
        if not player or player.status == "complete":
            continue
        states = {state.node_id: state for state in db.scalars(select(NodeState).where(NodeState.player_quest_id == player.id))}
        statuses = node_statuses(definition.definition, states)
        for node in definition.definition["nodes"]:
            if node["type"] in {"INTERACT_OBJECT", "REACH_LOCATION"} and node.get("target") in targets and statuses[node["id"]] == "ready":
                import hashlib
                event_id = hashlib.sha256(f"interaction:{session.id}:{definition.id}:{node['id']}".encode()).hexdigest()
                apply_quest_event(db, user, EventInput(classroom_id=classroom_id, quest_id=definition.id, node_id=node["id"],
                    event_id=event_id, evidence={"object_id": node["target"]} if node["type"] == "INTERACT_OBJECT" else {}))


def enter_building(db, user, classroom_id, building_id):
    from .quests import check_near
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


class AuthoringInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    expected_revision: int = Field(ge=1)
    npcs: list[dict] = Field(max_length=100)
    flows: list[dict] = Field(max_length=500)
    quests: list[dict] | None = Field(default=None, max_length=500)
    configuration: dict


class PreviewInput(AuthoringInput):
    student_id: int
    entry: str = Field(min_length=1, max_length=100)


class PublishInput(AuthoringInput):
    confirmation_token: str | None = Field(default=None, pattern=r"^[a-f0-9]{64}$")


def _instance(db, user, classroom_id, *, exclusive=False):
    lock_course(db, classroom_id, exclusive=exclusive)
    key = teacher_instance_id(db, user, classroom_id)
    return db.scalar(select(CourseInstance).where(CourseInstance.id == key).with_for_update()) if exclusive else db.get(CourseInstance, key)


def _data(db, instance):
    # The exchange serializer is also the editor's portable representation.
    from .course_exchange import _snapshot
    data, _, _, config, _ = _snapshot(db, instance)
    return data, {key: value for key, value in config.items() if key in {"world", "buildings", "assistant", "editor"}}


def _catalog(data):
    config, objects = load_world_config(), interaction_points()
    maps = []
    for key, area in config["maps"].items():
        items = []
        for object_id, positions in objects.items():
            points = [positions] if isinstance(positions[0], str) else positions
            points = [{"map_key": m, "tile_x": x, "tile_y": y} for m, x, y in points if m == key]
            if points:
                building = next((b for b in area["buildings"] if b["id"] == object_id), None)
                label = building["label"] if building else config["interactions"].get(object_id, {}).get("label", object_id)
                items.append({"id": object_id, "label": label, "points": points})
        maps.append({"key": key, **{field: area[field] for field in ("label", "width", "height")},
                     "walk_bounds": area.get("walk_bounds", {"x": 0, "y": 0, "width": area["width"], "height": area["height"]}), "objects": items})
    return {"maps": maps, "buildings": [{"id": b["id"], "label": b["label"], "map_key": key, "has_interior": bool(b.get("portal_id"))}
            for key, area in config["maps"].items() for b in area["buildings"]],
        "chapters": [{"id": row["id"], "title": row["title"]} for row in data["chapter"]],
        "activities": [{"id": row["id"], "title": row["title"], "kind": row["kind"]} for row in data["activity"]],
        "npcs": [{"id": row["id"], "name": row["name"], "map_key": row["placement"]["map_key"] if row["placement"] else None} for row in data["npc"]],
        "quests": [{"id": row["id"], "title": row["title"], "nodes": [{key: node[key] for key in ("id", "title", "type")} for node in row["definition"]["nodes"]]} for row in data["quest"]]}


@router.get("")
def get_authoring(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    instance = _instance(db, user, classroom_id)
    data, config = _data(db, instance)
    students = db.scalars(select(User).join(Membership, Membership.user_id == User.id).where(Membership.classroom_id == classroom_id, User.role == "student"))
    return {"revision": instance.revision, "catalog": _catalog(data), "npcs": data["npc"],
            "flows": [{**row, "title": row["content"].get("title", row["id"])} for row in data["story"]],
            "quests": data["quest"], "configuration": {key: config.get(key, {}) for key in ("world", "buildings", "assistant", "editor")},
            "students": [{"id": row.id, "name": row.name} for row in students]}


def _prepare(db, instance, body):
    from .quests import validate_definition
    from .story import validate_story
    if instance.revision != body.expected_revision:
        raise HTTPException(409, "课程已被其他编辑修改，请刷新后重试")
    data, _ = _data(db, instance)
    from .characters import stored_assets, validate_characters
    validate_characters(body.npcs, stored_assets(db, instance.id))
    data["npc"] = deepcopy(body.npcs)
    flows, seen = [], set()
    for index, row in enumerate(body.flows):
        with located("flows", index, target=row_target("flow", row), label=f"流程「{_flow_title(row)}」"):
            _object(row, {"id", "title", "chapter_id", "quest_id", "content"}, {"id", "chapter_id", "quest_id", "content"})
            if not _short(row["id"]) or row["id"] in seen:
                raise ValueError("流程ID重复或无效")
            seen.add(row["id"])
            copied = deepcopy(row)
            if "title" in copied:
                copied["content"]["title"] = copied.pop("title")
            flows.append(copied)
    data["story"] = flows
    if body.quests is not None:
        seen = set()
        for index, row in enumerate(body.quests):
            with located("quests", index, target=row_target("quest", row), label=f"任务「{row.get('title', index + 1)}」"):
                _object(row, {"id", "title", "chapter_id", "definition"}, {"id", "title", "chapter_id", "definition"})
                if not _short(row["id"]) or row["id"] in seen or row["definition"].get("id") != row["id"] or row["definition"].get("chapter_id") != row["chapter_id"]:
                    raise ValueError("任务ID或章节引用不一致")
                seen.add(row["id"])
        data["quest"] = deepcopy(body.quests)
    validate_configuration(body.configuration, data)
    maps = content_mapping(db, instance)
    maps["npc"] = {row["id"]: maps["npc"].get(row["id"], scoped_key(instance.id, "npc", row["id"])) for row in data["npc"]}
    for kind in ("story", "quest"):
        for row in data[kind]:
            maps[kind].setdefault(row["id"], scoped_key(instance.id, kind, row["id"]))
    class DraftCatalog:
        def get(self, model, key):
            if model is Npc:
                row = next((row for row in data["npc"] if maps["npc"][row["id"]] == key), None)
                return SimpleNamespace(**row) if row else None
            return db.get(model, key)

    draft_catalog = DraftCatalog()
    quests = {}
    for index, row in enumerate(data["quest"]):
        with located("quests", index, "definition", target=row_target("quest", row, quest_id=row["id"]),
                     label=f"任务「{row['title']}」"):
            actual = _json(row, maps)
            actual["definition"]["id"] = maps["quest"][row["id"]]
            objects = set(interaction_points()) | {row["id"] for row in body.configuration.get("world", {}).get("interactions", [])}
            validate_definition(actual["definition"], draft_catalog, objects=objects)
            quests[row["id"]] = actual
    entry_names = {**{row["id"]: row["name"] for row in data["npc"]},
                   **{f"interaction:{item['id']}": item["label"] for item in body.configuration.get("world", {}).get("interactions", [])}}
    stable_npc_ids = {value: key for key, value in maps["npc"].items()}
    for index, row in enumerate(data["story"]):
        with located("flows", index, "content", target=row_target("flow", row, flow_id=row["id"]),
                     label=f"流程「{_flow_title(row)}」"):
            actual = _json(row, maps)
            quest = quests.get(row["quest_id"])
            try:
                validate_story(actual["content"], draft_catalog, quest["definition"] if quest else None, maps["story"][row["id"]])
            except AuthoringIssue as issue:
                _name_entry_issue(issue, entry_names, stable_npc_ids)
                raise
    return data, maps


def _diagnostic(exc):
    issue = exc if isinstance(exc, AuthoringIssue) else AuthoringIssue(
        str(exc) if isinstance(exc, ValueError) else "字段格式不正确或缺少必填项")
    return issue.diagnostic()


def _error_detail(diagnostics):
    return {"message": diagnostics[0]["message"], "diagnostics": diagnostics}


@router.post("/validate")
def validate_authoring(classroom_id: int, body: AuthoringInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    instance = _instance(db, user, classroom_id)
    impact = None
    try:
        data, maps = _prepare(db, instance, body)
        diagnostics, plan = publication_plan(db, instance, data, maps, body.configuration)
        if not diagnostics:
            impact = plan.impact(instance, user.id, body.model_dump())
    except INPUT_ERRORS as exc:
        diagnostics = [_diagnostic(exc)]
    return {"valid": not diagnostics, "diagnostics": diagnostics, **({"reset_impact": impact} if impact else {})}


@router.put("")
def save_authoring(classroom_id: int, body: PublishInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    instance = _instance(db, user, classroom_id, exclusive=True)
    try:
        data, maps = _prepare(db, instance, body)
    except INPUT_ERRORS as exc:
        raise HTTPException(422, _error_detail([_diagnostic(exc)])) from exc
    # Recheck under the exclusive course lock in case a student starts after preflight.
    diagnostics, plan = publication_plan(db, instance, data, maps, body.configuration)
    if diagnostics:
        raise HTTPException(409, _error_detail(diagnostics))
    impact = plan.impact(instance, user.id, body.model_dump(exclude={"confirmation_token"}))
    if impact and impact["token"] != body.confirmation_token:
        raise HTTPException(409, {"message": "受影响的存档已更新，请核对后再次确认。" if body.confirmation_token
                                 else "本次修改会清理部分班级存档，请先确认影响范围。", "reset_impact": impact})
    plan.apply(db, classroom_id)
    from .characters import CHARACTER_FIELDS
    incoming_npcs = {row["id"]: row for row in data["npc"]}
    current_npcs = {row.stable_id: row for row in db.scalars(select(Npc).where(Npc.course_instance_id == instance.id))}
    for stable, npc in current_npcs.items():
        if stable not in incoming_npcs:
            db.delete(npc)
    for stable, row in incoming_npcs.items():
        npc = current_npcs.get(stable)
        if npc is None:
            npc = Npc(key=maps["npc"][stable], stable_id=stable, course_instance_id=instance.id)
            db.add(npc)
        for field in CHARACTER_FIELDS:
            setattr(npc, field, deepcopy(row[field]))
    db.flush()
    for model, kind, field in ((QuestDefinition, "quest", "definition"), (StoryDefinition, "story", "content")):
        incoming = {row["id"]: row for row in data[kind]}
        current = {row.stable_id or row.id: row for row in db.scalars(select(model).where(model.course_instance_id == instance.id))}
        for stable, existing in current.items():
            if stable not in incoming:
                db.delete(existing)
        for stable, row in incoming.items():
            actual = _json(row, maps)
            fields = {"chapter_id": actual["chapter_id"], field: actual[field]}
            if kind == "quest":
                fields.update(owner_id=db.get(Classroom, classroom_id).teacher_id, title=actual["title"])
                fields["definition"]["id"] = maps[kind][stable]
            else:
                fields["quest_id"] = actual["quest_id"]
            existing = current.get(stable)
            if existing:
                for key, value in fields.items():
                    setattr(existing, key, value)
            else:
                db.add(model(id=maps[kind][stable], stable_id=stable, course_instance_id=instance.id, **fields))
    state = db.get(CoursePackageState, instance.id)
    if not state:
        state = CoursePackageState(course_instance_id=instance.id, configuration={})
        db.add(state)
    state.configuration = {**deepcopy(body.configuration), **{key: value for key, value in state.configuration.items() if key.startswith("_")}}
    instance.revision += 1
    from .character_assets import retire_unused_assets
    retire_unused_assets(state, body.npcs)
    saved_revision = instance.revision
    db.commit()
    # File failures are retried by the periodic worker; the saved draft stays committed.
    from .character_assets import drain_character_garbage
    try:
        from .course_scope import lock_course
        lock_course(db, classroom_id, exclusive=True)
        db.refresh(state)
        drain_character_garbage(db, state)
    except Exception:
        db.rollback()
        import logging
        logging.getLogger(__name__).exception("Character cleanup deferred")
    return {"revision": saved_revision}


@router.post("/preview")
def preview_authoring(classroom_id: int, body: PreviewInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(require_teacher)]):
    from .story import select_flow
    from .story_conditions import condition_matches
    instance = _instance(db, user, classroom_id)
    student = db.get(User, body.student_id)
    if not student or student.role != "student" or not db.get(Membership, (student.id, classroom_id)):
        raise HTTPException(404, "预览学生不属于本班")
    try:
        data, maps = _prepare(db, instance, body)
    except INPUT_ERRORS as exc:
        raise HTTPException(422, _error_detail([_diagnostic(exc)])) from exc
    entry = body.entry if body.entry.startswith("interaction:") else maps["npc"].get(body.entry)
    definitions = {}
    for row in data["quest"]:
        actual = _json(row, maps)
        actual["id"] = maps["quest"][row["id"]]
        actual["definition"]["id"] = actual["id"]
        definitions[actual["id"]] = SimpleNamespace(**actual, course_instance_id=instance.id, owner_id=db.get(Classroom, classroom_id).teacher_id)
    candidates = []
    for row in data["story"]:
        actual = _json(row, maps)
        story = SimpleNamespace(**actual, course_instance_id=instance.id, preview_definitions=definitions)
        candidates.append(story)
    targets = {entry}
    if body.entry.startswith("interaction:"):
        interaction_id = body.entry.removeprefix("interaction:")
        item = next((row for row in body.configuration.get("world", {}).get("interactions", []) if row["id"] == interaction_id), None)
        if not item or not _condition(db, student, classroom_id, item.get("when", {"always": True})):
            return {"selected_flow_id": None, "node_id": None, "trace": [], "stopped_at": "unavailable"}
        targets = {interaction_id}
        if item["target"].get("object_id"):
            targets.add(item["target"]["object_id"])
    else:
        npc = next(row for row in body.npcs if row["id"] == body.entry)
        if npc["placement"] is None or not _condition(db, student, classroom_id, npc["appearance_condition"]):
            return {"selected_flow_id": None, "node_id": None, "trace": [], "stopped_at": "unavailable"}
    selected = select_flow(db, student, classroom_id, entry, candidates, targets=targets)
    if not selected:
        return {"selected_flow_id": None, "node_id": None, "trace": [], "stopped_at": "unavailable"}
    story = selected[0]
    current = next(route["node"] for route in story.content["entries"][entry] if condition_matches(db, student, classroom_id, story, route["when"]))
    nodes, trace = {node["id"]: node for node in story.content["nodes"]}, []
    for _ in range(len(nodes) + 1):
        node = nodes[current]
        trace.append({"node_id": current, "type": node["type"]})
        if node["type"] != "branch":
            break
        current = next(route["node"] for route in node["routes"] if condition_matches(db, student, classroom_id, story, route["when"]))
    return {"selected_flow_id": story.id, "node_id": current, "trace": trace, "stopped_at": node["type"]}
