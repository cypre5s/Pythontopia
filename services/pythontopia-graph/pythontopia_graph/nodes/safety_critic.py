from __future__ import annotations

import re

from pythontopia_graph.schemas.resource import (
    HandoutContent,
    PracticeContent,
    ResourceDraft,
    SafetyResult,
)


# PII 正则单一来源：resource 流水线（本模块）/ 答疑答后审查（nodes/tutor_review）/
# 章测卷面审查（agents/paper_reviews）三处共用，避免各自抄一份后漂移。
PII_PATTERNS = [
    re.compile(r"\b1[3-9]\d{9}\b"),  # 手机号
    # 身份证：15 位旧号 或 18 位新号（末位校验位可为 X）。两个分支必须整体包进
    # (?:...) 再由 \b 夹住——写成 `\b\d{15}|\d{17}[0-9Xx]\b` 会被解析为
    # `(\b\d{15})|(\d{17}[0-9Xx]\b)`，于是任意 ≥15 位数字串（如 16 位毫秒时间戳）
    # 都会命中 PII_DETECTED；而 safety_critic 失败在 resource_graph 里是终止性的。
    re.compile(r"\b(?:\d{15}|\d{17}[0-9Xx])\b"),  # 身份证
    re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"),  # 邮箱
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
    """安全审查：泄答关键词、歧视内容、PII、Practice hint 泄答、Handout 危险代码。"""
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
