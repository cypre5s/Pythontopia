from __future__ import annotations

from pyt_agent_lab.rsage_data import (
    EVENT_INTENTS,
    FAILURE_AXES,
    stable_fingerprint,
    validate_case,
    validate_teacher_vote,
)


def _case() -> dict[str, object]:
    return {
        "case_id": "rsage-read-wrong-0001",
        "chapter_id": 1,
        "kc_code": "PY43-KC-001",
        "student_input": "我看不懂变量为什么会变化。",
        "event_intent": "READ",
        "failure_axis": "wrong_decision",
        "evidence_refs": [
            {
                "evidence_id": "pack43-page-1",
                "page_id": 1,
                "excerpt": "变量用于保存程序运行中的数据。",
            }
        ],
    }


def _vote() -> dict[str, object]:
    return {
        "misconception_type": "value_binding",
        "admissible_intents": ["READ", "SCAFFOLD"],
        "preferred_intent": "READ",
        "forbidden_intents": ["CHECK"],
        "hint_level": 1,
        "action_goal": "先区分变量名与当前绑定的值。",
        "evidence_refs": ["pack43-page-1"],
        "safety_flags": [],
        "answer_leak": False,
        "preference_confidence": 0.8,
    }


def test_rsage_ontology_is_frozen_to_seven_intents_and_six_failure_axes() -> None:
    assert EVENT_INTENTS == (
        "READ",
        "REVIEW",
        "TRACE",
        "SCAFFOLD",
        "PRACTICE",
        "ASK",
        "CHECK",
    )
    assert FAILURE_AXES == (
        "wrong_decision",
        "weak_semantics",
        "unbound_evidence",
        "answer_leak",
        "over_refusal",
        "chinese_noisy_input",
    )


def test_stable_fingerprint_is_independent_of_dictionary_key_order() -> None:
    left = {"b": [2, 1], "a": {"x": "中文"}}
    right = {"a": {"x": "中文"}, "b": [2, 1]}

    assert stable_fingerprint(left) == stable_fingerprint(right)
    assert len(stable_fingerprint(left)) == 64


def test_validate_case_accepts_grounded_case_and_rejects_unknown_axis() -> None:
    accepted = validate_case(_case())
    assert accepted.passed is True
    assert accepted.errors == ()

    invalid = _case()
    invalid["failure_axis"] = "invented_axis"
    rejected = validate_case(invalid)

    assert rejected.passed is False
    assert any("失败轴" in error for error in rejected.errors)


def test_validate_teacher_vote_enforces_case_evidence_allowlist() -> None:
    vote = _vote()
    vote["evidence_refs"] = ["pack43-page-999"]

    result = validate_teacher_vote(vote, _case())

    assert result.passed is False
    assert any("证据白名单" in error for error in result.errors)


def test_validate_teacher_vote_rejects_inconsistent_preference_and_hint_bounds() -> None:
    vote = _vote()
    vote["preferred_intent"] = "TRACE"
    vote["hint_level"] = 4

    result = validate_teacher_vote(vote, _case())

    assert result.passed is False
    assert any("可接受动作" in error for error in result.errors)
    assert any("提示等级" in error for error in result.errors)


def test_validate_teacher_vote_rejects_answer_leak() -> None:
    vote = _vote()
    vote["answer_leak"] = True

    result = validate_teacher_vote(vote, _case())

    assert result.passed is False
    assert any("答案泄露" in error for error in result.errors)
