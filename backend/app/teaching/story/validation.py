"""Validate story nodes, branches, bindings and terminal reachability."""

from sqlalchemy.orm import Session

from app.authoring_diagnostics import AuthoringIssue, located, node_label, row_target
from app.content_relations import material_in_chapter
from app.models import Activity, Material, Npc
from app.teaching.conditions.validation import validate_condition


def _short(value):
    return isinstance(value, str) and 0 < len(value) <= 100 and bool(value.strip())


def validate_story(content: dict, db: Session | None = None, quest_definition: dict | None = None, story_id: str | None = None) -> dict:
    if not isinstance(content, dict) or set(content) - {
        "schema_version",
        "bindings",
        "entries",
        "nodes",
        "checkpoint_effects",
        "mode",
        "when",
        "priority",
        "title",
    }:
        raise ValueError("无效演出字段")
    if type(content.get("schema_version")) is not int or content["schema_version"] != 1:
        raise ValueError("不支持的演出格式版本")
    bindings, entries, nodes = content.get("bindings"), content.get("entries"), content.get("nodes")
    if not isinstance(bindings, dict) or len(bindings) > 30 or not isinstance(entries, dict) or not 1 <= len(entries) <= 30:
        raise AuthoringIssue(
            "流程需要 1 至 30 个角色或地图入口，活动绑定最多 30 项",
            code="invalid_flow_entries",
            suggestion="打开流程设置与入口，添加入口并连到起始节点。",
        )
    mode = content.get("mode", "task")
    if (
        not isinstance(mode, str)
        or mode not in {"task", "ambient", "public"}
        or mode == "ambient"
        and (quest_definition is not None or bindings or content.get("checkpoint_effects"))
    ):
        raise ValueError("日常对白不能绑定任务、活动或效果")
    if type(content.get("priority", 0)) is not int or not -1000 <= content.get("priority", 0) <= 1000:
        raise ValueError("流程优先级必须为-1000至1000整数")
    if "title" in content and (not isinstance(content["title"], str) or not 1 <= len(content["title"]) <= 200):
        raise ValueError("流程标题无效")
    if "when" in content:
        validate_condition(content["when"], bindings, quest_definition, db)
    goals = {node["id"]: node for node in quest_definition["nodes"]} if quest_definition else None
    for key, binding in bindings.items():
        fields = {"activity_id"} if mode == "public" else {"activity_id", "goal_id"}
        if not _short(key) or not isinstance(binding, dict) or set(binding) != fields:
            raise ValueError("无效活动绑定")
        if not _short(binding["activity_id"]) or mode != "public" and not _short(binding["goal_id"]):
            raise ValueError("无效活动或目标")
        if db:
            activity = db.get(Activity, binding["activity_id"])
            if not activity:
                raise ValueError("活动绑定不存在")
            if activity.content.get("published") is False:
                raise ValueError("演出不能绑定未发布活动")
            if quest_definition and activity.chapter_id != quest_definition["chapter_id"]:
                raise ValueError("演出活动必须属于当前章节")
            if activity.kind in {"lesson", "reading"}:
                from app.learning_rules import has_completion_rule, validate_learning

                if not has_completion_rule(activity):
                    raise ValueError("演出阅读活动必须声明阅读参与完成规则")
                for resource in activity.resources:
                    material = db.get(Material, resource.material_id)
                    if not material or not material_in_chapter(db, material.id, activity.chapter_id):
                        raise ValueError("学习资料必须属于活动章节")
                    validate_learning({"learning": resource.learning}, activity.kind, material, resolve=True)
        if goals is not None:
            goal = goals.get(binding["goal_id"])
            if not goal or goal.get("activity_id") != binding["activity_id"]:
                raise ValueError("活动绑定与任务目标不一致")
            if goal["type"] not in {"STUDY_LESSON", "COMPLETE_PRACTICE", "PASS_ASSESSMENT"}:
                raise ValueError("活动绑定必须引用学习或正式练习目标")
    if not isinstance(nodes, list) or not 1 <= len(nodes) <= 200 or any(not isinstance(node, dict) or not _short(node.get("id")) for node in nodes):
        raise ValueError("演出需要1至200个有效节点")
    mapping = {node["id"]: node for node in nodes}
    if len(mapping) != len(nodes):
        raise ValueError("演出节点ID重复")
    links = {}
    checkpoints = set()
    for index, node in enumerate(nodes):
        with located("nodes", index, target=row_target("node", node), label=node_label(node, index)):
            kind = node.get("type")
            targets = []
            if kind == "dialogue":
                if set(node) - {"id", "type", "text", "speaker", "speaker_npc_id", "next", "choices", "checkpoint"}:
                    raise ValueError("未知对白字段")
                if not isinstance(node.get("text"), str) or not 1 <= len(node["text"]) <= 8000:
                    raise ValueError("对白文本缺失或过长")
                if "speaker" in node and (not isinstance(node["speaker"], str) or len(node["speaker"]) > 80):
                    raise ValueError("对白说话人无效")
                if "speaker_npc_id" in node:
                    if "speaker" in node or not _short(node["speaker_npc_id"]) or (db is not None and not db.get(Npc, node["speaker_npc_id"])):
                        raise ValueError("对白角色引用无效或与自定义说话人冲突")
                choices = node.get("choices", [])
                if not isinstance(choices, list) or len(choices) > 10:
                    raise ValueError("对白选项过多")
                for choice in choices:
                    if (
                        not isinstance(choice, dict)
                        or set(choice) - {"id", "label", "next", "when"}
                        or not {"id", "label", "next"}.issubset(choice)
                        or not _short(choice["id"])
                    ):
                        raise ValueError("无效对白选项")
                    if "when" in choice:
                        validate_condition(choice["when"], bindings, quest_definition, db)
                    if not isinstance(choice["label"], str) or not 1 <= len(choice["label"]) <= 200:
                        raise ValueError("无效选项文本")
                    targets.append(choice["next"])
                if len({choice["id"] for choice in choices}) != len(choices) or (choices and node.get("next")):
                    raise ValueError("选项ID重复或与next冲突")
                if node.get("next"):
                    targets.append(node["next"])
                if "checkpoint" in node:
                    if mode != "task" or goals is None:
                        raise AuthoringIssue(
                            "只有绑定任务的流程才能设置对白检查点",
                            path=("checkpoint",),
                            suggestion="清除这个节点的交谈目标，或将流程绑定到相应任务。",
                        )
                    goal_id = node["checkpoint"]
                    if not _short(goal_id) or (
                        goals is not None and (goal_id not in goals or goals[goal_id]["type"] not in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"})
                    ):
                        raise ValueError("对白检查点必须引用交谈目标")
                    if goals is not None and story_id is not None and goals[goal_id]["type"] == "PLAY_DIALOGUE":
                        expected = f"story:{story_id}:{goal_id}"
                        if goals[goal_id].get("dialogue_id") != expected:
                            raise ValueError("对白检查点标记不匹配")
                    checkpoints.add(goal_id)
            elif kind == "activity":
                if mode == "ambient":
                    raise ValueError("日常对白不能打开学习活动")
                if set(node) != {"id", "type", "binding", "returns"} or not _short(node.get("binding")) or node["binding"] not in bindings:
                    raise ValueError("无效活动节点")
                returns = node.get("returns")
                if not isinstance(returns, dict) or set(returns) != {"completed", "suspended", "unavailable"}:
                    raise ValueError("活动需要完成、暂停和不可用返回分支")
                targets += list(returns.values())
            elif kind == "branch":
                if set(node) != {"id", "type", "routes"}:
                    raise ValueError("无效条件分支节点")
                with located("routes"):
                    _validate_routes(node["routes"], bindings, quest_definition, db)
                targets += [route["node"] for route in node["routes"]]
            elif kind == "random":
                if (
                    set(node) != {"id", "type", "next"}
                    or not isinstance(node["next"], list)
                    or not 1 <= len(node["next"]) <= 20
                    or any(not _short(target) for target in node["next"])
                    or len(set(node["next"])) != len(node["next"])
                ):
                    raise ValueError("随机分支需要1至20个不同节点")
                targets += node["next"]
            elif kind == "action":
                required = {"id", "type", "action", "next", "workspace" if node.get("action") == "open_workspace" else "building_id"}
                if set(node) != required:
                    raise ValueError("无效界面操作节点")
                if node["action"] == "open_workspace":
                    if node["workspace"] not in {"library", "journal", "classroom"}:
                        raise ValueError("不支持的工作区操作")
                elif node["action"] == "enter_building":
                    from app.teaching.world.rules import interior_buildings

                    if node["building_id"] not in interior_buildings():
                        raise ValueError("建筑没有可用室内")
                else:
                    raise ValueError("不支持的操作")
                targets.append(node["next"])
            elif kind == "unlock_building":
                from app.teaching.world.rules import completion_predicate, interior_buildings

                if mode == "ambient" or set(node) != {"id", "type", "building_id", "when", "next"} or node["building_id"] not in interior_buildings():
                    raise ValueError("无效建筑解锁节点")
                validate_condition(node["when"], bindings, quest_definition, db)
                if not completion_predicate(node["when"]):
                    raise ValueError("解锁必须绑定真实活动、章节或任务完成证据")
                targets.append(node["next"])
            elif kind == "end":
                if set(node) != {"id", "type"}:
                    raise ValueError("无效结束节点")
            else:
                raise ValueError("未知演出节点类型")
            if any(not _short(target) or target not in mapping for target in targets):
                raise AuthoringIssue("有输出未连接，或指向已删除的节点", code="missing_link", suggestion="把相应的输出端口重新连到画布上的节点。")
            links[node["id"]] = targets
    roots = []
    for npc_key, routes in entries.items():
        with located("entries", npc_key, target={"kind": "entry", "id": npc_key}):
            if not _short(npc_key) or (db and not npc_key.startswith("interaction:") and not db.get(Npc, npc_key)):
                raise ValueError("入口NPC不存在")
            _validate_routes(routes, bindings, quest_definition, db)
            for route in routes:
                if route["node"] not in mapping:
                    raise AuthoringIssue("入口指向已删除的节点", code="missing_link", suggestion="把入口端口重新连到起始节点。")
                roots.append(route["node"])
            if goals is not None:
                checked = set()

                def check_npc(node_id):
                    if node_id in checked:
                        return
                    checked.add(node_id)
                    node = mapping[node_id]
                    if node.get("checkpoint") and goals[node["checkpoint"]].get("target") != npc_key:
                        raise ValueError("对白检查点与入口NPC不匹配")
                    if node["type"] == "activity":
                        goal = goals[bindings[node["binding"]]["goal_id"]]
                        target = goal.get("target") or goal.get("navigation_target")
                        if target and target != npc_key:
                            raise ValueError("活动目标与入口NPC不匹配")
                    for target in links[node_id]:
                        check_npc(target)

                for route in routes:
                    check_npc(route["node"])
    effects = content.get("checkpoint_effects", {})
    if not isinstance(effects, dict) or set(effects) - checkpoints:
        raise ValueError("检查点效果必须引用实际对白检查点")
    for values in effects.values():
        from app.teaching.world.rules import interior_buildings

        if (
            not isinstance(values, list)
            or not 1 <= len(values) <= 20
            or any(
                not isinstance(effect, dict)
                or set(effect) != {"type", "building_id"}
                or effect["type"] != "unlock_building"
                or effect["building_id"] not in interior_buildings()
                for effect in values
            )
        ):
            raise ValueError("检查点仅支持建筑解锁效果")
    node_indices = {node["id"]: index for index, node in enumerate(nodes)}
    visiting, visited = set(), set()

    def visit(key):
        if key in visiting:
            raise AuthoringIssue(
                "此处的连线形成了循环",
                path=("nodes", node_indices[key]),
                target={"kind": "node", "id": key},
                label=node_label(mapping[key], node_indices[key]),
                code="flow_cycle",
                suggestion="删除返回上游节点的连线，重访流程请使用入口。",
            )
        if key in visited:
            return
        visiting.add(key)
        for target in links[key]:
            visit(target)
        visiting.remove(key)
        visited.add(key)

    for root in roots:
        visit(root)
    if len(visited) != len(nodes):
        index, node = next((index, node) for index, node in enumerate(nodes) if node["id"] not in visited)
        raise AuthoringIssue(
            "演出包含不可达节点",
            path=("nodes", index),
            target=row_target("node", node),
            label=node_label(node, index),
            code="unreachable_node",
            suggestion="从一个入口接到这个节点，或删除不再使用的节点。",
        )
    return content


def _validate_routes(routes, bindings, quest_definition, db):
    if not isinstance(routes, list) or not 1 <= len(routes) <= 30:
        raise ValueError("入口或分支必须是有界列表")
    for index, route in enumerate(routes):
        if isinstance(route, dict) and route.get("node") == "":
            raise AuthoringIssue(
                f"第 {index + 1} 个分支尚未连接", path=(index, "node"), code="missing_link", suggestion="将未连接的输出端口连到目标节点。"
            )
        if not isinstance(route, dict) or set(route) != {"when", "node"} or not _short(route.get("node")):
            raise ValueError("无效演出分支")
        validate_condition(route["when"], bindings, quest_definition, db)
        if route["when"] == {"always": True} and index != len(routes) - 1:
            raise ValueError("兜底入口必须位于最后")
    if routes[-1]["when"] != {"always": True}:
        raise ValueError("入口或分支缺少兜底分支")
