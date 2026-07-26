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
    PracticeSemanticIR,
)
from pythontopia_graph.llm.provider import LlmProvider, apply_compiled_prompt
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
    """生成练习题资源（STRUCT_JSON）；禁止 hidden_test/answer_key 等泄答 token，否则回落确定式兜底。"""

    AGENT_NAME = "PracticeAgent"

    def __init__(self, provider: LlmProvider) -> None:
        self.provider = provider

    async def design(
        self,
        request: ResourceProduceRequest,
        evidence: EvidenceBundle,
        revision_notes: list[str] | None = None,
    ) -> ResourceDraft:
        # P2-dspy-loader（§3.5）：命中离线 GEPA 冻结产物则套用，否则原样返回手写 prompt（零回归）。
        messages = apply_compiled_prompt(
            self._prompt(request, evidence, revision_notes), "practice_generate"
        )
        evidence_context = EvidenceBinder.bind(evidence)
        response = await self.provider.chat(
            messages,
            guard_context={
                "agent": self.AGENT_NAME,
                "json_schema": PracticeSemanticIR.model_json_schema(),
                "evidence_ids": evidence_context.allowed_ids,
                "evidence_fields": ["evidence_refs"],
            },
        )
        return self._build(
            request,
            response.content_json,
            allowed_evidence_ids=evidence_context.allowed_ids,
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
                    "你是 PracticeAgent，输出符合 PracticeSemanticIR 的 JSON，含 1-3 道题。"
                    "每题仅输出 title、statement、hints、sample_input、sample_output。"
                    "evidence_refs 只能引用 allowed_evidence 中的 evidence_id。"
                    "严禁返回 hidden_test_cases / answer_key / secret_test。"
                    "禁止给出完整答案；如需提示请放 hints 字段。"
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
                    f"KC: {','.join(request.kc_codes)}; 自评等级: {request.profile_snapshot.python_self_rated_level}"
                    + (
                        f"; 目标难度: {request.target_difficulty}（低掌握→basic建信心，高掌握→hard促成长）"
                        if request.target_difficulty else ""
                    )
                    + "; allowed_evidence: "
                    + json.dumps(
                        EvidenceBinder.bind(evidence).model_dump(mode="json"),
                        ensure_ascii=False,
                    )
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
        practice = self._build_content(
            request, content_json, allowed_evidence_ids=allowed_evidence_ids or []
        )
        return ResourceDraft(
            title=f"练习题：{','.join(request.kc_codes[:3])}",
            summary=f"针对自评等级 {request.profile_snapshot.python_self_rated_level} 的练习",
            content_format="STRUCT_JSON",
            practice=practice,
        )

    def _build_content(
        self,
        request: ResourceProduceRequest,
        content_json: dict[str, Any] | None,
        *,
        allowed_evidence_ids: list[str],
    ) -> PracticeContent:
        if isinstance(content_json, dict):
            try:
                semantic_ir = PracticeSemanticIR.model_validate(content_json)
                return ContractCompiler.compile_practice(
                    semantic_ir,
                    request=request,
                    difficulty=self._difficulty_for(request),  # type: ignore[arg-type]
                    allowed_evidence_ids=allowed_evidence_ids,
                )
            except (ValidationError, ValueError):
                pass
        if isinstance(content_json, dict) and "practice" in content_json:
            try:
                practice = PracticeContent.model_validate(content_json["practice"])
                if self._safe(practice):
                    return practice
            except Exception:  # noqa: BLE001
                pass
        legacy = self._legacy_practice_content(request, content_json)
        if legacy is not None and self._safe(legacy):
            return legacy
        return self._fallback(request)

    def _safe(self, practice: PracticeContent) -> bool:
        for problem in practice.problems:
            text = (problem.statement_md + "\n" + "\n".join(problem.hints)).lower()
            if any(token in text for token in FORBIDDEN_TOKENS):
                return False
        return True

    def _legacy_practice_content(
        self,
        request: ResourceProduceRequest,
        content_json: dict[str, Any] | None,
    ) -> PracticeContent | None:
        if not isinstance(content_json, dict):
            return None
        raw_problems = content_json.get("practice_problems")
        if not isinstance(raw_problems, list):
            raw_problems = content_json.get("questions")
        if not isinstance(raw_problems, list) or not raw_problems:
            return None
        diagnostic_hints = [
            str(hint)
            for hint in content_json.get("diagnostic_hints", [])
            if isinstance(hint, str) and hint.strip()
        ][:2]
        problems: list[ProblemSpec] = []
        for index, raw in enumerate(raw_problems[:3], start=1):
            if not isinstance(raw, dict):
                continue
            sample_io = self._legacy_sample_io(raw.get("sample_io"))
            if not sample_io:
                continue
            problem_type = str(raw.get("type") or "practice").replace("_", " ")
            title = str(raw.get("title") or raw.get("id") or f"练习 {index}")[:200]
            hint = str(raw.get("hint") or "").strip()
            raw_hints = raw.get("hints")
            list_hints = [
                str(item).strip()
                for item in raw_hints
                if isinstance(item, str) and item.strip()
            ] if isinstance(raw_hints, list) else []
            hints = [item for item in [hint, *list_hints, *diagnostic_hints] if item]
            description = str(raw.get("description") or "").strip()
            statement_parts = [
                description
                or f"围绕知识点 {request.kc_codes[0]} 完成一道 {problem_type} 小练习。",
                "请根据样例输入输出关系写出你的思路或代码。",
            ]
            expected = raw.get("expected_output_pattern")
            if isinstance(expected, str) and expected.strip():
                statement_parts.append(f"输出应符合样例呈现的模式：{expected.strip()[:120]}。")
            try:
                problems.append(
                    ProblemSpec(
                        title=title,
                        statement_md="\n".join(statement_parts),
                        hints=hints[:4],
                        sample_io=sample_io,
                        kc_codes=list(request.kc_codes),
                        difficulty=self._difficulty_for(request),  # type: ignore[arg-type]
                    )
                )
            except Exception:  # noqa: BLE001
                continue
        if not problems:
            return None
        try:
            return PracticeContent(problems=problems)
        except Exception:  # noqa: BLE001
            return None

    @staticmethod
    def _legacy_sample_io(raw: object) -> list[SampleIo]:
        if isinstance(raw, dict):
            input_value = raw.get("input")
            output_value = raw.get("output")
            if input_value is not None and output_value is not None:
                return [
                    SampleIo(
                        input=PracticeAgent._sample_value_to_text(input_value),
                        output=PracticeAgent._sample_value_to_text(output_value),
                    )
                ]
        if isinstance(raw, list):
            sample: list[SampleIo] = []
            for item in raw[:3]:
                if not isinstance(item, dict):
                    continue
                input_value = item.get("input")
                output_value = item.get("output")
                if input_value is not None and output_value is not None:
                    sample.append(
                        SampleIo(
                            input=PracticeAgent._sample_value_to_text(input_value),
                            output=PracticeAgent._sample_value_to_text(output_value),
                        )
                    )
            return sample
        return []

    @staticmethod
    def _sample_value_to_text(value: object) -> str:
        if isinstance(value, str):
            return value
        if isinstance(value, int | float | bool | list | dict) or value is None:
            return json.dumps(value, ensure_ascii=False)
        return str(value)

    @staticmethod
    def _difficulty_for(request: ResourceProduceRequest) -> str:
        if request.target_difficulty in {"basic", "medium", "hard"}:
            return str(request.target_difficulty)
        if request.profile_snapshot.python_self_rated_level <= 2:
            return "basic"
        if request.profile_snapshot.python_self_rated_level == 3:
            return "medium"
        return "hard"

    def _fallback(self, request: ResourceProduceRequest) -> PracticeContent:
        kc = request.kc_codes[0]
        difficulty = self._difficulty_for(request)
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
