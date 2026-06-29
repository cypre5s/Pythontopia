"""fetch_memory_brief 单元测试：HTTP 取回 + 全路径 fail-soft 降级（M2-mem-brief）。

冻结契约：``MemoryBrief``（``memory/contract.py``，只读）。后端 brief 端点
``GET /api/learner/memory/brief?query=...`` 由兄弟单元 M1-mem-backend 落地；此处用
``httpx.MockTransport`` 解耦，零真实联网。

测试矩阵（§4 M graph 侧 + §8.3 兼容性 + §0 fail-soft 红线）：
  ① 正常取回 → 解析为 MemoryBrief（全字段）
  ② 请求形状：query + user_id 走查询串、路径与端点契约一致
  ③ HTTP 超时 / 连接失败 → 空 MemoryBrief，不抛
  ④ 非 200 → 空 brief（多状态码参数化）
  ⑤ base_url 经 env(PYT_BACKEND_URL) 可配 / 默认 :18080 / 显式参数覆盖 env
  ⑥ 空响应字段 / 缺字段 → contract 默认值；未知字段向后兼容忽略
  ⑦ 响应体损坏（非 JSON / 非对象 / 字段类型错）→ fail-soft 空 brief
  ⑧ 可选内部令牌头按 env 挂载 / 省略
  ⑨ 自定义 timeout 透传 + 内部调用 trust_env=False
"""

from __future__ import annotations

from typing import Any

import httpx
import pytest

from pythontopia_graph.memory.brief_client import (
    DEFAULT_BACKEND_URL,
    fetch_memory_brief,
)
from pythontopia_graph.memory.contract import MemoryBrief

_FULL_BRIEF_BODY: dict[str, Any] = {
    "facts": ["上周完成 for 循环练习", "偏好看图解胜过读长文"],
    "weak_kcs": ["PY43-KC-030", "PY43-KC-031"],
    "style_notes": ["视觉型学习者", "需要循序渐进"],
    "as_of": "2026-06-17T05:00:00Z",
    "stable_preferences": ["偏好图解"],
    "struggle_patterns": ["微测未过：PY43-KC-030"],
    "recent_wins": ["练习通过：PY43-KC-001"],
    "intervention_hints": ["优先图解/结构化示例。"],
}


@pytest.fixture(autouse=True)
def _isolate_backend_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """隔离宿主 env，让每个测试自带 base_url / 内部令牌前提。"""
    monkeypatch.delenv("PYT_BACKEND_URL", raising=False)
    monkeypatch.delenv("PYT_BACKEND_INTERNAL_TOKEN", raising=False)


def _install_transport(
    monkeypatch: pytest.MonkeyPatch,
    handler: Any,
) -> None:
    """把 MockTransport 注入所有 httpx.AsyncClient 构造（沿用项目既有 provider 测试范式）。"""
    transport = httpx.MockTransport(handler)
    real_init = httpx.AsyncClient.__init__

    def patched_init(self: httpx.AsyncClient, *args: Any, **kwargs: Any) -> None:
        kwargs.setdefault("transport", transport)
        real_init(self, *args, **kwargs)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", patched_init)


def _capture_client_kwargs(
    monkeypatch: pytest.MonkeyPatch,
    handler: Any,
) -> list[dict[str, Any]]:
    """捕获 AsyncClient 构造 kwargs（断言 timeout / trust_env）。"""
    transport = httpx.MockTransport(handler)
    real_init = httpx.AsyncClient.__init__
    captured: list[dict[str, Any]] = []

    def patched_init(self: httpx.AsyncClient, *args: Any, **kwargs: Any) -> None:
        captured.append(dict(kwargs))
        kwargs.setdefault("transport", transport)
        real_init(self, *args, **kwargs)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", patched_init)
    return captured


def _ok_empty(_: httpx.Request) -> httpx.Response:
    return httpx.Response(200, json={})


# ── ① 正常取回 ──


@pytest.mark.asyncio
async def test_fetch_returns_parsed_brief(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=_FULL_BRIEF_BODY)

    _install_transport(monkeypatch, handler)

    brief = await fetch_memory_brief(42, "for 循环怎么写")

    assert isinstance(brief, MemoryBrief)
    assert brief.facts == _FULL_BRIEF_BODY["facts"]
    assert brief.weak_kcs == _FULL_BRIEF_BODY["weak_kcs"]
    assert brief.style_notes == _FULL_BRIEF_BODY["style_notes"]
    assert brief.as_of == "2026-06-17T05:00:00Z"
    assert brief.stable_preferences == _FULL_BRIEF_BODY["stable_preferences"]
    assert brief.struggle_patterns == _FULL_BRIEF_BODY["struggle_patterns"]
    assert brief.recent_wins == _FULL_BRIEF_BODY["recent_wins"]
    assert brief.intervention_hints == _FULL_BRIEF_BODY["intervention_hints"]


# ── ② 请求形状 ──


@pytest.mark.asyncio
async def test_fetch_sends_query_and_user_id_to_contract_path(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = request.url
        captured["method"] = request.method
        return httpx.Response(200, json=_FULL_BRIEF_BODY)

    _install_transport(monkeypatch, handler)

    await fetch_memory_brief(7, "列表推导式")

    url: httpx.URL = captured["url"]
    assert captured["method"] == "GET"
    assert url.path == "/api/learner/memory/brief"
    assert url.params.get("query") == "列表推导式"
    assert url.params.get("user_id") == "7"


# ── ③ 超时 / 连接失败 → 空 brief，不抛 ──


@pytest.mark.asyncio
async def test_failsoft_on_timeout(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        raise httpx.ConnectTimeout("backend unreachable")

    _install_transport(monkeypatch, handler)

    brief = await fetch_memory_brief(1, "q")

    assert brief == MemoryBrief()


@pytest.mark.asyncio
async def test_failsoft_on_connect_error(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused")

    _install_transport(monkeypatch, handler)

    brief = await fetch_memory_brief(1, "q")

    assert brief.facts == []
    assert brief.weak_kcs == []
    assert brief.style_notes == []
    assert brief.as_of == ""


@pytest.mark.asyncio
async def test_failsoft_on_read_timeout(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("slow backend")

    _install_transport(monkeypatch, handler)

    assert await fetch_memory_brief(1, "q") == MemoryBrief()


# ── ④ 非 200 → 空 brief ──


@pytest.mark.asyncio
@pytest.mark.parametrize("status", [400, 401, 403, 404, 429, 500, 502, 503])
async def test_failsoft_on_non_200(
    monkeypatch: pytest.MonkeyPatch, status: int
) -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(status, json={"error": "nope"})

    _install_transport(monkeypatch, handler)

    assert await fetch_memory_brief(1, "q") == MemoryBrief()


# ── ⑤ base_url 配置 ──


@pytest.mark.asyncio
async def test_base_url_from_env(monkeypatch: pytest.MonkeyPatch) -> None:
    # 末尾斜杠应被规整，端点拼接不出现双斜杠。
    monkeypatch.setenv("PYT_BACKEND_URL", "http://backend.internal:9999/")
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = request.url
        return httpx.Response(200, json={})

    _install_transport(monkeypatch, handler)

    await fetch_memory_brief(1, "q")

    url: httpx.URL = captured["url"]
    assert url.host == "backend.internal"
    assert url.port == 9999
    assert url.path == "/api/learner/memory/brief"


@pytest.mark.asyncio
async def test_default_base_url_when_env_unset(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = request.url
        return httpx.Response(200, json={})

    _install_transport(monkeypatch, handler)

    await fetch_memory_brief(1, "q")

    assert str(captured["url"]).startswith(DEFAULT_BACKEND_URL)


@pytest.mark.asyncio
async def test_explicit_base_url_overrides_env(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("PYT_BACKEND_URL", "http://env-host:1111")
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = request.url
        return httpx.Response(200, json={})

    _install_transport(monkeypatch, handler)

    await fetch_memory_brief(1, "q", base_url="http://param-host:2222")

    url: httpx.URL = captured["url"]
    assert url.host == "param-host"
    assert url.port == 2222


# ── ⑥ 空 / 缺 / 未知字段 ──


@pytest.mark.asyncio
async def test_empty_body_yields_default_brief(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _install_transport(monkeypatch, _ok_empty)

    brief = await fetch_memory_brief(1, "q")

    assert brief == MemoryBrief()
    assert brief.facts == []
    assert brief.weak_kcs == []
    assert brief.style_notes == []
    assert brief.as_of == ""


@pytest.mark.asyncio
async def test_partial_body_fills_defaults(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"facts": ["只有 facts 字段"]})

    _install_transport(monkeypatch, handler)

    brief = await fetch_memory_brief(1, "q")

    assert brief.facts == ["只有 facts 字段"]
    assert brief.weak_kcs == []
    assert brief.style_notes == []
    assert brief.as_of == ""


@pytest.mark.asyncio
async def test_unknown_fields_ignored_forward_compatible(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={**_FULL_BRIEF_BODY, "future_field": 123})

    _install_transport(monkeypatch, handler)

    brief = await fetch_memory_brief(1, "q")

    assert brief.facts == _FULL_BRIEF_BODY["facts"]
    assert brief.as_of == "2026-06-17T05:00:00Z"


# ── ⑦ 响应体损坏 ──


@pytest.mark.asyncio
async def test_failsoft_on_invalid_json(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b"not-json{{{")

    _install_transport(monkeypatch, handler)

    assert await fetch_memory_brief(1, "q") == MemoryBrief()


@pytest.mark.asyncio
async def test_failsoft_on_non_object_json(monkeypatch: pytest.MonkeyPatch) -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=[1, 2, 3])

    _install_transport(monkeypatch, handler)

    assert await fetch_memory_brief(1, "q") == MemoryBrief()


@pytest.mark.asyncio
async def test_failsoft_on_malformed_field_types(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # facts 应为 list[str]，给 str → Pydantic 校验失败 → fail-soft 空 brief（不注入半成品）。
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"facts": "should-be-a-list"})

    _install_transport(monkeypatch, handler)

    assert await fetch_memory_brief(1, "q") == MemoryBrief()


# ── ⑧ 可选内部令牌头 ──


@pytest.mark.asyncio
async def test_internal_token_attached_when_env_set(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("PYT_BACKEND_INTERNAL_TOKEN", "secret-token")
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["token"] = request.headers.get("X-Internal-Token")
        return httpx.Response(200, json={})

    _install_transport(monkeypatch, handler)

    await fetch_memory_brief(1, "q")

    assert captured["token"] == "secret-token"


@pytest.mark.asyncio
async def test_internal_token_omitted_when_env_unset(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["has_token"] = "X-Internal-Token" in request.headers
        return httpx.Response(200, json={})

    _install_transport(monkeypatch, handler)

    await fetch_memory_brief(1, "q")

    assert captured["has_token"] is False


# ── ⑨ timeout 透传 + 内部调用 trust_env=False ──


@pytest.mark.asyncio
async def test_custom_timeout_and_trust_env_false(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured = _capture_client_kwargs(monkeypatch, _ok_empty)

    await fetch_memory_brief(1, "q", timeout=1.25)

    assert captured
    assert captured[0].get("timeout") == 1.25
    assert captured[0].get("trust_env") is False
