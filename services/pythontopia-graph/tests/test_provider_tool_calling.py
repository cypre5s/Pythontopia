"""原生工具调用（function calling）provider 支持的单元测试（MVP-1 地基）。

覆盖：tool_calls 解析/透传、传 tools 时不强制 json_object、无 tools 时仍走 json_object
（零回归）、能力位探测、DryRun 离线确定性工具回合（供有界工具调用循环离线单测）。
"""

from __future__ import annotations

import json
from collections.abc import Callable
from typing import Any

import httpx
import pytest

from pythontopia_graph.llm.provider import (
    DryRunProvider,
    OpenAiCompatProvider,
    _parse_chat_message,
    provider_supports_tool_calling,
)

# 取自实测的 DeepSeek tool_calls 响应形态（content 为 null，tool_calls 为数组）。
_TOOL_CALL_PAYLOAD: dict[str, Any] = {
    "choices": [
        {
            "finish_reason": "tool_calls",
            "message": {
                "role": "assistant",
                "content": None,
                "tool_calls": [
                    {
                        "index": 0,
                        "id": "call_00_abc",
                        "type": "function",
                        "function": {
                            "name": "get_weather",
                            "arguments": '{"city": "北京"}',
                        },
                    }
                ],
            },
        }
    ]
}


@pytest.fixture(autouse=True)
def _env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OPENAI_BASE_URL", "https://api.deepseek.com")
    monkeypatch.setenv("OPENAI_API_KEY", "deepseek-key")
    monkeypatch.setenv("OPENAI_MODEL", "deepseek-v4-pro")
    monkeypatch.setenv("PYT_LLM_PROVIDER", "openai_compat")
    for name in (
        "PYT_OPENAI_BASE_URL",
        "PYT_OPENAI_API_KEY",
        "PYT_OPENAI_MODEL",
        "OPENAI_MODEL_FAST",
        "LLM_MODEL_FAST",
        "PYT_OPENAI_MODEL_FAST",
        "HTTPS_PROXY",
        "https_proxy",
        "HTTP_PROXY",
        "http_proxy",
    ):
        monkeypatch.delenv(name, raising=False)
    # 隔离 deploy/.env 注入，保证测试前提确定。
    monkeypatch.setattr(
        "pythontopia_graph.llm.provider.load_pythontopia_env", lambda *a, **k: None
    )


def _patch_client(
    monkeypatch: pytest.MonkeyPatch,
    handler: Callable[[httpx.Request], httpx.Response],
) -> None:
    transport = httpx.MockTransport(handler)
    real_init = httpx.AsyncClient.__init__

    def patched_init(self: httpx.AsyncClient, *args: Any, **kwargs: Any) -> None:
        kwargs.setdefault("transport", transport)
        real_init(self, *args, **kwargs)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", patched_init)


# ── 纯解析函数 ────────────────────────────────────────────────────────────


def test_parse_chat_message_surfaces_tool_calls() -> None:
    resp = _parse_chat_message(_TOOL_CALL_PAYLOAD)
    assert resp.tool_calls is not None
    assert resp.tool_calls[0]["function"]["name"] == "get_weather"
    assert resp.content_text is None
    assert resp.content_json is None


def test_parse_chat_message_plain_text_back_compatible() -> None:
    resp = _parse_chat_message({"choices": [{"message": {"content": '{"ok": true}'}}]})
    assert resp.content_json == {"ok": True}
    assert resp.content_text == '{"ok": true}'
    assert resp.tool_calls is None


def test_parse_chat_message_joins_array_content_parts() -> None:
    resp = _parse_chat_message(
        {
            "choices": [
                {
                    "message": {
                        "content": [
                            {"type": "text", "text": '{"ok": '},
                            {"type": "text", "text": "true}"},
                            {
                                "type": "image_url",
                                "image_url": {"url": "data:image/png;base64,ignored"},
                            },
                        ]
                    }
                }
            ]
        }
    )
    assert resp.content_json == {"ok": True}
    assert resp.content_text == '{"ok": true}'


def test_parse_chat_message_malformed_failsoft() -> None:
    assert _parse_chat_message({}).tool_calls is None
    assert _parse_chat_message({"choices": []}).content_text is None
    # tool_calls 为空数组视作无工具调用。
    empty = {"choices": [{"message": {"content": "hi", "tool_calls": []}}]}
    assert _parse_chat_message(empty).tool_calls is None


# ── OpenAiCompatProvider ─────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_openai_compat_returns_tool_calls(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=_TOOL_CALL_PAYLOAD)

    _patch_client(monkeypatch, handler)
    provider = OpenAiCompatProvider()
    tools = [{"type": "function", "function": {"name": "get_weather", "parameters": {}}}]
    resp = await provider.chat([{"role": "user", "content": "天气"}], tools=tools)

    assert resp.tool_calls is not None
    assert resp.tool_calls[0]["function"]["name"] == "get_weather"


@pytest.mark.asyncio
async def test_tools_request_skips_json_object(monkeypatch: pytest.MonkeyPatch) -> None:
    bodies: list[dict[str, Any]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        bodies.append(json.loads(request.content))
        return httpx.Response(200, json=_TOOL_CALL_PAYLOAD)

    _patch_client(monkeypatch, handler)
    provider = OpenAiCompatProvider()
    tools = [{"type": "function", "function": {"name": "x", "parameters": {}}}]
    await provider.chat([{"role": "user", "content": "hi"}], tools=tools)

    assert "response_format" not in bodies[0]
    assert "tools" in bodies[0]


@pytest.mark.asyncio
async def test_no_tools_still_forces_json_object(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bodies: list[dict[str, Any]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        bodies.append(json.loads(request.content))
        return httpx.Response(200, json={"choices": [{"message": {"content": "{}"}}]})

    _patch_client(monkeypatch, handler)
    provider = OpenAiCompatProvider()
    await provider.chat([{"role": "user", "content": "hi"}])

    assert bodies[0]["response_format"] == {"type": "json_object"}


# ── 能力位探测 ────────────────────────────────────────────────────────────


def test_capability_flags() -> None:
    assert provider_supports_tool_calling(OpenAiCompatProvider.__new__(OpenAiCompatProvider))
    assert provider_supports_tool_calling(DryRunProvider())
    assert not provider_supports_tool_calling(object())


# ── DryRun 离线工具回合（确定性、可终止）───────────────────────────────────


@pytest.mark.asyncio
async def test_dryrun_tool_turn_then_final() -> None:
    provider = DryRunProvider()
    tools = [{"type": "function", "function": {"name": "get_weather", "parameters": {}}}]

    first = await provider.chat([{"role": "user", "content": "天气"}], tools=tools)
    assert first.tool_calls is not None
    assert first.tool_calls[0]["function"]["name"] == "get_weather"

    final = await provider.chat(
        [
            {"role": "user", "content": "天气"},
            {"role": "tool", "content": "晴 26C"},
        ],
        tools=tools,
    )
    assert final.tool_calls is None
    assert final.content_text


@pytest.mark.asyncio
async def test_dryrun_without_tools_unchanged() -> None:
    # 不传 tools 时 dry-run 行为不变（仍返回 profile_draft 占位），零回归。
    provider = DryRunProvider()
    resp = await provider.chat([{"role": "user", "content": "你好"}])
    assert resp.tool_calls is None
    assert resp.content_json is not None
