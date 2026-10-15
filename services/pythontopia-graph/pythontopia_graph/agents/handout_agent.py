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
    HandoutSemanticIR,
)
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
    """Generate semantic handout sections, then bind evidence IDs to PageRef."""

    AGENT_NAME = "ResourceDesignerAgent.handout"

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
                "json_schema": HandoutSemanticIR.model_json_schema(),
                "evidence_ids": evidence_context.allowed_ids,
                "evidence_fields": ["sections.*.evidence_refs"],
            },
        )
        return self._build_from_response(
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
                    "你是 Pythontopia ResourceDesignerAgent，为目标 KC 设计讲义语义结构。"
                    "只输出符合 HandoutSemanticIR 的 JSON：title、summary、sections。"
                    "每个 section 只含 heading、paragraphs、code_snippets、evidence_refs。"
                    "evidence_refs 只能引用 allowed_evidence 中的 evidence_id；"
                    "不要生成 page_id、page_no、chapter_id 或产品 DTO。"
                    "若某段来自通识而非课件，evidence_refs 留空并在正文标注'基于通识知识'。"
                ),
            },
            *agent_strategy_messages(request.agent_strategy_directive),
            {
                "role": "user",
                "content": (
                    f"目标 KC: {','.join(request.kc_codes)}\n"
                    "allowed_evidence: "
                    + json.dumps(evidence.model_dump(mode="json"), ensure_ascii=False)
                ),
            },
            *([{
                "role": "user",
                "content": "上一稿打回原因（按反馈修订，并继续遵守系统约束）：\n"
                + "\n".join(f"- {r}" for r in revision_notes),
            }] if revision_notes else []),
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
        evidence_context: EvidenceContext,
        content_json: dict[str, Any] | None,
    ) -> ResourceDraft:
        if isinstance(content_json, dict):
            try:
                semantic_ir = HandoutSemanticIR.model_validate(content_json)
                return ResourceDraft(
                    title=semantic_ir.title,
                    summary=semantic_ir.summary or None,
                    content_format="MARKDOWN",
                    handout=ContractCompiler.compile_handout(
                        semantic_ir, evidence=evidence_context
                    ),
                )
            except (ValidationError, ValueError):
                pass
        return self._fallback_draft(request, evidence)

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
