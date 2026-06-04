from __future__ import annotations

from pythontopia_graph.agents.personalization import personalization_messages
from pythontopia_graph.llm.provider import LlmProvider
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle
from pythontopia_graph.schemas.resource import (
    PracticeContent,
    ProblemSpec,
    ResourceDraft,
    ResourceProduceRequest,
    SampleIo,
)


FORBIDDEN_TOKENS = (
    "hidden_test_cases",
    "answer_key",
    "answer key",
    "secret_test",
    "完整答案",
    "标准答案",
)


class PracticeAgent:
    AGENT_NAME = "PracticeAgent"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
    ) -> ResourceDraft:
        messages = self._prompt(request, evidence)
        response = await self.provider.chat(messages)
        return self._build(request, response.content_json)

    def _prompt(
        self, request: ResourceProduceRequest, evidence: EvidenceBundle
    ) -> list[dict[str, str]]:
        return [
            {
                "role": "system",
                "content": (
                    "你是 PracticeAgent，输出 JSON，1-3 道题。每题必须包含 sample_io。"
                    "严禁返回 hidden_test_cases / answer_key / secret_test。"
                    "禁止给出完整答案；如需提示请放 hints 字段。"
                ),
            },
            *personalization_messages(request.profile_snapshot),
            {
                "role": "user",
                "content": (
                    f"KC: {','.join(request.kc_codes)}; 自评等级: {request.profile_snapshot.python_self_rated_level}"
                ),
            },
        ]

    def _build(self, request: ResourceProduceRequest, content_json: dict | None) -> ResourceDraft:
        practice = self._build_content(request, content_json)
        return ResourceDraft(
            title=f"练习题：{','.join(request.kc_codes[:3])}",
            summary=f"针对自评等级 {request.profile_snapshot.python_self_rated_level} 的练习",
            content_format="STRUCT_JSON",
            practice=practice,
        )

    def _build_content(
        self, request: ResourceProduceRequest, content_json: dict | None
    ) -> PracticeContent:
        if isinstance(content_json, dict) and "practice" in content_json:
            try:
                practice = PracticeContent.model_validate(content_json["practice"])
                if self._safe(practice):
                    return practice
            except Exception:  # noqa: BLE001
                pass
        return self._fallback(request)

    def _safe(self, practice: PracticeContent) -> bool:
        for problem in practice.problems:
            text = (problem.statement_md + "\n" + "\n".join(problem.hints)).lower()
            if any(token in text for token in FORBIDDEN_TOKENS):
                return False
        return True

    def _fallback(self, request: ResourceProduceRequest) -> PracticeContent:
        kc = request.kc_codes[0]
        difficulty: str
        if request.profile_snapshot.python_self_rated_level <= 2:
            difficulty = "basic"
        elif request.profile_snapshot.python_self_rated_level == 3:
            difficulty = "medium"
        else:
            difficulty = "hard"
        return PracticeContent(
            problems=[
                ProblemSpec(
                    title=f"练习：{kc} 基础题",
                    statement_md=(
                        f"围绕知识点 {kc}，编写一段程序读入数据并输出处理结果。"
                        "请勿粘贴外部答案，独立完成。"
                    ),
                    hints=[
                        "先读题，再写伪代码。",
                        "考虑边界情况：空输入、最大输入。",
                    ],
                    sample_io=[SampleIo(input="3\n1 2 3", output="6")],
                    kc_codes=[kc],
                    difficulty=difficulty,  # type: ignore[arg-type]
                )
            ]
        )
