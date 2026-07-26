"""百晓工坊路由链路已确认缺陷的回归测试（修复前必红）。

覆盖：
② 无 Agent 的 secondary_intent 不得把 /agents/intent/route 打成 500；
③ BROWSE 复合步必须落在 BrowseAgent，不得被静默替换成 HANDOUT；
④ 流式回复先审后放：越权 token 一个字都不到学生屏幕；
⑦ C3 执行期以签发时的 search 为权威，学情漂移不得整轮报错；
⑧ required_context 声明必须被策略门执行，缺 KC 时澄清而非硬编码 PY43-KC-001；
⑪ 空证据目录 + require_grounding 不得静默放行；
⑫ 路由 / 执行指标的失败面必须进分母。
"""

from __future__ import annotations

from typing import Any

import pytest

from pythontopia_graph.agents.dispatch import AgentDispatcher, DispatchContext
from pythontopia_graph.agents.orchestrator_agent import SAFETY_KEYWORDS
from pythontopia_graph.graph.orchestrator_graph import OrchestratorGraph
from pythontopia_graph.llm.provider import LlmProvider, LlmResponse
from pythontopia_graph.routing.workshop_metrics import (
    record_route_error,
    reset_routing_metrics,
    routing_metrics_snapshot,
)
from pythontopia_graph.routing.workshop_policy import (
    RoutePolicyError,
    compile_route_decision,
    validate_route_decision,
)
from pythontopia_graph.routing.workshop_task_plan import compile_task_plan
from pythontopia_graph.schemas.intent import (
    IntentRouteRequest,
    LearnerRoutingContext,
    LearnerRecentSubmission,
)
from pythontopia_graph.schemas.orchestrator import PlanStep
from pythontopia_graph.schemas.workshop_route import RouteDecision
from pythontopia_graph.tools.registry import TOOLS


class _SilentProvider(LlmProvider):
    """本模块只驱动 _stream_reply / _node_dispatch，不需要真实 LLM。"""

    async def chat(self, messages, **kwargs):  # type: ignore[override]
        return LlmResponse(content_json=None)

    async def chat_stream(self, messages, **kwargs):  # type: ignore[override]
        yield ""


@pytest.fixture
def orchestrator_graph() -> OrchestratorGraph:
    return OrchestratorGraph(provider=_SilentProvider())

# ── ② 无 Agent 的 secondary_intent（VIDEO / PROGRESS / NAVIGATE / MY_STUDY / REVIEW）──

_AGENTLESS_TOOLS = ("VIDEO", "PROGRESS", "NAVIGATE", "MY_STUDY", "REVIEW")


@pytest.mark.parametrize("secondary", _AGENTLESS_TOOLS)
def test_agentless_secondary_intent_degrades_instead_of_raising(secondary: str) -> None:
    """没有注册 Agent 的前端工具当 secondary 时降级为单工具，绝不抛 RoutePolicyError。"""
    assert TOOLS[secondary].allowed_agents == ()

    decision = compile_route_decision(
        IntentRouteRequest(message="讲讲循环，再给我找个视频"),
        intent="ASK",
        secondary_intent=secondary,
        confidence=0.9,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="answer",
        resolved_kc_codes=["PY43-KC-031"],
    )

    assert decision.tools.required == ["ASK"]
    assert decision.task.family != "MULTI_TASK"
    assert secondary in decision.tools.forbidden


@pytest.mark.asyncio
@pytest.mark.parametrize("secondary", _AGENTLESS_TOOLS)
async def test_router_never_emits_agentless_secondary_intent(secondary: str) -> None:
    """路由器自身也必须把这类 secondary 归零，否则整个工坊入口 502。"""
    from tests.test_intent_router_agent import StubProvider, request_of  # noqa: PLC0415

    from pythontopia_graph.agents.intent_router_agent import IntentRouterAgent  # noqa: PLC0415

    agent = IntentRouterAgent(
        StubProvider({"intent": "ASK", "secondary_intent": secondary, "confidence": 0.9})
    )

    resp = await agent.route(request_of("讲讲循环，再给我找个视频"))

    assert resp.secondary_intent is None
    assert resp.route_decision is not None
    assert resp.route_decision.tools.required == ["ASK"]


# ── ③ BROWSE 必须落在 BrowseAgent ──────────────────────────────────────────


def test_browse_tool_is_registered_to_browse_agent() -> None:
    assert TOOLS["BROWSE"].allowed_agents == ("BrowseAgent",)


def test_compound_browse_step_targets_browse_agent_not_resource_dispatch() -> None:
    """ASK + BROWSE 的第二步必须是 BrowseAgent；写成 OrchestratorAgent 会掉进
    资源派发默认分支被解析成 HANDOUT（执行 RouteDecision 明令 forbidden 的工具）。"""
    request = IntentRouteRequest(message="讲讲循环，然后去官网查一下 sorted 稳定性")
    decision = compile_route_decision(
        request,
        intent="ASK",
        secondary_intent="BROWSE",
        confidence=0.92,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="answer",
        resolved_kc_codes=["PY43-KC-031"],
    )

    assert decision.tools.required == ["ASK", "BROWSE"]
    assert "HANDOUT" in decision.tools.forbidden

    plan = compile_task_plan(decision, message=request.message, kc_codes=["PY43-KC-031"])
    browse_step = next(step for step in plan.steps if step.tool_name == "BROWSE")
    assert browse_step.agent == "BrowseAgent"
    assert browse_step.resource_type is None


# ── ④ 流式回复「先审后放」──────────────────────────────────────────────────


class _StubReplyAgent:
    """只提供 _stream_reply 需要的 reply_stream / fallback_reply。"""

    def __init__(self, tokens: list[str]) -> None:
        self._tokens = tokens

    async def reply_stream(self, *_args: Any, **_kwargs: Any):  # noqa: ANN202
        for token in self._tokens:
            yield token

    def fallback_reply(self, *_args: Any, **_kwargs: Any) -> str:
        return "确定性兜底"


@pytest.mark.asyncio
async def test_stream_reply_withholds_tokens_that_fail_safety(
    orchestrator_graph: OrchestratorGraph,
) -> None:
    """命中安全关键词后停止放行剩余 token；越权正文一个字都不许 emit。"""
    keyword = SAFETY_KEYWORDS[0]
    tokens = ["先讲个安全的开头。", f"接着{keyword}", "然后是剩下的越权正文。"]
    emitted: list[str] = []

    async def emit(event: str, data: dict[str, Any]) -> None:
        if event == "token":
            emitted.append(data["delta"])

    orchestrator_graph._agent = _StubReplyAgent(tokens)  # type: ignore[assignment]
    orchestrator_graph._emit_cb = emit  # type: ignore[assignment]

    reply, step = await orchestrator_graph._stream_reply(None, [], None)  # type: ignore[arg-type]

    streamed = "".join(emitted)
    assert keyword not in streamed
    assert "越权正文" not in streamed
    assert streamed == "先讲个安全的开头。"
    # 完整文本仍返回给 _assemble，由它统一降级（否则截断文本会骗过 critic）。
    assert keyword in reply
    assert step.note == "safety_withheld_stream"


@pytest.mark.asyncio
async def test_stream_reply_emits_full_safe_text(
    orchestrator_graph: OrchestratorGraph,
) -> None:
    """安全内容必须完整放行（含末尾没有标点的残余缓冲）。"""
    tokens = ["列表切片是左闭右开的。", "常见坑是步长为负", "时的边界"]
    emitted: list[str] = []

    async def emit(event: str, data: dict[str, Any]) -> None:
        if event == "token":
            emitted.append(data["delta"])

    orchestrator_graph._agent = _StubReplyAgent(tokens)  # type: ignore[assignment]
    orchestrator_graph._emit_cb = emit  # type: ignore[assignment]

    reply, step = await orchestrator_graph._stream_reply(None, [], None)  # type: ignore[arg-type]

    assert "".join(emitted) == "".join(tokens) == reply
    assert step.status == "ok"


# ── ⑦ C3 执行期不重算成本 ──────────────────────────────────────────────────

_C3_MESSAGE = (
    "我循环这一章学得很乱，先给我总结一下，再根据我以前犯的错出几道题，并告诉我先做哪一道。"
)


def _c3_context(cognitive_load: str) -> LearnerRoutingContext:
    return LearnerRoutingContext(
        weak_kcs=["PY43-KC-031"],
        cognitive_load=cognitive_load,  # type: ignore[arg-type]
        recent_submissions=[
            LearnerRecentSubmission(problem_id=1, kc_code="PY43-KC-031", passed=False)
        ],
    )


def _c3_request(cognitive_load: str) -> IntentRouteRequest:
    return IntentRouteRequest(
        message=_C3_MESSAGE,
        manual_kc_codes=["PY43-KC-031"],
        learner_context=_c3_context(cognitive_load),
    )


def _c3_decision(cognitive_load: str):  # noqa: ANN202
    return compile_route_decision(
        _c3_request(cognitive_load),
        intent="HANDOUT",
        secondary_intent="PRACTICE",
        confidence=0.94,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
        resolved_kc_codes=["PY43-KC-031"],
    )


def test_c3_execution_survives_learner_context_drift() -> None:
    """路由与开流之间又落一条失败判题（cognitive_load 变化）不得让整轮 SSE 报错。"""
    issued = _c3_decision("MEDIUM")
    assert issued.task.complexity == "C3"
    # 路由与开流之间又落一条失败判题：cognitive_load MEDIUM→HIGH，A* penalty 变化。
    drifted = _c3_request("HIGH")

    # 签发期语义不变：按当前上下文重算，漂移后的成本确实对不上。
    with pytest.raises(RoutePolicyError, match="diverge from current context"):
        validate_route_decision(issued, scope="default", request=drifted, stage="issue")

    # 执行期以签发时的 search 为权威，结构/授权照验，不重算候选与成本。
    validate_route_decision(issued, scope="default", request=drifted, stage="execute")


def test_c3_execution_still_rejects_unauthorized_candidate_agent() -> None:
    """执行期放过的只有成本重算；工具/Agent 授权仍必须拦住。"""
    payload = _c3_decision("MEDIUM").model_dump()
    payload["search"]["candidates"][0]["agent_path"][0] = "DebugAgent"
    tampered = RouteDecision.model_validate(payload)

    with pytest.raises(RoutePolicyError, match="not allowed for candidate tool"):
        validate_route_decision(tampered, scope="default", request=None, stage="execute")


# ── ⑧ required_context 声明被执行 ──────────────────────────────────────────

_TOPIC_TOOLS = ("HANDOUT", "MINDMAP", "READING", "STORYBOARD", "PRACTICE", "CODE_LAB")


@pytest.mark.parametrize("intent", _TOPIC_TOOLS)
def test_topic_or_kc_missing_forces_clarification(intent: str) -> None:
    """无 @KC、无章节、消息里也没有主题名词 → 澄清，绝不硬编码 PY43-KC-001。"""
    assert "topic_or_kc" in TOOLS[intent].required_context

    decision = compile_route_decision(
        IntentRouteRequest(message="给我整理一份资料"),
        intent=intent,
        confidence=0.95,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
        resolved_kc_codes=[],
    )

    assert decision.task.needs_clarification is True
    assert decision.tools.required == []
    assert decision.output.schema_name == "WorkshopClarificationCard"
    assert "知识点" in decision.task.clarification_question


@pytest.mark.parametrize(
    "request_kwargs",
    [
        {"message": "给我整理一份函数的讲义"},
        {"message": "给我整理一份资料", "manual_kc_codes": ["PY43-KC-031"]},
        {"message": "给我整理一份资料", "current_chapter": 3},
    ],
)
def test_topic_anchor_present_keeps_direct_route(request_kwargs: dict[str, Any]) -> None:
    decision = compile_route_decision(
        IntentRouteRequest(**request_kwargs),
        intent="HANDOUT",
        confidence=0.95,
        needs_clarification=False,
        clarification_prompt="",
        render_mode="resource",
        resolved_kc_codes=[],
    )

    assert decision.task.needs_clarification is False
    assert decision.tools.required == ["HANDOUT"]


@pytest.mark.asyncio
async def test_dispatch_without_kc_fails_structurally_instead_of_forging_kc001() -> None:
    """缺知识点锚漏到派发层时必须结构化失败，不得生成 PY43-KC-001 讲义入库。"""

    class _BoomProvider:
        async def chat(self, *_args: Any, **_kwargs: Any) -> str:  # pragma: no cover
            raise AssertionError("provider must not be called without a KC anchor")

    dispatcher = AgentDispatcher(
        DispatchContext(
            provider=_BoomProvider(),  # type: ignore[arg-type]
            evidence_fetcher=None,  # type: ignore[arg-type]
            valid_kc_codes=set(),
        )
    )
    step = PlanStep(
        id="task-1-handout",
        phase="learn",
        title="讲义",
        detail="给我整理一份资料",
        agent="HandoutAgent",
        kc_codes=[],
        resource_type="HANDOUT",
        tool_name="HANDOUT",
    )

    result = await dispatcher.dispatch(step, user_id=1)

    assert result.ok is False
    assert result.note == "missing_required_context:topic_or_kc"
    assert result.artifact is not None
    assert result.artifact["status"] == "missing_required_context"
    assert "PY43-KC-001" not in str(result.artifact)


# ── ⑪ require_grounding 在空证据目录下不得静默放行 ─────────────────────────


def test_grounding_fails_closed_when_policy_requires_evidence() -> None:
    plan = [
        PlanStep(
            id="task-1-handout",
            phase="learn",
            title="讲义",
            detail="",
            agent="HandoutAgent",
            kc_codes=["PY43-KC-031"],
        )
    ]

    permissive = OrchestratorGraph._grounding(plan, set(), require_grounding=False)
    strict = OrchestratorGraph._grounding(plan, set(), require_grounding=True)

    assert permissive.passed is True
    assert strict.passed is False
    assert strict.reasons == ["EVIDENCE_CATALOGUE_UNAVAILABLE"]


# ── ⑫ 失败面进分母 ────────────────────────────────────────────────────────


def test_route_policy_failure_is_counted_in_routes_total() -> None:
    reset_routing_metrics()

    record_route_error(latency_ms=12)

    routes = routing_metrics_snapshot()["routes"]
    assert routes["total"] == 1
    assert routes["errors"] == 1
    assert routes["errorRate"] == 1.0
    assert routes["byComplexity"]["ERROR"] == 1
    reset_routing_metrics()


@pytest.mark.asyncio
async def test_dispatch_node_records_error_execution_on_policy_failure(
    orchestrator_graph: OrchestratorGraph,
) -> None:
    """TaskPlanPolicyError 等失败原先一次都不进 executions.total，故障期指标反而更好看。"""
    reset_routing_metrics()

    class _Boom(Exception):
        pass

    async def _boom(_state: dict[str, Any]) -> dict[str, Any]:
        raise _Boom("dependency graph has no runnable step")

    orchestrator_graph._dispatch_governed = _boom  # type: ignore[assignment]

    with pytest.raises(_Boom):
        await orchestrator_graph._node_dispatch({"task_plan": object()})  # type: ignore[arg-type]

    executions = routing_metrics_snapshot()["executions"]
    assert executions["total"] == 1
    assert executions["byStatus"]["ERROR"] == 1
    assert executions["errorRate"] == 1.0
    reset_routing_metrics()
