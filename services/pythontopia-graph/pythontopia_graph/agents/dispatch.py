"""内部调度层：把子 agent 暴露为进程内可 await 的协程，不经 HTTP 自调。

供 OrchestratorGraph._dispatch_step 真实调用子 agent，产物写入 DispatchResult，
替代旧「播报式」纯文本分派。dispatch 内的 LLM 子调用（资源 design / tutor ask）
一律经 :func:`run_with_governance` 包装：超时/异常/熔断/预算失败转 ok=False 的
DispatchResult（artifact.kind=dispatch_failure），不抛裸异常打断编排。
"""

from __future__ import annotations

import time
from dataclasses import dataclass

from pythontopia_graph.agents.code_lab_agent import CodeLabAgent
from pythontopia_graph.agents.handout_agent import HandoutAgent
from pythontopia_graph.agents.mind_map_agent import MindMapAgent
from pythontopia_graph.agents.path_planner_agent import PathPlannerAgent
from pythontopia_graph.agents.practice_agent import PracticeAgent
from pythontopia_graph.agents.reading_agent import ReadingAgent
from pythontopia_graph.agents.storyboard_agent import StoryboardAgent
from pythontopia_graph.graph.resource_graph import (
    ResourceGraph,
    ResourceGraphConfig,
    ResourceGraphError,
)
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceFetcher
from pythontopia_graph.schemas.orchestrator import DispatchResult, PlanStep
from pythontopia_graph.schemas.intent import (
    LearnerRoutingContext,
    WorkshopInteractionContextRef,
)
from pythontopia_graph.schemas.resource import (
    GraphTraceStep,
    ResourceDraft,
    ResourceProduceRequest,
    ResourceProduceResponse,
    ResourceType,
)
from pythontopia_graph.tools.harness import ToolObservation, run_with_governance

# 编排内 LLM 子调用的治理参数：单步上限 120s、不重试（失败转 ok=False 由总管聚合）。
_GOVERNANCE_TIMEOUT_S = 120.0

# learn 阶段「有界反思」（OrchestratorGraph reflect 节点，N=1）的资源类型降级链：
# 复杂 → 简单。链尾 READING 不再降级（返回 None → 不重派，杜绝无界重试）。
_LEARN_DOWNGRADE_CHAIN: dict[str, str] = {
    "CODE_LAB": "HANDOUT",
    "HANDOUT": "READING",
}

# PlanStep.agent → 确定性子 agent 映射。
_AGENT_MAP: dict[str, str | None] = {
    "StoryboardAgent": "STORYBOARD",
    "ResourceDesignerAgent": None,  # 由 step.resource_type 决定
    "PracticeAgent": "PRACTICE",
    "PathPlannerAgent": None,  # 确定性 agent，不走 LLM
    "DebugAgent": None,
    "TutorAgent": None,
}


@dataclass
class DispatchContext:
    """调度器所需的外部依赖（由 OrchestratorGraph 注入）。"""

    provider: LlmProvider
    evidence_fetcher: EvidenceFetcher
    valid_kc_codes: set[str]
    # 本轮请求的学生画像简报（OrchestratorRequest.profile_brief），真实透传给子 agent。
    profile_brief: str = ""
    # 仅由 Java 端按当前用户校验并从数据库回填；客户端原始 code/error 不得直达这里。
    debug_context: WorkshopInteractionContextRef | None = None
    learner_context: LearnerRoutingContext | None = None


class AgentDispatcher:
    """把 PlanStep 路由到真实子 agent 并返回 DispatchResult。"""

    def __init__(self, ctx: DispatchContext) -> None:
        self._ctx = ctx

    async def dispatch(
        self, step: PlanStep, user_id: int = 1, focus_hint_extra: str = ""
    ) -> DispatchResult:
        """真正 await 子 agent，产物写入 DispatchResult.artifact。

        ``focus_hint_extra``：前序步骤工件摘要（如 learn 讲义要点），追加进资源类
        步骤的 focus_hint 实现步骤间协作。LLM 子调用经 run_with_governance 包装，
        观测失败转 ok=False；其余意外异常仍向上抛，由
        OrchestratorGraph._node_dispatch 逐 step 捕获并转 SSE error + ok=False。
        """
        started = time.monotonic()
        if not step.kc_codes:
            # 绝不用硬编码的 PY43-KC-001 兜住缺失的知识点锚：那会产出一份被标注成
            # KC-001 的伪造讲义并入库，而 hallucination_detector 因 KC-001 合法而放行。
            # 路由侧 required_context 闸门本该先澄清；漏到这里必须结构化失败。
            return self._missing_kc_failure(step, started)
        agent_name = step.agent
        if agent_name == "DebugAgent":
            return await self._dispatch_debug(step, started)
        if agent_name == "PathPlannerAgent":
            return await self._dispatch_path_planner(step, user_id, started)
        if agent_name == "TutorAgent":
            return await self._dispatch_tutor(step, started)
        # 资源类 agent：StoryboardAgent / ResourceDesignerAgent / PracticeAgent
        return await self._dispatch_resource(step, user_id, started, focus_hint_extra)

    def _missing_kc_failure(self, step: PlanStep, started: float) -> DispatchResult:
        """缺知识点锚 → 结构化失败（不伪造 KC，不生成任何会入库的资源）。"""
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=False,
            summary=f"{step.title} 缺少知识点锚定，未执行（不伪造课件引用）",
            note="missing_required_context:topic_or_kc",
            elapsed_ms=int((time.monotonic() - started) * 1000),
            artifact={
                "kind": "dispatch_failure",
                "status": "missing_required_context",
                "error_class": "MissingKnowledgeAnchor",
                "missing": "topic_or_kc",
            },
        )

    async def _dispatch_resource(
        self, step: PlanStep, user_id: int, started: float, focus_hint_extra: str = ""
    ) -> DispatchResult:
        """资源生成类 agent：StoryboardAgent / ResourceDesignerAgent / PracticeAgent。"""
        resource_type = self._resolve_resource_type(step)
        if resource_type is None:
            elapsed_ms = int((time.monotonic() - started) * 1000)
            return DispatchResult(
                step_id=step.id,
                agent=step.agent,
                ok=True,
                summary=f"资源类型未指定，跳过生成（{step.title}）",
                elapsed_ms=elapsed_ms,
            )

        request = ResourceProduceRequest(
            user_id=user_id,
            kc_codes=step.kc_codes,
            type=resource_type,
            focus_hint=f"{step.title}{focus_hint_extra}"[:512],
            profile_snapshot=self._resource_profile_snapshot(),
        )
        graph = ResourceGraph(
            provider=self._ctx.provider,
            evidence_fetcher=self._ctx.evidence_fetcher,
            config=ResourceGraphConfig(valid_kc_codes=self._ctx.valid_kc_codes),
        )
        try:
            response = await graph.run(request)
        except ResourceGraphError as exc:
            return self._resource_graph_failure(step, exc.response, started, str(exc))
        except Exception as exc:  # noqa: BLE001 - dispatcher must return structured failure
            elapsed_ms = int((time.monotonic() - started) * 1000)
            return DispatchResult(
                step_id=step.id,
                agent=step.agent,
                ok=False,
                summary=f"{step.title} 执行失败（error）",
                note=exc.__class__.__name__,
                elapsed_ms=elapsed_ms,
                artifact={
                    "kind": "dispatch_failure",
                    "status": "error",
                    "error_class": exc.__class__.__name__,
                },
            )

        draft = response.artifact
        elapsed_ms = int((time.monotonic() - started) * 1000)
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=True,
            summary=f"已生成「{draft.title}」({resource_type})",
            note=draft.title,
            elapsed_ms=elapsed_ms,
            artifact=self._resource_graph_artifact(response),
        )

    async def _dispatch_debug(
        self, step: PlanStep, started: float
    ) -> DispatchResult:
        """DebugAgent 编排步：产出确定性的调试补练规划。

        说明：完整 `DebugAgent.analyse` 需要真实失败提交（submission_id/problem_id/
        verdict/code/counter_example），编排规划阶段并无这些数据，**不得**伪造
        counter_example 去硬调 analyse（违反「无假数据」红线，旧实现因此恒抛
        ValidationError 又被吞成假 ok）。这里返回如实的「去矿洞针对 KC 补练」确定性
        规划结果：ok=True、有实质摘要、不谎称已分析代码。学生进入矿洞产生真实失败
        提交后，再由 DebugAgent 走真实分析链。
        """
        kc_codes = step.kc_codes
        context = self._ctx.debug_context
        if (
            context is not None
            and context.latest_submission_id
            and context.current_problem_id
            and context.current_code.strip()
            and context.error_text.strip()
        ):
            return self._verified_debug_diagnosis(step, context, kc_codes, started)
        elapsed_ms = int((time.monotonic() - started) * 1000)
        kc_label = "、".join(kc_codes)
        return DispatchResult(
            step_id=step.id,
            agent="DebugAgent",
            ok=True,
            summary=f"已规划调试补练：进入错题矿洞针对 {kc_label} 修 Bug（{len(kc_codes)} 个 KC）",
            note=step.detail or None,
            elapsed_ms=elapsed_ms,
            artifact={
                "kind": "debug_plan",
                "kc_codes": kc_codes,
                "panel": step.panel or "workshop",
            },
        )

    @staticmethod
    def _verified_debug_diagnosis(
        step: PlanStep,
        context: WorkshopInteractionContextRef,
        kc_codes: list[str],
        started: float,
    ) -> DispatchResult:
        """Diagnose only from backend-verified submission evidence; never invent a counterexample."""
        error = context.error_text.strip()
        lowered = error.lower()
        if "syntaxerror" in lowered or lowered.startswith("ce"):
            category, remediation = "语法/静态检查", "先定位报错行与括号、缩进、禁用语法"
        elif "typeerror" in lowered:
            category, remediation = "类型不匹配", "逐项核对参与运算的变量类型与转换位置"
        elif "indexerror" in lowered or "off_by_one" in lowered:
            category, remediation = "边界错误", "核对 range 右边界、下标起点和循环次数"
        elif lowered.startswith("tle") or "time limit" in lowered:
            category, remediation = "超时/终止条件", "核对循环终止条件与重复计算量"
        elif lowered.startswith("wa") or "wrong answer" in lowered:
            category, remediation = "答案不一致", "从输出格式、循环边界和累积变量三个方向做最小反例复核"
        else:
            category, remediation = "运行失败", "沿判题错误末行回溯到首个异常变量"
        code_lines = [line for line in context.current_code.splitlines() if line.strip()]
        suspicious_line = max(1, len(code_lines))
        elapsed_ms = int((time.monotonic() - started) * 1000)
        summary = (
            f"已基于失败提交 #{context.latest_submission_id} 完成诊断：{category}；{remediation}"
        )
        return DispatchResult(
            step_id=step.id,
            agent="DebugAgent",
            ok=True,
            summary=summary,
            note=error[:200],
            elapsed_ms=elapsed_ms,
            artifact={
                "kind": "verified_debug_diagnosis",
                "title": "失败提交诊断",
                "summary": f"{category}：{remediation}",
                "submission_id": context.latest_submission_id,
                "problem_id": context.current_problem_id,
                "error_category": category,
                "error_excerpt": error[:300],
                "suspicious_line": suspicious_line,
                "kc_codes": kc_codes,
                "counter_example_available": False,
            },
        )

    async def _dispatch_path_planner(
        self, step: PlanStep, user_id: int, started: float
    ) -> DispatchResult:
        """PathPlannerAgent：确定性路径规划，用可用上下文构建最小请求并真调 agent。"""
        from pythontopia_graph.schemas.path import (
            PathPlannerRequest,
            PlannerKcSnapshot,
            PlannerProblemSubmission,
            PlannerProfileSnapshot,
            PlannerResourceUsage,
        )

        context = self._ctx.learner_context
        kc_snapshots = [
            PlannerKcSnapshot(
                kc_code=code,
                mastery_score=(context.kc_mastery.get(code, 0.0) if context else 0.0),
                confidence=(context.kc_confidence.get(code, 0.0) if context else 0.0),
                unlocked=True,
            )
            for code in step.kc_codes
        ]
        try:
            request = PathPlannerRequest(
                user_id=user_id,
                pack_id=43,
                profile=PlannerProfileSnapshot(
                    has_profile=bool(context and context.profile_brief),
                    major=context.profile_major if context else "OTHER",
                    python_self_rated_level=context.python_level if context else 2,
                    days_since_last_profile_update=0 if context and context.profile_brief else 999,
                ),
                kcs=kc_snapshots,
                recent_resources=[
                    PlannerResourceUsage(
                        kc_code=item.kc_code,
                        type=item.type,
                        created_at=item.created_at,
                    )
                    for item in (context.recent_resources if context else [])
                ],
                recent_submissions=[
                    PlannerProblemSubmission(
                        problem_id=item.problem_id,
                        kc_code=item.kc_code,
                        passed=item.passed,
                    )
                    for item in (context.recent_submissions if context else [])
                ],
                error_pattern_kcs=context.recent_error_kcs if context else [],
            )
            response = PathPlannerAgent().plan(request)
            elapsed_ms = int((time.monotonic() - started) * 1000)
            return DispatchResult(
                step_id=step.id,
                agent="PathPlannerAgent",
                ok=response.status != "FAILED",
                summary=f"路径规划{response.status}：{response.goal_summary}（{len(response.nodes)} 节点）",
                note=response.goal_summary,
                elapsed_ms=elapsed_ms,
                artifact={
                    "status": response.status,
                    "goal_summary": response.goal_summary,
                    "node_count": len(response.nodes),
                },
            )
        except Exception:
            elapsed_ms = int((time.monotonic() - started) * 1000)
            return DispatchResult(
                step_id=step.id,
                agent="PathPlannerAgent",
                ok=False,
                summary="路径规划失败，请稍后重试",
                note="PathPlannerAgent failed.",
                elapsed_ms=elapsed_ms,
            )

    def _resource_profile_snapshot(self):  # type: ignore[no-untyped-def]
        """Map the same authoritative context into the existing resource-agent profile schema."""
        from pythontopia_graph.schemas.resource import ProfileSnapshot

        context = self._ctx.learner_context
        if context is None:
            return ProfileSnapshot()
        return ProfileSnapshot(
            major=context.profile_major,
            python_self_rated_level=context.python_level,
            media_priority=context.learning_preferences,
            cognitive_style=context.cognitive_style,
            error_patterns=context.recent_error_kcs,
            recent_insight=context.struggle_patterns[0] if context.struggle_patterns else "",
            memory_highlights=[*context.struggle_patterns, *context.recent_wins][:8],
            memory_directives=context.intervention_hints,
        )

    async def _dispatch_tutor(
        self, step: PlanStep, started: float
    ) -> DispatchResult:
        """TutorAgent：真调用 ask() 产出答疑草稿，作为编排内协同一步。

        画像经 DispatchContext.profile_brief 真实透传（不再写死空串）。
        """
        from pythontopia_graph.agents.tutor_agent import TutorAgent

        evidence = await self._ctx.evidence_fetcher.fetch(
            kc_codes=step.kc_codes,
            query=step.title,
        )
        chunks = list(evidence.chunks)
        question = f"{step.title}\n\n{step.detail}" if step.detail else step.title
        agent = TutorAgent(self._ctx.provider)
        observation = await run_with_governance(
            tool=f"orchestrator.{step.agent}",
            factory=lambda: agent.ask(
                question=question,
                profile_brief=self._ctx.profile_brief,
                conversation_history=[],
                evidence_chunks=chunks,
            ),
            breaker_key="provider",
            timeout_s=_GOVERNANCE_TIMEOUT_S,
            max_retries=0,
        )
        if not observation.ok:
            return self._governed_failure(step, observation, started)
        response = observation.value
        elapsed_ms = int((time.monotonic() - started) * 1000)
        return DispatchResult(
            step_id=step.id,
            agent="TutorAgent",
            ok=response.safety_result.passed,
            summary=f"答疑完成：{step.title}（{len(chunks)} 条课件证据"
                    + ("，已标注通识来源" if response.based_on_general_knowledge else "")
                    + "）",
            note=response.answer_md[:200] if response.answer_md else None,
            elapsed_ms=elapsed_ms,
            artifact={
                "kind": "tutor_answer",
                "topic": step.title,
                "evidence_count": len(chunks),
                "based_on_general_knowledge": response.based_on_general_knowledge,
                "safety_passed": response.safety_result.passed,
            },
        )

    @staticmethod
    def _governed_failure(
        step: PlanStep, observation: ToolObservation, started: float
    ) -> DispatchResult:
        """治理观测失败（timeout/error/circuit_open/budget_skipped）→ ok=False 结构化结果。

        artifact.kind=dispatch_failure 供 OrchestratorGraph 识别为「执行类失败」
        （区别于子 agent 诚实返回的业务性 FAILED），沿用 SSE error + ok=False 上报模式。
        """
        elapsed_ms = int((time.monotonic() - started) * 1000)
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=False,
            summary=f"{step.title} 执行失败（{observation.status}）",
            note=observation.error_class,
            elapsed_ms=elapsed_ms,
            artifact={
                "kind": "dispatch_failure",
                "status": observation.status,
                "error_class": observation.error_class,
            },
        )

    def _resolve_resource_type(self, step: PlanStep) -> ResourceType | None:
        """从 PlanStep 解析资源类型。"""
        if step.agent == "StoryboardAgent":
            return "STORYBOARD"
        if step.agent == "PracticeAgent":
            return "PRACTICE"
        if step.resource_type:
            rt = step.resource_type.upper()
            if rt in {"HANDOUT", "MINDMAP", "PRACTICE", "CODE_LAB", "READING", "STORYBOARD"}:
                return rt  # type: ignore[return-value]
        # ResourceDesignerAgent 但无 resource_type → 按 phase 推断
        if step.phase == "watch":
            return "STORYBOARD"
        if step.phase == "practice":
            return "PRACTICE"
        return "HANDOUT"  # learn 默认讲义

    def _resource_graph_failure(
        self, step: PlanStep, response: ResourceProduceResponse, started: float, message: str
    ) -> DispatchResult:
        elapsed_ms = int((time.monotonic() - started) * 1000)
        artifact = self._resource_graph_artifact(response)
        artifact["kind"] = "resource_graph_failure"
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=False,
            summary=f"ResourceGraph 审查未通过：{message}",
            note=message,
            elapsed_ms=elapsed_ms,
            artifact=artifact,
        )

    @staticmethod
    def _resource_graph_artifact(response: ResourceProduceResponse) -> dict[str, object]:
        draft = response.artifact
        return {
            "kind": "resource_graph_result",
            "title": draft.title,
            "summary": draft.summary,
            "type": str(response.type),
            "content_format": str(draft.content_format),
            "section_count": AgentDispatcher._resource_section_count(draft),
            "graph_status": response.status,
            "evidence_page_ids": list(response.evidence_page_ids),
            "grounding_passed": response.grounding_result.passed,
            "grounding_reasons": list(response.grounding_result.reasons),
            "safety_passed": response.safety_result.passed,
            "safety_reasons": list(response.safety_result.reasons),
            "graph_trace": AgentDispatcher._trace_payload(response.graph_trace),
        }

    @staticmethod
    def _trace_payload(trace: list[GraphTraceStep]) -> list[dict[str, object]]:
        return [step.model_dump(mode="json") for step in trace]

    @staticmethod
    def _resource_section_count(draft: ResourceDraft) -> int:
        if draft.handout:
            return len(draft.handout.sections)
        if draft.practice:
            return len(draft.practice.problems)
        if draft.storyboard:
            return len(draft.storyboard.scenes)
        if draft.reading:
            return len(draft.reading.items)
        if draft.code_lab:
            return len(draft.code_lab.walkthroughs)
        return 1 if draft.mindmap else 0

    def downgraded_learn_step(self, step: PlanStep) -> PlanStep | None:
        """把一个 learn 资源步沿 CODE_LAB→HANDOUT→READING 降一档后返回副本。

        供 OrchestratorGraph 的有界反思（A3 · N=1）使用：learn 步执行类失败时以更
        简单的资源类型经本 dispatcher 重派一次。降级源取**已解析**的资源类型
        （``_resolve_resource_type``），故 ``resource_type=None`` 的 learn 步按其默认
        （HANDOUT）入链。已是链尾 READING、或解析结果不在降级链上（如 STORYBOARD /
        PRACTICE / MINDMAP）→ 返回 ``None``（不重派）。仅改 ``resource_type``，
        ``agent`` 等其余字段保持不变（沿用原步身份与 step_id）。
        """
        current = self._resolve_resource_type(step)
        downgraded = _LEARN_DOWNGRADE_CHAIN.get(current) if current else None
        if downgraded is None:
            return None
        return step.model_copy(update={"resource_type": downgraded})

    def _select_resource_agent(
        self, resource_type: ResourceType
    ) -> HandoutAgent | PracticeAgent | CodeLabAgent | ReadingAgent | StoryboardAgent | MindMapAgent:
        if resource_type == "HANDOUT":
            return HandoutAgent(self._ctx.provider)
        if resource_type == "MINDMAP":
            return MindMapAgent(self._ctx.provider)
        if resource_type == "PRACTICE":
            return PracticeAgent(self._ctx.provider)
        if resource_type == "CODE_LAB":
            return CodeLabAgent(self._ctx.provider)
        if resource_type == "READING":
            return ReadingAgent(self._ctx.provider)
        if resource_type == "STORYBOARD":
            return StoryboardAgent(self._ctx.provider)
        raise ValueError(f"Unsupported resource type: {resource_type}")
