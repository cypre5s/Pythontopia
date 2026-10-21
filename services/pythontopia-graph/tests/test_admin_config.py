from __future__ import annotations

from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from pythontopia_graph.routes import admin_config


class FakeProvider:
    async def chat(self, *_args: object, **_kwargs: object) -> object:
        return object()


@pytest.mark.asyncio
async def test_connection_check_returns_mask_safe_status_without_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(admin_config, "_make_provider", lambda *_args: FakeProvider())

    result = await admin_config.test_llm_config(
        admin_config.LlmConfigRequest(model="deepseek-flash", apiKey="secret-test-key")
    )

    assert result.success is True
    assert result.model == "deepseek-flash"
    assert "secret-test-key" not in result.model_dump_json()


def test_config_request_rejects_models_outside_deepseek_allowlist() -> None:
    with pytest.raises(ValidationError):
        admin_config.LlmConfigRequest(model="gpt-4o", apiKey="test-api-key")  # type: ignore[arg-type]


@pytest.mark.asyncio
async def test_apply_updates_runtime_provider_for_existing_route_dependencies(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(admin_config, "_make_provider", lambda *_args: FakeProvider())
    request = SimpleNamespace(app=SimpleNamespace(state=SimpleNamespace()))

    result = await admin_config.apply_llm_config(
        admin_config.LlmConfigRequest(model="deepseek-v4-pro", apiKey="deepseek-test-key"),
        request,  # type: ignore[arg-type]
    )

    assert result.model == "deepseek-v4-pro"
    assert result.apiKeyConfigured is True
    assert request.app.state.llm_provider.__class__ is FakeProvider
    assert admin_config.os.environ["PYT_OPENAI_MODEL"] == "deepseek-v4-pro"
