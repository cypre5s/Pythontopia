
from fastapi.testclient import TestClient

from pythontopia_graph.main import app


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
