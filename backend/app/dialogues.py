"""Bounded deterministic dialogue interpreter. No model calls or arbitrary effects."""

from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy.orm import Session
from .auth import current_user
from .courses import authorize_activity, authorize_classroom
from .db import get_db
from .models import Npc, User, WorldFlag

router = APIRouter(tags=["dialogues"])


def validate_condition(condition):
    if condition is not None and (
        not isinstance(condition, dict)
        or set(condition) != {"flag", "equals"}
        or not isinstance(condition["flag"], str)
        or len(condition["flag"]) > 100
        or not isinstance(condition["equals"], bool)
    ):
        raise ValueError("对白条件仅允许 flag 与 equals 布尔值")


def validate_dialogue(nodes):
    if not isinstance(nodes, list) or not 1 <= len(nodes) <= 100 or any(not isinstance(n, dict) for n in nodes):
        raise ValueError("对白需要1至100个对象节点")
    if any(not isinstance(n.get("id"), str) or not 1 <= len(n["id"]) <= 100 for n in nodes):
        raise ValueError("对白节点ID必须为短字符串")
    mapping = {n.get("id"): n for n in nodes}
    if len(mapping) != len(nodes) or any(not isinstance(key, str) or not 1 <= len(key) <= 100 for key in mapping):
        raise ValueError("对白节点ID缺失或重复")
    links = {}
    for node in nodes:
        if set(node) - {"id", "speaker", "portrait", "text", "next", "choices", "condition", "once", "effects"}:
            raise ValueError("未知对白字段")
        if not isinstance(node.get("text"), str) or len(node["text"]) > 8000:
            raise ValueError("对白文本缺失或过长")
        if node.get("speaker") is not None and (not isinstance(node["speaker"], str) or len(node["speaker"]) > 80):
            raise ValueError("说话人必须是短字符串")
        if node.get("portrait") and (not isinstance(node["portrait"], str) or not node["portrait"].startswith("/assets/pixel/")):
            raise ValueError("对白立绘须使用已审计本地素材")
        if node.get("once") is not None and (not isinstance(node["once"], str) or len(node["once"]) > 100):
            raise ValueError("一次性标记必须为短字符串")
        validate_condition(node.get("condition"))
        choices = node.get("choices", [])
        if (
            not isinstance(choices, list)
            or len(choices) > 10
            or any(
                not isinstance(c, dict)
                or set(c) - {"id", "label", "next", "condition"}
                or not isinstance(c.get("id"), str)
                or not isinstance(c.get("label"), str)
                for c in choices
            )
        ):
            raise ValueError("无效对白选项")
        if len({c["id"] for c in choices}) != len(choices):
            raise ValueError("对白选项ID重复")
        for choice in choices:
            validate_condition(choice.get("condition"))
        targets = [node["next"]] if node.get("next") else []
        targets += [c["next"] for c in choices if c.get("next")]
        if any(target not in mapping for target in targets):
            raise ValueError("对白 next 引用了不存在的节点")
        links[node["id"]] = targets
        if not isinstance(node.get("effects", []), list) or len(node.get("effects", [])) > 10:
            raise ValueError("对白效果必须为有界列表")
        for effect in node.get("effects", []):
            if not isinstance(effect, dict):
                raise ValueError("无效对白效果")
            if effect.get("type") == "open_activity" and set(effect) == {"type", "activity_id"} and isinstance(effect["activity_id"], str):
                continue
            if effect.get("type") == "remember" and set(effect) == {"type", "key"} and isinstance(effect["key"], str) and len(effect["key"]) <= 100:
                continue
            raise ValueError("对白仅允许打开活动或记录剧情标记，不能直接通关")
    visiting, visited = set(), set()

    def visit(key):
        if key in visiting:
            raise ValueError("对白不允许意外循环")
        if key in visited:
            return
        visiting.add(key)
        for target in links[key]:
            visit(target)
        visiting.remove(key)
        visited.add(key)

    visit(nodes[0]["id"])
    if len(visited) != len(nodes):
        raise ValueError("对白包含不可达节点")
    return nodes


class StepInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    classroom_id: int
    node_id: str = Field(min_length=1, max_length=100)
    choice: str | None = Field(default=None, max_length=80)


def remember(db, user, classroom_id, key):
    if not db.get(WorldFlag, (user.id, classroom_id, key)):
        db.add(WorldFlag(user_id=user.id, classroom_id=classroom_id, key=key))
        db.flush()


def condition_allowed(db, user, classroom_id, condition):
    if not condition:
        return True
    present = bool(db.get(WorldFlag, (user.id, classroom_id, condition["flag"])))
    return present is condition["equals"]


@router.post("/dialogues/{npc_key}/steps")
def step(npc_key: str, body: StepInput, db: Annotated[Session, Depends(get_db)], user: Annotated[User, Depends(current_user)]):
    from .quests import check_near
    from sqlalchemy import select

    authorize_classroom(db, user, body.classroom_id)
    db.execute(select(User.id).where(User.id == user.id).with_for_update())
    npc = db.get(Npc, npc_key)
    if not npc:
        raise HTTPException(404, "NPC不存在")
    check_near(db, user, body.classroom_id, (npc.map_key, npc.tile_x, npc.tile_y))
    try:
        validate_dialogue(npc.dialogue)
    except ValueError:
        raise HTTPException(409, "对白配置未通过校验")
    mapping = {n["id"]: n for n in npc.dialogue}
    node = mapping.get(body.node_id)
    if not node or not condition_allowed(db, user, body.classroom_id, node.get("condition")):
        raise HTTPException(403, "对白节点当前不可用")
    root = npc.dialogue[0]["id"]
    if node["id"] != root:
        permitted = False
        for parent in npc.dialogue:
            marker = f"dialogue:visited:{npc_key}:{parent['id']}"
            if parent.get("next") == node["id"] and db.get(WorldFlag, (user.id, body.classroom_id, marker)):
                permitted = True
            for choice in parent.get("choices", []):
                marker = f"dialogue:choice:{npc_key}:{parent['id']}:{choice['id']}"
                if choice.get("next") == node["id"] and db.get(WorldFlag, (user.id, body.classroom_id, marker)):
                    permitted = True
        if not permitted:
            raise HTTPException(409, "请按对白顺序推进")
    choices = [c for c in node.get("choices", []) if condition_allowed(db, user, body.classroom_id, c.get("condition"))]
    if node.get("choices") and not choices:
        raise HTTPException(409, "当前没有满足条件的对白选项")
    selected = next((c for c in choices if c["id"] == body.choice), None)
    if choices and not selected:
        raise HTTPException(422, "请选择当前可用的对白选项")
    if body.choice and not choices:
        raise HTTPException(422, "此对白没有选项")
    next_id = selected.get("next") if selected else node.get("next")
    remember(db, user, body.classroom_id, f"dialogue:visited:{npc_key}:{node['id']}")
    if selected:
        remember(db, user, body.classroom_id, f"dialogue:choice:{npc_key}:{node['id']}:{selected['id']}")
    effects = []
    once_key = f"story:{node['once']}" if node.get("once") else None
    first = not once_key or not db.get(WorldFlag, (user.id, body.classroom_id, once_key))
    if first:
        for effect in node.get("effects", []):
            if effect["type"] == "remember":
                remember(db, user, body.classroom_id, f"story:{effect['key']}")
            elif effect["type"] == "open_activity":
                activity = authorize_activity(db, user, body.classroom_id, effect["activity_id"])
                effects.append({**effect, "kind": activity.kind})
        if once_key:
            remember(db, user, body.classroom_id, once_key)
    completed_root = None if next_id else root
    if completed_root:
        remember(db, user, body.classroom_id, f"dialogue:completed:{npc_key}:{root}")
    db.commit()
    return {"node": node, "next_id": next_id, "completed_root": completed_root, "effects": effects}
