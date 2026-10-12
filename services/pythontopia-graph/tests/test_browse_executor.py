"""BROWSE executor + sanitize + registry 目录 单测（§3.1 / §4 B1）。

零真实联网：executor 注入 fake browser engine；默认 engine 的 browser-use 缺失/拦截分支
用 monkeypatch 强制走 fail-fast。覆盖矩阵：
①白名单内 goal→受界步骤 + citations ②白名单外域→拒绝 fail-fast 不发请求
③超 max_steps→truncated ④sanitize 去敏（脚本/隐藏域/PII 不回传）
⑤executor 异常→error 字段 + 已走步骤 ⑥registry 目录含 BROWSE 且 quiz scope 不暴露。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import pytest

from pythontopia_graph.tools.browse.contract import BrowseRequest
from pythontopia_graph.tools.browse.executor import (
    EXTERNAL_CONTENT_LABEL,
    BrowseDependencyError,
    ProposedStep,
    _PlaywrightReadOnlySession,
    create_default_engine,
    execute_browse,
)
from pythontopia_graph.tools.browse import executor as browse_executor_module
from pythontopia_graph.tools.browse.sanitize import (
    redact_pii,
    sanitize_dom_summary,
)
from pythontopia_graph.tools.registry import (
    TOOLS,
    intent_catalog_lines,
    tool_catalog_payload,
    tools_for_scope,
)

DOCS = "docs.python.org"


# ── 可注入 fake browser engine（零联网，脚本化步骤）──


@dataclass
class _Plan:
    action: str
    url: str = ""
    content: str = ""


@dataclass
class FakeBrowseEngine:
    """脚本化假浏览器：propose 返回当前计划步，execute 才真正"执行"并推进游标。

    记录 opened/closed/executed_urls/open_args，供断言"未发请求"等不变量。
    """

    plans: list[_Plan]
    raise_on_index: int | None = None
    raise_on_open: bool = False
    _cursor: int = 0
    opened: bool = False
    closed: bool = False
    executed_urls: list[str] = field(default_factory=list)
    open_args: dict[str, object] = field(default_factory=dict)

    async def open(
        self,
        *,
        goal: str,
        allowed_domains: list[str],
        max_steps: int,
        max_bytes: int = 131_072,
        timeout_seconds: float = 45.0,
    ) -> None:
        if self.raise_on_open:
            raise RuntimeError("boom-open")
        self.opened = True
        self.open_args = {
            "goal": goal,
            "allowed_domains": allowed_domains,
            "max_steps": max_steps,
            "max_bytes": max_bytes,
            "timeout_seconds": timeout_seconds,
        }

    async def propose(self) -> ProposedStep | None:
        if self._cursor >= len(self.plans):
            return None
        plan = self.plans[self._cursor]
        return ProposedStep(action=plan.action, url=plan.url)

    async def execute(self, step: ProposedStep) -> str:
        cur = self._cursor
        self._cursor += 1
        if self.raise_on_index is not None and cur == self.raise_on_index:
            raise RuntimeError("boom-exec")
        self.executed_urls.append(step.url)
        return self.plans[cur].content

    async def close(self) -> None:
        self.closed = True


def _req(goal: str = "去 python 官网查 sorted 的稳定性", **kw: object) -> BrowseRequest:
    base: dict[str, object] = {
        "goal": goal,
        "allowed_domains": [DOCS],
        "max_steps": 6,
        "user_id": 1,
    }
    base.update(kw)
    return BrowseRequest(**base)  # type: ignore[arg-type]


# ── ① 白名单内 goal → 受界步骤 + citations ──


async def test_happy_path_bounded_steps_with_citations() -> None:
    engine = FakeBrowseEngine(
        plans=[
            _Plan(
                "navigate",
                "https://docs.python.org/3/howto/sorting.html",
                "<p>sorted() is guaranteed stable.</p><script>window.x='EVILJS'</script>",
            ),
            _Plan(
                "read",
                "https://docs.python.org/3/library/functions.html#sorted",
                "sorted returns a new sorted list; the sort is stable.",
            ),
            _Plan("stop"),
        ]
    )
    result = await execute_browse(_req(), engine=engine, env={})

    assert engine.opened is True
    assert engine.closed is True
    assert result.error == ""
    assert result.truncated is False
    assert len(result.steps) == 2
    assert result.steps[0].action == "navigate"
    assert result.steps[0].index == 0
    assert result.steps[1].index == 1
    # citations = 命中 url（去重，外部内容，不进课件引用）。
    assert "https://docs.python.org/3/howto/sorting.html" in result.citations
    assert "https://docs.python.org/3/library/functions.html#sorted" in result.citations
    # 摘要可读且去敏（脚本体不回传）。
    assert "stable" in result.final_summary
    assert "EVILJS" not in result.final_summary
    for step in result.steps:
        assert "EVILJS" not in step.summary
    # 有界 + 有效白名单透传给 engine。
    assert engine.open_args["allowed_domains"] == [DOCS]
    assert engine.open_args["max_steps"] == 6


async def test_final_summary_carries_external_content_label() -> None:
    engine = FakeBrowseEngine(
        plans=[_Plan("read", "https://docs.python.org/3/", "list comprehension basics")]
    )
    result = await execute_browse(_req(), engine=engine, env={})
    assert EXTERNAL_CONTENT_LABEL in result.final_summary


async def test_env_judge_domain_enables_execution() -> None:
    """env 注入的判题站 host 经交集后可被执行并产出引用。"""
    engine = FakeBrowseEngine(
        plans=[_Plan("read", "https://judge.pythontopia.cn/p/1", "题面：编写函数返回阶乘")]
    )
    result = await execute_browse(
        _req(allowed_domains=["judge.pythontopia.cn"]),
        engine=engine,
        env={"BROWSE_ALLOWED_DOMAINS": "judge.pythontopia.cn"},
    )
    assert result.error == ""
    assert len(result.steps) == 1
    assert "https://judge.pythontopia.cn/p/1" in result.citations


# ── ② 白名单外域 → 拒绝 fail-fast，不发请求 ──


async def test_out_of_allowlist_step_failfast_without_request() -> None:
    """提案越界 URL → 不调用 execute（不发请求），error 填充，已走步骤为空。"""
    engine = FakeBrowseEngine(
        plans=[_Plan("navigate", "https://evil.example.com/x", "should-not-be-read")]
    )
    result = await execute_browse(_req(), engine=engine, env={})

    assert result.error != ""
    assert "evil.example.com" in result.error
    assert engine.executed_urls == []  # 关键：越界 URL 从未被执行
    assert result.steps == []
    assert result.citations == []
    assert engine.closed is True


async def test_empty_intersection_rejects_without_opening() -> None:
    """请求域与安全墙无交集 → 整次拒绝，engine 从不 open（零请求）。"""
    engine = FakeBrowseEngine(plans=[_Plan("navigate", "https://evil.com/x", "x")])
    result = await execute_browse(_req(allowed_domains=["evil.com"]), engine=engine, env={})

    assert result.error != ""
    assert engine.opened is False
    assert engine.executed_urls == []
    assert result.steps == []


async def test_explicit_empty_allowlist_rejects() -> None:
    engine = FakeBrowseEngine(plans=[])
    result = await execute_browse(
        _req(allowed_domains=[], max_steps=3), engine=engine, env={}
    )
    assert result.error != ""
    assert engine.opened is False


# ── ③ 超 max_steps → truncated ──


async def test_truncates_when_engine_exceeds_max_steps() -> None:
    plans = [
        _Plan("read", f"https://docs.python.org/3/p{i}", f"content chunk {i}")
        for i in range(8)
    ]
    engine = FakeBrowseEngine(plans=plans)
    result = await execute_browse(_req(max_steps=3), engine=engine, env={})

    assert result.truncated is True
    assert len(result.steps) == 3
    assert len(engine.executed_urls) == 3
    assert result.error == ""
    assert "已达" in result.final_summary  # 截断提示


async def test_exact_max_steps_not_truncated() -> None:
    plans = [_Plan("read", f"https://docs.python.org/3/p{i}", f"c{i}") for i in range(3)]
    engine = FakeBrowseEngine(plans=plans)
    result = await execute_browse(_req(max_steps=3), engine=engine, env={})
    assert result.truncated is False
    assert len(result.steps) == 3


# ── ④ sanitize 去敏（脚本 / 隐藏域 / PII 不回传）──


def test_sanitize_strips_script_hidden_and_redacts_pii() -> None:
    raw = (
        "<p>sorted() is stable.</p>"
        "<script>window.steal='SCRIPT-SECRET'</script>"
        "<style>.x{color:red}</style>"
        '<input type="hidden" name="csrf" value="HIDDEN-CSRF-TOKEN">'
        '<div aria-hidden="true">do-not-show-secret</div>'
        "联系 alice@example.com 或 +1 (555) 123-4567 或 13812345678"
    )
    out = sanitize_dom_summary(raw)

    assert "sorted() is stable." in out
    # 脚本 / 样式体不回传
    assert "SCRIPT-SECRET" not in out
    assert "steal" not in out
    assert "color:red" not in out
    # 隐藏表单域 / 隐藏容器内容不回传
    assert "HIDDEN-CSRF-TOKEN" not in out
    assert "csrf" not in out
    assert "do-not-show-secret" not in out
    # PII 去敏
    assert "alice@example.com" not in out
    assert "555" not in out
    assert "13812345678" not in out
    # 不残留原始标签
    assert "<" not in out and ">" not in out


def test_sanitize_keeps_encoded_markup_inert() -> None:
    """HTML 实体反转义后不得重新引入可解析标签（无尖括号残留）。"""
    out = sanitize_dom_summary("safe text &lt;script&gt;alert(1)&lt;/script&gt; tail")
    assert "<" not in out and ">" not in out
    assert "safe text" in out


def test_sanitize_truncates_to_max_chars() -> None:
    out = sanitize_dom_summary("A" * 5000, max_chars=200)
    assert len(out) <= 200


def test_redact_pii_covers_common_patterns() -> None:
    redacted = redact_pii(
        "mail bob@school.edu.cn card 4111 1111 1111 1111 id 110101199003078888"
    )
    assert "bob@school.edu.cn" not in redacted
    assert "4111 1111 1111 1111" not in redacted
    assert "110101199003078888" not in redacted


def test_sanitize_empty_input_is_safe() -> None:
    assert sanitize_dom_summary("") == ""
    assert sanitize_dom_summary("   \n  ") == ""


# ── ⑤ executor 异常 → error 字段 + 已走步骤 ──


async def test_engine_execute_exception_returns_error_and_walked_steps() -> None:
    plans = [
        _Plan("read", "https://docs.python.org/a", "alpha content"),
        _Plan("read", "https://docs.python.org/b", "beta content"),  # 此步抛错
        _Plan("read", "https://docs.python.org/c", "gamma content"),
    ]
    engine = FakeBrowseEngine(plans=plans, raise_on_index=1)
    result = await execute_browse(_req(), engine=engine, env={})

    assert result.error != ""
    assert len(result.steps) == 1  # 仅第 0 步完成
    assert result.steps[0].summary  # alpha 已去敏入摘要
    assert "https://docs.python.org/a" in result.citations
    assert engine.closed is True  # 清理仍执行


async def test_engine_open_exception_is_structured_error() -> None:
    engine = FakeBrowseEngine(plans=[], raise_on_open=True)
    result = await execute_browse(_req(max_steps=3), engine=engine, env={})
    assert result.error != ""
    assert result.steps == []
    assert engine.closed is True


async def test_playwright_transfers_only_bounded_body_text(monkeypatch: pytest.MonkeyPatch) -> None:
    class Response:
        status = 200

        async def header_value(self, name: str) -> str:
            return "text/html; charset=utf-8"

    class Page:
        url = "https://docs.python.org/3/"

        async def goto(self, url: str, **kwargs: object) -> Response:
            return Response()

        async def evaluate(self, expression: str, argument: object = None) -> object:
            if "document.title" in expression:
                return "Python docs"
            assert argument == 64
            return {"text": "x" * 64, "truncated": True}

        async def close(self) -> None:
            pass

    class Context:
        async def new_page(self) -> Page:
            return Page()

    async def allow_url(*args: object, **kwargs: object) -> None:
        return None

    monkeypatch.setattr(browse_executor_module, "validate_network_url", allow_url)
    session = _PlaywrightReadOnlySession()
    session._context = Context()
    session._allowed_domains = [DOCS]

    result = await session.read("https://docs.python.org/3/", max_bytes=64)

    assert result.title == "Python docs"
    assert result.snippet == "x" * 64
    assert result.bytes_read == 65


async def test_default_engine_failsfast_when_browseruse_unavailable(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """默认 engine：browser-use 缺失（lazy-import）→ 结构化 fail-fast，零真实联网。"""
    import importlib

    real_import = importlib.import_module

    def fake_import(name: str, *args: object, **kwargs: object) -> object:
        if name.split(".")[0] in {"browser_use", "playwright"}:
            raise ImportError(f"no module {name}")
        return real_import(name, *args, **kwargs)  # type: ignore[arg-type]

    monkeypatch.setattr(importlib, "import_module", fake_import)
    result = await execute_browse(_req(max_steps=2), env={})
    assert result.error != ""
    assert result.steps == []
    assert "依赖" in result.error or "browser" in result.error.lower()


def test_create_default_engine_returns_lazy_engine() -> None:
    """构造默认 engine 不应在导入期触发 browser-use（lazy）。"""
    engine = create_default_engine(env={})
    assert hasattr(engine, "open")
    assert hasattr(engine, "propose")
    assert hasattr(engine, "execute")
    assert hasattr(engine, "close")


def test_browse_dependency_error_is_runtime_error() -> None:
    assert issubclass(BrowseDependencyError, RuntimeError)


# ── ⑥ 默认 Playwright engine：真实只读执行（注入 session，零联网）──


@dataclass
class FakeReadOnlySession:
    pages: dict[str, tuple[str, str]]
    opened: bool = False
    closed: bool = False
    read_urls: list[str] = field(default_factory=list)
    open_args: dict[str, object] = field(default_factory=dict)

    async def open(self, *, allowed_domains: list[str], timeout_seconds: float) -> None:
        self.opened = True
        self.open_args = {
            "allowed_domains": list(allowed_domains),
            "timeout_seconds": timeout_seconds,
        }

    async def read(self, url: str, *, max_bytes: int) -> Any:
        self.read_urls.append(url)
        title, snippet = self.pages[url]
        captured_type = getattr(browse_executor_module, "CapturedPage")
        return captured_type(
            url=url,
            title=title,
            snippet=snippet,
            bytes_read=len(snippet.encode("utf-8")),
        )

    async def close(self) -> None:
        self.closed = True


async def test_default_engine_reads_explicit_url_and_returns_title_snippet() -> None:
    url = "https://docs.python.org/3/howto/sorting.html"
    session = FakeReadOnlySession({url: ("Sorting HOW TO", "Sorts are guaranteed stable.")})
    engine_type = getattr(browse_executor_module, "_BrowserUseEngine")
    engine = engine_type(env={}, session_factory=lambda: session)

    result = await execute_browse(
        _req(goal=f"请查证 {url}", max_steps=2, max_bytes=4096, timeout_seconds=5),
        engine=engine,
        env={},
    )

    assert result.error == ""
    assert session.opened is True
    assert session.closed is True
    assert session.read_urls == [url]
    assert result.steps[0].url == url
    assert result.steps[0].title == "Sorting HOW TO"
    assert result.steps[0].snippet == "Sorts are guaranteed stable."
    # 兼容旧端：summary 继续携带同一份去敏摘要。
    assert result.steps[0].summary == result.steps[0].snippet


async def test_default_engine_builds_bounded_official_search_without_react() -> None:
    goal = "sorted 稳定性"
    search_url = (
        "https://docs.python.org/3/search.html?"
        "q=sorted+%E7%A8%B3%E5%AE%9A%E6%80%A7&check_keywords=yes&area=default"
    )
    session = FakeReadOnlySession({search_url: ("Search", "Search results for sorted 稳定性")})
    engine_type = getattr(browse_executor_module, "_BrowserUseEngine")
    engine = engine_type(env={}, session_factory=lambda: session)

    result = await execute_browse(
        _req(goal=goal, allowed_domains=[DOCS], max_steps=1), engine=engine, env={}
    )

    assert result.error == ""
    assert session.read_urls == [search_url]
    assert len(result.steps) == 1
    assert result.steps[0].action == "search"


async def test_default_engine_enforces_total_byte_budget_and_keeps_partial_evidence() -> None:
    url = "https://docs.python.org/3/"
    session = FakeReadOnlySession({url: ("Python", "A" * 128)})
    engine_type = getattr(browse_executor_module, "_BrowserUseEngine")
    engine = engine_type(env={}, session_factory=lambda: session)

    result = await execute_browse(
        _req(goal=url, max_bytes=32, max_steps=1), engine=engine, env={}
    )

    assert len(result.steps) == 1
    assert len(result.steps[0].snippet.encode("utf-8")) <= 32
    assert result.error != ""
    assert "字节" in result.error


@pytest.mark.parametrize(
    "url",
    [
        "http://127.0.0.1/admin",
        "http://169.254.169.254/latest/meta-data/",
        "http://metadata.google.internal/computeMetadata/v1/",
        "file:///etc/passwd",
        "https://docs.python.org:8443/",
    ],
)
async def test_network_guard_rejects_internal_metadata_and_nonstandard_ports(url: str) -> None:
    guard = getattr(browse_executor_module, "validate_network_url")
    with pytest.raises(ValueError):
        await guard(url, [DOCS], resolver=lambda _host: ["93.184.216.34"])


async def test_network_guard_rejects_allowlisted_hostname_resolving_private_ip() -> None:
    guard = getattr(browse_executor_module, "validate_network_url")
    with pytest.raises(ValueError, match="公网"):
        await guard(
            "https://docs.python.org/3/",
            [DOCS],
            resolver=lambda _host: ["10.0.0.8"],
        )


async def test_network_guard_rejects_credentials_and_mixed_public_private_dns() -> None:
    guard = getattr(browse_executor_module, "validate_network_url")
    with pytest.raises(ValueError, match="凭据"):
        await guard(
            "https://user:secret@docs.python.org/3/",
            [DOCS],
            resolver=lambda _host: ["151.101.0.223"],
        )
    with pytest.raises(ValueError, match="公网"):
        await guard(
            "https://docs.python.org/3/",
            [DOCS],
            resolver=lambda _host: ["151.101.0.223", "192.168.1.9"],
        )


async def test_network_guard_accepts_public_allowlisted_https() -> None:
    guard = getattr(browse_executor_module, "validate_network_url")
    await guard(
        "https://docs.python.org/3/",
        [DOCS],
        resolver=lambda _host: ["151.101.0.223"],
    )


def test_runtime_probe_reports_missing_browser_binary(monkeypatch: pytest.MonkeyPatch) -> None:
    probe = getattr(browse_executor_module, "browse_runtime_status")
    monkeypatch.setattr(
        browse_executor_module,
        "_playwright_executable_path",
        lambda _module, _env: "/definitely/missing/chromium",
    )
    ready, reason = probe(env={}, playwright_module=object())
    assert ready is False
    assert "Chromium" in reason


def test_runtime_probe_reports_missing_playwright_dependency() -> None:
    probe = getattr(browse_executor_module, "browse_runtime_status")
    ready, reason = probe(env={}, playwright_module=None)
    assert ready is False
    assert "Playwright" in reason


# ── ⑦ registry 目录含 BROWSE 且 quiz scope 不暴露 ──


def test_registry_includes_browse_with_full_governance_metadata() -> None:
    assert "BROWSE" in TOOLS
    spec = TOOLS["BROWSE"]
    assert spec.exec_mode == "orchestrator_sse"
    assert spec.risk_class == "navigational"
    assert spec.cost_class == "high"
    assert spec.timeout_s == 60.0
    assert spec.max_retries == 0
    assert spec.fallback == "VIDEO"
    assert spec.scopes == ("default",)
    assert spec.open_world is True
    assert spec.destructive is False
    assert spec.idempotent is False
    assert spec.renderer == "browse"
    assert spec.visibility == "default_hidden"


def test_browse_in_default_catalog_but_not_quiz_scope() -> None:
    default_lines = intent_catalog_lines("default")
    assert any(line.startswith("BROWSE=") for line in default_lines)

    quiz_names = {spec.name for spec in tools_for_scope("quiz")}
    assert "BROWSE" not in quiz_names
    quiz_lines = intent_catalog_lines("quiz")
    assert not any(line.startswith("BROWSE=") for line in quiz_lines)


def test_browse_present_in_tool_catalog_payload() -> None:
    payload = {entry["name"]: entry for entry in tool_catalog_payload()}
    assert "BROWSE" in payload
    browse = payload["BROWSE"]
    assert browse["renderer"] == "browse"
    assert browse["execMode"] == "orchestrator_sse"
    assert browse["openWorld"] is True
    assert browse["idempotent"] is False
    assert browse["riskClass"] == "navigational"
