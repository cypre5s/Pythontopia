"""Portable teaching roles with immutable server authorization and hard runtime budgets."""
from copy import deepcopy
import os

from fastapi import HTTPException

from .authoring_diagnostics import located
from .course_exchange_models import CoursePackageState
from .course_instances import classroom_course

CAPS = {"model_calls": 3, "tool_calls": 5, "output_tokens": 512, "request_tokens": 12000, "timeout_seconds": 45}


def validate_policy(value, bundle_data):
    allowed = {"schema_version", "enabled", "roles", "default_role_id", "library_role_id", "activity_roles", "npc_roles"}
    if not isinstance(value, dict) or set(value) - allowed:
        raise ValueError("助教教学配置包含未知字段，不能携带模型连接或权限")
    if not value:
        return value
    if type(value.get("schema_version")) is not int or value["schema_version"] != 1 or type(value.get("enabled", True)) is not bool:
        raise ValueError("助教教学配置版本或启用状态无效")
    roles = value.get("roles", {})
    if not isinstance(roles, dict) or len(roles) > 100:
        raise ValueError("助教角色数量超限")
    for key, role in roles.items():
        target = {"kind": "assistant", "role_id": key} if isinstance(key, str) else {"kind": "assistant"}
        label = role.get("name") if isinstance(role, dict) else None
        with located("roles", key, target=target, label=f"角色「{label or key}」"):
            if not isinstance(key, str) or not key.strip() or len(key) > 80:
                raise ValueError("助教角色ID无效")
            if not isinstance(role, dict) or set(role) - {"name", "instructions", "limits"} or not {"name", "instructions"}.issubset(role):
                raise ValueError("助教角色字段无效")
            if any(not isinstance(role[field], str) or not 1 <= len(role[field].strip()) <= cap for field, cap in (("name", 80), ("instructions", 2000))):
                raise ValueError("助教角色名称或教学说明无效")
            limits = role.get("limits", {})
            if not isinstance(limits, dict) or set(limits) - CAPS.keys():
                raise ValueError("助教角色预算字段无效")
            if any(type(limit) is not int or not (0 if key == "tool_calls" else 1) <= limit <= CAPS[key] for key, limit in limits.items()):
                raise ValueError("助教角色预算不得超过系统硬上限")
    for field in ("default_role_id", "library_role_id"):
        if field in value and (not isinstance(value[field], str) or value[field] not in roles):
            raise ValueError("助教默认角色不存在")
    for field, kind in (("activity_roles", "activity"), ("npc_roles", "npc")):
        bindings = value.get(field, {})
        ids = {row["id"] for row in bundle_data[kind]}
        if not isinstance(bindings, dict) or len(bindings) > 10000:
            raise ValueError("助教角色绑定无效")
        if any(key not in ids or not isinstance(role, str) or role not in roles for key, role in bindings.items()):
            raise ValueError("助教角色绑定引用不存在")
    return value


def policy(db, classroom_id):
    instance = classroom_course(db, classroom_id)
    state = db.get(CoursePackageState, instance.id) if instance else None
    return state.configuration.get("assistant", {}) if state else {}


def ensure_enabled(db, classroom_id):
    if policy(db, classroom_id).get("enabled") is False:
        raise HTTPException(403, "当前班级课程未开放助教")


def runtime_policy(db, classroom_id, activity, npc):
    value = policy(db, classroom_id)
    role_id = value.get("default_role_id")
    if activity is None:
        role_id = value.get("library_role_id", role_id)
    if npc:
        role_id = value.get("npc_roles", {}).get(npc.stable_id or npc.key, role_id)
    if activity:
        role_id = value.get("activity_roles", {}).get(activity.stable_id or activity.id, role_id)
    role = deepcopy(value.get("roles", {}).get(role_id, {}))
    limits = {key: min(role.get("limits", {}).get(key, cap), cap) for key, cap in CAPS.items()}
    limits["request_tokens"] = min(limits["request_tokens"], int(os.getenv("REBORN_AI_REQUEST_TOKEN_RESERVATION", "12000")))
    limits["timeout_seconds"] = min(limits["timeout_seconds"], int(os.getenv("REBORN_AI_TIMEOUT_SECONDS", "45")))
    return role, limits


def decorate_context(db, classroom_id, activity, npc, context):
    role, limits = runtime_policy(db, classroom_id, activity, npc)
    context["role"] = {**context["role"], **{key: role[key] for key in ("name", "instructions") if key in role}}
    context["limits"] = limits
    return context
