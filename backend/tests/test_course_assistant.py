"""Course teaching policy is portable; providers and authorization remain server-owned."""
from copy import deepcopy

import pytest

from app.course_assistant import validate_policy


def sample():
    return {"schema_version": 1, "roles": {"guide": {"name": "引导老师", "instructions": "先给一个提示，再请学生解释想法。",
            "limits": {"model_calls": 2, "tool_calls": 3, "output_tokens": 256, "request_tokens": 6000, "timeout_seconds": 20}}},
            "activity_roles": {"lesson": "guide"}, "library_role_id": "guide"}


@pytest.mark.parametrize("bad", ["credentials", "tools", "overbudget", "boolean_budget", "bad_binding"])
def test_policy_rejects_privileges_and_invalid_limits(bad):
    value = sample()
    if bad == "credentials":
        value["api_key"] = "must-not-import"
    elif bad == "tools":
        value["roles"]["guide"]["tools"] = ["grade"]
    elif bad == "overbudget":
        value["roles"]["guide"]["limits"]["model_calls"] = 100
    elif bad == "boolean_budget":
        value["roles"]["guide"]["limits"]["tool_calls"] = True
    else:
        value["activity_roles"] = {"outside": "guide"}
    with pytest.raises(ValueError):
        validate_policy(value, {"activity": [{"id": "lesson"}], "npc": []})


def test_policy_role_and_budgets_apply_only_selected_class(db, teacher, student, classroom, monkeypatch):
    from app.assistant import MessageInput, build_context, new_session, NewSession
    from app.course_assistant import ensure_enabled
    from app.course_exchange_models import CoursePackageState
    from app.course_instances import clone_current_course, content_mapping
    from app.models import Activity, Classroom, User
    from app.integration_models import AssistantSession
    from fastapi import HTTPException
    source = clone_current_course(db, db.get(Classroom, classroom["id"]))
    maps = content_mapping(db, source)
    value = sample()
    value["activity_roles"] = {"ch1-lesson": "guide"}
    state = db.get(CoursePackageState, source.id)
    state.configuration = {**state.configuration, "assistant": value}
    db.commit()
    user = db.get(User, student["id"])
    activity = db.get(Activity, maps["activity"]["ch1-lesson"])
    session = AssistantSession(user_id=user.id, classroom_id=source.classroom_id, activity_id=activity.id,
                               material_id=activity.material_id, messages=[])
    monkeypatch.setenv("REBORN_AI_TIMEOUT_SECONDS", "10")
    context = build_context(db, user, session, activity, MessageInput(message="解释此页", page_no=42))
    assert context["role"]["name"] == "引导老师" and context["limits"]["model_calls"] == 2
    assert context["limits"]["timeout_seconds"] == 10
    assert "hidden_cases" not in str(context)
    updated = deepcopy(state.configuration)
    updated["assistant"]["enabled"] = False
    state.configuration = updated
    db.commit()
    with pytest.raises(HTTPException) as denied:
        ensure_enabled(db, source.classroom_id)
    assert denied.value.status_code == 403
    with pytest.raises(HTTPException):
        new_session(NewSession(classroom_id=source.classroom_id, activity_id=activity.id), user, db)


def test_role_budgets_compile_real_agent_without_network(monkeypatch):
    from app.assistant import AgentFactory
    monkeypatch.setenv("REBORN_AI_API_KEY", "test-not-real")
    monkeypatch.setenv("REBORN_AI_MODEL", "test-not-real")
    context = {"pages": [], "activity": {"id": "test", "questions": []}, "role": {"name": "老师", "tone": "清晰", "instructions": "分层提示"},
               "current_page": None, "progress": [], "submissions": [], "student_code": None,
               "limits": {"tool_calls": 0, "model_calls": 1, "output_tokens": 64, "request_tokens": 2000, "timeout_seconds": 5}}
    agent = AgentFactory.create(context, {}, {"model_calls": 0})
    assert "model" in agent.get_graph().nodes
