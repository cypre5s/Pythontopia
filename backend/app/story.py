"""Deterministic story scenes. All progress is verified by business services."""

import hashlib
import json
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select
from sqlalchemy.orm import Session

from .auth import current_user
from .courses import authorize_activity, authorize_chapter, authorize_classroom, chapter_available, material_payload, authorize_material
from .db import get_db
from .models import Activity, ActivityProgress, ChapterRelease, LearningRecord, Material, Npc, NodeState, PlayerQuest, QuestDefinition, StoryDefinition, StorySession, User, WorldFlag
from .quests import EventInput, apply_quest_event, authorize_quest, check_near, ensure_quests, node_statuses, quest_visible, validate_evidence, world_payload

router = APIRouter(tags=["story"])


def _short(value):
    return isinstance(value, str) and 0 < len(value) <= 100 and bool(value.strip())


def validate_story(
    content: dict, db: Session | None = None, quest_definition: dict | None = None, story_id: str | None = None
) -> dict:
    if not isinstance(content, dict) or set(content) - {"schema_version", "bindings", "entries", "nodes", "checkpoint_effects"}:
        raise ValueError("无效演出字段")
    if type(content.get("schema_version")) is not int or content["schema_version"] != 1:
        raise ValueError("不支持的演出格式版本")
    bindings, entries, nodes = content.get("bindings"), content.get("entries"), content.get("nodes")
    if not isinstance(bindings, dict) or len(bindings) > 30 or not isinstance(entries, dict) or not 1 <= len(entries) <= 30:
        raise ValueError("演出需要有界活动绑定和NPC入口")
    goals = {node["id"]: node for node in quest_definition["nodes"]} if quest_definition else None
    for key, binding in bindings.items():
        if not _short(key) or not isinstance(binding, dict) or set(binding) != {"activity_id", "goal_id"}:
            raise ValueError("无效活动绑定")
        if not _short(binding["activity_id"]) or not _short(binding["goal_id"]):
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
                completion = activity.content.get("completion")
                if not isinstance(completion, dict) or set(completion) != {"required_page_ids"}:
                    raise ValueError("演出阅读活动必须声明阅读参与完成规则")
                required = completion["required_page_ids"]
                material_id = activity.material_id
                material = db.get(Material, material_id)
                if not isinstance(required, list) or not required or any(type(key) is not int for key in required) or len(set(required)) != len(required):
                    raise ValueError("阅读参与规则必须引用非重复真实页面ID")
                if not material or not set(required).issubset({page["id"] for page in material.content.get("pages", [])}):
                    raise ValueError("阅读参与规则引用了不存在的资料页")
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
    for node in nodes:
        kind = node.get("type")
        targets = []
        if kind == "dialogue":
            if set(node) - {"id", "type", "text", "speaker", "next", "choices", "checkpoint"}:
                raise ValueError("未知对白字段")
            if not isinstance(node.get("text"), str) or not 1 <= len(node["text"]) <= 8000:
                raise ValueError("对白文本缺失或过长")
            if "speaker" in node and (not isinstance(node["speaker"], str) or len(node["speaker"]) > 80):
                raise ValueError("对白说话人无效")
            choices = node.get("choices", [])
            if not isinstance(choices, list) or len(choices) > 10:
                raise ValueError("对白选项过多")
            for choice in choices:
                if not isinstance(choice, dict) or set(choice) != {"id", "label", "next"} or not _short(choice["id"]):
                    raise ValueError("无效对白选项")
                if not isinstance(choice["label"], str) or not 1 <= len(choice["label"]) <= 200:
                    raise ValueError("无效选项文本")
                targets.append(choice["next"])
            if len({choice["id"] for choice in choices}) != len(choices) or (choices and node.get("next")):
                raise ValueError("选项ID重复或与next冲突")
            if node.get("next"):
                targets.append(node["next"])
            if "checkpoint" in node:
                goal_id = node["checkpoint"]
                if not _short(goal_id) or (goals is not None and (goal_id not in goals or goals[goal_id]["type"] not in {"TALK_TO_NPC", "PLAY_DIALOGUE", "REPORT_BACK"})):
                    raise ValueError("对白检查点必须引用交谈目标")
                if goals is not None and story_id is not None and goals[goal_id]["type"] == "PLAY_DIALOGUE":
                    expected = f"story:{story_id}:{goal_id}"
                    if goals[goal_id].get("dialogue_id") != expected:
                        raise ValueError("对白检查点标记不匹配")
                    if len(f"dialogue:completed:{goals[goal_id]['target']}:{expected}") > 100:
                        raise ValueError("对白检查点标记过长")
                checkpoints.add(goal_id)
        elif kind == "activity":
            if set(node) != {"id", "type", "binding", "returns"} or not _short(node.get("binding")) or node["binding"] not in bindings:
                raise ValueError("无效活动节点")
            returns = node.get("returns")
            if not isinstance(returns, dict) or set(returns) != {"completed", "suspended", "unavailable"}:
                raise ValueError("活动需要完成、暂停和不可用返回分支")
            targets += list(returns.values())
        elif kind == "end":
            if set(node) != {"id", "type"}:
                raise ValueError("无效结束节点")
        else:
            raise ValueError("未知演出节点类型")
        if any(not _short(target) or target not in mapping for target in targets):
            raise ValueError("演出引用了不存在的节点")
        links[node["id"]] = targets
    roots = []
    for npc_key, routes in entries.items():
        if not _short(npc_key) or (db and not db.get(Npc, npc_key)):
            raise ValueError("入口NPC不存在")
        if not isinstance(routes, list) or not 1 <= len(routes) <= 30:
            raise ValueError("NPC入口必须是有界列表")
        for index, route in enumerate(routes):
            if not isinstance(route, dict) or set(route) != {"when", "node"} or not _short(route.get("node")) or route["node"] not in mapping:
                raise ValueError("无效演出入口")
            condition = route["when"]
            if not isinstance(condition, dict) or len(condition) != 1:
                raise ValueError("入口必须包含一个受限条件")
            key, value = next(iter(condition.items()))
            if key == "always":
                if value is not True or index != len(routes) - 1:
                    raise ValueError("兜底入口必须位于最后")
            elif key in {"goal_complete", "goal_ready"}:
                if not _short(value) or (goals is not None and value not in goals):
                    raise ValueError("入口引用了不存在的任务目标")
            elif key == "activity_started":
                if not _short(value) or value not in bindings:
                    raise ValueError("入口引用了不存在的活动绑定")
            else:
                raise ValueError("未知入口条件")
            roots.append(route["node"])
        if routes[-1]["when"] != {"always": True}:
            raise ValueError("NPC入口缺少兜底分支")
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
        if values != [{"type": "set_world_flag", "key": "library_unlocked"}]:
            raise ValueError("检查点仅支持明确的图书馆门禁效果")
    visiting, visited = set(), set()

    def visit(key):
        if key in visiting:
            raise ValueError("演出包含循环；重访应通过NPC入口处理")
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
        raise ValueError("演出包含不可达节点")
    return content


class InteractionInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    npc_key: str = Field(min_length=1, max_length=80)


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
    definition = authorize_quest(db, user, session.classroom_id, story.quest_id)
    if definition.chapter_id != story.chapter_id:
        raise HTTPException(409, "演出与当前任务章节不一致")
    authorize_chapter(db, user, session.classroom_id, story.chapter_id)
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
    classroom = authorize_classroom(db, user, classroom_id)
    for instance in db.scalars(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == classroom_id,
                                                        PlayerQuest.status != "complete")):
        if not quest_visible(db.get(QuestDefinition, instance.quest_id), user, classroom):
            continue
        if db.scalar(select(StoryDefinition.id).where(StoryDefinition.quest_id == instance.quest_id)):
            continue
        definition, statuses = _states(db, instance)
        if not chapter_available(db, user, classroom_id, definition["chapter_id"]):
            continue
        if any(statuses[node["id"]] == "ready" and (node.get("target") == npc_key or node.get("navigation_target") == npc_key)
               for node in definition["nodes"]):
            return True
    return False


def _near(db, user, session):
    npc = db.get(Npc, session.npc_key)
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
        if node["type"] == "dialogue":
            session.status = "active"
            return
        binding = story.content["bindings"][node["binding"]]
        _, instance = _definition(db, user, session)
        _, statuses = _states(db, instance)
        if statuses[binding["goal_id"]] not in {"ready", "complete"}:
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
    story, _ = _definition(db, user, session)
    node, activity = None, None
    if session.status == "active":
        source = _nodes(story)[session.node_id]
        node = {key: source[key] for key in ("id", "text", "speaker", "choices") if key in source}
    elif session.status == "waiting":
        item = authorize_activity(db, user, session.classroom_id, session.pending_activity_id)
        progress = db.get(ActivityProgress, (user.id, session.classroom_id, item.id))
        activity = {"id": item.id, "kind": item.kind, "page_no": progress.page_no if progress else None}
    return {"id": session.id, "revision": session.revision, "status": session.status, "npc_key": session.npc_key, "node": node, "activity": activity}


def _response(db, user, session):
    result = _view(db, user, session)
    result["world"] = world_payload(db, user, session.classroom_id)
    return result


@router.post("/story/interactions")
def interact(body: InteractionInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    classroom = authorize_classroom(db, user, body.classroom_id)
    ensure_quests(db, user, body.classroom_id)
    _student_lock(db, user, body.classroom_id)
    npc = db.get(Npc, body.npc_key)
    if not npc:
        raise HTTPException(404, "NPC不存在")
    check_near(db, user, body.classroom_id, (npc.map_key, npc.tile_x, npc.tile_y))
    previous = _open_session(db, user, body.classroom_id)
    abandoned = False
    if previous:
        previous_story = db.get(StoryDefinition, previous.story_id)
        previous_quest = db.get(QuestDefinition, previous_story.quest_id) if previous_story else None
        if (not previous_story or not quest_visible(previous_quest, user, classroom)
                or not chapter_available(db, user, body.classroom_id, previous_story.chapter_id)):
            previous.status = "ended"
            previous.revision += 1
            previous.pending_activity_id = None
            previous.return_mapping = {}
            db.flush()
            previous = None
            abandoned = True
    if previous and previous.status == "active":
        _, instance = _definition(db, user, previous)
        if instance.status == "complete" and _legacy_ready(db, user, body.classroom_id, body.npc_key):
            previous.status = "ended"
            db.commit()
            raise HTTPException(404, "当前目标使用既有章节对白")
    if previous and (previous.npc_key == body.npc_key or previous.status == "waiting"):
        return _response(db, user, previous)
    selected = None
    for story in db.scalars(select(StoryDefinition)):
        if not quest_visible(db.get(QuestDefinition, story.quest_id), user, classroom):
            continue
        if body.npc_key not in story.content["entries"]:
            continue
        release = db.get(ChapterRelease, (body.classroom_id, story.chapter_id))
        if not release or not chapter_available(db, user, body.classroom_id, story.chapter_id):
            continue
        instance = db.scalar(select(PlayerQuest).where(PlayerQuest.user_id == user.id, PlayerQuest.classroom_id == body.classroom_id,
                                                      PlayerQuest.quest_id == story.quest_id))
        if instance:
            authorize_chapter(db, user, body.classroom_id, story.chapter_id)
            selected = story, instance
            break
    if not selected:
        if abandoned:
            db.commit()
        raise HTTPException(404, "此NPC尚未绑定当前演出")
    story, instance = selected
    if instance.status == "complete" and _legacy_ready(db, user, body.classroom_id, body.npc_key):
        if previous:
            previous.status = "ended"
        db.commit()
        raise HTTPException(404, "当前目标使用既有章节对白")
    _, statuses = _states(db, instance)
    root = None
    for route in story.content["entries"][body.npc_key]:
        key, value = next(iter(route["when"].items()))
        allowed = key == "always" or key == "goal_complete" and statuses[value] == "complete" or key == "goal_ready" and statuses[value] == "ready"
        if key == "activity_started":
            binding = story.content["bindings"][value]
            allowed = bool(db.get(ActivityProgress, (user.id, body.classroom_id, binding["activity_id"])))
        if allowed:
            root = route["node"]
            break
    if previous:
        previous.status = "ended"
        db.flush()
    session = StorySession(user_id=user.id, classroom_id=body.classroom_id, story_id=story.id,
                           npc_key=body.npc_key, node_id=root, status="active", revision=0, receipts=[])
    db.add(session)
    _settle(db, user, session, story)
    db.commit()
    return _response(db, user, session)


@router.get("/story/current")
def current(classroom_id: int, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    _student_lock(db, user, classroom_id)
    session = _open_session(db, user, classroom_id)
    return _response(db, user, session) if session else None


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
    choices = node.get("choices", [])
    selected = next((choice for choice in choices if choice["id"] == body.choice), None)
    if choices and not selected or body.choice and not choices:
        raise HTTPException(422, "请选择当前对白选项")
    checkpoint = node.get("checkpoint")
    if checkpoint:
        definition, _ = _states(db, instance)
        goal = next(item for item in definition["nodes"] if item["id"] == checkpoint)
        if goal.get("target") != session.npc_key:
            raise HTTPException(409, "对白检查点与NPC不匹配")
        marker = f"story:{story.id}:{checkpoint}"
        if goal["type"] == "PLAY_DIALOGUE" and goal.get("dialogue_id", session.npc_key) != marker:
            raise HTTPException(409, "对白检查点标记不匹配")
        flag = f"dialogue:completed:{session.npc_key}:{marker}"
        if not db.get(WorldFlag, (user.id, session.classroom_id, flag)):
            db.add(WorldFlag(user_id=user.id, classroom_id=session.classroom_id, key=flag))
            db.flush()
        _goal_event(db, user, session, story, checkpoint, {"npc_key": session.npc_key, "dialogue_id": marker}, body.event_id)
        for effect in story.content.get("checkpoint_effects", {}).get(checkpoint, []):
            if not db.get(WorldFlag, (user.id, session.classroom_id, effect["key"])):
                db.add(WorldFlag(user_id=user.id, classroom_id=session.classroom_id, key=effect["key"]))
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
    definition, statuses = _states(db, instance)
    goal = next(item for item in definition["nodes"] if item["id"] == binding["goal_id"])
    if not activity:
        outcome = "unavailable"
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
