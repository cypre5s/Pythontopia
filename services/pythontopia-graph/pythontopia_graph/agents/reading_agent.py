from __future__ import annotations

import json
from typing import Any

from pydantic import ValidationError

from pythontopia_graph.agents.personalization import (
    agent_strategy_messages,
    personalization_messages,
)
from pythontopia_graph.contracts.semantic_ir import (
    ContractCompiler,
    EvidenceBinder,
    EvidenceContext,
    ReadingSemanticIR,
)
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
    """Generate reading semantics, then compile evidence IDs into product PageRef."""

    AGENT_NAME = "ResourceDesignerAgent.reading"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceBundle,
        revision_notes: list[str] | None = None,
    ) -> ResourceDraft:
        evidence_context = EvidenceBinder.bind(evidence)
        messages = self._prompt(request, evidence_context, revision_notes)
        response = await self.provider.chat(
            messages,
            guard_context={
                "agent": self.AGENT_NAME,
                "json_schema": ReadingSemanticIR.model_json_schema(),
                "evidence_ids": evidence_context.allowed_ids,
                "evidence_fields": ["items.*.evidence_refs"],
            },
        )
        return self._build(
            request, evidence, evidence_context, response.content_json
        )

    def _prompt(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceContext,
        revision_notes: list[str] | None = None,
    ) -> list[dict[str, str]]:
        return [
            {
                "role": "system",
                "content": (
                    "你是 ReadingAgent，只输出符合 ReadingSemanticIR 的 JSON，含 2-4 个 items。"
                    "每个 item 只含 summary_md 与 evidence_refs。"
                    "evidence_refs 只能引用 allowed_evidence 中的 evidence_id；"
                    "不要生成 page_id、page_no、chapter_id 或产品 DTO。"
                ),
            },
            *agent_strategy_messages(request.agent_strategy_directive),
            *([{
                "role": "user",
                "content": "上一稿打回原因（按反馈修订，并继续遵守系统约束）：\n"
                + "\n".join(f"- {r}" for r in revision_notes),
            }] if revision_notes else []),
            *personalization_messages(request.profile_snapshot),
            {
                "role": "user",
                "content": (
                    f"目标 KC: {','.join(request.kc_codes)}; allowed_evidence: "
                    + json.dumps(evidence.model_dump(mode="json"), ensure_ascii=False)
                ),
            },
        ]

    def _build(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceBundle,
        evidence_context: EvidenceContext,
        content_json: dict[str, Any] | None,
    ) -> ResourceDraft:
        content = self._build_content(
            request, evidence, evidence_context, content_json
        )
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
        evidence_context: EvidenceContext,
        content_json: dict[str, Any] | None,
    ) -> ReadingContent:
        if isinstance(content_json, dict):
            try:
                semantic_ir = ReadingSemanticIR.model_validate(content_json)
                return ContractCompiler.compile_reading(
                    semantic_ir,
                    evidence=evidence_context,
                    kc_codes=request.kc_codes,
                )
            except (ValidationError, ValueError):
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
