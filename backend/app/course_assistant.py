"""Course assistant settings. Persona text is authored but not sent to models."""
import os
from fastapi import HTTPException
from .course_exchange_models import CoursePackageState
from .course_instances import classroom_course

CAPS = {"model_calls": 3, "tool_calls": 5, "output_tokens": 512, "request_tokens": 12000, "timeout_seconds": 45}


def validate_policy(value, bundle_data):
    if not isinstance(value, dict) or set(value) - {"enabled", "limits", "default_npc_id"}:
        raise ValueError("助教设置字段无效，请使用新版角色引用")
    if type(value.get("enabled", True)) is not bool:
        raise ValueError("助教启用状态无效")
    limits = value.get("limits", {})
    if not isinstance(limits, dict) or set(limits) - CAPS.keys():
        raise ValueError("助教预算字段无效")
    if any(type(limit) is not int or not (0 if key == "tool_calls" else 1) <= limit <= CAPS[key] for key, limit in limits.items()):
        raise ValueError("助教预算不得超过系统硬上限")
    default_npc = value.get("default_npc_id")
    if default_npc is not None and (not isinstance(default_npc, str) or default_npc not in {row["id"] for row in bundle_data["npc"]}):
        raise ValueError("课程默认答疑角色不存在")
    return value


def policy(db, classroom_id):
    instance = classroom_course(db, classroom_id)
    state = db.get(CoursePackageState, instance.id) if instance else None
    return state.configuration.get("assistant", {}) if state else {}


def default_npc(db, classroom_id):
    from .course_scope import npc_by_stable_id
    key = policy(db, classroom_id).get("default_npc_id")
    return npc_by_stable_id(db, classroom_id, key) if key else None


def activity_npc(db, classroom_id, activity):
    from .models import Npc
    return db.get(Npc, activity.npc_key) if activity.npc_key is not None else default_npc(db, classroom_id)


def ensure_enabled(db, classroom_id):
    if policy(db, classroom_id).get("enabled") is False:
        raise HTTPException(403, "当前班级课程未开放助教")


def runtime_policy(db, classroom_id, activity=None, npc=None):
    value = policy(db, classroom_id)
    limits = {key: min(value.get("limits", {}).get(key, cap), cap) for key, cap in CAPS.items()}
    limits["request_tokens"] = min(limits["request_tokens"], int(os.getenv("REBORN_AI_REQUEST_TOKEN_RESERVATION", "12000")))
    limits["timeout_seconds"] = min(limits["timeout_seconds"], int(os.getenv("REBORN_AI_TIMEOUT_SECONDS", "45")))
    return {}, limits


def decorate_context(db, classroom_id, activity, npc, context):
    from .characters import presentation
    role = presentation(db, classroom_id, npc)
    if role is None:
        raise HTTPException(409, "请先配置此学习场景的助教角色")
    context["role"] = {**role, "tone": "温和、清晰，以分层提示引导学生自己思考"}
    _, context["limits"] = runtime_policy(db, classroom_id)
    return context
