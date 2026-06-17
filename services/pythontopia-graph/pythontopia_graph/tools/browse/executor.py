"""BROWSE 窄工具化 executor（§3.1 / §4 B1）：把 browser-use 封装成**有界、只读、去敏**的查证工具。

设计要点（红线：非自由 ReAct / 业务 fail-fast / 外部内容标签隔离）：
- **有界**：步数受 ``BrowseRequest.max_steps`` 上界约束，越界 ``truncated=True`` 提前停。
- **白名单 fail-fast**：先与安全墙求交集（``allowlist.enforce_allowlist``，防御纵深）；交集为空
  直接拒绝、engine 永不 open（零请求）；逐步提案的越界 URL 在 **execute 之前**拦截，绝不发请求。
- **只读动作**：navigate / read / search / stop；无任何写/提交/点击副作用动作进入回路。
- **去敏**：每步 DOM 摘要过 ``sanitize.sanitize_dom_summary``（脚本/隐藏域/PII 不回传）。
- **结构化降级**：engine 任意异常被捕获 → 返回**已走步骤 + error**（绝不抛断主链路）。
- **外部内容标签隔离**：``final_summary`` 冠以 :data:`EXTERNAL_CONTENT_LABEL`，``citations`` 为外部
  URL，**不进课件证据/引用**（由 S/B2/F 以"外部内容，非课件"通道注入与渲染）。
- **lazy-import**：browser-use/playwright 仅在默认 engine 的 ``open()`` 内惰性导入；缺包结构化
  fail-fast（dispatch 回退 ``VIDEO``）。测试经注入 fake engine 零真实联网。

可注入 :class:`BrowseEngine`（propose→执行前白名单校验→execute 的两段式，便于"未发请求"校验）。
"""

from __future__ import annotations

import contextlib
import importlib
import inspect
import re
from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any, Protocol, cast

from pythontopia_graph.tools.browse.allowlist import enforce_allowlist, is_domain_allowed
from pythontopia_graph.tools.browse.contract import (
    BrowseAction,
    BrowseRequest,
    BrowseResult,
    BrowseStep,
)
from pythontopia_graph.tools.browse.sanitize import redact_pii, sanitize_dom_summary

__all__ = [
    "EXTERNAL_CONTENT_LABEL",
    "BrowseDependencyError",
    "BrowseEngine",
    "ProposedStep",
    "create_default_engine",
    "execute_browse",
]

# 外部内容标签（红线：浏览/上传外部内容只进带标签 context 通道，绝不冒充课件证据）。
EXTERNAL_CONTENT_LABEL = "【浏览器读取·外部内容，非课件证据】"

# 只读动作集合（与 contract.BrowseAction 对齐）。
_KNOWN_ACTIONS: tuple[str, ...] = ("navigate", "read", "search", "stop")
# 产生外部引用的动作（命中 URL 入 citations）。
_CITE_ACTIONS: frozenset[str] = frozenset({"navigate", "read", "search"})

_MAX_FINAL_SUMMARY_CHARS = 1200
_MAX_ERROR_CHARS = 240
_WS_RE = re.compile(r"\s+")


class BrowseDependencyError(RuntimeError):
    """BROWSE 运行依赖缺失/未接线（browser-use / playwright / LLM）→ 业务 fail-fast。"""


@dataclass
class ProposedStep:
    """engine 拟执行的下一步（执行前用于白名单校验；不含已抓取内容）。"""

    action: str
    url: str = ""


class BrowseEngine(Protocol):
    """浏览器引擎抽象：两段式 propose→execute，便于在 execute 前做白名单 fail-fast。

    生产实现 :class:`_BrowserUseEngine`（lazy browser-use）；测试注入 fake（零联网）。
    """

    async def open(self, *, goal: str, allowed_domains: list[str], max_steps: int) -> None:
        """初始化会话（只读、受界、域约束）。失败应抛异常，由 executor 结构化降级。"""
        ...

    async def propose(self) -> ProposedStep | None:
        """返回下一步提案；``None`` 表示自然结束（无更多步）。"""
        ...

    async def execute(self, step: ProposedStep) -> str:
        """执行该（已通过白名单校验的）步，返回原始 DOM/文本摘要（未去敏）。"""
        ...

    async def close(self) -> None:
        """释放会话资源（幂等；executor 在 finally 中防御性调用）。"""
        ...


def _coerce_action(action: str) -> BrowseAction:
    """把 engine 提案动作收敛到合法只读动作（未知动作按 read 处理）。"""
    if action in _KNOWN_ACTIONS:
        return cast("BrowseAction", action)
    return "read"


def _short_error(text: str) -> str:
    """错误信息去敏 + 折行 + 截断（避免把抓取/异常细节里的敏感内容回带）。"""
    cleaned = _WS_RE.sub(" ", redact_pii(text)).strip()
    if len(cleaned) > _MAX_ERROR_CHARS:
        cleaned = cleaned[: _MAX_ERROR_CHARS - 1].rstrip() + "…"
    return cleaned


def _truncate_summary(text: str, max_chars: int) -> str:
    if len(text) <= max_chars:
        return text
    return text[: max_chars - 1].rstrip() + "…"


def _compose_final_summary(steps: list[BrowseStep], *, truncated: bool, error: str) -> str:
    body = " ".join(step.summary for step in steps if step.summary).strip()
    notes: list[str] = []
    if truncated:
        notes.append("（已达浏览步数上限，结果可能不完整）")
    if error:
        notes.append(f"（浏览中断：{error}）")
    if not body:
        if error:
            return f"未能完成联网查证：{error}"
        return f"{EXTERNAL_CONTENT_LABEL}未获取到可引用的外部内容。"
    body = _truncate_summary(body, _MAX_FINAL_SUMMARY_CHARS)
    return f"{EXTERNAL_CONTENT_LABEL}{body}{''.join(notes)}"


async def execute_browse(
    request: BrowseRequest,
    *,
    engine: BrowseEngine | None = None,
    env: Mapping[str, str] | None = None,
) -> BrowseResult:
    """有界执行一次白名单内浏览查证，永远返回结构化 :class:`BrowseResult`（绝不抛断主链路）。"""
    effective = enforce_allowlist(request.allowed_domains, env)
    if not effective:
        msg = "无有效白名单域（请求与安全墙无交集），已拒绝发起浏览"
        return BrowseResult(
            steps=[],
            final_summary=f"未能完成联网查证：{msg}",
            citations=[],
            truncated=False,
            error=msg,
        )

    eng = engine if engine is not None else create_default_engine(env=env)
    steps: list[BrowseStep] = []
    citations: list[str] = []
    truncated = False
    error = ""

    try:
        await eng.open(
            goal=request.goal,
            allowed_domains=list(effective),
            max_steps=request.max_steps,
        )
        idx = 0
        proposed = await eng.propose()
        while proposed is not None and proposed.action != "stop":
            if idx >= request.max_steps:
                truncated = True
                break
            url = (proposed.url or "").strip()
            # execute 之前做白名单校验：越界 URL fail-fast，绝不发起请求。
            if url and not is_domain_allowed(url, effective):
                error = _short_error(f"越界域被拦截，未发起请求: {url}")
                break
            raw = await eng.execute(proposed)
            action = _coerce_action(proposed.action)
            steps.append(
                BrowseStep(index=idx, action=action, url=url, summary=sanitize_dom_summary(raw))
            )
            if url and action in _CITE_ACTIONS and url not in citations:
                citations.append(url)
            idx += 1
            proposed = await eng.propose()
    except Exception as exc:  # noqa: BLE001 - 业务故障也必须结构化降级（返回已走步骤 + error）
        error = _short_error(f"{type(exc).__name__}: {exc}")
    finally:
        with contextlib.suppress(Exception):
            await eng.close()

    return BrowseResult(
        steps=steps,
        final_summary=_compose_final_summary(steps, truncated=truncated, error=error),
        citations=citations,
        truncated=truncated,
        error=error,
    )


def create_default_engine(*, env: Mapping[str, str] | None = None) -> BrowseEngine:
    """构造默认（lazy browser-use）engine；不在此触发任何导入/联网。"""
    return _BrowserUseEngine(env=env)


# ── 默认 engine：lazy 封装 browser-use（真实联网路径，单测不覆盖）──


def _import_or_fail(module_name: str) -> Any:
    """惰性导入可选浏览依赖；缺失 → 结构化 :class:`BrowseDependencyError`（含"依赖"）。"""
    try:
        return importlib.import_module(module_name)
    except Exception as exc:  # noqa: BLE001 - ImportError 等一律转结构化业务错误
        raise BrowseDependencyError(
            f"BROWSE 依赖缺失：未安装 {module_name}（pip install '.[browse]'），"
            "已 fail-fast；dispatch 将回退 VIDEO"
        ) from exc


class _BrowserUseEngine:
    """browser-use(Playwright) 适配器：open() 内 lazy-import + 受界域约束运行，replay 为受治理步骤。

    真实执行需注入 LLM（Phase 2 由编排/后端接线），单测一律走注入 fake engine，零真实联网。
    """

    def __init__(self, *, env: Mapping[str, str] | None = None) -> None:
        self._env = env
        self._steps: list[tuple[str, str, str]] = []  # (action, url, content)
        self._cursor = 0
        self._session: Any = None

    async def open(self, *, goal: str, allowed_domains: list[str], max_steps: int) -> None:
        browser_use = _import_or_fail("browser_use")
        _import_or_fail("playwright")
        self._steps = await self._run(
            browser_use, goal=goal, allowed_domains=allowed_domains, max_steps=max_steps
        )

    async def propose(self) -> ProposedStep | None:
        if self._cursor >= len(self._steps):
            return None
        action, url, _ = self._steps[self._cursor]
        return ProposedStep(action=action, url=url)

    async def execute(self, step: ProposedStep) -> str:
        _, _, content = self._steps[self._cursor]
        self._cursor += 1
        return content

    async def close(self) -> None:
        session = self._session
        self._session = None
        if session is None:
            return
        closer = getattr(session, "close", None) or getattr(session, "kill", None)
        if closer is None:
            return
        result = closer()
        if inspect.isawaitable(result):
            await result

    async def _run(  # pragma: no cover - 真实 browser-use + LLM 联网路径，Phase 2 接线
        self,
        browser_use: Any,
        *,
        goal: str,
        allowed_domains: list[str],
        max_steps: int,
    ) -> list[tuple[str, str, str]]:
        raise BrowseDependencyError(
            "BROWSE 真实执行未接线：browser-use Agent 需注入 LLM 与 Playwright 会话"
            "（Phase 2 由编排/后端注入受界、域约束的 engine）；当前 fail-fast，dispatch 回退 VIDEO"
        )
