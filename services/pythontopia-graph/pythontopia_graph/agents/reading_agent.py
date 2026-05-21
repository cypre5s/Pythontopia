from __future__ import annotations

from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    PageRef,
    ReadingContent,
    ReadingItem,
    ResourceDraft,
    ResourceProduceRequest,
)


class ReadingAgent:
    AGENT_NAME = "ResourceDesignerAgent.reading"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
    ) -> ResourceDraft:
        messages = self._prompt(request, evidence)
        response = await self.provider.chat(messages)
        return self._build(request, evidence, response.content_json)

    def _prompt(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
    ) -> list[dict[str, str]]:
        return [
            {
                "role": "system",
                "content": (
                    "你是 ReadingAgent，输出 JSON，至少 2 个 item。"
                    "每个 item 必须含 summary_md (>=10 字) 与 page_refs。"
                ),
            },
            {"role": "user", "content": ",".join(request.kc_codes)},
        ]

    def _build(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceBundle,
        content_json: dict | None,
    ) -> ResourceDraft:
        content = self._build_content(request, evidence, content_json)
        return ResourceDraft(
            title=f"拓展阅读：{','.join(request.kc_codes[:3])}",
            summary="围绕 KC 的延伸阅读卡片",
            content_format="STRUCT_JSON",
            reading=content,
        )

    def _build_content(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceBundle,
        content_json: dict | None,
    ) -> ReadingContent:
        if isinstance(content_json, dict) and "reading" in content_json:
            try:
                candidate = ReadingContent.model_validate(content_json["reading"])
                if len(candidate.items) >= 2:
                    return candidate
            except Exception:  # noqa: BLE001
                pass
        return self._fallback(request, evidence)

    def _fallback(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
    ) -> ReadingContent:
        items: list[ReadingItem] = []
        for hit in evidence.chunks[:3]:
            items.append(
                ReadingItem(
                    summary_md=hit.text[:200] or f"第 {hit.page_no} 页课件摘要。",
                    page_refs=[
                        PageRef(
                            page_id=hit.page_id,
                            page_no=hit.page_no,
                            chapter_id=hit.chapter_id,
                        )
                    ],
                    kc_codes=hit.kc_codes,
                )
            )
        while len(items) < 2:
            items.append(
                ReadingItem(
                    summary_md=(
                        f"针对 {','.join(request.kc_codes)} 的通识拓展：建议复习课程章节并尝试动手实验。"
                    ),
                    kc_codes=request.kc_codes,
                )
            )
        return ReadingContent(items=items)
