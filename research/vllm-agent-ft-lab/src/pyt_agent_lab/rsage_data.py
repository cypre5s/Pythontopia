from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from typing import Any, Mapping


EVENT_INTENTS = (
    "READ",
    "REVIEW",
    "TRACE",
    "SCAFFOLD",
    "PRACTICE",
    "ASK",
    "CHECK",
)

FAILURE_AXES = (
    "wrong_decision",
    "weak_semantics",
    "unbound_evidence",
    "answer_leak",
    "over_refusal",
    "chinese_noisy_input",
)


@dataclass(frozen=True)
class ValidationResult:
    passed: bool
    errors: tuple[str, ...]


def stable_fingerprint(value: Any) -> str:
    payload = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def validate_case(case: Mapping[str, Any]) -> ValidationResult:
    errors: list[str] = []
    _require_non_empty_text(case, "case_id", "案例编号", errors)
    _require_non_empty_text(case, "kc_code", "知识点编码", errors)
    _require_non_empty_text(case, "student_input", "学生输入", errors)

    chapter_id = case.get("chapter_id")
    if isinstance(chapter_id, bool) or not isinstance(chapter_id, int) or chapter_id <= 0:
        errors.append("章节编号必须是正整数")

    if case.get("event_intent") not in EVENT_INTENTS:
        errors.append("教学动作不在已冻结的七类动作中")
    if case.get("failure_axis") not in FAILURE_AXES:
        errors.append("失败轴不在已冻结的六类失败轴中")

    evidence_refs = case.get("evidence_refs")
    if not isinstance(evidence_refs, list) or not evidence_refs:
        errors.append("课程证据不能为空")
    else:
        seen: set[str] = set()
        for index, item in enumerate(evidence_refs):
            if not isinstance(item, Mapping):
                errors.append(f"第 {index + 1} 条课程证据必须是对象")
                continue
            evidence_id = item.get("evidence_id")
            if not isinstance(evidence_id, str) or not evidence_id.strip():
                errors.append(f"第 {index + 1} 条课程证据缺少证据编号")
            elif evidence_id in seen:
                errors.append(f"课程证据编号重复：{evidence_id}")
            else:
                seen.add(evidence_id)
            if not isinstance(item.get("excerpt"), str) or not str(item.get("excerpt")).strip():
                errors.append(f"第 {index + 1} 条课程证据缺少文本摘录")

    return ValidationResult(passed=not errors, errors=tuple(errors))


def validate_teacher_vote(vote: Mapping[str, Any], case: Mapping[str, Any]) -> ValidationResult:
    errors: list[str] = []
    _require_non_empty_text(vote, "misconception_type", "错因类型", errors)
    _require_non_empty_text(vote, "action_goal", "教学动作目标", errors)

    admissible = _intent_list(vote.get("admissible_intents"), "可接受动作", errors)
    forbidden = _intent_list(vote.get("forbidden_intents"), "禁止动作", errors, allow_empty=True)
    preferred = vote.get("preferred_intent")
    if preferred not in EVENT_INTENTS:
        errors.append("首选动作不在已冻结的七类动作中")
    elif preferred not in admissible:
        errors.append("首选动作必须属于可接受动作集合")
    if set(admissible) & set(forbidden):
        errors.append("可接受动作与禁止动作不能重叠")

    hint_level = vote.get("hint_level")
    if isinstance(hint_level, bool) or not isinstance(hint_level, int) or not 0 <= hint_level <= 3:
        errors.append("提示等级必须是 0 到 3 的整数")

    confidence = vote.get("preference_confidence")
    if isinstance(confidence, bool) or not isinstance(confidence, (int, float)) or not 0.5 <= float(confidence) <= 1.0:
        errors.append("偏好置信度必须位于 0.5 到 1.0")

    evidence_refs = vote.get("evidence_refs")
    if not isinstance(evidence_refs, list) or not evidence_refs:
        errors.append("教师投票必须至少选择一条课程证据")
    else:
        allowlist = _case_evidence_ids(case)
        unknown = sorted(
            {
                item
                for item in evidence_refs
                if isinstance(item, str) and item not in allowlist
            }
        )
        if any(not isinstance(item, str) or not item for item in evidence_refs):
            errors.append("教师投票中的证据编号必须是非空字符串")
        if unknown:
            errors.append(f"教师投票引用了证据白名单之外的编号：{', '.join(unknown)}")

    if vote.get("answer_leak") is not False:
        errors.append("教师投票存在答案泄露或未明确关闭答案泄露")
    safety_flags = vote.get("safety_flags")
    if not isinstance(safety_flags, list):
        errors.append("安全标记必须是列表")

    return ValidationResult(passed=not errors, errors=tuple(errors))


def _case_evidence_ids(case: Mapping[str, Any]) -> set[str]:
    refs = case.get("evidence_refs")
    if not isinstance(refs, list):
        return set()
    return {
        str(item["evidence_id"])
        for item in refs
        if isinstance(item, Mapping)
        and isinstance(item.get("evidence_id"), str)
        and str(item["evidence_id"]).strip()
    }


def _intent_list(
    value: Any,
    label: str,
    errors: list[str],
    *,
    allow_empty: bool = False,
) -> tuple[str, ...]:
    if not isinstance(value, list) or (not value and not allow_empty):
        errors.append(f"{label}必须是{'可为空的' if allow_empty else '非空'}列表")
        return ()
    invalid = sorted({str(item) for item in value if item not in EVENT_INTENTS})
    if invalid:
        errors.append(f"{label}包含未知教学动作：{', '.join(invalid)}")
    return tuple(str(item) for item in value if item in EVENT_INTENTS)


def _require_non_empty_text(
    value: Mapping[str, Any],
    key: str,
    label: str,
    errors: list[str],
) -> None:
    text = value.get(key)
    if not isinstance(text, str) or not text.strip():
        errors.append(f"{label}不能为空")
