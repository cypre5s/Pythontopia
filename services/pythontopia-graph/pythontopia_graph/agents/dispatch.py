"""内部调度层：把子 agent 暴露为进程内可 await 的协程，不经 HTTP 自调。

供 OrchestratorGraph._dispatch_step 真实调用子 agent，产物写入 DispatchResult，
替代旧「播报式」纯文本分派。
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
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceFetcher
from pythontopia_graph.schemas.orchestrator import DispatchResult, PlanStep
from pythontopia_graph.schemas.resource import ResourceDraft, ResourceProduceRequest, ResourceType

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


class AgentDispatcher:
    """把 PlanStep 路由到真实子 agent 并返回 DispatchResult。"""

    def __init__(self, ctx: DispatchContext) -> None:
        self._ctx = ctx

    async def dispatch(self, step: PlanStep, user_id: int = 1) -> DispatchResult:
        """真正 await 子 agent，产物写入 DispatchResult.artifact。

        Provider 错误不在此层捕获 —— 向上 propagate（fail-fast），
        由 OrchestratorGraph._node_dispatch 逐 step 捕获并转 SSE error + ok=False。
        """
        started = time.monotonic()
        agent_name = step.agent
        if agent_name == "DebugAgent":
            return await self._dispatch_debug(step, started)
        if agent_name == "PathPlannerAgent":
            return await self._dispatch_path_planner(step, started)
        if agent_name == "TutorAgent":
            return await self._dispatch_tutor(step, started)
        # 资源类 agent：StoryboardAgent / ResourceDesignerAgent / PracticeAgent
        return await self._dispatch_resource(step, user_id, started)

    async def _dispatch_resource(
        self, step: PlanStep, user_id: int, started: float
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

        agent = self._select_resource_agent(resource_type)
        evidence = await self._ctx.evidence_fetcher.fetch(
            kc_codes=step.kc_codes,
            query=step.title,
        )
        request = ResourceProduceRequest(
            user_id=user_id,
            kc_codes=step.kc_codes if step.kc_codes else ["PY43-KC-001"],
            type=resource_type,
            focus_hint=step.title,
        )
        draft: ResourceDraft = await agent.design(request, evidence)
        elapsed_ms = int((time.monotonic() - started) * 1000)
        section_count = (
            len(draft.handout.sections) if draft.handout
            else len(draft.practice.problems) if draft.practice
            else len(draft.storyboard.scenes) if draft.storyboard
            else len(draft.reading.items) if draft.reading
            else len(draft.code_lab.walkthroughs) if draft.code_lab
            else (1 if draft.mindmap else 0)
        )
        return DispatchResult(
            step_id=step.id,
            agent=step.agent,
            ok=True,
            summary=f"已生成「{draft.title}」({resource_type})",
            note=draft.title,
            elapsed_ms=elapsed_ms,
            artifact={
                "title": draft.title,
                "summary": draft.summary,
                "type": str(resource_type),
                "content_format": str(draft.content_format),
                "section_count": section_count,
            },
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
        kc_codes = step.kc_codes if step.kc_codes else ["PY43-KC-001"]
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

    async def _dispatch_path_planner(
        self, step: PlanStep, started: float
    ) -> DispatchResult:
        """PathPlannerAgent：确定性路径规划，用可用上下文构建最小请求并真调 agent。"""
        from pythontopia_graph.schemas.path import (
            PathPlannerRequest,
            PlannerKcSnapshot,
            PlannerProfileSnapshot,
        )

        kc_snapshots = [
            PlannerKcSnapshot(
                kc_code=code,
                mastery_score=0.5,
                unlocked=True,
            )
            for code in (step.kc_codes if step.kc_codes else ["PY43-KC-001"])
        ]
        try:
            request = PathPlannerRequest(
                user_id=1,
                pack_id=43,
                profile=PlannerProfileSnapshot(has_profile=False),
                kcs=kc_snapshots,
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
        except Exception as exc:
            elapsed_ms = int((time.monotonic() - started) * 1000)
            return DispatchResult(
                step_id=step.id,
                agent="PathPlannerAgent",
                ok=False,
                summary=f"路径规划失败: {exc}",
                note=str(exc),
                elapsed_ms=elapsed_ms,
            )

    async def _dispatch_tutor(
        self, step: PlanStep, started: float
    ) -> DispatchResult:
        """TutorAgent：真调用 ask() 产出答疑草稿，作为编排内协同一步。"""
        from pythontopia_graph.agents.tutor_agent import TutorAgent

        evidence = await self._ctx.evidence_fetcher.fetch(
            kc_codes=step.kc_codes,
            query=step.title,
        )
        question = f"{step.title}\n\n{step.detail}" if step.detail else step.title
        agent = TutorAgent(self._ctx.provider)
        response = await agent.ask(
            question=question,
            profile_brief="",  # orchestrator 无完整画像
            conversation_history=[],
            evidence_chunks=list(evidence) if evidence else [],
        )
        elapsed_ms = int((time.monotonic() - started) * 1000)
        return DispatchResult(
            step_id=step.id,
            agent="TutorAgent",
            ok=response.safety_result.passed,
            summary=f"答疑完成：{step.title}（{len(evidence)} 条课件证据"
                    + ("，已标注通识来源" if response.based_on_general_knowledge else "")
                    + "）",
            note=response.answer_md[:200] if response.answer_md else None,
            elapsed_ms=elapsed_ms,
            artifact={
                "kind": "tutor_answer",
                "topic": step.title,
                "evidence_count": len(evidence),
                "based_on_general_knowledge": response.based_on_general_knowledge,
                "safety_passed": response.safety_result.passed,
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
