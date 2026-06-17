from __future__ import annotations

import re
from typing import Any

from pythontopia_graph.agents.personalization import (
    agent_strategy_messages,
    personalization_messages,
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
        response = await self.provider.chat(messages)
        return self._build(request, response.content_json)

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
        return all("-->" in line or line.strip().endswith("]") or line.strip().endswith(")") for line in body_lines)

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
                    "你是 MindMapAgent，输出 JSON 含 mermaid 字段。"
                    "mermaid 必须以 graph TD/LR/RL/BT 或 flowchart 开头，节点用 [] 包裹。"
                    "kc_map 字段：以中心 KC 为 key，相关 KC code 数组为 value。"
                ),
            },
            *([{
                "role": "system",
                "content": "上一稿被打回原因：\n" + "\n".join(f"- {r}" for r in revision_notes)
                + "\n请针对性修订，修正上述问题后重新生成。",
            }] if revision_notes else []),
            *personalization_messages(request.profile_snapshot),
            *agent_strategy_messages(request.agent_strategy_directive),
            {"role": "user", "content": ",".join(request.kc_codes)},
        ]

    def _build(self, request: ResourceProduceRequest, content_json: dict[str, Any] | None) -> ResourceDraft:
        mindmap = self._build_content(request, content_json)
        return ResourceDraft(
            title=f"思维导图：{','.join(request.kc_codes[:3])}",
            summary="围绕选定 KC 的关联结构",
            content_format="MERMAID",
            mindmap=mindmap,
        )

    def _build_content(
        self, request: ResourceProduceRequest, content_json: dict[str, Any] | None
    ) -> MindMapContent:
        candidate = None
        if isinstance(content_json, dict) and "mindmap" in content_json:
            try:
                candidate = MindMapContent.model_validate(content_json["mindmap"])
            except Exception:  # noqa: BLE001
                candidate = None
        if candidate and self.syntax_valid(candidate.mermaid):
            return candidate
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
