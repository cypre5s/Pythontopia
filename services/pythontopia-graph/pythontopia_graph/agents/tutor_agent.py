"""TutorAgent – conversational Q&A with RAG evidence and streaming."""

from __future__ import annotations

import re
from collections.abc import AsyncIterator

from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import RagHit
from pythontopia_graph.schemas.tutor import (
    GroundingResult,
    SafetyResult,
    TutorResponse,
    TutorVisualAid,
)

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
        built = self._build_response(answer, evidence_chunks)
        if built.safety_result.passed:
            built.visual_aids = self.build_visual_aids(question, [])
        return built

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
            directive = self._cognitive_style_directive(profile_brief)
            if directive:
                messages.append({"role": "system", "content": directive})
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

    def build_visual_aids(
        self, question: str, kc_codes: list[str] | None = None,
    ) -> list[TutorVisualAid]:
        """为一次答疑构造多模态图解：一张文生图插画 + 一张结构 mermaid 图。

        mermaid 用确定式「概念→示例→易错→练习」教学结构，保证离线/无 key 时也能渲染；
        插画的 image_prompt 交给前端调讯飞文生图（无 key 自动占位）。
        """
        topic = self._short_topic(question)
        codes = kc_codes or []
        return [
            TutorVisualAid(
                kind="image",
                caption=f"AI 图解：{topic}",
                image_prompt=(
                    f"像素教学插画，{topic} 的概念图解，Python 教学场景，"
                    "暖色调，无真实人物，无文字水印"
                ),
                kc_codes=codes,
            ),
            TutorVisualAid(
                kind="mermaid",
                caption=f"知识结构图：{topic}",
                mermaid=self._fallback_mermaid(topic),
                kc_codes=codes,
            ),
        ]

    @staticmethod
    def _cognitive_style_directive(profile_brief: str | None) -> str | None:
        """因材施教：从 profile_brief 解析认知风格 → 首屏讲法指令（视觉/逻辑/试错）；无命中返回 None。

        最终仍受 SYSTEM_PROMPT 防泄答约束（不给完整答案，只给引导/关键片段）。
        """
        if not profile_brief:
            return None
        text = profile_brief.lower()
        if "visual" in text or "视觉" in text:
            return "该生为视觉型：先用图解/结构图（可给 mermaid 思路）建立直观，再配简短文字说明。"
        if "logical" in text or "逻辑" in text:
            return "该生为逻辑型：先给清晰的推理链/步骤拆解，再总结要点。"
        if "trial" in text or "试错" in text:
            return "该生为试错型：先给一个最小可运行的示例片段（非完整答案）让其上手，再点拨原理。"
        return None

    @staticmethod
    def _short_topic(question: str) -> str:
        cleaned = re.sub(r'["\'\[\]{}()<>|]', "", (question or "").strip())
        cleaned = cleaned.replace("\n", " ").strip()
        if len(cleaned) > 24:
            cleaned = cleaned[:24] + "…"
        return cleaned or "本题概念"

    @staticmethod
    def _fallback_mermaid(topic: str) -> str:
        label = topic.replace('"', "")
        return "\n".join(
            [
                "flowchart TD",
                f'  Q["{label}"] --> A["核心概念"]',
                f'  Q["{label}"] --> B["典型示例"]',
                f'  Q["{label}"] --> C["常见易错"]',
                '  A["核心概念"] --> D["动手练习"]',
                '  B["典型示例"] --> D["动手练习"]',
                '  C["常见易错"] --> D["动手练习"]',
            ]
        )
