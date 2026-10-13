
from collections.abc import AsyncIterator

from fastapi.testclient import TestClient

from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.main import app


class _StubProvider(LlmProvider):
    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(
            content_json={
                "intent": "plan",
                "ambiguous": False,
                "clarifying_question": None,
                "goal": "学习循环",
                "focus": "for 循环",
                "steps": [],
                "profile_signals": [],
            }
        )

    async def chat_stream(self, messages, **kwargs) -> AsyncIterator[str]:  # type: ignore[override]
        yield "ok"


def _orchestrator_body(*, user_id: int = 1) -> dict[str, object]:
    return {
        "user_id": user_id,
        "session_id": "internal-auth-session",
        "user_message": "安排学习",
        "course_kc_index": ["PY43-KC-001"],
    }


def _workflow_body(*, user_id: int = 1) -> dict[str, object]:
    return {
        "user_id": user_id,
        "session_id": "internal-auth-workflow",
        "event": "CODING",
        "problem": {
            "problem_id": 476,
            "title": "圆面积",
            "statement": "读取半径并输出面积",
            "kc_codes": ["PY43-KC-016"],
        },
    }


def test_healthz_skips_internal_token(monkeypatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "secret")
    client = TestClient(app)
    response = client.get("/healthz")
    assert response.status_code == 200


def test_protected_route_requires_token(monkeypatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "secret")
    client = TestClient(app)
    response = client.post(
        "/agents/curriculum/map",
        json={"user_id": 1, "profile_summary": {"major": "OTHER", "python_self_rated_level": 2}, "chapters": [], "kcs": []},
    )
    assert response.status_code == 401


def test_malformed_host_cannot_bypass_internal_auth(monkeypatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "secret")
    client = TestClient(app)
    response = client.post(
        "/agents/curriculum/map",
        json={
            "user_id": 1,
            "profile_summary": {"major": "OTHER", "python_self_rated_level": 2},
            "chapters": [],
            "kcs": [],
        },
        headers={"Host": "example.com/healthz?bypass="},
    )

    assert response.status_code == 401


def test_protected_route_accepts_valid_token(monkeypatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "secret")
    client = TestClient(app)
    response = client.post(
        "/agents/curriculum/map",
        json={"user_id": 1, "profile_summary": {"major": "OTHER", "python_self_rated_level": 2}, "chapters": [], "kcs": []},
        headers={"X-Internal-Token": "secret"},
    )
    assert response.status_code != 401


def test_missing_token_fails_closed(monkeypatch) -> None:
    # 未配置令牌且未显式开启 dev 逃生舱 → 拒绝服务（503），绝不裸奔放行 LLM Agent。
    monkeypatch.delenv("PYT_RAG_ADMIN_TOKEN", raising=False)
    monkeypatch.delenv("PYT_INTERNAL_AUTH_OPTIONAL", raising=False)
    client = TestClient(app)
    response = client.post(
        "/agents/curriculum/map",
        json={"user_id": 1, "profile_summary": {"major": "OTHER", "python_self_rated_level": 2}, "chapters": [], "kcs": []},
    )
    assert response.status_code == 503


def test_stateful_route_requires_trusted_internal_subject(monkeypatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "secret")
    app.state.llm_provider = _StubProvider()
    client = TestClient(app)

    response = client.post(
        "/agents/orchestrator/turn",
        json=_orchestrator_body(),
        headers={"X-Internal-Token": "secret"},
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "missing internal subject"
    del app.state.llm_provider


def test_stateful_route_rejects_forged_body_user_id(monkeypatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "secret")
    app.state.llm_provider = _StubProvider()
    client = TestClient(app)

    response = client.post(
        "/agents/orchestrator/turn",
        json=_orchestrator_body(user_id=999),
        headers={
            "X-Internal-Token": "secret",
            "X-Internal-Subject": "user:1",
        },
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "internal subject does not match user_id"
    del app.state.llm_provider


def test_stateful_route_rejects_malformed_internal_subject(monkeypatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "secret")
    app.state.llm_provider = _StubProvider()
    client = TestClient(app)

    response = client.post(
        "/agents/orchestrator/turn",
        json=_orchestrator_body(),
        headers={
            "X-Internal-Token": "secret",
            "X-Internal-Subject": "1",
        },
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "invalid internal subject"
    del app.state.llm_provider


def test_stateful_route_accepts_matching_authenticated_subject(monkeypatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "secret")
    app.state.llm_provider = _StubProvider()
    client = TestClient(app)

    response = client.post(
        "/agents/orchestrator/turn",
        json=_orchestrator_body(user_id=17),
        headers={
            "X-Internal-Token": "secret",
            "X-Internal-Subject": "user:17",
        },
    )

    assert response.status_code == 200
    del app.state.llm_provider


def test_tutor_workflow_rejects_forged_body_user_id_before_streaming(monkeypatch) -> None:
    monkeypatch.setenv("PYT_RAG_ADMIN_TOKEN", "secret")
    client = TestClient(app)

    response = client.post(
        "/agents/tutor-workflow/run/stream",
        json=_workflow_body(user_id=999),
        headers={
            "X-Internal-Token": "secret",
            "X-Internal-Subject": "user:17",
        },
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "internal subject does not match user_id"
