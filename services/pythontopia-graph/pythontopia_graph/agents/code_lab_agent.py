from __future__ import annotations

import ast

from pythontopia_graph.agents.personalization import personalization_messages
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    CodeLabContent,
    ResourceDraft,
    ResourceProduceRequest,
    Walkthrough,
)


class CodeLabAgent:
    """生成含走读与常见错误的代码实验室资源（STRUCT_JSON）；LLM 输出须语法合法且行数合规，否则回落确定式兜底。"""

    AGENT_NAME = "CodeLabAgent"

    MAX_LINES = 50

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceBundle,
        revision_notes: list[str] | None = None,
    ) -> ResourceDraft:
        messages = self._prompt(request, revision_notes)
        response = await self.provider.chat(messages)
        return self._build(request, response.content_json)

    def _prompt(
        self, request: ResourceProduceRequest, revision_notes: list[str] | None = None,
    ) -> list[dict[str, str]]:
        return [
            {
                "role": "system",
                "content": (
                    "你是 CodeLabAgent，输出 JSON，1-3 个走读案例。每个 code 不超过 50 行，"
                    "必须是合法 Python 3.11 语法，并附 common_mistakes。"
                ),
            },
            *([{
                "role": "system",
                "content": f"上一稿被打回原因：\n" + "\n".join(f"- {r}" for r in revision_notes)
                + "\n请针对性修订，修正上述问题后重新生成。",
            }] if revision_notes else []),
            *personalization_messages(request.profile_snapshot),
            {"role": "user", "content": ",".join(request.kc_codes)},
        ]

    def _build(self, request: ResourceProduceRequest, content_json: dict | None) -> ResourceDraft:
        content = self._build_content(request, content_json)
        return ResourceDraft(
            title=f"代码实验室：{','.join(request.kc_codes[:3])}",
            summary="带走读与常见错误的代码示例",
            content_format="STRUCT_JSON",
            code_lab=content,
        )

    def _build_content(
        self, request: ResourceProduceRequest, content_json: dict | None
    ) -> CodeLabContent:
        if isinstance(content_json, dict) and "code_lab" in content_json:
            try:
                candidate = CodeLabContent.model_validate(content_json["code_lab"])
                if self._all_valid(candidate):
                    return candidate
            except Exception:  # noqa: BLE001
                pass
        return self._fallback(request)

    def _all_valid(self, content: CodeLabContent) -> bool:
        for walkthrough in content.walkthroughs:
            if walkthrough.code.count("\n") + 1 > self.MAX_LINES:
                return False
            try:
                ast.parse(walkthrough.code)
            except SyntaxError:
                return False
        return True

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
