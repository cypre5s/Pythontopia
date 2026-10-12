from fastapi.testclient import TestClient

from pythontopia_graph.llm.provider import DryRunProvider, OpenAiCompatProvider
from pythontopia_graph.main import app


def test_healthz_returns_ok() -> None:
    client = TestClient(app)

    response = client.get("/healthz")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_readyz_returns_ok_for_real_provider(monkeypatch) -> None:
    provider = OpenAiCompatProvider(
        base_url="https://api.deepseek.com/v1",
        api_key="test-key",
        model="test-model",
    )
    monkeypatch.setattr(app.state, "llm_provider", provider, raising=False)
    client = TestClient(app)

    response = client.get("/readyz")

    assert response.status_code == 200
    assert response.json() == {"status": "ready"}


def test_readyz_rejects_dryrun_provider(monkeypatch) -> None:
    monkeypatch.setattr(app.state, "llm_provider", DryRunProvider(), raising=False)
    client = TestClient(app)

    response = client.get("/readyz")

    assert response.status_code == 503
    assert response.json() == {"detail": "real LLM provider is not ready"}


def test_readyz_rejects_provider_initialization_failure(monkeypatch) -> None:
    monkeypatch.setattr(app.state, "llm_provider", None, raising=False)
    client = TestClient(app)

    response = client.get("/readyz")

    assert response.status_code == 503
    assert response.json() == {"detail": "real LLM provider is not ready"}


def test_readyz_rejects_enabled_browse_without_runtime(monkeypatch) -> None:
    provider = OpenAiCompatProvider(
        base_url="https://api.deepseek.com/v1",
        api_key="test-key",
        model="test-model",
    )
    monkeypatch.setattr(app.state, "llm_provider", provider, raising=False)
    monkeypatch.setenv("BROWSE_ENABLED", "on")
    monkeypatch.setattr(
        "pythontopia_graph.main.browse_runtime_status",
        lambda: (False, "Playwright Chromium 不可用"),
    )

    response = TestClient(app).get("/readyz")

    assert response.status_code == 503
    assert response.json() == {"detail": "Playwright Chromium 不可用"}


def test_readyz_accepts_enabled_browse_with_runtime(monkeypatch) -> None:
    provider = OpenAiCompatProvider(
        base_url="https://api.deepseek.com/v1",
        api_key="test-key",
        model="test-model",
    )
    monkeypatch.setattr(app.state, "llm_provider", provider, raising=False)
    monkeypatch.setenv("BROWSE_ENABLED", "on")
    monkeypatch.setattr(
        "pythontopia_graph.main.browse_runtime_status",
        lambda: (True, "ready"),
    )

    response = TestClient(app).get("/readyz")

    assert response.status_code == 200
    assert response.json() == {"status": "ready"}
