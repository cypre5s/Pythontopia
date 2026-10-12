"""OpenAiCompatProvider 单元测试。"""

from __future__ import annotations

import json
from collections.abc import Callable
from typing import Any

import httpx
import pytest

from pythontopia_graph.llm.provider import (
    DryRunProvider,
    FineTunedFallbackProvider,
    LlmResponse,
    RoutedFineTunedProvider,
    OpenAiCompatProvider,
    provider_from_env,
    provider_label,
)


@pytest.fixture(autouse=True)
def _openai_compat_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OPENAI_BASE_URL", "https://api.deepseek.com/v1")
    monkeypatch.setenv("OPENAI_API_KEY", "deepseek-key")
    monkeypatch.setenv("OPENAI_MODEL", "deepseek-v4-pro")
    monkeypatch.setenv("PYT_LLM_PROVIDER", "openai_compat")
    monkeypatch.setattr(
        "pythontopia_graph.llm.provider.load_pythontopia_env", lambda *a, **k: None
    )
    for name in (
        "LLM_BASE_URL",
        "LLM_API_KEY",
        "LLM_MODEL",
        "OPENAI_MODEL_FAST",
        "OPENAI_MODEL_VISION",
        "LLM_MODEL_FAST",
        "LLM_MODEL_VISION",
        "PYT_OPENAI_BASE_URL",
        "PYT_OPENAI_API_KEY",
        "PYT_OPENAI_MODEL",
        "PYT_OPENAI_MODEL_FAST",
        "PYT_OPENAI_MODEL_VISION",
        "HTTP_PROXY",
        "http_proxy",
        "HTTPS_PROXY",
        "https_proxy",
        "ALL_PROXY",
        "all_proxy",
    ):
        monkeypatch.delenv(name, raising=False)


def test_project_deepseek_settings_take_precedence_over_generic_aliases(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("PYT_OPENAI_BASE_URL", "https://api.deepseek.com")
    monkeypatch.setenv("PYT_OPENAI_API_KEY", "project-deepseek-key")
    monkeypatch.setenv("PYT_OPENAI_MODEL", "deepseek-v4-pro")
    monkeypatch.setenv("PYT_OPENAI_MODEL_FAST", "deepseek-flash")
    monkeypatch.setenv("PYT_OPENAI_MODEL_VISION", "deepseek-flash")
    monkeypatch.setenv("OPENAI_API_KEY", "generic-old-key")
    monkeypatch.setenv("OPENAI_MODEL", "generic-old-model")
    monkeypatch.setenv("OPENAI_MODEL_FAST", "generic-old-fast")
    monkeypatch.setenv("OPENAI_MODEL_VISION", "generic-old-vision")

    provider = OpenAiCompatProvider()

    assert provider.base_url == "https://api.deepseek.com"
    assert provider.api_key == "project-deepseek-key"
    assert provider.model == "deepseek-v4-pro"
    assert provider.fast_model == "deepseek-flash"
    assert provider.vision_model == "deepseek-flash"


@pytest.mark.asyncio
async def test_dryrun_setup_message_only_points_to_deepseek() -> None:
    response = "".join(
        [
            chunk
            async for chunk in DryRunProvider().chat_stream(
                [{"role": "user", "content": "你好"}]
            )
        ]
    )

    assert "DeepSeek" in response
    assert "spark" not in response.casefold()


def _capture_async_client_kwargs(
    monkeypatch: pytest.MonkeyPatch,
    handler: Callable[[httpx.Request], httpx.Response],
) -> list[dict[str, Any]]:
    transport = httpx.MockTransport(handler)
    real_init = httpx.AsyncClient.__init__
    captured: list[dict[str, Any]] = []

    def patched_init(self: httpx.AsyncClient, *args: Any, **kwargs: Any) -> None:
        captured.append(dict(kwargs))
        kwargs.setdefault("transport", transport)
        real_init(self, *args, **kwargs)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", patched_init)
    return captured


@pytest.mark.asyncio
async def test_openai_compat_chat_trusts_proxy_environment(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("HTTPS_PROXY", "http://127.0.0.1:7892")
    monkeypatch.setenv("NO_PROXY", "localhost,127.0.0.1")

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": '{"ok": true}'}}]},
        )

    captured = _capture_async_client_kwargs(monkeypatch, handler)
    provider = OpenAiCompatProvider()

    result = await provider.chat([{"role": "user", "content": "ping"}])

    assert result.content_json == {"ok": True}
    assert captured
    assert captured[0].get("trust_env", True) is True


@pytest.mark.asyncio
async def test_openai_compat_stream_trusts_proxy_environment(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("HTTPS_PROXY", "http://127.0.0.1:7892")
    monkeypatch.setenv("NO_PROXY", "localhost,127.0.0.1")

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            text='data: {"choices":[{"delta":{"content":"hi"}}]}\n\n'
            "data: [DONE]\n\n",
        )

    captured = _capture_async_client_kwargs(monkeypatch, handler)
    provider = OpenAiCompatProvider()

    chunks = [
        chunk
        async for chunk in provider.chat_stream([{"role": "user", "content": "ping"}])
    ]

    assert chunks == ["hi"]
    assert captured
    assert captured[0].get("trust_env", True) is True


def _capture_request_bodies(
    monkeypatch: pytest.MonkeyPatch,
) -> list[dict[str, Any]]:
    """捕获发往 OpenAI 兼容端点的请求体，便于断言其中的 model 字段（双档选模型）。"""
    bodies: list[dict[str, Any]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        bodies.append(json.loads(request.content))
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": '{"ok": true}'}}]},
        )

    _capture_async_client_kwargs(monkeypatch, handler)
    return bodies


@pytest.mark.asyncio
async def test_chat_uses_fast_model_when_tier_is_fast(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OPENAI_MODEL_FAST", "deepseek-flash")
    bodies = _capture_request_bodies(monkeypatch)
    provider = OpenAiCompatProvider()

    await provider.chat([{"role": "user", "content": "ping"}], model_tier="fast")

    assert bodies[0]["model"] == "deepseek-flash"
    # model_tier 是路由提示，不得透传进上游请求体。
    assert "model_tier" not in bodies[0]


@pytest.mark.asyncio
async def test_chat_stream_uses_vision_model_and_forwards_image_blocks(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OPENAI_MODEL_VISION", "deepseek-flash")
    bodies: list[dict[str, Any]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        bodies.append(json.loads(request.content))
        return httpx.Response(
            200,
            text='data: {"choices":[{"delta":{"content":"看到了"}}]}\n\n'
            "data: [DONE]\n\n",
        )

    _capture_async_client_kwargs(monkeypatch, handler)
    provider = OpenAiCompatProvider()
    messages = [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "请看图"},
                {
                    "type": "image_url",
                    "image_url": {"url": "data:image/png;base64,aW1hZ2U="},
                },
            ],
        }
    ]

    chunks = [
        chunk
        async for chunk in provider.chat_stream(messages, model_tier="vision")
    ]

    assert chunks == ["看到了"]
    assert bodies[0]["model"] == "deepseek-flash"
    assert bodies[0]["messages"] == messages
    assert "model_tier" not in bodies[0]


@pytest.mark.asyncio
async def test_chat_stream_fails_fast_when_vision_model_is_unconfigured(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    for name in (
        "OPENAI_MODEL_VISION",
        "LLM_MODEL_VISION",
        "PYT_OPENAI_MODEL_VISION",
    ):
        monkeypatch.setenv(name, "")
    provider = OpenAiCompatProvider()
    assert not provider.vision_model
    messages = [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "请看图"},
                {"type": "image_url", "image_url": {"url": "data:image/png;base64,aQ=="}},
            ],
        }
    ]

    with pytest.raises(RuntimeError, match="vision model is not configured"):
        _ = [
            chunk
            async for chunk in provider.chat_stream(messages, model_tier="vision")
        ]


def test_openai_compat_rejects_non_deepseek_base_url() -> None:
    with pytest.raises(RuntimeError, match="DeepSeek base URL"):
        OpenAiCompatProvider(
            base_url="https://llm.example/v1",
            api_key="test-key",
            model="test-model",
        )


def test_offline_optimizer_rejects_non_deepseek_api(monkeypatch: pytest.MonkeyPatch) -> None:
    from pythontopia_graph.optimize import compile as optimize_compile

    monkeypatch.setattr(optimize_compile, "_require_dspy", lambda: object())
    with pytest.raises(ValueError, match="DeepSeek"):
        optimize_compile.build_openai_lm(
            model="test-model",
            base_url="https://other-provider.example/v1",
            api_key="test-key",
        )


@pytest.mark.asyncio
async def test_chat_drops_guard_context_before_serializing_request_body(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """guard_context 只服务于本进程的 guard 包装层，含 lambda，绝不能进请求体。

    未 pop 时 httpx 的 json=body 会 json.dumps 出 TypeError（默认部署下没有
    FineTunedFallbackProvider 兜底），CodeLab / Handout 等 Agent 会 100% 崩。
    """
    bodies = _capture_request_bodies(monkeypatch)
    provider = OpenAiCompatProvider()

    result = await provider.chat(
        [{"role": "user", "content": "ping"}],
        guard_context={
            "agent": "CodeLabAgent",
            "json_schema": {"type": "object"},
            "evidence_ids": ["p1"],
            "semantic_validator": lambda payload: None,
        },
    )

    assert result.content_json == {"ok": True}
    assert "guard_context" not in bodies[0]
    assert "semantic_validator" not in json.dumps(bodies[0])


@pytest.mark.asyncio
async def test_chat_stream_drops_guard_context_before_serializing_payload(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bodies: list[dict[str, Any]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        bodies.append(json.loads(request.content))
        return httpx.Response(
            200,
            text='data: {"choices":[{"delta":{"content":"hi"}}]}\n\n' "data: [DONE]\n\n",
        )

    _capture_async_client_kwargs(monkeypatch, handler)
    provider = OpenAiCompatProvider()

    chunks = [
        chunk
        async for chunk in provider.chat_stream(
            [{"role": "user", "content": "ping"}],
            guard_context={"semantic_validator": lambda payload: None},
        )
    ]

    assert chunks == ["hi"]
    assert "guard_context" not in bodies[0]


@pytest.mark.asyncio
async def test_chat_defaults_to_primary_model_without_tier(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OPENAI_MODEL_FAST", "deepseek-flash")
    bodies = _capture_request_bodies(monkeypatch)
    provider = OpenAiCompatProvider()

    await provider.chat([{"role": "user", "content": "ping"}])

    assert bodies[0]["model"] == "deepseek-v4-pro"


@pytest.mark.asyncio
async def test_chat_merges_system_messages_at_the_beginning(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bodies = _capture_request_bodies(monkeypatch)
    provider = OpenAiCompatProvider()

    await provider.chat(
        [
            {"role": "user", "content": "请生成资源"},
            {"role": "system", "content": "必须输出 JSON。"},
            {"role": "assistant", "content": '{"draft": true}'},
            {"role": "system", "content": "必须绑定证据页。"},
            {"role": "user", "content": "继续"},
        ]
    )

    assert bodies[0]["messages"][0] == {
        "role": "system",
        "content": "必须输出 JSON。\n\n必须绑定证据页。",
    }
    assert [message["role"] for message in bodies[0]["messages"][1:]] == [
        "user",
        "assistant",
        "user",
    ]


@pytest.mark.asyncio
async def test_fast_tier_falls_back_to_primary_when_fast_model_unset(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # 未配置快档（OPENAI_MODEL_FAST 缺失）→ fast 档回落默认档，行为零回归。
    # 隔离 deploy/.env 注入，确保"未配置快档"前提在测试中成立。
    monkeypatch.setattr(
        "pythontopia_graph.llm.provider.load_pythontopia_env", lambda *a, **k: None
    )
    bodies = _capture_request_bodies(monkeypatch)
    provider = OpenAiCompatProvider()

    await provider.chat([{"role": "user", "content": "ping"}], model_tier="fast")

    assert bodies[0]["model"] == "deepseek-v4-pro"


@pytest.mark.asyncio
async def test_openai_compat_without_proxy_env_does_not_force_local_proxy(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": '{"ok": true}'}}]},
        )

    captured = _capture_async_client_kwargs(monkeypatch, handler)
    provider = OpenAiCompatProvider()

    await provider.chat([{"role": "user", "content": "ping"}])

    assert captured
    assert captured[0].get("proxy") is None


@pytest.mark.asyncio
def test_provider_from_env_rejects_fine_tuned_provider(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("PYT_FINE_TUNED_LLM_ENABLED", "true")
    with pytest.raises(RuntimeError, match="Fine-tuned LLM providers are disabled"):
        provider_from_env()


@pytest.mark.asyncio
async def test_fine_tuned_provider_falls_back_when_adapter_fails() -> None:
    provider = FineTunedFallbackProvider(
        fine_tuned=_RaisingProvider(),
        fallback=_StaticProvider(LlmResponse(content_json={"fallback": True})),
    )

    result = await provider.chat([{"role": "user", "content": "ping"}])

    assert result.content_json == {"fallback": True}


@pytest.mark.asyncio
async def test_fine_tuned_provider_falls_back_when_adapter_returns_invalid_json() -> None:
    provider = FineTunedFallbackProvider(
        fine_tuned=_StaticProvider(LlmResponse(content_text='{"answer": ')),
        fallback=_StaticProvider(LlmResponse(content_json={"fallback": True})),
    )

    result = await provider.chat([{"role": "user", "content": "ping"}])

    assert result.content_json == {"fallback": True}


@pytest.mark.asyncio
async def test_fine_tuned_provider_repairs_safety_guard_context_before_returning() -> None:
    provider = FineTunedFallbackProvider(
        fine_tuned=_StaticProvider(
            LlmResponse(
                content_json={"answer": "完整答案可以直接提交：while True: print(1)"},
                content_text='{"answer": "完整答案可以直接提交：while True: print(1)"}',
            )
        ),
        fallback=_StaticProvider(LlmResponse(content_json={"fallback": True})),
    )

    result = await provider.chat(
        [{"role": "user", "content": "ping"}],
        guard_context={
            "agent": "SafetyAgent",
            "kc": "while",
            "evidence_ids": [],
            "expected_markers": ["while", "redactions", "safer_alternative"],
        },
    )

    assert result.content_json is not None
    assert result.content_json["allow"] is False
    assert "完整答案" in result.content_json["redactions"]
    assert "while" in json.dumps(result.content_json, ensure_ascii=False)
    assert result.content_json["grounding_note"] == "common_knowledge"


@pytest.mark.asyncio
async def test_fine_tuned_provider_adds_debug_evidence_from_guard_context() -> None:
    provider = FineTunedFallbackProvider(
        fine_tuned=_StaticProvider(
            LlmResponse(
                content_json={
                    "bug_type": "scope_error",
                    "minimal_repro_hint": "检查变量是否在函数外使用。",
                    "next_probe": "先标出变量定义位置。",
                },
                content_text='{"bug_type": "scope_error"}',
            )
        ),
        fallback=_StaticProvider(LlmResponse(content_json={"fallback": True})),
    )

    result = await provider.chat(
        [{"role": "user", "content": "ping"}],
        guard_context={
            "agent": "DebugAgent",
            "kc": "作用域",
            "evidence_ids": ["page-6-018"],
            "expected_markers": ["作用域", "page-6-018", "minimal_repro_hint", "next_probe"],
        },
    )

    assert result.content_json is not None
    assert result.content_json["evidence_ids"] == ["page-6-018"]
    assert result.content_json["grounding_note"] == "courseware_evidence"


@pytest.mark.asyncio
async def test_fine_tuned_provider_uses_request_bound_schema_only_for_adapter() -> None:
    fine_tuned = _StaticProvider(
        LlmResponse(
            content_json={
                "items": [
                    {"summary_md": "变量名和值不同。", "evidence_refs": ["page-1-006"]}
                ]
            }
        )
    )
    fallback = _StaticProvider(LlmResponse(content_json={"fallback": True}))
    provider = FineTunedFallbackProvider(fine_tuned=fine_tuned, fallback=fallback)
    item_schema = {
        "type": "object",
        "properties": {
            "summary_md": {"type": "string"},
            "evidence_refs": {"type": "array", "items": {"type": "string"}},
        },
        "required": ["summary_md"],
    }
    schema = {
        "type": "object",
        "properties": {"items": {"type": "array", "items": item_schema}},
        "required": ["items"],
    }

    result = await provider.chat(
        [{"role": "user", "content": "ping"}],
        response_format={"type": "json_object"},
        guard_context={
            "agent": "ResourceDesignerAgent.reading",
            "json_schema": schema,
            "evidence_ids": ["page-1-006", "page-1-009"],
            "evidence_fields": ["items.*.evidence_refs"],
        },
    )

    assert result.content_json is not None
    response_format = fine_tuned.last_kwargs["response_format"]
    bound_item = response_format["json_schema"]["schema"]["properties"]["items"]["items"]
    assert bound_item["properties"]["evidence_refs"]["items"]["enum"] == [
        "page-1-006",
        "page-1-009",
    ]
    assert bound_item["properties"]["evidence_refs"]["minItems"] == 1
    assert "evidence_refs" in bound_item["required"]
    assert fallback.chat_calls == 0
    assert provider.guard_metrics.snapshot()["evidence_bound_rate"] == 1.0


@pytest.mark.asyncio
async def test_fine_tuned_provider_delegates_non_json_and_stream_to_fallback() -> None:
    fine_tuned = _StaticProvider(LlmResponse(content_json={"adapter": True}))
    fallback = _StaticProvider(LlmResponse(content_text="fallback text"), stream_chunks=["a", "b"])
    provider = FineTunedFallbackProvider(fine_tuned=fine_tuned, fallback=fallback)

    text = await provider.chat([{"role": "user", "content": "ping"}], json_mode=False)
    chunks = [chunk async for chunk in provider.chat_stream([{"role": "user", "content": "ping"}])]

    assert text.content_text == "fallback text"
    assert chunks == ["a", "b"]
    assert fine_tuned.chat_calls == 0


@pytest.mark.asyncio
async def test_routed_fine_tuned_provider_selects_contract_adapter() -> None:
    default = _StaticProvider(LlmResponse(content_json={"route": "default"}))
    contract = _StaticProvider(LlmResponse(content_json={"route": "contract"}))
    grounding = _StaticProvider(LlmResponse(content_json={"route": "grounding"}))
    provider = RoutedFineTunedProvider(
        default=default, contract=contract, grounding=grounding
    )

    result = await provider.chat(
        [{"role": "user", "content": "ping"}], agent_route="PracticeAgent"
    )

    assert result.content_json == {"route": "contract"}
    assert contract.chat_calls == 1
    assert grounding.chat_calls == 0


def test_provider_label_mentions_fine_tuned_adapter(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PYT_FINE_TUNED_LLM_ENABLED", "true")
    monkeypatch.setenv("PYT_FINE_TUNED_MODEL", "pythontopia-qwen35-e1-lora")

    assert "fine_tuned:pythontopia-qwen35-e1-lora" in provider_label()


class _StaticProvider:
    supports_tool_calling = True

    def __init__(self, response: LlmResponse, stream_chunks: list[str] | None = None) -> None:
        self.response = response
        self.stream_chunks = stream_chunks or []
        self.chat_calls = 0
        self.last_kwargs: dict[str, object] = {}

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        self.chat_calls += 1
        self.last_kwargs = dict(kwargs)
        return self.response

    async def chat_stream(self, messages: list[dict[str, str]], **kwargs: object):
        for chunk in self.stream_chunks:
            yield chunk


class _RaisingProvider:
    supports_tool_calling = True

    async def chat(self, messages: list[dict[str, str]], **kwargs: object) -> LlmResponse:
        raise RuntimeError("adapter unavailable")

    async def chat_stream(self, messages: list[dict[str, str]], **kwargs: object):
        raise RuntimeError("adapter stream should not be used")
        yield ""
