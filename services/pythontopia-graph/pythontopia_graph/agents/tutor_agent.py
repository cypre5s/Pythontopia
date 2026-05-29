"""TutorAgent – conversational Q&A with RAG evidence and streaming."""

from __future__ import annotations

from collections.abc import AsyncIterator

from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import RagHit
from pythontopia_graph.schemas.tutor import GroundingResult, SafetyResult, TutorResponse

SYSTEM_PROMPT = (
    "你是 Pythontopia 智能答疑助教。\n"
    "规则：\n"
    "1. 用中文回答，Markdown 格式。\n"
    "2. 如果提供了课件证据，必须在答案中使用 [页 X] 格式引用。\n"
    '3. 如果没有课件证据，回答开头必须注明「基于通识知识」。\n'
    "4. 禁止伪造页码或章节。\n"
    "5. 根据学生画像调整语言难度。\n"
    "6. 不输出完整答案代码，只给引导和关键片段。"
)

SAFETY_KEYWORDS = ("answer_key", "hidden_test", "完整答案", "直接给我代码")


class TutorAgent:
    AGENT_NAME = "TutorAgent"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def ask(
        self,
        question: str,
        profile_brief: str,
        conversation_history: list[dict[str, str]],
        evidence_chunks: list[RagHit],
    ) -> TutorResponse:
        messages = self._build_messages(
            question, profile_brief, conversation_history, evidence_chunks,
        )
        response = await self.provider.chat(messages)
        answer = response.content_text or ""
        return self._build_response(answer, evidence_chunks)

    async def ask_stream(
        self,
        question: str,
        profile_brief: str,
        conversation_history: list[dict[str, str]],
        evidence_chunks: list[RagHit],
    ) -> AsyncIterator[str]:
        messages = self._build_messages(
            question, profile_brief, conversation_history, evidence_chunks,
        )
        async for token in self.provider.chat_stream(messages):
            yield token

    def _build_messages(
        self,
        question: str,
        profile_brief: str,
        conversation_history: list[dict[str, str]],
        evidence_chunks: list[RagHit],
    ) -> list[dict[str, str]]:
        messages: list[dict[str, str]] = [{"role": "system", "content": SYSTEM_PROMPT}]
        if profile_brief:
            messages.append({"role": "system", "content": f"学生画像: {profile_brief}"})
        if evidence_chunks:
            evidence_text = "\n---\n".join(
                f"[页 {c.page_no}] (page_id={c.page_id}): {c.text}"
                for c in evidence_chunks
            )
            messages.append({"role": "system", "content": f"课件证据:\n{evidence_text}"})
        else:
            messages.append({"role": "system", "content": "无课件证据可用，请标注「基于通识知识」。"})
        for turn in conversation_history:
            messages.append({"role": turn["role"], "content": turn["content"]})
        messages.append({"role": "user", "content": question})
        return messages

    def _build_response(
        self, answer: str, evidence_chunks: list[RagHit],
    ) -> TutorResponse:
        safety_failed = any(kw in answer for kw in SAFETY_KEYWORDS)
        page_ids = list({c.page_id for c in evidence_chunks})
        based_on_general = not evidence_chunks
        return TutorResponse(
            answer_md=answer,
            evidence_page_ids=page_ids,
            grounding_result=GroundingResult(
                passed=not based_on_general,
                reasons=["NO_EVIDENCE"] if based_on_general else [],
            ),
            safety_result=SafetyResult(
                passed=not safety_failed,
                reasons=["SAFETY_KEYWORD_DETECTED"] if safety_failed else [],
            ),
            based_on_general_knowledge=based_on_general,
        )
