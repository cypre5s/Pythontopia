from __future__ import annotations

import re
from typing import Any

from pydantic import ValidationError

from pythontopia_graph.agents.personalization import (
    agent_strategy_messages,
    personalization_messages,
)
from pythontopia_graph.contracts.semantic_ir import (
    ContractCompiler,
    EvidenceBinder,
    MindMapSemanticIR,
)
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    MindMapContent,
    ResourceDraft,
    ResourceProduceRequest,
)


MERMAID_HEADER_PATTERN = re.compile(r"^(graph|flowchart)\s+(TD|LR|RL|BT)", re.MULTILINE)


class MindMapAgent:
    """生成 Mermaid 思维导图资源；mermaid 须通过 syntax_valid，否则回落确定式兜底。"""

    AGENT_NAME = "MindMapAgent"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceBundle,
        revision_notes: list[str] | None = None,
    ) -> ResourceDraft:
        messages = self._prompt(request, evidence, revision_notes)
        evidence_context = EvidenceBinder.bind(evidence)
        response = await self.provider.chat(
            messages,
            guard_context={
                "agent": self.AGENT_NAME,
                "json_schema": MindMapSemanticIR.model_json_schema(),
                "evidence_ids": evidence_context.allowed_ids,
                "evidence_fields": ["evidence_refs"],
            },
        )
        return self._build(
            request,
            response.content_json,
            allowed_evidence_ids=evidence_context.allowed_ids,
        )

    @staticmethod
    def syntax_valid(mermaid_text: str) -> bool:
        if not mermaid_text:
            return False
        head = mermaid_text.strip().splitlines()[0]
        if not MERMAID_HEADER_PATTERN.match(head):
            return False
        body_lines = [line for line in mermaid_text.strip().splitlines()[1:] if line.strip()]
        if not body_lines:
            return False
        return all(
            "-->" in line
            or line.strip().endswith("]")
            or line.strip().endswith(")")
            or line.strip().startswith("style ")
            for line in body_lines
        )

    def _prompt(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceBundle,
        revision_notes: list[str] | None = None,
    ) -> list[dict[str, str]]:
        return [
            {
                "role": "system",
                "content": (
                    "你是 MindMapAgent，输出符合 MindMapSemanticIR 的 JSON。"
                    "只生成 nodes、edges、evidence_refs，不直接拼 Mermaid。"
                    "node_id 必须以英文字母开头且只含字母、数字、下划线；"
                    "kc_code 只能来自请求 KC；evidence_refs 只能引用 allowed_evidence。"
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
                    f"allowed_kc_codes={request.kc_codes}; allowed_evidence="
                    + EvidenceBinder.bind(evidence).model_dump_json()
                ),
            },
        ]

    def _build(
        self,
        request: ResourceProduceRequest,
        content_json: dict[str, Any] | None,
        *,
        allowed_evidence_ids: list[str] | None = None,
    ) -> ResourceDraft:
        mindmap = self._build_content(
            request,
            content_json,
            allowed_evidence_ids=allowed_evidence_ids or [],
        )
        return ResourceDraft(
            title=f"思维导图：{','.join(request.kc_codes[:3])}",
            summary="围绕选定 KC 的关联结构",
            content_format="MERMAID",
            mindmap=mindmap,
        )

    def _build_content(
        self,
        request: ResourceProduceRequest,
        content_json: dict[str, Any] | None,
        *,
        allowed_evidence_ids: list[str],
    ) -> MindMapContent:
        if isinstance(content_json, dict):
            try:
                semantic_ir = MindMapSemanticIR.model_validate(content_json)
                candidate = ContractCompiler.compile_mind_map(
                    semantic_ir,
                    allowed_kc_codes=request.kc_codes,
                    allowed_evidence_ids=allowed_evidence_ids,
                )
                if self.syntax_valid(candidate.mermaid):
                    return candidate
            except (ValidationError, ValueError):
                pass
        candidate = None
        if isinstance(content_json, dict) and "mindmap" in content_json:
            try:
                candidate = MindMapContent.model_validate(content_json["mindmap"])
            except Exception:  # noqa: BLE001
                candidate = None
        elif isinstance(content_json, dict) and "mermaid" in content_json:
            try:
                candidate = MindMapContent.model_validate(
                    {
                        "mermaid": content_json.get("mermaid"),
                        "kc_map": content_json.get("kc_map") or {},
                    }
                )
            except Exception:  # noqa: BLE001
                candidate = None
        if candidate and self.syntax_valid(candidate.mermaid):
            return MindMapContent(
                mermaid=candidate.mermaid,
                kc_map=self._filter_kc_map(candidate.kc_map, request.kc_codes),
            )
        return self._fallback(request)

    def _fallback(self, request: ResourceProduceRequest) -> MindMapContent:
        root = request.kc_codes[0]
        related = list(request.kc_codes[1:])
        lines = ["graph TD", f"  {self._node_id(root)}[{root}]"]
        if related:
            for index, code in enumerate(related[:5]):
                node = self._node_id(code)
                lines.append(f"  {self._node_id(root)} --> {node}[{code}]")
                lines.append(f"  note{index}({code} 详解)")
                lines.append(f"  {node} --> note{index}")
        else:
            # 单 KC 时用通用概念子节点（非 KC code），避免编造 KC 触发 grounding 的 Unknown KC。
            for index, label in enumerate(("核心概念", "典型用法", "常见误区")):
                lines.append(f"  {self._node_id(root)} --> concept{index}[{label}]")
        mermaid = "\n".join(lines)
        kc_map = {root: related[:5]}
        return MindMapContent(mermaid=mermaid, kc_map=kc_map)

    @staticmethod
    def _node_id(code: str) -> str:
        return re.sub(r"[^A-Za-z0-9]", "_", code) or "node"

    @staticmethod
    def _filter_kc_map(kc_map: dict[str, list[str]], allowed_codes: list[str]) -> dict[str, list[str]]:
        allowed = set(allowed_codes)
        filtered: dict[str, list[str]] = {}
        for key, values in kc_map.items():
            if key not in allowed:
                continue
            filtered[key] = [value for value in values if value in allowed and value != key]
        return filtered or {allowed_codes[0]: []}
