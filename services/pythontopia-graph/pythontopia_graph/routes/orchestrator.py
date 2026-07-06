"""OrchestratorAgent HTTP + SSE 端点（学习总管）。

* ``POST /agents/orchestrator/turn`` → 非流式 ``OrchestratorResponse``；
* ``POST /agents/orchestrator/turn/stream`` → SSE（``trace`` / ``token`` / ``result`` / ``error``），
  线格式 ``event: <name>\\ndata: <json>\\n\\n``（与前端 useSseStream 契约一致）。

SSE 生成器沿用 ``routes/streaming.py`` 模式：``asyncio.Queue`` + ``emit`` 回调 + 后台图任务，
``finally`` 入队 ``None`` 哨兵后 drain 产出 chunk。
"""

from __future__ import annotations

import asyncio
import os
from collections.abc import AsyncIterator
from typing import Any, cast

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse

from pythontopia_graph.agents.dispatch import AgentDispatcher, DispatchContext
from pythontopia_graph import sse_agui
from pythontopia_graph.agents.intent_router_agent import IntentRouterAgent
from pythontopia_graph.graph.agentic_loop import BrowseExecutor, MemoryBriefProvider
from pythontopia_graph.graph.hybrid_loop import (
    HybridResult,
    HybridToolOrchestrator,
    IntentRouterSelector,
)
from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.graph.tool_executor import RegistryToolExecutor
from pythontopia_graph.graph.tool_loop import ToolCallingLoop, ToolLoopStep
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.schemas.intent import CourseKc
from pythontopia_graph.tools.consolidated import (
    CONSOLIDATED_TOOL_NAMES,
    consolidated_tool_schemas,
)
from pythontopia_graph.memory.brief_client import fetch_memory_brief
from pythontopia_graph.memory.contract import MemoryBrief
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceFetcher,
    HttpRagEvidenceFetcher,
)
from pythontopia_graph.schemas.orchestrator import (
    OrchestratorRequest,
    OrchestratorResponse,
)
from pythontopia_graph.tools.browse.allowlist import load_allowed_domains
from pythontopia_graph.tools.browse.contract import BrowseRequest, BrowseResult
from pythontopia_graph.tools.browse.executor import execute_browse

router = APIRouter()

_TRUTHY = frozenset({"1", "true", "yes", "on"})


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return cast(LlmProvider, provider)
    return provider_from_env()


def _evidence_fetcher(request: Request) -> EvidenceFetcher:
    fetcher = getattr(request.app.state, "evidence_fetcher", None)
    if fetcher is not None:
        return cast(EvidenceFetcher, fetcher)
    return HttpRagEvidenceFetcher(os.environ.get("PYT_RAG_URL"))


def _valid_kc_codes(request: Request) -> set[str]:
    codes = getattr(request.app.state, "valid_kc_codes", None)
    if codes is not None:
        return cast(set[str], codes)
    return set()


# ── Agentic Loop 侧挂依赖（Phase 2 集成接线；env 选型 + fail-soft，默认零行为变化）──


class _HttpMemoryBriefProvider:
    """M2 brief_client → MemoryBriefProvider 适配：HTTP 取后端聚合 brief，任何失败 fail-soft 空 brief。"""

    async def fetch(self, *, user_id: int, query: str) -> MemoryBrief:
        return await fetch_memory_brief(user_id, query)


class _ExecuteBrowseAdapter:
    """B1 execute_browse → BrowseExecutor 适配：白名单内有界浏览；缺浏览依赖结构化降级（dispatch 回退 VIDEO）。"""

    async def run(self, request: BrowseRequest) -> BrowseResult:
        return await execute_browse(request)


def _memory_brief_provider(request: Request) -> MemoryBriefProvider | None:
    """记忆 brief 提供者（M2）。默认 off：后端 brief 端点当前需学生 JWT（与 graph 内部令牌
    契约不一致），启用前需后端开放内部令牌路径；置 ``PYT_MEMORY_BRIEF=on`` 显式开启（fail-soft）。"""
    provider = getattr(request.app.state, "memory_brief_provider", None)
    if provider is not None:
        return cast(MemoryBriefProvider, provider)
    if os.environ.get("PYT_MEMORY_BRIEF", "").strip().lower() not in _TRUTHY:
        return None
    return _HttpMemoryBriefProvider()


def _browse_executor(request: Request) -> BrowseExecutor | None:
    """BROWSE 执行器（B1）。默认 off：真实 browser-use 需注入 LLM + Playwright；置
    ``BROWSE_ENABLED=on`` 显式开启（缺依赖时 executor 结构化 fail-fast → dispatch 回退 VIDEO）。"""
    executor = getattr(request.app.state, "browse_executor", None)
    if executor is not None:
        return cast(BrowseExecutor, executor)
    if os.environ.get("BROWSE_ENABLED", "").strip().lower() not in _TRUTHY:
        return None
    return _ExecuteBrowseAdapter()


def _browse_allowed_domains() -> list[str] | None:
    """BROWSE 安全墙白名单（§3.4 冻结默认集 + ``BROWSE_ALLOWED_DOMAINS`` env）。
    BROWSE 关闭时返回 None，闭环 BROWSE 闸门据空 allowlist 拒派（回退 VIDEO）。"""
    if os.environ.get("BROWSE_ENABLED", "").strip().lower() not in _TRUTHY:
        return None
    return load_allowed_domains()


def _format_sse(event: str, data: dict[str, Any]) -> str:
    return sse_agui.translate(event, data)


@router.post("/agents/orchestrator/turn", response_model=OrchestratorResponse)
async def orchestrator_turn(
    body: OrchestratorRequest,
    provider: LlmProvider = Depends(_provider),
    evidence_fetcher: EvidenceFetcher = Depends(_evidence_fetcher),
    valid_kc_codes: set[str] = Depends(_valid_kc_codes),
    memory_brief_provider: MemoryBriefProvider | None = Depends(_memory_brief_provider),
    browse_executor: BrowseExecutor | None = Depends(_browse_executor),
) -> OrchestratorResponse:
    graph = OrchestratorGraph(
        provider=provider,
        evidence_fetcher=evidence_fetcher,
        valid_kc_codes=valid_kc_codes,
        memory_brief_provider=memory_brief_provider,
        browse_executor=browse_executor,
        browse_allowed_domains=_browse_allowed_domains(),
    )
    return await graph.run(body)


async def _orchestrator_stream_generator(
    body: OrchestratorRequest,
    provider: LlmProvider,
    evidence_fetcher: EvidenceFetcher,
    valid_kc_codes: set[str],
    memory_brief_provider: MemoryBriefProvider | None,
    browse_executor: BrowseExecutor | None,
) -> AsyncIterator[str]:
    queue: asyncio.Queue[str | None] = asyncio.Queue()

    async def emit(event: str, data: dict[str, Any]) -> None:
        await queue.put(_format_sse(event, data))

    graph = OrchestratorGraph(
        provider=provider,
        evidence_fetcher=evidence_fetcher,
        valid_kc_codes=valid_kc_codes,
        memory_brief_provider=memory_brief_provider,
        browse_executor=browse_executor,
        browse_allowed_domains=_browse_allowed_domains(),
    )

    async def run_graph() -> None:
        try:
            await graph.run_streaming(body, emit)
        finally:
            await queue.put(None)

    task = asyncio.create_task(run_graph())
    await queue.put(
        _format_sse(
            "RUN_STARTED",
            {"runId": f"orchestrator:{body.session_id}", "threadId": body.session_id},
        )
    )
    await queue.put(
        _format_sse(
            "STEP_STARTED",
            {
                "node": "orchestrator",
                "phase": "start",
                "status": "running",
                "elapsed_ms": 0,
                "agent_label": "学习总管",
            },
        )
    )
    try:
        while True:
            item = await queue.get()
            if item is None:
                break
            yield item
    finally:
        if not task.done():
            task.cancel()


@router.post("/agents/orchestrator/turn/stream")
async def orchestrator_turn_stream(
    body: OrchestratorRequest,
    provider: LlmProvider = Depends(_provider),
    evidence_fetcher: EvidenceFetcher = Depends(_evidence_fetcher),
    valid_kc_codes: set[str] = Depends(_valid_kc_codes),
    memory_brief_provider: MemoryBriefProvider | None = Depends(_memory_brief_provider),
    browse_executor: BrowseExecutor | None = Depends(_browse_executor),
) -> StreamingResponse:
    return StreamingResponse(
        _orchestrator_stream_generator(
            body,
            provider,
            evidence_fetcher,
            valid_kc_codes,
            memory_brief_provider,
            browse_executor,
        ),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


# ── 混合工具编排端点（Phase F · flag-gated PYT_TOOL_LOOP，默认 off，加法不改既有路径）──
# 选择=既有意图路由器（held-out 实测 100% vs 纯循环 64%）；编排=有界工具调用循环；
# 桥接既有 AgentDispatcher。默认 off → 在线默认路径零回归；开启前应以 held-out 验证选择质量。

_TOOL_LOOP_SYSTEM_PROMPT = (
    "你是 Pythontopia 的学习助手。系统已为你选好并执行了首个最合适的工具；"
    "请根据工具结果判断是否需要再调用工具来更好地满足学生，完成后用一句话作答。"
    "只在确有需要时继续调用工具，不要臆造工具，不要泄露完整答案。"
)


def _tool_loop_enabled() -> bool:
    """混合工具编排开关；默认 off（不改在线默认路径，零回归）。"""
    return os.environ.get("PYT_TOOL_LOOP", "").strip().lower() in _TRUTHY


def _build_hybrid(
    provider: LlmProvider,
    evidence_fetcher: EvidenceFetcher,
    valid_kc_codes: set[str],
    body: OrchestratorRequest,
) -> HybridToolOrchestrator:
    """从路由依赖 + 请求构造混合编排器（路由器选择 + 有界循环 + 桥接既有 dispatcher）。"""
    selector = IntentRouterSelector(
        IntentRouterAgent(provider),
        course_kcs=[CourseKc(code=code) for code in body.course_kc_index if code],
    )
    loop = ToolCallingLoop(
        provider=provider,
        tools=consolidated_tool_schemas("default"),
        valid_names=set(CONSOLIDATED_TOOL_NAMES),
    )
    executor = RegistryToolExecutor(
        AgentDispatcher(
            DispatchContext(
                provider=provider,
                evidence_fetcher=evidence_fetcher,
                valid_kc_codes=valid_kc_codes,
            )
        ),
        user_id=body.user_id,
    )
    return HybridToolOrchestrator(selector=selector, loop=loop, executor=executor)


@router.post("/agents/orchestrator/tool-loop", response_model=HybridResult)
async def orchestrator_tool_loop(
    body: OrchestratorRequest,
    provider: LlmProvider = Depends(_provider),
    evidence_fetcher: EvidenceFetcher = Depends(_evidence_fetcher),
    valid_kc_codes: set[str] = Depends(_valid_kc_codes),
) -> HybridResult:
    """混合工具编排（非流式）；flag off 时 404，绝不影响既有 orchestrator 路径。"""
    if not _tool_loop_enabled():
        raise HTTPException(status_code=404, detail="tool loop disabled (set PYT_TOOL_LOOP=on)")
    orchestrator = _build_hybrid(provider, evidence_fetcher, valid_kc_codes, body)
    return await orchestrator.run(
        system_prompt=_TOOL_LOOP_SYSTEM_PROMPT, user_message=body.user_message
    )


async def _tool_loop_stream_generator(
    body: OrchestratorRequest,
    provider: LlmProvider,
    evidence_fetcher: EvidenceFetcher,
    valid_kc_codes: set[str],
) -> AsyncIterator[str]:
    queue: asyncio.Queue[str | None] = asyncio.Queue()

    async def emit(event: str, data: dict[str, Any]) -> None:
        await queue.put(_format_sse(event, data))

    async def on_step(step: ToolLoopStep) -> None:
        # 映射为前端 loopSlice 冻结契约（AG-UI CUSTOM/loop_step，LoopStep 形状），
        # 复用既有 LoopProgress.vue 渲染（phase=act 工具执行；status done/failed）。
        loop_step: dict[str, Any] = {
            "id": f"tool-{step.step}",
            "phase": "act",
            "label": step.tool_name,
            "status": "done" if step.ok else "failed",
            "toolName": step.tool_name,
            "reasonSummary": step.summary,
        }
        if not step.ok:
            loop_step["error"] = step.summary
        await emit("CUSTOM", {"name": "loop_step", "value": loop_step})

    orchestrator = _build_hybrid(provider, evidence_fetcher, valid_kc_codes, body)

    async def run_hybrid() -> None:
        try:
            result = await orchestrator.run(
                system_prompt=_TOOL_LOOP_SYSTEM_PROMPT,
                user_message=body.user_message,
                on_step=on_step,
            )
            await emit("result", result.model_dump(mode="json"))
        except Exception as exc:  # noqa: BLE001 - surface as SSE error event
            await emit("error", {"code": "TOOL_LOOP_FAILED", "message": str(exc)})
        finally:
            await queue.put(None)

    task = asyncio.create_task(run_hybrid())
    try:
        while True:
            item = await queue.get()
            if item is None:
                break
            yield item
    finally:
        if not task.done():
            task.cancel()


@router.post("/agents/orchestrator/tool-loop/stream")
async def orchestrator_tool_loop_stream(
    body: OrchestratorRequest,
    provider: LlmProvider = Depends(_provider),
    evidence_fetcher: EvidenceFetcher = Depends(_evidence_fetcher),
    valid_kc_codes: set[str] = Depends(_valid_kc_codes),
) -> StreamingResponse:
    """混合工具编排（SSE）：增量 ``loop_step`` 事件 + 终态 ``result``；flag off 时 404。"""
    if not _tool_loop_enabled():
        raise HTTPException(status_code=404, detail="tool loop disabled (set PYT_TOOL_LOOP=on)")
    return StreamingResponse(
        _tool_loop_stream_generator(body, provider, evidence_fetcher, valid_kc_codes),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
