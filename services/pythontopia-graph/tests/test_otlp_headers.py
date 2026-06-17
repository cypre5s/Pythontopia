"""observability.otel 的 OTLP exporter endpoint/headers 解析与挂载覆盖测试（O1-otel-langfuse）。

聚焦并发构建主契约 §3.6 第 1 条：让既有 OTLP 导出可带 ``OTEL_EXPORTER_OTLP_HEADERS``
（Basic auth ``pk:sk``），并支持由 ``LANGFUSE_HOST`` / ``LANGFUSE_PUBLIC_KEY`` /
``LANGFUSE_SECRET_KEY`` 推导出指向 Langfuse ``/api/public/otel`` 的 traces endpoint +
Basic auth header。严格保持既有降级语义：缺 endpoint / 缺 OTLP exporter 包 / 任何异常
→ no-op，绝不拖垮主链路（§0 fail-soft 观测）。

测试矩阵：
- headers 解析正确（OTEL_EXPORTER_OTLP_HEADERS 单/多对、base64 padding、Langfuse 拼装、precedence、malformed 回退）
- endpoint 解析正确（traces signal as-is / base 补 /v1/traces / Langfuse host 推导 / 末尾斜杠 / precedence）
- 无 endpoint → no-op（不尝试构造 exporter、不挂 processor）
- 缺 OTLP exporter 包 → no-op（sys.modules 注入 None 模拟缺包）
- _maybe_attach_otlp_exporter 把解析出的 endpoint+headers 正确透传到 exporter 并挂 BatchSpanProcessor
- 异常被吞（构造 exporter 抛错 → no-op）
"""

from __future__ import annotations

import base64
import sys
from typing import Any

import pytest

from pythontopia_graph.observability import otel

_OTLP_ENV_VARS = (
    "OTEL_EXPORTER_OTLP_ENDPOINT",
    "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT",
    "OTEL_EXPORTER_OTLP_HEADERS",
    "LANGFUSE_HOST",
    "LANGFUSE_PUBLIC_KEY",
    "LANGFUSE_SECRET_KEY",
)

_OTLP_EXPORTER_MODULE = "opentelemetry.exporter.otlp.proto.http.trace_exporter"


@pytest.fixture(autouse=True)
def _clean_otlp_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """每个用例从干净环境起步：清掉宿主可能注入的 OTLP / Langfuse 变量，避免串扰。"""
    for var in _OTLP_ENV_VARS:
        monkeypatch.delenv(var, raising=False)


class _FakeProvider:
    """记录 add_span_processor 调用的最小 TracerProvider 替身（不依赖真实 SDK）。"""

    def __init__(self) -> None:
        self.processors: list[Any] = []

    def add_span_processor(self, processor: Any) -> None:
        self.processors.append(processor)


# ─────────────────────────────────────────────────────────────────────────────
# headers 解析（纯函数，始终运行，不依赖 otel 安装）
# ─────────────────────────────────────────────────────────────────────────────


def test_resolve_headers_none_when_unset() -> None:
    assert otel._resolve_otlp_headers() is None


def test_resolve_headers_parses_single_pair(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_HEADERS", "Authorization=Basic cGs6c2s=")
    assert otel._resolve_otlp_headers() == {"Authorization": "Basic cGs6c2s="}


def test_resolve_headers_parses_multiple_pairs(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv(
        "OTEL_EXPORTER_OTLP_HEADERS", "Authorization=Basic abc==, x-scope = prod "
    )
    assert otel._resolve_otlp_headers() == {
        "Authorization": "Basic abc==",
        "x-scope": "prod",
    }


def test_resolve_headers_keeps_base64_padding(monkeypatch: pytest.MonkeyPatch) -> None:
    # base64 值里的 '=' padding 必须保留：解析须 partition 首个 '='，不能整体 split。
    monkeypatch.setenv(
        "OTEL_EXPORTER_OTLP_HEADERS", "Authorization=Basic dXNlcjpwYXNz=="
    )
    assert otel._resolve_otlp_headers() == {"Authorization": "Basic dXNlcjpwYXNz=="}


def test_resolve_headers_from_langfuse_keys(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LANGFUSE_PUBLIC_KEY", "pk-lf-123")
    monkeypatch.setenv("LANGFUSE_SECRET_KEY", "sk-lf-456")
    token = base64.b64encode(b"pk-lf-123:sk-lf-456").decode("ascii")
    assert otel._resolve_otlp_headers() == {"Authorization": f"Basic {token}"}


def test_resolve_headers_langfuse_needs_both_keys(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("LANGFUSE_PUBLIC_KEY", "pk-only")
    assert otel._resolve_otlp_headers() is None


def test_resolve_headers_env_takes_precedence_over_langfuse(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_HEADERS", "Authorization=Basic explicit")
    monkeypatch.setenv("LANGFUSE_PUBLIC_KEY", "pk")
    monkeypatch.setenv("LANGFUSE_SECRET_KEY", "sk")
    assert otel._resolve_otlp_headers() == {"Authorization": "Basic explicit"}


def test_resolve_headers_malformed_env_falls_back_to_langfuse(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # 设了 headers env 但无任何合法 k=v → 视作"未提供 header"，回退 Langfuse Basic auth。
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_HEADERS", "garbage-no-equals")
    monkeypatch.setenv("LANGFUSE_PUBLIC_KEY", "pk")
    monkeypatch.setenv("LANGFUSE_SECRET_KEY", "sk")
    token = base64.b64encode(b"pk:sk").decode("ascii")
    assert otel._resolve_otlp_headers() == {"Authorization": f"Basic {token}"}


# ─────────────────────────────────────────────────────────────────────────────
# endpoint 解析（纯函数，始终运行）
# ─────────────────────────────────────────────────────────────────────────────


def test_resolve_endpoint_none_when_unset() -> None:
    assert otel._resolve_otlp_traces_endpoint() is None


def test_resolve_endpoint_traces_signal_used_as_is(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv(
        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "https://collector.example/v1/traces"
    )
    assert (
        otel._resolve_otlp_traces_endpoint() == "https://collector.example/v1/traces"
    )


def test_resolve_endpoint_base_appends_v1_traces(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "https://collector.example/")
    assert (
        otel._resolve_otlp_traces_endpoint() == "https://collector.example/v1/traces"
    )


def test_resolve_endpoint_from_langfuse_host(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LANGFUSE_HOST", "https://cloud.langfuse.com")
    assert (
        otel._resolve_otlp_traces_endpoint()
        == "https://cloud.langfuse.com/api/public/otel/v1/traces"
    )


def test_resolve_endpoint_langfuse_host_trailing_slash(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("LANGFUSE_HOST", "http://localhost:3000/")
    assert (
        otel._resolve_otlp_traces_endpoint()
        == "http://localhost:3000/api/public/otel/v1/traces"
    )


def test_resolve_endpoint_precedence(monkeypatch: pytest.MonkeyPatch) -> None:
    # 优先级：signal-specific traces endpoint > base endpoint > Langfuse host。
    monkeypatch.setenv(
        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "https://signal/v1/traces"
    )
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "https://base")
    monkeypatch.setenv("LANGFUSE_HOST", "https://lf")
    assert otel._resolve_otlp_traces_endpoint() == "https://signal/v1/traces"


# ─────────────────────────────────────────────────────────────────────────────
# _build_otlp_span_exporter 降级（缺包）
# ─────────────────────────────────────────────────────────────────────────────


def test_build_exporter_missing_package_returns_none(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # sys.modules 注入 None → `from ... import` 抛 ImportError → 降级 None（即便宿主装了包）。
    monkeypatch.setitem(sys.modules, _OTLP_EXPORTER_MODULE, None)
    assert otel._build_otlp_span_exporter("https://x/v1/traces", None) is None


# ─────────────────────────────────────────────────────────────────────────────
# _maybe_attach_otlp_exporter 行为
# ─────────────────────────────────────────────────────────────────────────────


def test_maybe_attach_no_endpoint_is_noop(monkeypatch: pytest.MonkeyPatch) -> None:
    called: list[Any] = []
    monkeypatch.setattr(
        otel, "_build_otlp_span_exporter", lambda *a, **k: called.append((a, k))
    )
    provider = _FakeProvider()
    otel._maybe_attach_otlp_exporter(provider)
    assert provider.processors == []
    assert called == []  # 无 endpoint 时根本不尝试构造 exporter


def test_maybe_attach_missing_package_is_noop(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "https://lf/api/public/otel")
    monkeypatch.setitem(sys.modules, _OTLP_EXPORTER_MODULE, None)
    provider = _FakeProvider()
    otel._maybe_attach_otlp_exporter(provider)
    assert provider.processors == []


def test_maybe_attach_passes_endpoint_and_headers(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    pytest.importorskip("opentelemetry.sdk.trace.export")
    captured: dict[str, Any] = {}

    def fake_build(endpoint: str, headers: Any) -> Any:
        captured["endpoint"] = endpoint
        captured["headers"] = headers
        return object()

    class _FakeBatchProcessor:
        def __init__(self, exporter: Any) -> None:
            self.exporter = exporter

    monkeypatch.setattr(otel, "_build_otlp_span_exporter", fake_build)
    monkeypatch.setattr(
        "opentelemetry.sdk.trace.export.BatchSpanProcessor", _FakeBatchProcessor
    )
    monkeypatch.setenv(
        "OTEL_EXPORTER_OTLP_ENDPOINT", "https://lf.example/api/public/otel"
    )
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_HEADERS", "Authorization=Basic cGs6c2s=")

    provider = _FakeProvider()
    otel._maybe_attach_otlp_exporter(provider)

    assert captured["endpoint"] == "https://lf.example/api/public/otel/v1/traces"
    assert captured["headers"] == {"Authorization": "Basic cGs6c2s="}
    assert len(provider.processors) == 1
    assert isinstance(provider.processors[0], _FakeBatchProcessor)
    assert provider.processors[0].exporter is not None


def test_maybe_attach_langfuse_env_end_to_end(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """仅给 Langfuse 三件套 → 推导 /api/public/otel/v1/traces + Basic auth header。"""
    pytest.importorskip("opentelemetry.sdk.trace.export")
    captured: dict[str, Any] = {}

    def fake_build(endpoint: str, headers: Any) -> Any:
        captured["endpoint"] = endpoint
        captured["headers"] = headers
        return object()

    class _FakeBatchProcessor:
        def __init__(self, exporter: Any) -> None:
            self.exporter = exporter

    monkeypatch.setattr(otel, "_build_otlp_span_exporter", fake_build)
    monkeypatch.setattr(
        "opentelemetry.sdk.trace.export.BatchSpanProcessor", _FakeBatchProcessor
    )
    monkeypatch.setenv("LANGFUSE_HOST", "https://cloud.langfuse.com")
    monkeypatch.setenv("LANGFUSE_PUBLIC_KEY", "pk-lf-1")
    monkeypatch.setenv("LANGFUSE_SECRET_KEY", "sk-lf-2")

    provider = _FakeProvider()
    otel._maybe_attach_otlp_exporter(provider)

    token = base64.b64encode(b"pk-lf-1:sk-lf-2").decode("ascii")
    assert (
        captured["endpoint"]
        == "https://cloud.langfuse.com/api/public/otel/v1/traces"
    )
    assert captured["headers"] == {"Authorization": f"Basic {token}"}
    assert len(provider.processors) == 1


def test_maybe_attach_swallows_exporter_exception(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def boom(endpoint: str, headers: Any) -> Any:
        raise RuntimeError("exporter blew up")

    monkeypatch.setattr(otel, "_build_otlp_span_exporter", boom)
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "https://lf/api/public/otel")
    provider = _FakeProvider()
    # 异常必须被吞：no-op，不抛、不挂 processor。
    otel._maybe_attach_otlp_exporter(provider)
    assert provider.processors == []
