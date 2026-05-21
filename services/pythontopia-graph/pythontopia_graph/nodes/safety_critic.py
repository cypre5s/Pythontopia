from __future__ import annotations

import re

from pythontopia_graph.schemas.resource import (
    HandoutContent,
    PracticeContent,
    ResourceDraft,
    SafetyResult,
)


PII_PATTERNS = [
    re.compile(r"\b1[3-9]\d{9}\b"),
    re.compile(r"\b\d{15}|\d{17}[0-9Xx]\b"),
    re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"),
]
ANSWER_LEAK_KEYWORDS = [
    "hidden_test_cases",
    "answer_key",
    "secret_test",
    "标准答案",
    "完整答案",
]
DISCRIMINATION_KEYWORDS = [
    "种族歧视",
    "暴力分裂",
    "色情",
]


def safety_critic(draft: ResourceDraft) -> SafetyResult:
    reasons: list[str] = []
    payload = _flatten(draft)
    lowered = payload.lower()
    for keyword in ANSWER_LEAK_KEYWORDS:
        if keyword in lowered:
            reasons.append(f"ANSWER_LEAK:{keyword}")
    for keyword in DISCRIMINATION_KEYWORDS:
        if keyword in payload:
            reasons.append(f"DISCRIMINATION:{keyword}")
    for pattern in PII_PATTERNS:
        if pattern.search(payload):
            reasons.append("PII_DETECTED")
            break
    if isinstance(draft.practice, PracticeContent):
        for problem in draft.practice.problems:
            for hint in problem.hints:
                if "完整答案" in hint or "answer key" in hint.lower():
                    reasons.append(f"PRACTICE_LEAK:{problem.title}")
    if isinstance(draft.handout, HandoutContent):
        for section in draft.handout.sections:
            for snippet in section.code_snippets:
                if "os.system" in snippet or "rm -rf" in snippet:
                    reasons.append("DANGEROUS_CODE")
    return SafetyResult(passed=not reasons, reasons=reasons)


def _flatten(draft: ResourceDraft) -> str:
    parts: list[str] = [draft.title]
    if draft.summary:
        parts.append(draft.summary)
    body = draft.content_payload()
    parts.append(str(body))
    return "\n".join(parts)
