from __future__ import annotations

import json
from typing import Any

from pydantic import ValidationError

from pythontopia_graph.agents.personalization import (
    agent_strategy_messages,
    personalization_messages,
)
from pythontopia_graph.contracts.semantic_ir import (
    CodeLabPlanSemanticIR,
    ContractCompiler,
    EvidenceBinder,
    EvidenceContext,
)
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    CodeLabContent,
    ResourceDraft,
    ResourceProduceRequest,
    Walkthrough,
)


class CodeLabAgent:
    """Compile evidence-bound walkthrough semantics into a safe CodeLab product."""

    AGENT_NAME = "CodeLabAgent"

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
                "json_schema": CodeLabPlanSemanticIR.model_json_schema(),
                "evidence_ids": evidence_context.allowed_ids,
                "evidence_fields": ["walkthroughs.*.evidence_refs"],
                "semantic_validator": lambda payload: self._semantic_error(
                    payload, evidence_context
                ),
            },
        )
        return self._build(request, evidence_context, response.content_json)

    def _semantic_error(
        self, payload: dict[str, Any], evidence: EvidenceContext
    ) -> str | None:
        try:
            semantic_ir = CodeLabPlanSemanticIR.model_validate(payload)
            ContractCompiler.compile_code_lab_plan(semantic_ir, evidence=evidence)
        except (ValidationError, ValueError) as exc:
            return str(exc)[:500]
        return None

    def _prompt(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceContext | EvidenceBundle | None = None,
        revision_notes: list[str] | None = None,
    ) -> list[dict[str, str]]:
        evidence_context = (
            evidence
            if isinstance(evidence, EvidenceContext)
            else EvidenceBinder.bind(evidence or EvidenceBundle(chunks=[]))
        )
        return [
            {
                "role": "system",
                "content": (
                    "你是 CodeLabAgent，只输出符合 CodeLabPlanSemanticIR 的 JSON，含 1-3 个 walkthroughs。"
                    "每个案例只表达 title、narrative、example_pattern、observation_goal、"
                    "common_mistakes、evidence_refs，不生成 code、expected_output 或产品包装字段。"
                    "example_pattern 仅可选 assignment_trace/conditional_branch/while_control/"
                    "for_accumulate/list_iteration/function_scope/string_cleanup/exception_guard；"
                    "根据 KC 与误区选择最匹配的教学模式，Python 语法由 compiler 生成。"
                    "evidence_refs 只能引用 allowed_evidence 中的 evidence_id。"
                ),
            },
            *([{
                "role": "system",
                "content": "上一稿被打回原因：\n" + "\n".join(f"- {r}" for r in revision_notes)
                + "\n请针对性修订，修正上述问题后重新生成。",
            }] if revision_notes else []),
            *personalization_messages(request.profile_snapshot),
            *agent_strategy_messages(request.agent_strategy_directive),
            {
                "role": "user",
                "content": (
                    f"目标 KC: {','.join(request.kc_codes)}; allowed_evidence: "
                    + json.dumps(
                        evidence_context.model_dump(mode="json"), ensure_ascii=False
                    )
                ),
            },
        ]

    def _build(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceContext,
        content_json: dict[str, Any] | None,
    ) -> ResourceDraft:
        content = self._build_content(request, evidence, content_json)
        return ResourceDraft(
            title=f"代码实验室：{','.join(request.kc_codes[:3])}",
            summary="带走读与常见错误的代码示例",
            content_format="STRUCT_JSON",
            code_lab=content,
        )

    def _build_content(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceContext,
        content_json: dict[str, Any] | None,
    ) -> CodeLabContent:
        if isinstance(content_json, dict):
            try:
                semantic_ir = CodeLabPlanSemanticIR.model_validate(content_json)
                return ContractCompiler.compile_code_lab_plan(
                    semantic_ir, evidence=evidence
                )
            except (ValidationError, ValueError):
                pass
        return self._fallback(request)

    def _fallback(self, request: ResourceProduceRequest) -> CodeLabContent:
        kc = request.kc_codes[0]
        code = (
            "nums = [int(x) for x in input().split()]\n"
            "total = 0\n"
            "for value in nums:\n"
            "    total += value\n"
            "print(total)\n"
        )
        return CodeLabContent(
            walkthroughs=[
                Walkthrough(
                    title=f"{kc} 走读：列表求和",
                    narrative="读入一行数字，循环累加并输出结果。",
                    code=code,
                    expected_output="6",
                    common_mistakes=[
                        "忘记把 input 转 int。",
                        "把 total 写到循环外。",
                        "把 print 写在循环里。",
                    ],
                )
            ]
        )
