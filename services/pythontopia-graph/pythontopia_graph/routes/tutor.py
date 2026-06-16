"""TutorAgent SSE 流式答疑端点。

答疑链路：馆员(RAG) → 导师(LLM) → 审查官 → 校对官 → 安全员（资源流水线同款三道审查）。
开场发 ``plan`` 工位清单 + 各智能体 ``trace`` start/finish，驱动侧栏 marvis「智能体工作室」
（与资源流同一套 SSE 线协议）。
"""

from __future__ import annotations

import asyncio
import os
import re
import time
from collections.abc import AsyncIterator, Callable
from typing import Any

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from pythontopia_graph import sse_agui
from pythontopia_graph.agents.tutor_agent import TutorAgent
from pythontopia_graph.graph.debate_graph import DebateGraph, DebateResult
from pythontopia_graph.llm.provider import LlmProvider, provider_from_env
from pythontopia_graph.nodes.evidence_fetcher import (
    EvidenceBundle,
    EvidenceFetcher,
    HttpRagEvidenceFetcher,
)
from pythontopia_graph.nodes.tutor_review import (
    grounding_review,
    hallucination_review,
    safety_review,
)
from pythontopia_graph.schemas.tutor import TutorAskRequest, TutorResponse, TutorVisualAid
from pythontopia_graph.tools.harness import (
    BudgetGuard,
    ToolObservation,
    run_with_governance,
)

router = APIRouter()

_TOP_K = 5

# 每请求 LLM 子调用预算（修订/图解/证据注等；主作答流式调用不计入）。
# 预算不足时低优先级步骤（图解、证据注）先被砍，trace 标 budget_skipped。
_MAX_LLM_CALLS = 6

# RAG 检索治理：15s 超时 + 1 次重试 + rag 熔断；失败回空证据（自然落入「通识知识标注」路径）。
_RAG_TIMEOUT_S = 15.0

# SSE 心跳：距上次向客户端发送超过该秒数则补一行注释 ping（EventSource 解析器自动忽略），
# 防止修订/图解等非流式长等待阶段被代理/浏览器判定空闲断连。
_HEARTBEAT_S = 10.0
_PING = ": ping\n\n"

# 答疑开场工位清单：与资源流 `plan` 同形状，让侧栏 marvis 复用同一套渲染 + 交接动画。
# 忠实反映真实节点：evidence_fetcher / TutorAgent / 三道审查，绝不摆假 agent。
_ASK_PLAN: dict[str, Any] = {
    "type": "ASK",
    "nodes": [
        {"node": "evidence_fetcher", "agent_label": "证据检索·馆员", "kind": "evidence"},
        {"node": "TutorAgent", "agent_label": "导师·作答", "kind": "tutor"},
        {"node": "grounding_critic", "agent_label": "引用审查·审查官", "kind": "grounding"},
        {
            "node": "hallucination_detector",
            "agent_label": "防幻觉核查·校对官",
            "kind": "hallucination",
        },
        {"node": "safety_critic", "agent_label": "安全审查·安全员", "kind": "safety"},
    ],
}

# 工位中文名（C1）：plan 与每条 STEP_* trace 都带 agent_label，前端优先读它、绝不显英文 node 名。
# 与 _ASK_PLAN 的 5 个工位 label 保持一致；debate 子节点（solver/critic/referee）以 DebateGraph
# 事件自带的 agent_label 为准（事件源头），此处登记一份兜底，避免事件偶发缺字段时回落英文。
_NODE_LABELS: dict[str, str] = {
    "evidence_fetcher": "证据检索·馆员",
    "TutorAgent": "导师·作答",
    "code_runner": "代码运行·沙箱",
    "grounding_critic": "引用审查·审查官",
    "hallucination_detector": "防幻觉核查·校对官",
    "safety_critic": "安全审查·安全员",
    "TutorAgent.revise": "导师·返工",
    "tutor_diagram": "图解生成",
    "tutor_evidence_note": "证据导读",
    "debate": "辩论复核·三方",
    "solver": "求解者",
    "critic": "质疑者",
    "referee": "仲裁者",
    "quiz_question": "考官·出题",
    "quiz_judge": "考官·评卷",
}

# 考考我（ASKME 多轮填空）开场工位清单（C4）：出题流只有出题官；评卷流含馆员→评卷→引用审查→安全。
_QUIZ_ASK_PLAN: dict[str, Any] = {
    "type": "ASKME",
    "nodes": [
        {"node": "quiz_question", "agent_label": _NODE_LABELS["quiz_question"], "kind": "quiz"},
    ],
}

_QUIZ_JUDGE_PLAN: dict[str, Any] = {
    "type": "ASKME",
    "nodes": [
        {
            "node": "evidence_fetcher",
            "agent_label": _NODE_LABELS["evidence_fetcher"],
            "kind": "evidence",
        },
        {"node": "quiz_judge", "agent_label": _NODE_LABELS["quiz_judge"], "kind": "quiz"},
        {
            "node": "grounding_critic",
            "agent_label": _NODE_LABELS["grounding_critic"],
            "kind": "grounding",
        },
        {"node": "safety_critic", "agent_label": _NODE_LABELS["safety_critic"], "kind": "safety"},
    ],
}

# 考考我分流标记（C4）：context 含 QUIZ_TURN_<n> → 出题；含 QUIZ_JUDGE → 评卷。
_QUIZ_ASK_RE = re.compile(r"QUIZ_TURN_(\d+)")
# 评卷点评里的课件引用标记 [页 X]，用于判断「是否真的引用了 PPT」。
_CITATION_RE = re.compile(r"\[页\s*(\d+)\]")


def _detect_quiz_mode(context: str) -> tuple[str, int] | None:
    """识别考考我分流（C4）：QUIZ_JUDGE → ("judge", 0)；QUIZ_TURN_<n> → ("ask", n)；都没有 → None。"""
    if "QUIZ_JUDGE" in (context or ""):
        return ("judge", 0)
    match = _QUIZ_ASK_RE.search(context or "")
    if match:
        return ("ask", int(match.group(1)))
    return None


def _label_for(node: str) -> str:
    return _NODE_LABELS.get(node, node)


def _note_kwargs(reasons: list[str]) -> dict[str, str]:
    """审查理由拼成 note；为空则不带 note 字段（避免前端把空列表显示成字面量 "[]"）。"""
    note = "; ".join(reason for reason in reasons if reason)
    return {"note": note} if note else {}


def _render_quiz_stem(stem: str, blanks: list[dict[str, Any]]) -> str:
    """把填空题渲染成可读 Markdown：题干 +（可选）逐空提示行。"""
    lines = [stem]
    hints = [f"({b['index']}) {b['hint']}" for b in blanks if b.get("hint")]
    if hints:
        lines.append("")
        lines.append("提示：" + "；".join(hints))
    return "\n".join(lines)


def _provider(request: Request) -> LlmProvider:
    provider = getattr(request.app.state, "llm_provider", None)
    if provider is not None:
        return provider
    return provider_from_env()


def _evidence_fetcher(request: Request) -> EvidenceFetcher:
    fetcher = getattr(request.app.state, "evidence_fetcher", None)
    if fetcher is not None:
        return fetcher
    return HttpRagEvidenceFetcher(os.environ.get("PYT_RAG_URL"))


def _format_sse(event: str, data: dict[str, Any]) -> str:
    # 统一经 AG-UI 语义编码：历史名 trace/token/result/error/plan → AG-UI 官方事件，
    # 调用点（含 _trace_start/_trace_finish）零改动全量迁移。见 sse_agui.translate。
    return sse_agui.translate(event, data)


def _stream_pieces(text: str, size: int = 24) -> list[str]:
    """把（非流式产出的）辩论定稿切成 token 分片，复用前端 token 累积逻辑、保留流式观感。"""
    if not text:
        return []
    return [text[i : i + size] for i in range(0, len(text), size)]


def _trace_start(node: str, *, agent_label: str | None = None) -> str:
    return _format_sse(
        "trace",
        {
            "node": node,
            "phase": "start",
            "status": "running",
            "elapsed_ms": 0,
            "note": None,
            # C1：每条 STEP_* 都带中文 agent_label（debate 子节点用事件源头自带值，其余查表）。
            "agent_label": agent_label or _label_for(node),
        },
    )


def _trace_finish(
    node: str, status: str, elapsed_ms: int, *, agent_label: str | None = None, **extra: Any
) -> str:
    payload: dict[str, Any] = {
        "node": node,
        "phase": "finish",
        "status": status,
        "elapsed_ms": elapsed_ms,
        "agent_label": agent_label or _label_for(node),
    }
    payload.update(extra)
    return _format_sse("trace", payload)


class _SseHeartbeat:
    """SSE 发送计时器：外层发送出口每出一个 chunk 调 :meth:`mark_sent` 刷新时间戳；

    修订/图解等非流式长等待阶段用 :meth:`due` / :meth:`seconds_until_due` 决定何时补
    ping 注释行。时钟可注入，便于测试用假时钟直接断言节奏逻辑。
    """

    def __init__(
        self,
        heartbeat_s: float = _HEARTBEAT_S,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self.heartbeat_s = heartbeat_s
        self._clock = clock
        self._last_sent = clock()

    def mark_sent(self) -> None:
        self._last_sent = self._clock()

    def idle_s(self) -> float:
        return self._clock() - self._last_sent

    def due(self) -> bool:
        return self.idle_s() >= self.heartbeat_s

    def seconds_until_due(self) -> float:
        """距下一次心跳应触发的秒数；已逾期时返回小下限（尽快触发、避免零超时空转）。"""
        return max(0.05, self.heartbeat_s - self.idle_s())


async def _pings_while(
    task: asyncio.Future[Any], heartbeat: _SseHeartbeat
) -> AsyncIterator[str]:
    """task 完成前按心跳节奏产出 ping 注释行（不消费 task 结果，结果由调用方 await）。"""
    while not task.done():
        await asyncio.wait({task}, timeout=heartbeat.seconds_until_due())
        if not task.done() and heartbeat.due():
            yield _PING


def _build_revise_prompt(question: str, answer: str, reasons: list[str]) -> str:
    """构造 critique→revise 修订提示：原问题 + 原回答 + 审查意见 + 修订要求。"""
    reason_lines = "\n".join(f"- {reason}" for reason in reasons)
    return (
        "你刚才的回答未通过证据审查，请修订。\n\n"
        f"【原问题】\n{question}\n\n"
        f"【你的原回答】\n{answer}\n\n"
        f"【审查意见】\n{reason_lines}\n\n"
        "【修订要求】\n"
        "1. 只修正审查意见指出的问题：删除或更正编造的页码引用与无证据支撑的统计数字；\n"
        "2. 引用页码必须严格来自课件证据，不得新增证据之外的页码或数字；\n"
        "3. 其余内容保持原回答的结构与语气，仍然不给完整答案代码。\n"
        "直接输出修订后的完整回答（Markdown）。"
    )


@router.post("/agents/tutor/ask/stream")
async def tutor_ask_stream(
    body: TutorAskRequest,
    provider: LlmProvider = Depends(_provider),
    evidence_fetcher: EvidenceFetcher = Depends(_evidence_fetcher),
) -> StreamingResponse:
    heartbeat = _SseHeartbeat(heartbeat_s=_HEARTBEAT_S)

    async def stages() -> AsyncIterator[str]:
        try:
            base_question = body.question
            context = (body.context or "").strip()
            # 上下文存在时，把它并入检索 query，让证据更贴合当前视频/PPT/题目。
            query = f"{context}\n{base_question}".strip() if context else base_question
            kc_codes = body.kc_codes or []

            # ── 考考我（ASKME 多轮填空）分流（跨包契约 C4）：context 含 QUIZ_TURN_<n> → 出题；
            #     含 QUIZ_JUDGE → 评卷（仅 RAG 命中才引用 PPT）。命中即走考核专用流，不跑普通答疑。 ──
            quiz = _detect_quiz_mode(context)
            if quiz is not None:
                async for chunk in _quiz_stages(
                    quiz, body, base_question, kc_codes, query,
                    provider, evidence_fetcher, heartbeat,
                ):
                    yield chunk
                return

            # 每请求 LLM 子调用预算：修订先用、图解/证据注优先级最低后用（预算不足先被砍）。
            guard = BudgetGuard(max_llm_calls=_MAX_LLM_CALLS)

            # 开场工位清单（零 LLM 成本，在任何节点运行前发出）。
            yield _format_sse("plan", _ASK_PLAN)

            # ── 馆员：课件 RAG 检索（治理：超时 + 重试 + rag 熔断；失败回空证据 → 通识标注路径） ──
            # 统一发 start（C2：所有节点 start+finish 对称，便于前端按 node 折叠去重）。
            yield _trace_start("evidence_fetcher")
            started = time.monotonic()

            async def _fetch_evidence() -> EvidenceBundle:
                return await evidence_fetcher.fetch(
                    kc_codes=kc_codes, query=query, top_k=_TOP_K,
                )

            async def _empty_evidence() -> EvidenceBundle:
                return EvidenceBundle(chunks=[], no_evidence_found=True)

            fetch_obs = await run_with_governance(
                "evidence_fetch",
                _fetch_evidence,
                timeout_s=_RAG_TIMEOUT_S,
                max_retries=1,
                fallback=_empty_evidence,
                breaker_key="rag",
            )
            evidence = (
                fetch_obs.value
                if isinstance(fetch_obs.value, EvidenceBundle)
                else EvidenceBundle(chunks=[], no_evidence_found=True)
            )
            fetch_extra: dict[str, Any] = {"chunks": len(evidence.chunks)}
            if fetch_obs.status != "ok":
                fetch_extra["note"] = fetch_obs.as_trace_note()
            yield _trace_finish(
                "evidence_fetcher",
                "ok" if fetch_obs.status == "ok" else "retry",
                int((time.monotonic() - started) * 1000),
                **fetch_extra,
            )

            # ── 导师：流式作答 ──
            agent = TutorAgent(provider)
            history = [{"role": t.role, "content": t.content} for t in body.conversation_history]
            collected: list[str] = []

            # 上下文随题面一并给到 agent，让引导贴合「学生当前在看/在做什么」。
            agent_question = (
                f"【当前学习上下文】{context}\n\n【学生问题】{base_question}"
                if context
                else base_question
            )
            # 与 TutorAgent 同一判定：带 ```python 片段 + 追问错误/输出时，附加 code_runner
            # trace（前端据此渲染「已验证」徽标）。判定串须与传入 ask_stream 的完全一致。
            verify = TutorAgent.should_verify(agent_question)
            # 难题 / 需严谨论证的问题路由进 Solver→Critic→Referee 复核（与 code 验证互斥优先）。
            debate_mode = (not verify) and TutorAgent.should_debate(base_question)

            answer = ""
            if debate_mode:
                debate = DebateGraph(provider)
                debate_result: DebateResult | None = None
                pending: dict[str, str] | None = None
                node_started = time.monotonic()
                debate_started = time.monotonic()
                # 复合 debate 工位先发 start，子节点 solver/critic/referee 嵌套其中（start+finish 对称·C2）。
                yield _trace_start("debate")
                async for kind, payload in debate.run_events(
                    agent_question, evidence.chunks, body.profile_brief,
                ):
                    # node_* 载荷为 {node, agent_label}（事件源头自带中文 label·C1）。
                    if kind == "node_start" and isinstance(payload, dict):
                        pending = payload
                        node_started = time.monotonic()
                        yield _trace_start(
                            payload["node"], agent_label=payload.get("agent_label"),
                        )
                    elif kind == "node_finish" and isinstance(payload, dict):
                        pending = None
                        yield _trace_finish(
                            payload["node"], "ok",
                            int((time.monotonic() - node_started) * 1000),
                            agent_label=payload.get("agent_label"),
                        )
                    elif kind == "result":
                        debate_result = payload if isinstance(payload, DebateResult) else None
                # 仅当复核产出非空定稿时采用；否则（含异常/空答）回退普通答疑，绝不让难题崩答疑。
                if debate_result is not None and debate_result.answer.strip():
                    answer = debate_result.answer
                    # 辩论完成后关闭复合 debate 工位（前端可视为"多个 agent 争论"）。
                    yield _trace_finish(
                        "debate", "ok",
                        int((time.monotonic() - debate_started) * 1000),
                        note=f"confidence={debate_result.confidence:.2f}",
                    )
                    for piece in _stream_pieces(answer):
                        collected.append(piece)
                        yield _format_sse("token", {"delta": piece})
                else:
                    # 子节点 start 未配对 finish 时补 error finish，再关闭复合 debate 工位（保持对称）。
                    if pending is not None:
                        yield _trace_finish(
                            pending["node"], "error", 0,
                            agent_label=pending.get("agent_label"),
                        )
                    yield _trace_finish(
                        "debate", "error",
                        int((time.monotonic() - debate_started) * 1000),
                    )
                    debate_mode = False

            if not debate_mode:
                yield _trace_start("TutorAgent")
                tutor_started = time.monotonic()
                code_started = tutor_started
                if verify:
                    yield _trace_start("code_runner")
                    code_started = time.monotonic()
                async for token in agent.ask_stream(
                    question=agent_question,
                    profile_brief=body.profile_brief,
                    conversation_history=history,
                    evidence_chunks=evidence.chunks,
                ):
                    collected.append(token)
                    yield _format_sse("token", {"delta": token})
                answer = "".join(collected)
                if verify:
                    yield _trace_finish(
                        "code_runner", "ok", int((time.monotonic() - code_started) * 1000)
                    )
                yield _trace_finish(
                    "TutorAgent", "ok", int((time.monotonic() - tutor_started) * 1000)
                )

            # ── 审查官：引用审查（答案 [页 X] 是否命中证据页） ──
            yield _trace_start("grounding_critic")
            g_started = time.monotonic()
            grounding = grounding_review(answer, evidence.chunks)
            yield _trace_finish(
                "grounding_critic",
                "ok" if grounding.passed else "retry",
                int((time.monotonic() - g_started) * 1000),
                **_note_kwargs(grounding.reasons),
            )

            # ── 校对官：防幻觉（统计型数字是否有据） ──
            yield _trace_start("hallucination_detector")
            h_started = time.monotonic()
            hallucination = hallucination_review(answer, evidence.chunks)
            yield _trace_finish(
                "hallucination_detector",
                "ok" if hallucination.passed else "retry",
                int((time.monotonic() - h_started) * 1000),
                **_note_kwargs(hallucination.reasons),
            )

            # ── 安全员：泄答 / 歧视 / PII / 危险代码 ──
            yield _trace_start("safety_critic")
            s_started = time.monotonic()
            safety = safety_review(answer)
            yield _trace_finish(
                "safety_critic",
                "ok" if safety.passed else "fail",
                int((time.monotonic() - s_started) * 1000),
                **_note_kwargs(safety.reasons),
            )

            # ── critique→revise 闭环：grounding/hallucination 未过 → 注入审查意见做一轮有界修订 ──
            # 修订发生在 token 流之后（非流式），前端以 result.answer_md 终值渲染（契约本就支持）。
            revised = False
            if not (grounding.passed and hallucination.passed):
                if heartbeat.due():
                    yield _PING
                yield _trace_start("TutorAgent.revise")
                revise_started = time.monotonic()
                revise_reasons = list(grounding.reasons) + list(hallucination.reasons)
                revise_prompt = _build_revise_prompt(agent_question, answer, revise_reasons)

                async def _revise() -> TutorResponse:
                    return await agent.ask(
                        question=revise_prompt,
                        profile_brief=body.profile_brief,
                        conversation_history=history,
                        evidence_chunks=evidence.chunks,
                        allow_diagram=False,
                    )

                revise_task = asyncio.ensure_future(
                    run_with_governance(
                        "tutor_revise", _revise, breaker_key="provider", budget=guard,
                    )
                )
                async for ping in _pings_while(revise_task, heartbeat):
                    yield ping
                revise_obs = await revise_task
                revise_elapsed = int((time.monotonic() - revise_started) * 1000)
                if revise_obs.status == "budget_skipped":
                    yield _trace_finish(
                        "TutorAgent.revise", "skipped", revise_elapsed, note="budget_skipped",
                    )
                else:
                    candidate = ""
                    if revise_obs.ok and isinstance(revise_obs.value, TutorResponse):
                        candidate = (revise_obs.value.answer_md or "").strip()
                    if candidate:
                        # 修订稿重跑三道审查：全过才替换；最多 1 轮，修订失败或仍未过则
                        # 保留原答案与原审查结果（答案优先不丢弃）。
                        revised_grounding = grounding_review(candidate, evidence.chunks)
                        revised_hallucination = hallucination_review(candidate, evidence.chunks)
                        revised_safety = safety_review(candidate)
                        if (
                            revised_grounding.passed
                            and revised_hallucination.passed
                            and revised_safety.passed
                        ):
                            answer = candidate
                            grounding = revised_grounding
                            hallucination = revised_hallucination
                            safety = revised_safety
                            revised = True
                    yield _trace_finish(
                        "TutorAgent.revise",
                        "ok" if revised else "retry",
                        revise_elapsed,
                        note="revised" if revised else "kept_original",
                    )

            # ── 汇总结果（答案优先：审查只标注、不丢弃答案；安全失败时不再附图解） ──
            based_on_general = not evidence.chunks
            # 命中证据按 page_id 去重投影成可展示引用卡（页码 + 原文片段），page_ids 同源同序，
            # 供前端「课件证据」面板（CoursewareEvidence）渲染——复用 TutorAgent 同一套截断逻辑。
            citations = TutorAgent._build_citations(evidence.chunks)
            evidence_list = [c.model_dump(mode="json") for c in citations]
            page_ids = [c.page_id for c in citations]
            combined_passed = grounding.passed and hallucination.passed
            if based_on_general:
                grounding_status = "NO_EVIDENCE"
            elif combined_passed:
                grounding_status = "PASSED"
            else:
                grounding_status = "FAILED"

            # ── 尾部并行：图解 ∥ 证据注（asyncio.gather；各自经 provider 熔断 + 预算治理） ──
            # 图解按需触发：仅当学生语句明确「要图/流程/结构」且安全通过时生成；
            # 证据注仅在有证据时生成。两者优先级最低，预算不足先被砍（trace 标 budget_skipped）。
            want_diagram = safety.passed and TutorAgent.wants_diagram(base_question)

            async def _make_diagram() -> list[TutorVisualAid]:
                return await agent.build_diagram(base_question, evidence.chunks)

            async def _make_evidence_note() -> str:
                return await agent.summarize_evidence(
                    base_question, context, body.profile_brief, evidence.chunks,
                )

            async def _skip() -> None:
                return None

            diagram_coro = (
                run_with_governance(
                    "tutor_diagram", _make_diagram, breaker_key="provider", budget=guard,
                )
                if want_diagram
                else _skip()
            )
            note_coro = (
                run_with_governance(
                    "tutor_evidence_note",
                    _make_evidence_note,
                    breaker_key="provider",
                    budget=guard,
                )
                if evidence.chunks
                else _skip()
            )
            if heartbeat.due():
                yield _PING
            # 尾部工位 start（仅对实际会运行的节点发，与 finish 对称·C2）：未触发图解/无证据则不发。
            tail_started = time.monotonic()
            if want_diagram:
                yield _trace_start("tutor_diagram")
            if evidence.chunks:
                yield _trace_start("tutor_evidence_note")
            tail_task = asyncio.ensure_future(asyncio.gather(diagram_coro, note_coro))
            async for ping in _pings_while(tail_task, heartbeat):
                yield ping
            diagram_obs, note_obs = await tail_task
            tail_elapsed = int((time.monotonic() - tail_started) * 1000)

            visual_aids: list[dict[str, Any]] = []
            if isinstance(diagram_obs, ToolObservation):
                if diagram_obs.status == "budget_skipped":
                    yield _trace_finish(
                        "tutor_diagram", "skipped", tail_elapsed, note="budget_skipped",
                    )
                elif diagram_obs.ok and isinstance(diagram_obs.value, list):
                    visual_aids = [aid.model_dump(mode="json") for aid in diagram_obs.value]
                    yield _trace_finish("tutor_diagram", "ok", tail_elapsed)
                else:
                    yield _trace_finish("tutor_diagram", "retry", tail_elapsed)

            # 「课件证据」整组一句话总览说明（结合提问/上下文/画像）；best-effort，失败回空串。
            evidence_note = ""
            if isinstance(note_obs, ToolObservation):
                if note_obs.status == "budget_skipped":
                    yield _trace_finish(
                        "tutor_evidence_note", "skipped", tail_elapsed, note="budget_skipped",
                    )
                elif note_obs.ok and isinstance(note_obs.value, str):
                    evidence_note = note_obs.value
                    yield _trace_finish("tutor_evidence_note", "ok", tail_elapsed)
                else:
                    yield _trace_finish("tutor_evidence_note", "retry", tail_elapsed)

            yield _format_sse("result", _build_result(
                answer=answer,
                page_ids=page_ids,
                evidence=evidence_list,
                grounding_status=grounding_status,
                combined_passed=combined_passed,
                grounding=grounding,
                hallucination=hallucination,
                safety=safety,
                based_on_general=based_on_general,
                visual_aids=visual_aids,
                evidence_note=evidence_note,
                revised=revised,
                personalization_note=TutorAgent.personalization_note(body.profile_brief),
            ))
        except Exception as exc:
            yield _format_sse("error", {"code": "TUTOR_FAILED", "message": str(exc)})

    async def generate() -> AsyncIterator[str]:
        # 统一发送出口：每个 chunk 出站即刷新心跳时间戳（ping 本身也经此出站）。
        async for chunk in stages():
            heartbeat.mark_sent()
            yield chunk

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


def _build_result(
    *,
    answer: str,
    page_ids: list[int],
    evidence: list[dict[str, Any]],
    grounding_status: str,
    combined_passed: bool,
    grounding: Any,
    hallucination: Any,
    safety: Any,
    based_on_general: bool,
    visual_aids: list[dict[str, Any]],
    evidence_note: str = "",
    revised: bool = False,
    personalization_note: str = "",
) -> dict[str, Any]:
    """组装答疑结果 payload。

    同时给出 snake_case 与 camelCase 两套键：
      - Java ``TutorService.persistSession`` 读 snake_case（``answer_md`` /
        ``evidence_page_ids`` / ``grounding_status``）落库；
      - 前端 ``useSseStream`` 直接 ``JSON.parse`` 后按 camelCase 读（``answerMd`` /
        ``evidencePageIds`` / ``groundingStatus``，``visual_aids`` 历史沿用 snake，
        ``personalization_note`` 按 §0 契约前端直读 snake_case）。
    另附 ``review`` 三道审查明细，供答案气泡展示「已过审查官/校对官/安全员」。
    """
    return {
        # snake_case（落库 + 规范）
        "answer_md": answer,
        "evidence_page_ids": page_ids,
        "evidence": evidence,
        "grounding_result": {
            "passed": combined_passed,
            "reasons": list(grounding.reasons) + list(hallucination.reasons),
        },
        "safety_result": {"passed": safety.passed, "reasons": list(safety.reasons)},
        "based_on_general_knowledge": based_on_general,
        "grounding_status": grounding_status,
        "visual_aids": visual_aids,
        # 「课件证据」整组总览说明（结合提问/上下文/画像；无证据或生成失败时为空串）。
        "evidence_note": evidence_note,
        # critique→revise 闭环是否替换过答案（修订稿通过重跑三审才替换）。
        "revised": revised,
        # 「为你定制」一句话说明（确定性拼接画像信号，零 LLM 成本；brief 空则空串）。
        "personalization_note": personalization_note,
        # camelCase（前端直读）
        "answerMd": answer,
        "evidencePageIds": page_ids,
        "groundingStatus": grounding_status,
        # 三道审查明细（透明化）
        "review": {
            "grounding": {"passed": grounding.passed, "reasons": list(grounding.reasons)},
            "hallucination": {
                "passed": hallucination.passed,
                "reasons": list(hallucination.reasons),
            },
            "safety": {"passed": safety.passed, "reasons": list(safety.reasons)},
        },
    }


async def _quiz_stages(
    quiz: tuple[str, int],
    body: TutorAskRequest,
    base_question: str,
    kc_codes: list[str],
    query: str,
    provider: LlmProvider,
    evidence_fetcher: EvidenceFetcher,
    heartbeat: _SseHeartbeat,
) -> AsyncIterator[str]:
    """考考我（ASKME 多轮填空）专用 SSE 流（跨包契约 C4）。

    出题（QUIZ_TURN_<n>）：出题官产出一道填空题（不引用 PPT、不给证据页）。
    评卷（QUIZ_JUDGE）：馆员检索 → 评卷官判定 → 引用审查 → 安全审查；仅当课件 RAG 命中、
    且点评里确有合法 [页 X] 引用时才回带证据卡，否则不提 PPT（防伪造页码红线由引用审查兜底）。
    plan 与每条 trace 均带中文 agent_label，结构与普通答疑流一致，前端复用同一套时间轴。
    """
    phase, round_no = quiz
    agent = TutorAgent(provider)

    if phase == "ask":
        yield _format_sse("plan", _QUIZ_ASK_PLAN)
        yield _trace_start("quiz_question")
        q_started = time.monotonic()
        topic = base_question or "、".join(kc_codes) or "Python 基础"
        ask_task = asyncio.ensure_future(
            agent.ask_quiz_question(
                topic=topic, profile_brief=body.profile_brief, round_no=round_no,
            )
        )
        async for ping in _pings_while(ask_task, heartbeat):
            yield ping
        try:
            quiz_q = await ask_task
        except Exception as exc:  # noqa: BLE001 - 出题失败 fail-fast 报错，不静默降级
            yield _trace_finish(
                "quiz_question", "error", int((time.monotonic() - q_started) * 1000),
            )
            yield _format_sse("error", {"code": "QUIZ_FAILED", "message": str(exc)})
            return
        stem = quiz_q["stem"]
        blanks = quiz_q["blanks"]
        answer_md = _render_quiz_stem(stem, blanks)
        safety = safety_review(answer_md)
        yield _trace_finish(
            "quiz_question",
            "ok" if safety.passed else "fail",
            int((time.monotonic() - q_started) * 1000),
            **_note_kwargs(safety.reasons),
        )
        for piece in _stream_pieces(answer_md):
            yield _format_sse("token", {"delta": piece})
        yield _format_sse("result", _build_quiz_result(
            answer_md=answer_md,
            quiz={
                "phase": "ask",
                "round": round_no,
                "stem": stem,
                "blanks": blanks,
                "cite_evidence": False,
            },
            page_ids=[],
            evidence=[],
            grounding_status="NO_EVIDENCE",
            based_on_general=True,
            safety=safety,
            profile_brief=body.profile_brief,
        ))
        return

    # ── phase == "judge"：先检索判定 RAG 是否命中（决定能否引用 PPT） ──
    yield _format_sse("plan", _QUIZ_JUDGE_PLAN)
    yield _trace_start("evidence_fetcher")
    started = time.monotonic()

    async def _fetch_evidence() -> EvidenceBundle:
        return await evidence_fetcher.fetch(kc_codes=kc_codes, query=query, top_k=_TOP_K)

    async def _empty_evidence() -> EvidenceBundle:
        return EvidenceBundle(chunks=[], no_evidence_found=True)

    fetch_obs = await run_with_governance(
        "evidence_fetch",
        _fetch_evidence,
        timeout_s=_RAG_TIMEOUT_S,
        max_retries=1,
        fallback=_empty_evidence,
        breaker_key="rag",
    )
    evidence = (
        fetch_obs.value
        if isinstance(fetch_obs.value, EvidenceBundle)
        else EvidenceBundle(chunks=[], no_evidence_found=True)
    )
    fetch_extra: dict[str, Any] = {"chunks": len(evidence.chunks)}
    if fetch_obs.status != "ok":
        fetch_extra["note"] = fetch_obs.as_trace_note()
    yield _trace_finish(
        "evidence_fetcher",
        "ok" if fetch_obs.status == "ok" else "retry",
        int((time.monotonic() - started) * 1000),
        **fetch_extra,
    )

    # 上一轮题目（评卷依据）：取最近一条 assistant 历史。
    prev_question = next(
        (t.content for t in reversed(body.conversation_history) if t.role == "assistant"),
        "",
    )
    yield _trace_start("quiz_judge")
    j_started = time.monotonic()
    judge_task = asyncio.ensure_future(
        agent.judge_quiz(
            prev_question=prev_question,
            student_answer=base_question,
            profile_brief=body.profile_brief,
            evidence_chunks=evidence.chunks,
        )
    )
    async for ping in _pings_while(judge_task, heartbeat):
        yield ping
    try:
        judged = await judge_task
    except Exception as exc:  # noqa: BLE001 - 评卷失败 fail-fast 报错
        yield _trace_finish(
            "quiz_judge", "error", int((time.monotonic() - j_started) * 1000),
        )
        yield _format_sse("error", {"code": "QUIZ_FAILED", "message": str(exc)})
        return
    explanation = judged["explanation_md"]
    verdict = judged["verdict"]
    yield _trace_finish(
        "quiz_judge", "ok", int((time.monotonic() - j_started) * 1000),
        note=f"verdict={verdict}",
    )

    # 引用审查：点评里的 [页 X] 必须命中证据页（防伪造页码红线）。
    yield _trace_start("grounding_critic")
    g_started = time.monotonic()
    grounding = grounding_review(explanation, evidence.chunks)
    yield _trace_finish(
        "grounding_critic",
        "ok" if grounding.passed else "retry",
        int((time.monotonic() - g_started) * 1000),
        **_note_kwargs(grounding.reasons),
    )
    yield _trace_start("safety_critic")
    s_started = time.monotonic()
    safety = safety_review(explanation)
    yield _trace_finish(
        "safety_critic",
        "ok" if safety.passed else "fail",
        int((time.monotonic() - s_started) * 1000),
        **_note_kwargs(safety.reasons),
    )

    for piece in _stream_pieces(explanation):
        yield _format_sse("token", {"delta": piece})

    # 仅 RAG 命中时引用 PPT：有证据→回带证据卡 + 页码；无证据→不提 PPT。
    cited_pages = {int(m) for m in _CITATION_RE.findall(explanation)}
    has_valid_citation = bool(
        evidence.chunks
        and grounding.passed
        and cited_pages & {chunk.page_no for chunk in evidence.chunks}
    )
    if evidence.chunks:
        citations = TutorAgent._build_citations(evidence.chunks)
        evidence_list = [c.model_dump(mode="json") for c in citations]
        page_ids = [c.page_id for c in citations]
        grounding_status = "PASSED" if grounding.passed else "FAILED"
        based_on_general = False
    else:
        evidence_list = []
        page_ids = []
        grounding_status = "NO_EVIDENCE"
        based_on_general = True

    yield _format_sse("result", _build_quiz_result(
        answer_md=explanation,
        quiz={
            "phase": "judge",
            "round": round_no,
            "verdict": verdict,
            "cite_evidence": has_valid_citation,
        },
        page_ids=page_ids,
        evidence=evidence_list,
        grounding_status=grounding_status,
        based_on_general=based_on_general,
        safety=safety,
        profile_brief=body.profile_brief,
        grounding=grounding,
    ))


def _build_quiz_result(
    *,
    answer_md: str,
    quiz: dict[str, Any],
    page_ids: list[int],
    evidence: list[dict[str, Any]],
    grounding_status: str,
    based_on_general: bool,
    safety: Any,
    profile_brief: str = "",
    grounding: Any = None,
) -> dict[str, Any]:
    """组装考考我结果 payload：复用答疑结果的标准键（含 camelCase）+ 新增 ``quiz`` 结构（C4）。

    ``quiz``：
      - 出题 → ``{"phase":"ask","round":n,"stem":...,"blanks":[{"index","hint"}],"cite_evidence":false}``
      - 评卷 → ``{"phase":"judge","round":n,"verdict":"correct|partial|wrong","cite_evidence":bool}``
    其余键与 :func:`_build_result` 对齐，前端答案气泡/课件证据面板可零改动复用。
    """
    grounding_passed = grounding.passed if grounding is not None else True
    grounding_reasons = list(grounding.reasons) if grounding is not None else []
    return {
        "answer_md": answer_md,
        "evidence_page_ids": page_ids,
        "evidence": evidence,
        "grounding_result": {"passed": grounding_passed, "reasons": grounding_reasons},
        "safety_result": {"passed": safety.passed, "reasons": list(safety.reasons)},
        "based_on_general_knowledge": based_on_general,
        "grounding_status": grounding_status,
        "visual_aids": [],
        "evidence_note": "",
        "revised": False,
        "personalization_note": TutorAgent.personalization_note(profile_brief),
        "answerMd": answer_md,
        "evidencePageIds": page_ids,
        "groundingStatus": grounding_status,
        "review": {
            "grounding": {"passed": grounding_passed, "reasons": grounding_reasons},
            "hallucination": {"passed": True, "reasons": []},
            "safety": {"passed": safety.passed, "reasons": list(safety.reasons)},
        },
        "quiz": quiz,
    }
