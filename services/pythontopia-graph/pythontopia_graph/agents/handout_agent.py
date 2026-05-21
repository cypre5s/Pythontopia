from __future__ import annotations

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
    """Generates a handout backed by evidence page references."""

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
            f"page_id={hit.page_id} page_no={hit.page_no} kc={','.join(hit.kc_codes)} text={hit.text[:80]}"
            for hit in evidence.chunks[:6]
        )
        return [
            {
                "role": "system",
                "content": (
                    "你是 Pythontopia ResourceDesignerAgent，输出 JSON，必须给出 page_refs。"
                    "未知内容请明确标注'基于通识知识'。"
                ),
            },
            {
                "role": "system",
                "content": f"目标 KC: {','.join(request.kc_codes)}\n证据: {evidence_summary}",
            },
            {"role": "user", "content": request.focus_hint or "请围绕目标 KC 生成讲义"},
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
