"""有界、只读的 Playwright 联网查证器。

它不是自治浏览 Agent：只读取 goal 中明确给出的 URL；没有 URL 时，只构造 Python 官方站点
自身的搜索 URL。所有网络请求在 Playwright ``BrowserContext.route`` 层再次经过 scheme、端口、
allowlist 与 DNS 公网地址校验；Chromium 连接使用启动前固定的公网解析结果，脚本、Service Worker、下载
和非 document 子资源全部关闭。
"""

from __future__ import annotations

import asyncio
import contextlib
import importlib
import inspect
import ipaddress
import os
import re
import socket
from collections.abc import Awaitable, Callable, Iterable, Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol, cast
from urllib.parse import quote_plus, urlsplit

from fastapi import HTTPException

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
    "CapturedPage",
    "ProposedStep",
    "browse_runtime_status",
    "create_default_engine",
    "execute_browse",
    "validate_network_url",
]

EXTERNAL_CONTENT_LABEL = "【浏览器读取·外部内容，非课件证据】"

_KNOWN_ACTIONS: tuple[str, ...] = ("navigate", "read", "search", "stop")
_CITE_ACTIONS: frozenset[str] = frozenset({"navigate", "read", "search"})
_MAX_FINAL_SUMMARY_CHARS = 1200
_MAX_ERROR_CHARS = 240
_MAX_TITLE_CHARS = 200
_WS_RE = re.compile(r"\s+")
_URL_RE = re.compile(r"https?://[^\s<>\"']+", re.IGNORECASE)
_TRAILING_URL_PUNCTUATION = ".,;:!?)]}，。；：！？）》」』"
_METADATA_HOSTS = frozenset(
    {
        "metadata.google.internal",
        "metadata.google.internal.",
        "instance-data.ec2.internal",
        "instance-data.ec2.internal.",
    }
)
_MAX_CONCURRENCY = max(1, min(4, int(os.environ.get("BROWSE_MAX_CONCURRENCY", "2"))))
_BROWSE_GATE = asyncio.Semaphore(_MAX_CONCURRENCY)
_PLAYWRIGHT_SENTINEL = object()

Resolver = Callable[[str], Sequence[str] | Awaitable[Sequence[str]]]


class BrowseDependencyError(RuntimeError):
    """Playwright 或其 Chromium 运行时缺失。"""


@dataclass(frozen=True)
class ProposedStep:
    """即将执行的只读步骤；executor 会在 I/O 前再次验证 URL。"""

    action: str
    url: str = ""


@dataclass(frozen=True)
class CapturedPage:
    """Playwright 返回的最小证据单元，不保留原始 DOM、Cookie 或浏览器状态。"""

    url: str
    title: str
    snippet: str
    bytes_read: int


class BrowseEngine(Protocol):
    async def open(
        self,
        *,
        goal: str,
        allowed_domains: list[str],
        max_steps: int,
        max_bytes: int = 131_072,
        timeout_seconds: float = 45.0,
    ) -> None: ...

    async def propose(self) -> ProposedStep | None: ...

    async def execute(self, step: ProposedStep) -> str | CapturedPage: ...

    async def close(self) -> None: ...


class ReadOnlySession(Protocol):
    async def open(self, *, allowed_domains: list[str], timeout_seconds: float) -> None: ...

    async def read(self, url: str, *, max_bytes: int) -> CapturedPage: ...

    async def close(self) -> None: ...


def _coerce_action(action: str) -> BrowseAction:
    if action not in _KNOWN_ACTIONS:
        raise ValueError(f"非只读浏览动作已拒绝: {action}")
    return cast("BrowseAction", action)


def _short_error(text: str) -> str:
    cleaned = _WS_RE.sub(" ", redact_pii(text)).strip()
    if len(cleaned) > _MAX_ERROR_CHARS:
        cleaned = cleaned[: _MAX_ERROR_CHARS - 1].rstrip() + "…"
    return cleaned


def _truncate_summary(text: str, max_chars: int) -> str:
    if len(text) <= max_chars:
        return text
    return text[: max_chars - 1].rstrip() + "…"


def _truncate_utf8(text: str, max_bytes: int) -> str:
    """按 UTF-8 字节安全截断，永不切断多字节字符。"""
    if max_bytes <= 0:
        return ""
    encoded = text.encode("utf-8")
    if len(encoded) <= max_bytes:
        return text
    return encoded[:max_bytes].decode("utf-8", errors="ignore").rstrip()


def _compose_final_summary(steps: list[BrowseStep], *, truncated: bool, error: str) -> str:
    body = " ".join(step.snippet or step.summary for step in steps if step.snippet or step.summary)
    body = body.strip()
    notes: list[str] = []
    if truncated:
        notes.append("（已达浏览步数上限，结果可能不完整）")
    if error:
        notes.append(f"（浏览中断：{error}）")
    if not body:
        if error:
            return f"未能完成联网查证：{error}"
        return f"{EXTERNAL_CONTENT_LABEL}未获取到可引用的外部内容。"
    return (
        f"{EXTERNAL_CONTENT_LABEL}"
        f"{_truncate_summary(body, _MAX_FINAL_SUMMARY_CHARS)}{''.join(notes)}"
    )


async def execute_browse(
    request: BrowseRequest,
    *,
    engine: BrowseEngine | None = None,
    env: Mapping[str, str] | None = None,
) -> BrowseResult:
    """在总超时、并发、步数和文本字节预算内完成一次只读查证。"""
    effective = enforce_allowlist(request.allowed_domains, env)
    if not effective:
        message = "无有效白名单域（请求与安全墙无交集），已拒绝发起浏览"
        return BrowseResult(
            final_summary=f"未能完成联网查证：{message}",
            error=message,
        )

    if _BROWSE_GATE.locked():
        raise HTTPException(status_code=429, detail="联网查证繁忙，请稍后重试")

    browse_engine = engine if engine is not None else create_default_engine(env=env)
    steps: list[BrowseStep] = []
    citations: list[str] = []
    truncated = False
    error = ""
    bytes_used = 0

    try:
        async with asyncio.timeout(request.timeout_seconds):
            async with _BROWSE_GATE:
                await browse_engine.open(
                    goal=request.goal,
                    allowed_domains=list(effective),
                    max_steps=request.max_steps,
                    max_bytes=request.max_bytes,
                    timeout_seconds=request.timeout_seconds,
                )
                index = 0
                proposed = await browse_engine.propose()
                while proposed is not None and proposed.action != "stop":
                    if index >= request.max_steps:
                        truncated = True
                        break
                    url = (proposed.url or "").strip()
                    if url and not is_domain_allowed(url, effective):
                        error = _short_error(f"越界域被拦截，未发起请求: {url}")
                        break

                    captured = await browse_engine.execute(proposed)
                    if isinstance(captured, CapturedPage):
                        title_raw = captured.title
                        snippet_raw = captured.snippet
                        source_bytes = max(
                            captured.bytes_read,
                            len(title_raw.encode("utf-8")) + len(snippet_raw.encode("utf-8")),
                        )
                        final_url = captured.url.strip() or url
                    else:
                        title_raw = ""
                        snippet_raw = captured
                        source_bytes = len(captured.encode("utf-8"))
                        final_url = url

                    remaining = max(0, request.max_bytes - bytes_used)
                    remaining_before_step = remaining
                    title = sanitize_dom_summary(title_raw, max_chars=_MAX_TITLE_CHARS)
                    title = _truncate_utf8(title, remaining)
                    remaining -= len(title.encode("utf-8"))
                    snippet = sanitize_dom_summary(snippet_raw)
                    snippet = _truncate_utf8(snippet, remaining)
                    consumed = len(title.encode("utf-8")) + len(snippet.encode("utf-8"))
                    bytes_used += consumed

                    action = _coerce_action(proposed.action)
                    steps.append(
                        BrowseStep(
                            index=index,
                            action=action,
                            url=final_url,
                            summary=snippet,
                            title=title,
                            snippet=snippet,
                        )
                    )
                    if final_url and action in _CITE_ACTIONS and final_url not in citations:
                        citations.append(final_url)
                    index += 1

                    if source_bytes > remaining_before_step or bytes_used >= request.max_bytes:
                        error = "已达浏览文本字节上限，保留已读取的部分证据"
                        break
                    proposed = await browse_engine.propose()
    except TimeoutError:
        error = f"浏览超过 {request.timeout_seconds:g} 秒总时限"
    except Exception as exc:  # noqa: BLE001 - 对上游返回结构化失败和已完成证据
        error = "BROWSE 依赖缺失" if isinstance(exc, BrowseDependencyError) else type(exc).__name__
    finally:
        with contextlib.suppress(Exception):
            await asyncio.wait_for(browse_engine.close(), timeout=3.0)

    return BrowseResult(
        steps=steps,
        final_summary=_compose_final_summary(steps, truncated=truncated, error=error),
        citations=citations,
        truncated=truncated,
        error=error,
    )


def create_default_engine(*, env: Mapping[str, str] | None = None) -> BrowseEngine:
    return _BrowserUseEngine(env=env)


def _import_or_fail(module_name: str) -> Any:
    try:
        return importlib.import_module(module_name)
    except Exception as exc:  # noqa: BLE001
        raise BrowseDependencyError(
            f"BROWSE 依赖缺失：未安装 {module_name}（pip install '.[browse]'）"
        ) from exc


def _extract_urls(goal: str) -> list[str]:
    urls: list[str] = []
    for match in _URL_RE.findall(goal):
        url = match.rstrip(_TRAILING_URL_PUNCTUATION)
        if url and url not in urls:
            urls.append(url)
    return urls


def _official_search_url(goal: str, allowed_domains: Iterable[str]) -> str:
    domains = tuple(allowed_domains)
    query = quote_plus(goal.strip())
    if "docs.python.org" in domains:
        return (
            f"https://docs.python.org/3/search.html?q={query}"
            "&check_keywords=yes&area=default"
        )
    if "pypi.org" in domains:
        return f"https://pypi.org/search/?q={query}"
    raise ValueError("查证目标未包含明确 URL，且白名单中没有受支持的官方站内搜索")


class _BrowserUseEngine:
    """保留历史类名的 Playwright 适配器；不使用 browser-use，也不运行 ReAct/LLM。"""

    def __init__(
        self,
        *,
        env: Mapping[str, str] | None = None,
        session_factory: Callable[[], ReadOnlySession] | None = None,
    ) -> None:
        self._env = env
        self._session_factory = session_factory
        self._session: ReadOnlySession | None = None
        self._plans: list[ProposedStep] = []
        self._cursor = 0
        self._remaining_bytes = 0

    async def open(
        self,
        *,
        goal: str,
        allowed_domains: list[str],
        max_steps: int,
        max_bytes: int = 131_072,
        timeout_seconds: float = 45.0,
    ) -> None:
        session = (
            self._session_factory()
            if self._session_factory is not None
            else _PlaywrightReadOnlySession(env=self._env)
        )
        self._session = session
        self._remaining_bytes = max_bytes
        explicit_urls = _extract_urls(goal)
        if explicit_urls:
            self._plans = [
                ProposedStep(action="navigate" if index == 0 else "read", url=url)
                for index, url in enumerate(explicit_urls[:max_steps])
            ]
        else:
            self._plans = [
                ProposedStep(action="search", url=_official_search_url(goal, allowed_domains))
            ]
        await session.open(
            allowed_domains=allowed_domains,
            timeout_seconds=timeout_seconds,
        )

    async def propose(self) -> ProposedStep | None:
        if self._cursor >= len(self._plans):
            return None
        return self._plans[self._cursor]

    async def execute(self, step: ProposedStep) -> CapturedPage:
        if self._session is None:
            raise RuntimeError("Playwright 会话未初始化")
        if self._remaining_bytes <= 0:
            raise ValueError("已达浏览文本字节上限")
        captured = await self._session.read(step.url, max_bytes=self._remaining_bytes)
        self._remaining_bytes -= min(self._remaining_bytes, captured.bytes_read)
        self._cursor += 1
        return captured

    async def close(self) -> None:
        session = self._session
        self._session = None
        if session is not None:
            await session.close()


async def _resolve_public_ips(host: str) -> list[str]:
    def resolve() -> list[str]:
        infos = socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
        return list(dict.fromkeys(info[4][0] for info in infos))

    return await asyncio.to_thread(resolve)


async def _validated_public_addresses(
    host: str, resolver: Resolver
) -> list[ipaddress.IPv4Address | ipaddress.IPv6Address]:
    try:
        addresses = [ipaddress.ip_address(host)]
    except ValueError:
        resolved = resolver(host)
        raw_addresses = await resolved if inspect.isawaitable(resolved) else resolved
        if not raw_addresses:
            raise ValueError("目标主机 DNS 无可用地址")
        try:
            addresses = [ipaddress.ip_address(address) for address in raw_addresses]
        except ValueError as exc:
            raise ValueError("目标主机 DNS 返回了无效地址") from exc
    if any(not address.is_global for address in addresses):
        raise ValueError("目标主机解析到非公网地址")
    return addresses


async def _chromium_host_resolver_rules(allowed_domains: Iterable[str]) -> str:
    rules: list[str] = []
    domains = sorted(set(allowed_domains), key=len, reverse=True)
    for raw_domain in domains:
        host = raw_domain.rstrip(".").lower()
        try:
            ipaddress.ip_address(host)
            continue
        except ValueError:
            pass
        labels = host.split(".")
        if (
            not host
            or len(host) > 253
            or any(
                not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", label)
                for label in labels
            )
        ):
            raise ValueError("白名单包含无效主机名")
        addresses = await _validated_public_addresses(host, _resolve_public_ips)
        # IPv4 is more widely reachable in the current container deployment.
        pinned = next((address for address in addresses if address.version == 4), addresses[0])
        replacement = f"[{pinned}]" if pinned.version == 6 else str(pinned)
        # shortcut: subdomains share the base IP; pin concrete hosts separately if access needs differ.
        rules.extend((f"MAP {host} {replacement}", f"MAP *.{host} {replacement}"))
    return ",".join(rules)


async def validate_network_url(
    url: str,
    allowed_domains: Iterable[str],
    *,
    resolver: Resolver = _resolve_public_ips,
) -> None:
    """阻止 scheme/端口/allowlist/私网 DNS 绕过；每次重定向请求都应调用。"""
    parsed = urlsplit((url or "").strip())
    if parsed.scheme.lower() not in {"http", "https"} or not parsed.hostname:
        raise ValueError("仅允许带主机名的 http/https URL")
    if parsed.username is not None or parsed.password is not None:
        raise ValueError("URL 不允许内嵌凭据")
    try:
        port = parsed.port
    except ValueError as exc:
        raise ValueError("URL 端口无效") from exc
    expected_port = 443 if parsed.scheme.lower() == "https" else 80
    if port is not None and port != expected_port:
        raise ValueError("仅允许 http/https 标准端口")
    if not is_domain_allowed(url, allowed_domains):
        raise ValueError("目标域不在只读浏览白名单")

    host = parsed.hostname.rstrip(".").lower()
    if host in _METADATA_HOSTS or host == "localhost" or host.endswith(".localhost"):
        raise ValueError("目标主机不是公网地址")
    await _validated_public_addresses(host, resolver)


class _PlaywrightReadOnlySession:
    def __init__(self, *, env: Mapping[str, str] | None = None) -> None:
        self._env = os.environ if env is None else env
        self._allowed_domains: list[str] = []
        self._timeout_ms = 45_000
        self._playwright: Any = None
        self._browser: Any = None
        self._context: Any = None
        self._blocked_reason = ""

    async def open(self, *, allowed_domains: list[str], timeout_seconds: float) -> None:
        async_api = _import_or_fail("playwright.async_api")
        self._allowed_domains = list(allowed_domains)
        self._timeout_ms = int(timeout_seconds * 1000)
        self._playwright = await async_api.async_playwright().start()
        executable = self._env.get("BROWSE_CHROMIUM_EXECUTABLE_PATH", "").strip() or None
        resolver_rules = await _chromium_host_resolver_rules(self._allowed_domains)
        args = ["--disable-dev-shm-usage"]
        if resolver_rules:
            args.append(f"--host-resolver-rules={resolver_rules}")
        self._browser = await self._playwright.chromium.launch(
            headless=True,
            executable_path=executable,
            args=args,
        )
        self._context = await self._browser.new_context(
            accept_downloads=False,
            java_script_enabled=False,
            service_workers="block",
            ignore_https_errors=False,
            viewport={"width": 1024, "height": 768},
        )
        self._context.set_default_timeout(self._timeout_ms)
        self._context.set_default_navigation_timeout(self._timeout_ms)
        await self._context.route("**/*", self._guard_route)

    async def _guard_route(self, route: Any, request: Any) -> None:
        if request.method.upper() != "GET" or request.resource_type != "document":
            await route.abort("blockedbyclient")
            return
        try:
            await validate_network_url(request.url, self._allowed_domains)
        except ValueError as exc:
            self._blocked_reason = str(exc)
            await route.abort("blockedbyclient")
            return
        await route.continue_()

    async def read(self, url: str, *, max_bytes: int) -> CapturedPage:
        if self._context is None:
            raise RuntimeError("Playwright context 未初始化")
        await validate_network_url(url, self._allowed_domains)
        page = await self._context.new_page()
        self._blocked_reason = ""
        try:
            try:
                response = await page.goto(
                    url,
                    wait_until="domcontentloaded",
                    timeout=self._timeout_ms,
                )
            except Exception as exc:
                if self._blocked_reason:
                    raise ValueError(f"重定向或请求被安全墙拦截：{self._blocked_reason}") from exc
                raise
            if response is None:
                raise RuntimeError("目标站未返回 document 响应")
            final_url = page.url
            await validate_network_url(final_url, self._allowed_domains)
            if response.status >= 400:
                raise RuntimeError(f"目标站返回 HTTP {response.status}")
            content_type = (await response.header_value("content-type") or "").lower()
            if content_type and not any(
                token in content_type
                for token in ("text/html", "application/xhtml+xml", "text/plain")
            ):
                raise ValueError(f"目标内容类型不允许：{content_type[:80]}")
            title = await page.evaluate("() => (document.title || '').slice(0, 200)")
            body = await page.evaluate(
                "maxChars => {"
                " const text = document.body?.innerText || '';"
                " return {text: text.slice(0, maxChars), truncated: text.length > maxChars};"
                "}",
                max_bytes,
            )
            text = body.get("text", "") if isinstance(body, dict) else ""
            truncated = isinstance(body, dict) and bool(body.get("truncated"))
            body_bytes = max_bytes + 1 if truncated else len(text.encode("utf-8"))
            return CapturedPage(
                url=final_url,
                title=title,
                snippet=_truncate_utf8(text, max_bytes),
                bytes_read=body_bytes,
            )
        finally:
            await page.close()

    async def close(self) -> None:
        if self._context is not None:
            with contextlib.suppress(Exception):
                await self._context.close()
            self._context = None
        if self._browser is not None:
            with contextlib.suppress(Exception):
                await self._browser.close()
            self._browser = None
        if self._playwright is not None:
            with contextlib.suppress(Exception):
                await self._playwright.stop()
            self._playwright = None


def _playwright_executable_path(module: Any, env: Mapping[str, str]) -> str:
    configured = env.get("BROWSE_CHROMIUM_EXECUTABLE_PATH", "").strip()
    if configured:
        return configured
    with module.sync_playwright() as playwright:
        return str(playwright.chromium.executable_path)


def browse_runtime_status(
    *,
    env: Mapping[str, str] | None = None,
    playwright_module: Any = _PLAYWRIGHT_SENTINEL,
) -> tuple[bool, str]:
    """无联网探测 Playwright 包和配套 Chromium 二进制，用于生产 readyz。"""
    environ = os.environ if env is None else env
    if playwright_module is None:
        return False, "Playwright Python 依赖未安装"
    module = playwright_module
    if module is _PLAYWRIGHT_SENTINEL:
        try:
            module = importlib.import_module("playwright.sync_api")
        except Exception:
            return False, "Playwright Python 依赖未安装"
    try:
        executable = Path(_playwright_executable_path(module, environ))
    except Exception as exc:  # noqa: BLE001
        return False, f"Playwright Chromium 探测失败：{_short_error(str(exc))}"
    if not executable.is_file() or not os.access(executable, os.X_OK):
        return False, f"Playwright Chromium 二进制不存在：{executable}"
    return True, "ready"
