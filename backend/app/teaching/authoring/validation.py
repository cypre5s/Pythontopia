"""Validate authored world rules and route entry conditions."""

import json
from types import SimpleNamespace

from app.authoring_diagnostics import AuthoringIssue, located, node_label, row_target
from app.course_instances import MODEL_NAMES, remap_references
from app.teaching.world.rules import interaction_points_for, interior_buildings, reachable_cells
from app.world_config import load_world_config, validate_point


def validate_short_text(value, maximum=100):
    return isinstance(value, str) and 0 < len(value) <= maximum and bool(value.strip())


def validate_object(value, allowed, required=()):
    if not isinstance(value, dict) or set(value) - set(allowed) or set(required) - set(value):
        raise ValueError("未知或缺失的课程世界字段")


def flow_title(row):
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
                with located(key, index, target=row_target("node", node), label=node_label(node, index) if isinstance(node, dict) else None):
                    result[key].append(remap_references(node, maps))
        elif key == "entries" and isinstance(value, dict):
            result[key] = {}
            for entry, routes in value.items():
                with located(key, entry, target={"kind": "entry", "id": entry}):
                    result[key].update(remap_references({key: {entry: routes}}, maps)[key])
        elif key == "bindings" and isinstance(value, dict):
            result[key] = {}
            for binding, detail in value.items():
                location = (
                    next(((i, node) for i, node in enumerate(nodes) if isinstance(node, dict) and node.get("binding") == binding), None)
                    if isinstance(nodes, list)
                    else None
                )
                target = row_target("node", location[1]) if location else None
                label = node_label(location[1], location[0]) if location else None
                with located(key, binding, target=target, label=label, suggestion="在学习活动节点的属性里重新选择本班可用活动。"):
                    result[key].update(remap_references({key: {binding: detail}}, maps)[key])
        else:
            with located(key):
                result.update(remap_references({key: value}, maps))
    return result


def name_entry_issue(issue, names, stable_ids=None):
    if issue.target.get("kind") != "entry":
        return
    key = issue.target["id"]
    stable = (stable_ids or {}).get(key, key)
    issue.target["id"] = stable
    if issue.path[:2] == ("entries", key):
        issue.path = ("entries", stable, *issue.path[2:])
    if stable in names:
        issue.label = f"入口「{names[stable]}」"


def validate_configuration(config, bundle_data):
    """Validate portable stable references, including all map shapes and entry conflicts."""
    from app.teaching.conditions.validation import validate_condition
    from app.teaching.story.validation import validate_story

    validate_object(config, {"world", "buildings", "assistant", "editor"})
    maps = {
        kind: {row["id"]: index + 1 if kind in {"chapter", "material", "knowledge_point", "example"} else row["id"] for index, row in enumerate(rows)}
        for kind, rows in bundle_data.items()
    }
    converted = {}
    for kind, rows in bundle_data.items():
        if kind == "story":
            continue
        converted[kind] = {}
        for index, row in enumerate(rows):
            if kind == "quest":
                with located("quests", index, target=row_target("quest", row), label=f"任务「{row['title']}」"):
                    converted[kind][row["id"]] = remap_references(row, maps)
            else:
                converted[kind][row["id"]] = remap_references(row, maps)

    class Catalog:
        def get(self, model, key):
            kind = MODEL_NAMES[model]
            row = next((row for stable, row in converted[kind].items() if maps[kind][stable] == key), None)
            return SimpleNamespace(**row) if row else None

    catalog = Catalog()

    def condition(value, bindings=None, quest=None):
        validate_condition(remap_references(value, maps), bindings or {}, quest, catalog)

    world, buildings = config.get("world", {}), config.get("buildings", {})
    validate_object(world, {"interactions"})
    validate_object(buildings, interior_buildings())
    for building_id, policy in buildings.items():
        with located(
            "configuration",
            "buildings",
            building_id,
            target={"kind": "building", "id": building_id},
            label=f"建筑「{interior_buildings()[building_id]['label']}」",
        ):
            validate_object(policy, {"enabled", "default_unlocked", "when", "blocked_message"}, {"enabled", "default_unlocked", "blocked_message"})
            if (
                type(policy["enabled"]) is not bool
                or type(policy["default_unlocked"]) is not bool
                or not validate_short_text(policy["blocked_message"], 500)
            ):
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
        with located(
            "configuration",
            "world",
            "interactions",
            index,
            target=target,
            label=f"地图交互「{label or index + 1}」",
            suggestion="检查所选地图、触发方式、对象或绘制区域。",
        ):
            validate_object(
                item, {"id", "label", "prompt", "trigger", "map_key", "target", "when"}, {"id", "label", "prompt", "trigger", "map_key", "target"}
            )
            if (
                not validate_short_text(item["id"], 80)
                or item["id"] in ids
                or not validate_short_text(item["label"], 200)
                or not validate_short_text(item["prompt"], 500)
            ):
                raise ValueError("交互ID重复或名称/提示无效")
            ids.add(item["id"])
            if item["trigger"] not in {"click", "enter"} or item["map_key"] not in load_world_config()["maps"]:
                raise ValueError("交互触发方式或地图无效")
            target = item["target"]
            validate_object(target, {"object_id", "region"})
            if len(target) != 1:
                raise ValueError("交互需要一个对象或区域")
            if "object_id" in target and (not validate_short_text(target["object_id"]) or item["trigger"] == "enter"):
                raise ValueError("对象交互仅支持点击")
            if "region" in target:
                region = target["region"]
                validate_object(region, {"x", "y", "width", "height"}, {"x", "y", "width", "height"})
                if any(type(n) is not int for n in region.values()) or not 1 <= region["width"] <= 32 or not 1 <= region["height"] <= 32:
                    raise ValueError("区域大小无效")
                validate_point(item["map_key"], region["x"], region["y"])
                validate_point(item["map_key"], region["x"] + region["width"] - 1, region["y"] + region["height"] - 1)
            points = interaction_points_for(item)
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
    from app.characters import validate_references

    validate_references(bundle_data)
    entry_names = {**{row["id"]: row["name"] for row in bundle_data["npc"]}, **{f"interaction:{item['id']}": item["label"] for item in interactions}}
    priorities = {}
    checkpoints = set()
    for index, row in enumerate(bundle_data["story"]):
        with located("flows", index, "content", target=row_target("flow", row, flow_id=row["id"]), label=f"流程「{flow_title(row)}」"):
            content = row["content"]
            quest = converted["quest"].get(row["quest_id"])
            try:
                runtime = _portable_story(content, maps)
                validate_story(runtime, quest_definition=quest["definition"] if quest else None, story_id=row["id"])
            except AuthoringIssue as issue:
                name_entry_issue(issue, entry_names)
                raise
            if row["chapter_id"] is not None and row["chapter_id"] not in maps["chapter"] or row["quest_id"] is not None and quest is None:
                raise ValueError("流程引用了不存在的章节或任务")
            if quest and quest["chapter_id"] != maps["chapter"].get(row["chapter_id"]):
                raise ValueError("流程与任务章节不一致")
            if row["quest_id"] is None and content.get("mode") not in {"public", "ambient"}:
                raise ValueError("无任务流程必须声明公共业务或日常模式")
            if row["quest_id"] is not None and content.get("mode", "task") != "task":
                raise ValueError("任务流程必须声明task模式")
            checkpoints.update(
                (row["quest_id"], node["checkpoint"], f"story:{row['id']}:{node['checkpoint']}")
                for node in content["nodes"]
                if node.get("checkpoint")
            )

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
                with located(
                    "entries", entry, target={"kind": "entry", "id": entry}, label=f"入口「{entry_names[entry]}」" if entry in entry_names else None
                ):
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
                        raise AuthoringIssue(
                            f"同一入口的优先级与流程「{priorities[key]}」重复。",
                            code="entry_priority_conflict",
                            suggestion="打开流程设置与入口，设置不同的入口优先级。",
                        )
                    priorities[key] = flow_title(row)
    for quest_index, row in enumerate(bundle_data["quest"]):
        for goal_index, goal in enumerate(row["definition"]["nodes"]):
            marker = goal.get("dialogue_id")
            if goal.get("type") in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"} and not any(
                quest == row["id"] and node == goal["id"] and (goal["type"] != "PLAY_DIALOGUE" or checkpoint == marker)
                for quest, node, checkpoint in checkpoints
            ):
                raise AuthoringIssue(
                    f"任务「{row['title']}」的目标「{goal['title']}」仍引用已移除的流程对白。",
                    path=("quests", quest_index, "definition", "nodes", goal_index, "dialogue_id"),
                    target=row_target("goal", goal, quest_id=row["id"]),
                    code="missing_dialogue_checkpoint",
                    suggestion="恢复原对白检查点，或同步调整这个任务目标后再保存。",
                )
    from app.course_assistant import validate_policy

    with located("configuration", "assistant", target={"kind": "assistant"}, label="课程设置 · AI 助教"):
        validate_policy(config.get("assistant", {}), bundle_data)
    # Layouts are data only. The exchange parser already bounds size/depth; HTTP
    # authoring has the same explicit cap and never evaluates editor content.
    editor = config.get("editor", {})
    if not isinstance(editor, dict) or len(json.dumps(editor, ensure_ascii=False)) > 200_000:
        raise ValueError("编辑布局必须为有界对象")
    return config
