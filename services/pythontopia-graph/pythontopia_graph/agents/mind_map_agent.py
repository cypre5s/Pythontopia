from __future__ import annotations

import re

from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    MindMapContent,
    ResourceDraft,
    ResourceProduceRequest,
)


MERMAID_HEADER_PATTERN = re.compile(r"^(graph|flowchart)\s+(TD|LR|RL|BT)", re.MULTILINE)


class MindMapAgent:
    AGENT_NAME = "MindMapAgent"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
    ) -> ResourceDraft:
        messages = self._prompt(request, evidence)
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
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
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
            {"role": "user", "content": ",".join(request.kc_codes)},
        ]

    def _build(self, request: ResourceProduceRequest, content_json: dict | None) -> ResourceDraft:
        mindmap = self._build_content(request, content_json)
        return ResourceDraft(
            title=f"思维导图：{','.join(request.kc_codes[:3])}",
            summary="围绕选定 KC 的关联结构",
            content_format="MERMAID",
            mindmap=mindmap,
        )

    def _build_content(
        self, request: ResourceProduceRequest, content_json: dict | None
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
        related = request.kc_codes[1:] or [f"{root}-extend-1", f"{root}-extend-2", f"{root}-extend-3"]
        lines = ["graph TD"]
        lines.append(f"  {self._node_id(root)}[{root}]")
        for index, code in enumerate(related[:5]):
            node = self._node_id(code)
            lines.append(f"  {self._node_id(root)} --> {node}[{code}]")
            lines.append(f"  note{index}({code} 详解)")
            lines.append(f"  {node} --> note{index}")
        mermaid = "\n".join(lines)
        kc_map = {root: list(related[:5])}
        return MindMapContent(mermaid=mermaid, kc_map=kc_map)

    @staticmethod
    def _node_id(code: str) -> str:
        return re.sub(r"[^A-Za-z0-9]", "_", code) or "node"
