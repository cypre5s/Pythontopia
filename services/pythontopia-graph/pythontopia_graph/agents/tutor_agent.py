"""TutorAgent：基于 RAG 课件证据的流式答疑；无证据须标注通识来源，禁止伪造页码与完整答案。"""

from __future__ import annotations

import re
from collections.abc import AsyncIterator

from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import RagHit
from pythontopia_graph.schemas.tutor import (
    EvidenceCitation,
    GroundingResult,
    SafetyResult,
    TutorResponse,
    TutorVisualAid,
)

# 「课件证据」片段展示上限：保证面板里每条片段简短、整齐。
_SNIPPET_MAX_CHARS = 160

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

# 「要图解」的语句触发词（多字 token，避免裸「图」误伤「图书馆」等）。
DIAGRAM_KEYWORDS = (
    "图解",
    "画图",
    "画个",
    "画一",
    "画张",
    "流程图",
    "结构图",
    "思维导图",
    "示意图",
    "关系图",
    "对比图",
    "图示",
    "用图",
    "diagram",
    "mermaid",
)


class TutorAgent:
    """RAG 答疑助教：流式/非流式问答，按需附加 mermaid 图解；泄答关键词触发 safety 失败。"""

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
        if built.safety_result.passed and self.wants_diagram(question):
            built.visual_aids = await self.build_diagram(question, evidence_chunks)
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
        citations = self._build_citations(evidence_chunks)
        # page_ids 由 citations 去重投影，保证与「课件证据」面板同序、同集合。
        page_ids = [c.page_id for c in citations]
        based_on_general = not evidence_chunks
        return TutorResponse(
            answer_md=answer,
            evidence_page_ids=page_ids,
            evidence=citations,
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

    @staticmethod
    def _build_citations(evidence_chunks: list[RagHit]) -> list[EvidenceCitation]:
        """把 RAG 命中按 page_id 去重（保留首条＝最高分）后投影成可展示的课件证据。

        让学生「看见系统命中的证据」：每个证据页给出页码 + 原文片段（折叠空白 + 截断）。
        """
        seen: set[int] = set()
        citations: list[EvidenceCitation] = []
        for chunk in evidence_chunks:
            if chunk.page_id in seen:
                continue
            seen.add(chunk.page_id)
            snippet = re.sub(r"\s+", " ", chunk.text or "").strip()
            if len(snippet) > _SNIPPET_MAX_CHARS:
                snippet = snippet[:_SNIPPET_MAX_CHARS].rstrip() + "…"
            citations.append(
                EvidenceCitation(
                    page_id=chunk.page_id,
                    page_no=chunk.page_no,
                    snippet=snippet,
                    score=round(float(chunk.score), 3),
                )
            )
        return citations

    async def summarize_evidence(
        self,
        question: str,
        context: str,
        profile_brief: str,
        evidence_chunks: list[RagHit],
    ) -> str:
        """生成「整组课件证据」的一句话总览说明（结合提问/上下文/画像）。

        供「课件证据」面板把图片化的课件页配上一句贴合该生的说明：这些页和他的问题
        有什么关系、能帮他理解什么、按画像给一句提示。best-effort——任何失败/空结果都
        返回空串，绝不阻塞答疑主流程；无证据时不生成。
        """
        if not evidence_chunks:
            return ""
        evidence_lines: list[str] = []
        for chunk in evidence_chunks[:3]:
            snippet = re.sub(r"\s+", " ", chunk.text or "").strip()[:200]
            evidence_lines.append(f"[页 {chunk.page_no}] {snippet}")
        evidence_text = "\n".join(evidence_lines)
        messages: list[dict[str, str]] = [
            {
                "role": "system",
                "content": (
                    "你是 Pythontopia 答疑助教。请用一句话（不超过 60 个汉字、中文）向这位学生说明："
                    "下面这些课件页与他的问题有什么关系、能帮他理解什么；若画像体现了认知风格/"
                    "知识基础/易错点，请据此给一句贴合的提示。只输出 JSON 对象，"
                    'schema: {"note": "<一句话说明>"}；不要寒暄、不要逐页罗列页码。'
                ),
            }
        ]
        if profile_brief:
            messages.append({"role": "system", "content": f"学生画像: {profile_brief}"})
        user = f"学生问题：{question}"
        if context:
            user += f"\n当前学习上下文：{context}"
        user += f"\n课件证据：\n{evidence_text}"
        messages.append({"role": "user", "content": user})
        try:
            response = await self.provider.chat(messages)
        except Exception:  # noqa: BLE001 - best-effort，失败不阻塞答疑
            return ""
        data = response.content_json
        if isinstance(data, dict) and isinstance(data.get("note"), str):
            return data["note"].strip()
        text = (response.content_text or "").strip()
        return text[:120]

    @staticmethod
    def wants_diagram(question: str) -> bool:
        """学生是否在语句里明确要「图/流程/结构图」——图解仅按需触发，不再每答必带。"""
        text = (question or "").lower()
        return any(token in text for token in DIAGRAM_KEYWORDS)

    async def build_diagram(
        self, question: str, evidence_chunks: list[RagHit] | None = None,
    ) -> list[TutorVisualAid]:
        """按需生成一张**针对该主题**的 mermaid 结构图（LLM + 课件证据，确定式兜底）。

        不再生成易失败、且「泛泛而谈」的文生图插画；只产出一张结构清晰、贴合主题的
        mermaid。LLM 失败/产出不合法时回退到确定式结构，保证永远可渲染、不报「生成失败」。
        """
        topic = self._short_topic(question)
        mermaid = await self._llm_mermaid(question, evidence_chunks or [])
        if not mermaid:
            mermaid = self._fallback_mermaid(topic)
        return [
            TutorVisualAid(
                kind="mermaid",
                caption=f"知识结构图：{topic}",
                mermaid=mermaid,
                kc_codes=[],
            )
        ]

    async def _llm_mermaid(
        self, question: str, evidence_chunks: list[RagHit],
    ) -> str | None:
        """调 LLM 产出一段贴合主题的 mermaid flowchart；非法/失败回 None。

        provider.chat() 走的是 JSON 模式（DeepSeek 等要求 prompt 含 "json" 且输出 JSON），
        故让模型把 mermaid 源码放进 ``{"mermaid": "..."}`` 返回，再抽取 + 校验。
        """
        messages: list[dict[str, str]] = [
            {
                "role": "system",
                "content": (
                    "你是 Python 教学图解生成器，只输出 JSON 对象，"
                    'schema: {"mermaid": "<Mermaid flowchart 源码>"}。要求：\n'
                    "1) mermaid 第一行必须是 `flowchart TD`，换行用 \\n；\n"
                    "2) 紧扣主题画出**具体**的知识结构 / 执行流程（5-9 个节点，简洁中文）；\n"
                    "3) 严禁泛泛而谈——不要只写「核心概念 / 典型示例 / 常见易错」这类空话，"
                    "要写出该主题真正的步骤、分支或对比项；\n"
                    "4) 若有课件证据，结构与证据一致，不要编造。"
                ),
            }
        ]
        if evidence_chunks:
            evidence_text = "\n".join(
                f"[页 {c.page_no}] {c.text[:200]}" for c in evidence_chunks[:4]
            )
            messages.append({"role": "system", "content": f"课件证据:\n{evidence_text}"})
        messages.append({"role": "user", "content": f"主题：{question}"})
        try:
            response = await self.provider.chat(messages)
        except Exception:  # noqa: BLE001 - 失败回退确定式，不阻塞答疑
            return None
        data = response.content_json
        if isinstance(data, dict) and isinstance(data.get("mermaid"), str):
            return self._sanitize_mermaid(data["mermaid"])
        return self._sanitize_mermaid(response.content_text or "")

    @staticmethod
    def _sanitize_mermaid(text: str) -> str | None:
        """从 LLM 回复里稳健抽取一段合法 mermaid flowchart。

        容忍：① 模型在图前后加解说文字；② ```mermaid 围栏（出现在任意位置）；
        ③ <think>…</think> 推理段。抽取后要求以 flowchart/graph 开头且至少有一条边，
        否则返回 None（由 ``build_diagram`` 回退确定式结构）。
        """
        cleaned = (text or "").strip()
        if not cleaned:
            return None
        # 去掉可能的推理段（部分模型会前置 <think>…</think>）。
        cleaned = re.sub(r"<think>.*?</think>", "", cleaned, flags=re.DOTALL).strip()
        # 优先抽取任意位置的 ```mermaid … ``` / ``` … ``` 代码块。
        fence = re.search(r"```(?:mermaid)?\s*(.+?)```", cleaned, re.DOTALL)
        if fence:
            cleaned = fence.group(1).strip()
        # 丢弃图前的解说文字：从第一行 flowchart/graph 头开始截取。
        lines = cleaned.splitlines()
        start = next(
            (
                i
                for i, line in enumerate(lines)
                if line.strip().lower().startswith(("flowchart", "graph"))
            ),
            -1,
        )
        if start == -1:
            return None
        cleaned = "\n".join(lines[start:]).strip()
        if "-->" not in cleaned and "---" not in cleaned:
            return None
        return cleaned

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
