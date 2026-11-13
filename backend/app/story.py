"""Scripted story scenes. All progress is verified by business services."""

import hashlib
import json
import secrets
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field, model_validator
from sqlalchemy import select
from sqlalchemy.orm import Session

from .auth import current_user
from .authoring_diagnostics import AuthoringIssue, located, node_label, row_target
from .courses import authorize_activity, authorize_chapter, authorize_classroom, chapter_available, material_payload, authorize_material
from .content_relations import material_in_chapter
from .db import get_db
from .course_scope import belongs_to_course, course_clause, require_course_content
from .models import Activity, ActivityProgress, LearningRecord, Material, Npc, NodeState, PlayerPosition, PlayerQuest, QuestDefinition, StoryDefinition, StorySession, User, WorldFlag
from .quests import EventInput, apply_quest_event, authorize_quest, check_near, ensure_quests, node_statuses, quest_visible, validate_evidence, world_payload
from .story_conditions import condition_matches, validate_condition

router = APIRouter(tags=["story"])


def _short(value):
    return isinstance(value, str) and 0 < len(value) <= 100 and bool(value.strip())


def validate_story(
    content: dict, db: Session | None = None, quest_definition: dict | None = None, story_id: str | None = None
) -> dict:
    if not isinstance(content, dict) or set(content) - {"schema_version", "bindings", "entries", "nodes", "checkpoint_effects", "mode", "when", "priority", "title"}:
        raise ValueError("无效演出字段")
    if type(content.get("schema_version")) is not int or content["schema_version"] != 1:
        raise ValueError("不支持的演出格式版本")
    bindings, entries, nodes = content.get("bindings"), content.get("entries"), content.get("nodes")
    if not isinstance(bindings, dict) or len(bindings) > 30 or not isinstance(entries, dict) or not 1 <= len(entries) <= 30:
        raise AuthoringIssue("流程需要 1 至 30 个角色或地图入口，活动绑定最多 30 项", code="invalid_flow_entries", suggestion="打开流程设置与入口，添加入口并连到起始节点。")
    mode = content.get("mode", "task")
    if not isinstance(mode, str) or mode not in {"task", "ambient", "public"} or mode == "ambient" and (quest_definition is not None or bindings or content.get("checkpoint_effects")):
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
                from .learning_rules import has_completion_rule, validate_learning

                if not has_completion_rule(activity):
                    raise ValueError("演出阅读活动必须声明阅读参与完成规则")
                material = db.get(Material, activity.material_id) if activity.material_id else None
                if material and not material_in_chapter(db, material.id, activity.chapter_id):
                    raise ValueError("学习资料必须属于活动章节")
                validate_learning(activity.content, activity.kind, material, resolve=True)
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
                    if not isinstance(choice, dict) or set(choice) - {"id", "label", "next", "when"} or not {"id", "label", "next"}.issubset(choice) or not _short(choice["id"]):
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
                        raise AuthoringIssue("只有绑定任务的流程才能设置对白检查点", path=("checkpoint",),
                                             suggestion="清除这个节点的交谈目标，或将流程绑定到相应任务。")
                    goal_id = node["checkpoint"]
                    if not _short(goal_id) or (goals is not None and (goal_id not in goals or goals[goal_id]["type"] not in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"})):
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
                if (set(node) != {"id", "type", "next"} or not isinstance(node["next"], list) or not 1 <= len(node["next"]) <= 20
                        or any(not _short(target) for target in node["next"]) or len(set(node["next"])) != len(node["next"])):
                    raise ValueError("随机分支需要1至20个不同节点")
                targets += node["next"]
            elif kind == "action":
                required = {"id", "type", "action", "next", "workspace" if node.get("action") == "open_workspace" else "building_id"}
                if not required <= set(node) <= required | {"text"} or (
                    "text" in node and (not isinstance(node["text"], str) or not 1 <= len(node["text"]) <= 1000)
                ):
                    raise ValueError("无效界面操作节点")
                if node["action"] == "open_workspace":
                    if node["workspace"] not in {"library", "journal", "classroom"}:
                        raise ValueError("不支持的工作区操作")
                elif node["action"] == "enter_building":
                    from .world_authoring import interior_buildings
                    if node["building_id"] not in interior_buildings():
                        raise ValueError("建筑没有可用室内")
                else:
                    raise ValueError("不支持的操作")
                targets.append(node["next"])
            elif kind == "unlock_building":
                from .world_authoring import interior_buildings, completion_predicate
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
        from .world_authoring import interior_buildings
        if not isinstance(values, list) or not 1 <= len(values) <= 20 or any(not isinstance(effect, dict)
            or set(effect) != {"type", "building_id"} or effect["type"] != "unlock_building" or effect["building_id"] not in interior_buildings() for effect in values):
            raise ValueError("检查点仅支持建筑解锁效果")
    node_indices = {node["id"]: index for index, node in enumerate(nodes)}
    visiting, visited = set(), set()

    def visit(key):
        if key in visiting:
            raise AuthoringIssue("此处的连线形成了循环", path=("nodes", node_indices[key]), target={"kind": "node", "id": key}, label=node_label(mapping[key], node_indices[key]), code="flow_cycle", suggestion="删除返回上游节点的连线，重访流程请使用入口。")
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
        raise AuthoringIssue("演出包含不可达节点", path=("nodes", index), target=row_target("node", node), label=node_label(node, index), code="unreachable_node", suggestion="从一个入口接到这个节点，或删除不再使用的节点。")
    return content


def _validate_routes(routes, bindings, quest_definition, db):
    if not isinstance(routes, list) or not 1 <= len(routes) <= 30:
        raise ValueError("入口或分支必须是有界列表")
    for index, route in enumerate(routes):
        if isinstance(route, dict) and route.get("node") == "":
            raise AuthoringIssue(f"第 {index + 1} 个分支尚未连接", path=(index, "node"), code="missing_link", suggestion="将未连接的输出端口连到目标节点。")
        if not isinstance(route, dict) or set(route) != {"when", "node"} or not _short(route.get("node")):
            raise ValueError("无效演出分支")
        validate_condition(route["when"], bindings, quest_definition, db)
        if route["when"] == {"always": True} and index != len(routes) - 1:
            raise ValueError("兜底入口必须位于最后")
    if routes[-1]["when"] != {"always": True}:
        raise ValueError("入口或分支缺少兜底分支")


class InteractionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    npc_key: str | None = Field(default=None, min_length=1, max_length=80)
    interaction_id: str | None = Field(default=None, min_length=1, max_length=80)

    @model_validator(mode="after")
    def target(self):
        if (self.npc_key is None) == (self.interaction_id is None):
            raise ValueError("必须指定一个NPC或地图交互入口")
        return self


class AdvanceInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    event_id: str = Field(min_length=1, max_length=100)
    revision: int = Field(ge=0)
    choice: str | None = Field(default=None, max_length=100)


class ReturnInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    event_id: str = Field(min_length=1, max_length=100)
    revision: int = Field(ge=0)


def _student_lock(db, user, classroom_id):
    authorize_classroom(db, user, classroom_id)
    if user.role != "student":
        raise HTTPException(403, "演出存档仅供学生使用")
    db.execute(select(User.id).where(User.id == user.id).with_for_update())


def _open_session(db, user, classroom_id):
    return db.scalar(select(StorySession).where(StorySession.user_id == user.id, StorySession.classroom_id == classroom_id,
                                               StorySession.status.in_(["active", "waiting"])).with_for_update())


def _definition(db, user, session):
    story = db.get(StoryDefinition, session.story_id)
    if not story:
        raise HTTPException(409, "演出不存在")
    require_course_content(db, story, session.classroom_id)
    if story.chapter_id is not None:
        authorize_chapter(db, user, session.classroom_id, story.chapter_id)
    if story.quest_id is None:
        if story.content.get("mode") not in {"ambient", "public"}:
            raise HTTPException(409, "日常对白配置无效")
        return story, None
    definition = authorize_quest(db, user, session.classroom_id, story.quest_id)
    if definition.chapter_id != story.chapter_id:
        raise HTTPException(409, "演出与当前任务章节不一致")
    instance = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == session.classroom_id,
                                                  PlayerQuest.quest_id == story.quest_id))
    if not instance:
        raise HTTPException(403, "演出不属于当前任务")
    return story, instance


def _states(db, instance):
    definition = db.get(QuestDefinition, instance.quest_id)
    states = {state.node_id: state for state in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))}
    return definition.definition, node_statuses(definition.definition, states)


def _legacy_ready(db, user, classroom_id, npc_key):
    if npc_key is None:
        return False
    classroom = authorize_classroom(db, user, classroom_id)
    for item in db.scalars(select(QuestDefinition).where(course_clause(db, QuestDefinition, classroom_id))):
        if not quest_visible(item, user, classroom, db) or not chapter_available(db, user, classroom_id, item.chapter_id):
            continue
        if db.scalar(select(StoryDefinition.id).where(StoryDefinition.quest_id == item.id)):
            continue
        instance = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id,
                                                      PlayerQuest.quest_id == item.id))
        if instance and instance.status == "complete":
            continue
        definition = item.definition
        statuses = _states(db, instance)[1] if instance else node_statuses(definition, {})
        if any(statuses[node["id"]] == "ready" and (node.get("target") == npc_key or node.get("navigation_target") == npc_key)
               for node in definition["nodes"]):
            return True
    return False


def _near(db, user, session):
    from .world_authoring import npc_present, require_interaction
    if session.anchor:
        check_near(db, user, session.classroom_id, tuple(session.anchor[key] for key in ("map_key", "tile_x", "tile_y")), radius=session.anchor.get("radius", 3))
        return
    if session.interaction_id:
        require_interaction(db, user, session.classroom_id, session.interaction_id)
        return
    npc = db.get(Npc, session.npc_key)
    require_course_content(db, npc, session.classroom_id)
    if not npc_present(db, user, session.classroom_id, npc):
        raise HTTPException(403, "当前NPC尚未出现")
    check_near(db, user, session.classroom_id, (npc.map_key, npc.tile_x, npc.tile_y))


def _nodes(story):
    return {node["id"]: node for node in story.content["nodes"]}


def _available_activity(db, user, session, binding):
    try:
        activity = authorize_activity(db, user, session.classroom_id, binding["activity_id"])
        if activity.material_id:
            material = authorize_material(db, user, session.classroom_id, activity.material_id)
            if material_payload(db, material)["status"] != "ready":
                return None
        return activity
    except HTTPException as exc:
        if exc.status_code not in {404, 409, 503}:
            raise
        return None


def _settle(db, user, session, story):
    mapping = _nodes(story)
    for _ in range(len(mapping) + 1):
        node = mapping[session.node_id]
        if node["type"] == "end":
            session.status = "ended"
            session.pending_activity_id = None
            session.return_mapping = {}
            return
        if node["type"] in {"dialogue", "action"}:
            session.status = "active"
            return
        if node["type"] == "branch":
            session.node_id = next(route["node"] for route in node["routes"]
                                   if condition_matches(db, user, session.classroom_id, story, route["when"]))
            continue
        if node["type"] == "random":
            session.node_id = secrets.choice(node["next"])
            continue
        if node["type"] == "unlock_building":
            from .world_authoring import unlock_building
            if not condition_matches(db, user, session.classroom_id, story, node["when"]):
                raise HTTPException(409, "尚未获得建筑解锁所需的真实完成证据")
            unlock_building(db, user, session.classroom_id, node["building_id"])
            session.node_id = node["next"]
            continue
        binding = story.content["bindings"][node["binding"]]
        _, instance = _definition(db, user, session)
        statuses = _states(db, instance)[1] if instance else None
        if statuses is not None and statuses[binding["goal_id"]] not in {"ready", "complete"}:
            session.node_id = node["returns"]["unavailable"]
            continue
        activity = _available_activity(db, user, session, binding)
        if not activity:
            session.node_id = node["returns"]["unavailable"]
            continue
        session.status = "waiting"
        session.pending_activity_id = activity.id
        session.return_mapping = node["returns"]
        return
    raise HTTPException(409, "演出自动推进超过限制")


def _view(db, user, session):
    if session.status == "ended":
        story = db.get(StoryDefinition, session.story_id)
        return {"id": session.id, "revision": session.revision, "status": "ended",
                "mode": story.content.get("mode", "task") if story else "public", "npc_key": session.npc_key,
                "interaction_id": session.interaction_id, "node": None, "activity": None}
    story, _ = _definition(db, user, session)
    node, activity = None, None
    if session.status == "active":
        source = _nodes(story)[session.node_id]
        node = {key: source[key] for key in ("id", "type", "text", "speaker", "action", "workspace", "building_id") if key in source}
        if source["type"] == "dialogue":
            from .characters import presentation
            speaker_key = source.get("speaker_npc_id", session.npc_key if "speaker" not in source else None)
            speaker = db.get(Npc, speaker_key) if speaker_key else None
            identity = presentation(db, session.classroom_id, speaker)
            if identity:
                node.update(speaker=identity["name"], portrait=identity["portrait"])
            else:
                node["portrait"] = ""
        if source["type"] == "action":
            node.setdefault("text", "")
        rounds = source.get("text", "").split("\n\n")
        round_index = session.return_mapping.get("dialogue_round", 0) if source["type"] == "dialogue" else 0
        if source["type"] == "dialogue":
            node["text"] = rounds[min(round_index, len(rounds) - 1)]
        if "choices" in source and round_index >= len(rounds) - 1:
            node["choices"] = [{key: choice[key] for key in ("id", "label")} for choice in _choices(db, user, session, story, source)]
    elif session.status == "waiting":
        item = authorize_activity(db, user, session.classroom_id, session.pending_activity_id)
        progress = db.get(ActivityProgress, (user.id, session.classroom_id, item.id))
        activity = {"id": item.id, "kind": item.kind, "page_no": progress.page_no if progress else None}
    return {"id": session.id, "revision": session.revision, "status": session.status, "mode": story.content.get("mode", "task"),
            "npc_key": session.npc_key, "interaction_id": session.interaction_id, "node": node, "activity": activity}


def _choices(db, user, session, story, node):
    return [choice for choice in node.get("choices", []) if "when" not in choice
            or condition_matches(db, user, session.classroom_id, story, choice["when"])]


def _response(db, user, session):
    result = _view(db, user, session)
    result["world"] = world_payload(db, user, session.classroom_id, initialize_quests=result["mode"] != "ambient")
    return result


def select_flow(db, user, classroom_id, entry, stories, *, targets=None):
    """One deterministic resolver shared by the runtime and read-only preview."""
    classroom = authorize_classroom(db, user, classroom_id)
    targets = {entry} if targets is None else targets
    candidates = []
    for story in stories:
        if entry not in story.content["entries"] or story.chapter_id is not None and not chapter_available(db, user, classroom_id, story.chapter_id):
            continue
        if not condition_matches(db, user, classroom_id, story, story.content.get("when", {"always": True})):
            continue
        instance = None
        if story.quest_id is None:
            if story.content.get("mode") not in {"ambient", "public"}:
                continue
            layer = 1 if story.chapter_id is not None else 2
        else:
            definition = getattr(story, "preview_definitions", {}).get(story.quest_id) or db.get(QuestDefinition, story.quest_id)
            if not quest_visible(definition, user, classroom, db) or definition.chapter_id != story.chapter_id:
                continue
            instance = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == user.id,
                PlayerQuest.classroom_id == classroom_id, PlayerQuest.quest_id == story.quest_id))
            states = {row.node_id: row for row in db.scalars(select(NodeState).where(NodeState.player_quest_id == instance.id))} if instance else {}
            statuses = node_statuses(definition.definition, states)
            ready = (not instance or instance.status != "complete") and any(statuses[node["id"]] == "ready"
                and (node.get("target") in targets or node.get("navigation_target") in targets) for node in definition.definition["nodes"])
            layer = 0 if ready else 3 if not instance or instance.status != "complete" else 4
        candidates.append((layer, -story.content.get("priority", 0), story, instance))
    candidates.sort(key=lambda row: row[:2])
    if len(candidates) > 1 and candidates[0][:2] == candidates[1][:2]:
        raise HTTPException(409, "流程入口优先级冲突，请联系教师")
    return candidates[0][2:] if candidates else None


@router.post("/story/interactions")
def interact(body: InteractionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    classroom = authorize_classroom(db, user, body.classroom_id)
    _student_lock(db, user, body.classroom_id)
    from .world_authoring import npc_present, require_interaction
    if body.interaction_id:
        require_interaction(db, user, body.classroom_id, body.interaction_id)
    else:
        npc = db.get(Npc, body.npc_key)
        if not npc:
            raise HTTPException(404, "NPC不存在")
        require_course_content(db, npc, body.classroom_id)
        if not npc_present(db, user, body.classroom_id, npc):
            raise HTTPException(403, "当前NPC尚未出现")
        check_near(db, user, body.classroom_id, (npc.map_key, npc.tile_x, npc.tile_y))
    entry = f"interaction:{body.interaction_id}" if body.interaction_id else body.npc_key
    previous = _open_session(db, user, body.classroom_id)
    abandoned = False
    if previous:
        previous_story = db.get(StoryDefinition, previous.story_id)
        previous_quest = db.get(QuestDefinition, previous_story.quest_id) if previous_story and previous_story.quest_id is not None else None
        if (not belongs_to_course(db, previous_story, body.classroom_id) or previous_story.quest_id is not None and not quest_visible(previous_quest, user, classroom, db)
                or (previous_story.chapter_id is not None and not chapter_available(db, user, body.classroom_id, previous_story.chapter_id))):
            previous.status = "ended"
            previous.revision += 1
            previous.pending_activity_id = None
            previous.return_mapping = {}
            db.flush()
            previous = None
            abandoned = True
    if previous and previous.status == "active":
        _, instance = _definition(db, user, previous)
        if instance and instance.status == "complete" and _legacy_ready(db, user, body.classroom_id, body.npc_key):
            ensure_quests(db, user, body.classroom_id)
            _student_lock(db, user, body.classroom_id)
            previous.status = "ended"
            db.commit()
            raise HTTPException(404, "当前目标使用既有章节对白")
    if previous:
        if previous.npc_key == body.npc_key and previous.interaction_id == body.interaction_id:
            return _response(db, user, previous)
        raise HTTPException(409, "请先结束当前流程，再打开另一交互")
    targets = {body.npc_key} if body.npc_key else {body.interaction_id}
    if body.interaction_id:
        object_id = require_interaction(db, user, body.classroom_id, body.interaction_id)["target"].get("object_id")
        if object_id:
            targets.add(object_id)
    selected = select_flow(db, user, body.classroom_id, entry,
        db.scalars(select(StoryDefinition).where(course_clause(db, StoryDefinition, body.classroom_id))), targets=targets)
    if _legacy_ready(db, user, body.classroom_id, body.npc_key) and (
            not selected or selected[0].quest_id is None or selected[1] is not None and selected[1].status == "complete"):
        ensure_quests(db, user, body.classroom_id)
        _student_lock(db, user, body.classroom_id)
        if previous:
            previous.status = "ended"
        db.commit()
        raise HTTPException(404, "当前目标使用既有章节对白")
    if not selected:
        if abandoned:
            db.commit()
        raise HTTPException(404, "此NPC尚未绑定当前演出")
    story, instance = selected
    if previous:
        previous_story = db.get(StoryDefinition, previous.story_id)
        if previous_story.quest_id is not None and (previous.npc_key == body.npc_key or story.quest_id is None):
            return _response(db, user, previous)
        if previous_story.quest_id is None and story.quest_id is None and previous.npc_key == body.npc_key:
            return _response(db, user, previous)
    if story.quest_id is not None:
        ensure_quests(db, user, body.classroom_id, commit=False)
        _student_lock(db, user, body.classroom_id)
    root = next(route["node"] for route in story.content["entries"][entry]
                if condition_matches(db, user, body.classroom_id, story, route["when"]))
    if previous:
        previous.status = "ended"
        db.flush()
    session = StorySession(user_id=user.id, classroom_id=body.classroom_id, story_id=story.id,
                           npc_key=body.npc_key, interaction_id=body.interaction_id, node_id=root, status="active", revision=0, receipts=[])
    # Appearance/entry conditions may change as this flow supplies real evidence.
    # Retain the already-authorized physical interaction anchor while its current
    # chapter and course are reauthorized on every subsequent operation.
    from .world_config import load_world_config
    position = db.get(PlayerPosition, (user.id, body.classroom_id))
    player = ({key: getattr(position, key) for key in ("map_key", "tile_x", "tile_y")} if position
              else {key: load_world_config()["default_spawn"][key] for key in ("map_key", "tile_x", "tile_y")})
    if body.interaction_id:
        item = require_interaction(db, user, body.classroom_id, body.interaction_id)
        point = min((point for point in item["points"] if point["map_key"] == player["map_key"]),
                    key=lambda point: abs(point["tile_x"] - player["tile_x"]) + abs(point["tile_y"] - player["tile_y"]))
        session.anchor = {**point, "radius": item["radius"]}
    else:
        session.anchor = {"map_key": npc.map_key, "tile_x": npc.tile_x, "tile_y": npc.tile_y, "radius": 5}
    db.add(session)
    db.flush()
    if body.interaction_id and story.content.get("mode", "task") != "ambient":
        from .world_authoring import record_interaction
        record_interaction(db, user, body.classroom_id, body.interaction_id, session)
    _settle(db, user, session, story)
    db.commit()
    return _response(db, user, session)


@router.get("/story/current")
def current(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    _student_lock(db, user, classroom_id)
    session = _open_session(db, user, classroom_id)
    return _response(db, user, session) if session else None


@router.post("/story/sessions/{session_id}/end")
def end_session(session_id: str, body: ReturnInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    session, signature, duplicate = _request(db, user, session_id, body, "end")
    if not duplicate:
        session.status = "ended"
        session.pending_activity_id = None
        session.return_mapping = {}
        _receipt(session, body, signature)
        db.commit()
    return _response(db, user, session)


def _request(db, user, session_id, body, kind):
    _student_lock(db, user, body.classroom_id)
    session = db.get(StorySession, session_id)
    if not session or (session.user_id, session.classroom_id) != (user.id, body.classroom_id):
        raise HTTPException(404, "演出会话不存在")
    signature = hashlib.sha256(json.dumps({"kind": kind, **body.model_dump(exclude={"event_id"})}, sort_keys=True).encode()).hexdigest()
    previous = next((receipt for receipt in session.receipts if receipt["id"] == body.event_id), None)
    if previous:
        if previous["signature"] != signature:
            raise HTTPException(409, "幂等键已用于另一演出操作")
        return session, signature, True
    if session.revision != body.revision:
        raise HTTPException(409, "演出进度已变化，请恢复当前会话")
    if session.status == "ended":
        raise HTTPException(409, "演出会话已结束")
    if kind != "end":
        _near(db, user, session)
    return session, signature, False


def _receipt(session, body, signature):
    session.revision += 1
    session.receipts = [*session.receipts[-31:], {"id": body.event_id, "signature": signature}]


def _goal_event(db, user, session, story, goal_id, evidence, event_id):
    child_id = hashlib.sha256(f"{session.id}:{event_id}:{goal_id}".encode()).hexdigest()
    apply_quest_event(db, user, EventInput(classroom_id=session.classroom_id, event_id=child_id,
                                          quest_id=story.quest_id, node_id=goal_id, evidence=evidence))


@router.post("/story/sessions/{session_id}/advance")
def advance(session_id: str, body: AdvanceInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    session, signature, duplicate = _request(db, user, session_id, body, "advance")
    if duplicate:
        return _response(db, user, session)
    story, instance = _definition(db, user, session)
    if session.status != "active":
        raise HTTPException(409, "请先从当前学习活动返回")
    node = _nodes(story)[session.node_id]
    if node["type"] == "dialogue":
        rounds = node["text"].split("\n\n")
        round_index = session.return_mapping.get("dialogue_round", 0)
        if round_index < len(rounds) - 1:
            if body.choice:
                raise HTTPException(422, "请先读完当前对白")
            session.return_mapping = {"dialogue_round": round_index + 1}
            _receipt(session, body, signature)
            db.commit()
            return _response(db, user, session)
    session.return_mapping = {}
    if node["type"] == "action" and node["action"] == "enter_building":
        from .world_authoring import enter_building
        session.anchor = enter_building(db, user, session.classroom_id, node["building_id"])
    choices = _choices(db, user, session, story, node)
    selected = next((choice for choice in choices if choice["id"] == body.choice), None)
    if choices and not selected or body.choice and not choices:
        raise HTTPException(422, "请选择当前对白选项")
    checkpoint = node.get("checkpoint")
    if checkpoint:
        if instance is None:
            raise HTTPException(409, "这段对白的任务目标已失效，请结束本次互动后联系老师")
        definition, _ = _states(db, instance)
        goal = next((item for item in definition["nodes"] if item["id"] == checkpoint), None)
        if goal is None or goal.get("target") != session.npc_key:
            raise HTTPException(409, "对白检查点与NPC不匹配")
        marker = f"story:{story.id}:{checkpoint}"
        if goal["type"] == "PLAY_DIALOGUE" and goal.get("dialogue_id", session.npc_key) != marker:
            raise HTTPException(409, "对白检查点标记不匹配")
        flag = f"dialogue:completed:{session.npc_key}:{marker}"
        if not db.get(WorldFlag, (user.id, session.classroom_id, flag)):
            db.add(WorldFlag(user_id=user.id, classroom_id=session.classroom_id, key=flag))
            db.flush()
        evidence = {"npc_key": session.npc_key, "dialogue_id": marker}
        if selected:
            evidence["choice"] = selected["id"]
        _goal_event(db, user, session, story, checkpoint, evidence, body.event_id)
        for effect in story.content.get("checkpoint_effects", {}).get(checkpoint, []):
            from .world_authoring import unlock_building
            unlock_building(db, user, session.classroom_id, effect["building_id"])
    following = selected["next"] if selected else node.get("next")
    if following:
        session.node_id = following
        _settle(db, user, session, story)
    else:
        session.status = "ended"
    _receipt(session, body, signature)
    db.commit()
    return _response(db, user, session)


@router.post("/story/sessions/{session_id}/return")
def return_activity(session_id: str, body: ReturnInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    session, signature, duplicate = _request(db, user, session_id, body, "return")
    if duplicate:
        return _response(db, user, session)
    story, instance = _definition(db, user, session)
    if session.status != "waiting":
        raise HTTPException(409, "当前没有待返回的学习活动")
    node = _nodes(story)[session.node_id]
    binding = story.content["bindings"][node["binding"]]
    activity = _available_activity(db, user, session, binding)
    outcome, evidence = "suspended", {}
    definition, statuses = _states(db, instance) if instance else ({"nodes": []}, {})
    goal = next((item for item in definition["nodes"] if item["id"] == binding.get("goal_id")), None)
    if not activity:
        outcome = "unavailable"
    elif instance is None:
        from .story_conditions import activity_completed
        outcome = "completed" if activity_completed(db, user, session.classroom_id, activity.id) else "suspended"
    elif statuses[goal["id"]] == "complete":
        outcome = "completed"
    elif statuses[goal["id"]] == "ready":
        if activity.kind in {"lesson", "reading"}:
            record = db.scalar(select(LearningRecord).where(LearningRecord.user_id == user.id, LearningRecord.classroom_id == session.classroom_id,
                                                            LearningRecord.activity_id == activity.id)
                               .order_by(LearningRecord.created_at.desc()))
            evidence = {"record_id": record.id} if record else {}
        try:
            validate_evidence(db, user, session.classroom_id, goal, evidence)
        except HTTPException as exc:
            if exc.status_code != 422:
                raise
        else:
            _goal_event(db, user, session, story, goal["id"], evidence, body.event_id)
            outcome = "completed"
    session.node_id = session.return_mapping[outcome]
    session.pending_activity_id = None
    session.return_mapping = {}
    _settle(db, user, session, story)
    _receipt(session, body, signature)
    db.commit()
    return _response(db, user, session)
