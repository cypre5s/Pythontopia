"""Pedagogical guardrails for AI 导学 workflow cards."""

from __future__ import annotations

import json
import re
from typing import Any

from pydantic import BaseModel, Field


class GuardrailResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)


class GuideGuardrailCritic:
    """Check generated guide cards for high-confidence education safety violations."""

    def check(self, card_key: str, card: dict[str, Any]) -> GuardrailResult:
        reasons: list[str] = []
        if card_key == "skeleton_code":
            reasons.extend(self._check_skeleton(card))
        elif card_key == "error_diagnosis":
            reasons.extend(self._check_error_diagnosis(card))
        elif card_key == "transfer_problem":
            reasons.extend(self._check_transfer(card))
        elif card_key == "knowledge_review":
            reasons.extend(self._check_courseware_refs(card))
        return GuardrailResult(passed=not reasons, reasons=reasons)

    @staticmethod
    def _check_skeleton(card: dict[str, Any]) -> list[str]:
        skeleton = str(card.get("skeleton", ""))
        lowered = skeleton.lower()
        has_blank = "todo" in lowered or "___" in lowered or "pass  # " in lowered
        if not has_blank:
            return ["submit_ready"]
        return []

    @staticmethod
    def _check_error_diagnosis(card: dict[str, Any]) -> list[str]:
        text = _card_text(card)
        lowered = text.lower()
        has_code_fence = "```python" in lowered or "```" in lowered
        has_complete_marker = any(
            marker in text for marker in ("完整代码", "修正代码", "标准答案", "可直接提交")
        )
        has_submit_shape = bool(re.search(r"\binput\s*\(", text)) and bool(
            re.search(r"\bprint\s*\(", text)
        )
        if has_code_fence and (has_complete_marker or has_submit_shape):
            return ["complete_corrected_code"]
        return []

    @staticmethod
    def _check_transfer(card: dict[str, Any]) -> list[str]:
        reasons: list[str] = []
        forbidden_keys = {
            "reference_solution",
            "reference_solution_language",
            "reference_solution_code",
            "test_cases",
            "hidden_test_cases",
            "solution",
            "answer",
        }
        if forbidden_keys.intersection(card.keys()):
            reasons.append("reference_solution_leak")
        return reasons

    @staticmethod
    def _check_courseware_refs(card: dict[str, Any]) -> list[str]:
        refs = card.get("courseware_refs")
        if refs is None:
            return []
        if not isinstance(refs, list):
            return ["invalid_courseware_refs"]
        reasons: list[str] = []
        for ref in refs:
            if not isinstance(ref, dict):
                reasons.append("invalid_courseware_refs")
                continue
            page_no = int(ref.get("page_no") or 0)
            if page_no < 0:
                reasons.append("invalid_courseware_refs")
        return sorted(set(reasons))


def _card_text(card: dict[str, Any]) -> str:
    return json.dumps(card, ensure_ascii=False, sort_keys=True)
