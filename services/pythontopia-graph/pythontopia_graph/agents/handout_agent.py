from __future__ import annotations

from pythontopia_graph.agents.personalization import personalization_messages
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    HandoutContent,
    HandoutSection,
    PageRef,
    ResourceDraft,
    ResourceProduceRequest,
)


class HandoutAgent:
    """据 RAG 证据页生成结构化讲义；page_refs 仅可引用证据中的 page_id/page_no，禁止编造页码。"""

    AGENT_NAME = "ResourceDesignerAgent.handout"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
    ) -> ResourceDraft:
        messages = self._prompt(request, evidence)
        response = await self.provider.chat(messages)
        return self._build_from_response(request, evidence, response.content_json)

    def _prompt(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
    ) -> list[dict[str, str]]:
        evidence_summary = "\n".join(
            f"page_id={hit.page_id} page_no={hit.page_no} kc={','.join(hit.kc_codes)} text={hit.text[:200]}"
            for hit in evidence.chunks[:6]
        )
        # 约束顶层 handout 键与 page_refs 来源；缺键或校验失败时回落确定式兜底讲义。
        schema_hint = (
            '{"title": "字符串", "summary": "一句话摘要", '
            '"handout": {"sections": [{"heading": "小节标题", '
            '"paragraphs": ["正文段落"], "code_snippets": ["可选代码"], '
            '"page_refs": [{"page_id": 整数, "page_no": 整数, "chapter_id": 整数或null}]}]}}'
        )
        return [
            {
                "role": "system",
                "content": (
                    "你是 Pythontopia ResourceDesignerAgent，为目标 KC 生成结构化讲义。"
                    "只输出一个 JSON 对象，顶层必须包含 title、summary、handout 三个键，"
                    f"严格遵循此结构：{schema_hint}。"
                    "handout.sections 至少 1 个；每个 section 的 page_refs 只能引用下方证据中"
                    "出现过的 page_id/page_no，严禁编造页码；若某段来自通识而非课件，"
                    "page_refs 留空并在正文标注'基于通识知识'。"
                ),
            },
            {
                "role": "system",
                "content": (
                    f"目标 KC: {','.join(request.kc_codes)}\n"
                    f"可引用证据(page_id/page_no/kc/文本片段):\n{evidence_summary}"
                ),
            },
            *personalization_messages(request.profile_snapshot),
            {
                "role": "user",
                "content": request.focus_hint or "请围绕目标 KC，结合上述证据页生成一份讲义",
            },
        ]

    def _build_from_response(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceBundle,
        content_json: dict | None,
    ) -> ResourceDraft:
        if content_json is None or "handout" not in content_json:
            return self._fallback_draft(request, evidence)
        try:
            handout = HandoutContent.model_validate(content_json["handout"])
        except Exception:  # noqa: BLE001 - fail safe to deterministic fallback
            return self._fallback_draft(request, evidence)
        title = str(content_json.get("title") or self._fallback_title(request))
        summary = content_json.get("summary")
        if isinstance(summary, str):
            summary = summary[:500]
        else:
            summary = None
        return ResourceDraft(
            title=title,
            summary=summary,
            content_format="MARKDOWN",
            handout=handout,
        )

    def _fallback_title(self, request: ResourceProduceRequest) -> str:
        return f"KC 讲义：{','.join(request.kc_codes[:3])}"

    def _fallback_draft(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
    ) -> ResourceDraft:
        sections: list[HandoutSection] = []
        if evidence.chunks:
            for hit in evidence.chunks[:3]:
                sections.append(
                    HandoutSection(
                        heading=f"要点：第 {hit.page_no} 页",
                        paragraphs=[hit.text[:280]],
                        code_snippets=[],
                        page_refs=[
                            PageRef(
                                page_id=hit.page_id,
                                page_no=hit.page_no,
                                chapter_id=hit.chapter_id,
                            )
                        ],
                    )
                )
        else:
            sections.append(
                HandoutSection(
                    heading="基于通识知识的讲义",
                    paragraphs=[
                        "课件检索未命中，以下内容基于 Python 通识，仅供入门参考，请配合官方课件复习。",
                    ],
                )
            )
        return ResourceDraft(
            title=self._fallback_title(request),
            summary="自动兜底讲义",
            content_format="MARKDOWN",
            handout=HandoutContent(sections=sections),
        )
