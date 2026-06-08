"""Solver→Critic→Referee 辩论复核子图。

难题 / 低置信答疑时，先让 Solver 产出草答，Critic 挑错/找反例/查证据缺口，
Referee 综合定稿并给出 confidence。任一步异常一律回退 None，由调用方走普通答疑（不抛）。

全程沿用防泄答红线：只产出引导，不给可直接提交的完整答案；有证据须依据证据，不编造页码。
本仓「graph」沿用 profile_graph 的「纯异步 node 函数编排」风格（非 langgraph StateGraph），
保持轻量、易测、与既有约定一致。
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from dataclasses import dataclass

from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import RagHit

# critic 无异议时 referee 直接采纳 solver 的默认置信度。
_ADOPT_CONFIDENCE = 0.85

_SOLVER_SYSTEM = (
    "你是 Pythontopia 答疑 Solver。针对难题给出一版**草答**："
    "用中文、Markdown；有课件证据须用 [页 X] 引用，无证据须标注「基于通识知识」；"
    "只给引导与关键片段，禁止给出可直接提交的完整答案。"
    '只输出 JSON 对象，schema: {"draft": "<草答 Markdown>"}。'
)

_CRITIC_SYSTEM = (
    "你是 Pythontopia 答疑 Critic。审阅 Solver 的草答：找逻辑漏洞、反例、边界情况、"
    "与课件证据不一致或证据缺口。不要重写答案，只指出问题。"
    '只输出 JSON 对象，schema: {"has_objection": <bool>, "critique": "<一句话问题，无问题留空>"}。'
)

_REFEREE_SYSTEM = (
    "你是 Pythontopia 答疑 Referee。综合 Solver 草答与 Critic 意见，给出复核后的定稿："
    "中文、Markdown，沿用证据引用与防泄答红线（不给完整答案）。"
    '只输出 JSON 对象，schema: {"answer": "<定稿 Markdown>", "confidence": <0~1 浮点>}。'
)


@dataclass
class DebateRound:
    """一轮发言：role ∈ {solver, critic, referee}，content 为该轮可读内容。"""

    role: str
    content: str


@dataclass
class DebateResult:
    answer: str
    rounds: list[DebateRound]
    confidence: float


class DebateGraph:
    """Solver→Critic→Referee 复核子图（异常回退 None）。"""

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def run(
        self,
        question: str,
        evidence_chunks: list[RagHit],
        profile_brief: str = "",
    ) -> DebateResult | None:
        """跑完整复核，仅返回终态 DebateResult（异常回退 None）；内部委托 run_events 保持单一逻辑源。"""
        result: DebateResult | None = None
        async for kind, payload in self.run_events(question, evidence_chunks, profile_brief):
            if kind == "result":
                result = payload if isinstance(payload, DebateResult) else None
        return result

    async def run_events(
        self,
        question: str,
        evidence_chunks: list[RagHit],
        profile_brief: str = "",
    ) -> AsyncIterator[tuple[str, str | DebateResult | None]]:
        """逐节点产出事件，供路由发真实 solver/critic/referee start/finish trace。

        事件序列：(node_start, name) → (node_finish, name) …，末尾 (result, DebateResult|None)。
        任一步异常 → 末尾产出 (result, None)（此前 start 未 finish 的节点由调用方补 error finish）。
        """
        try:
            yield ("node_start", "solver")
            solver_text = await self._solver(question, evidence_chunks, profile_brief)
            yield ("node_finish", "solver")

            yield ("node_start", "critic")
            has_objection, critique = await self._critic(question, solver_text, evidence_chunks)
            yield ("node_finish", "critic")

            rounds = [
                DebateRound("solver", solver_text),
                DebateRound("critic", critique),
            ]

            yield ("node_start", "referee")
            if has_objection:
                answer, confidence = await self._referee(
                    question, solver_text, critique, evidence_chunks,
                )
            else:
                # critic 无异议 → referee 直接采纳 solver，不再消耗一次 LLM 调用。
                answer, confidence = solver_text, _ADOPT_CONFIDENCE
            rounds.append(DebateRound("referee", answer))
            yield ("node_finish", "referee")

            yield ("result", DebateResult(answer=answer, rounds=rounds, confidence=confidence))
        except Exception:  # noqa: BLE001 - 子图边界：任一步失败回退普通答疑，绝不让难题崩答疑
            yield ("result", None)

    async def _solver(
        self, question: str, evidence_chunks: list[RagHit], profile_brief: str,
    ) -> str:
        messages: list[dict[str, str]] = [{"role": "system", "content": _SOLVER_SYSTEM}]
        if profile_brief:
            messages.append({"role": "system", "content": f"学生画像: {profile_brief}"})
        evidence_message = self._evidence_message(evidence_chunks)
        if evidence_message is not None:
            messages.append(evidence_message)
        messages.append({"role": "user", "content": question})
        response = await self.provider.chat(messages)
        draft = self._text_field(response.content_json, "draft")
        return draft or (response.content_text or "").strip()

    async def _critic(
        self, question: str, solver_text: str, evidence_chunks: list[RagHit],
    ) -> tuple[bool, str]:
        messages: list[dict[str, str]] = [{"role": "system", "content": _CRITIC_SYSTEM}]
        evidence_message = self._evidence_message(evidence_chunks)
        if evidence_message is not None:
            messages.append(evidence_message)
        messages.append(
            {"role": "user", "content": f"问题：{question}\n\nSolver 草答：\n{solver_text}"},
        )
        response = await self.provider.chat(messages)
        data = response.content_json or {}
        has_objection = bool(data.get("has_objection", False))
        critique = str(data.get("critique", "") or "").strip()
        return has_objection, critique

    async def _referee(
        self,
        question: str,
        solver_text: str,
        critique: str,
        evidence_chunks: list[RagHit],
    ) -> tuple[str, float]:
        messages: list[dict[str, str]] = [{"role": "system", "content": _REFEREE_SYSTEM}]
        evidence_message = self._evidence_message(evidence_chunks)
        if evidence_message is not None:
            messages.append(evidence_message)
        messages.append(
            {
                "role": "user",
                "content": (
                    f"问题：{question}\n\nSolver 草答：\n{solver_text}\n\nCritic 意见：\n{critique}"
                ),
            },
        )
        response = await self.provider.chat(messages)
        data = response.content_json or {}
        answer = str(data.get("answer", "") or "").strip() or solver_text
        confidence = self._clamp_confidence(data.get("confidence"))
        return answer, confidence

    @staticmethod
    def _evidence_message(evidence_chunks: list[RagHit]) -> dict[str, str] | None:
        if not evidence_chunks:
            return None
        evidence_text = "\n---\n".join(
            f"[页 {chunk.page_no}] (page_id={chunk.page_id}): {chunk.text}"
            for chunk in evidence_chunks
        )
        return {"role": "system", "content": f"课件证据:\n{evidence_text}"}

    @staticmethod
    def _text_field(content_json: dict[str, object] | None, key: str) -> str:
        if isinstance(content_json, dict):
            value = content_json.get(key)
            if isinstance(value, str):
                return value.strip()
        return ""

    @staticmethod
    def _clamp_confidence(raw: object) -> float:
        try:
            value = float(raw)  # type: ignore[arg-type]
        except (TypeError, ValueError):
            return _ADOPT_CONFIDENCE
        return max(0.0, min(1.0, value))
