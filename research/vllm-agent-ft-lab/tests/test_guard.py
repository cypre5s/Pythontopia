from pyt_agent_lab.guard import GuardDecision, apply_guard, guard_failure_summary


# ---------------------------------------------------------------------------
# Helper: minimal valid response per agent type
# ---------------------------------------------------------------------------


def _tutor_case(**kwargs: object) -> dict:
    return {
        "id": "test-tutor",
        "agent": "TutorAgent",
        "expected_markers": ["next_hint"],
        "evidence_ids": kwargs.get("evidence_ids", []),
        **kwargs,
    }


def test_guard_passes_clean_response() -> None:
    case = _tutor_case()
    # Include "common_knowledge" so grounding_safe passes when evidence_ids is empty
    response = (
        '{"diagnosis": "explanation of variables", '
        '"next_hint": "Try explaining what value the variable stores.", '
        '"evidence_ids": [], '
        '"safety_flags": [], '
        '"grounding_note": "common_knowledge"}'
    )
    decision = apply_guard(case, response)
    assert decision.passed, f"unexpected failures: {decision.failures}"
    assert decision.failures == []
    assert decision.score is not None


def test_guard_detects_invalid_json() -> None:
    case = _tutor_case()
    response = "not json at all, just some prose about variables"
    decision = apply_guard(case, response)
    assert not decision.passed
    assert "json_invalid" in decision.failures


def test_guard_detects_missing_grounding() -> None:
    case = _tutor_case(evidence_ids=["page-1-006"])
    # Valid JSON but doesn't mention the required evidence ID
    response = (
        '{"diagnosis": "variables", '
        '"next_hint": "Explain the assignment step.", '
        '"evidence_ids": [], '
        '"safety_flags": []}'
    )
    decision = apply_guard(case, response)
    assert not decision.passed
    assert "grounding_failed" in decision.failures


def test_guard_detects_forbidden_leak() -> None:
    case = _tutor_case(expected_markers=[])
    # Contains a forbidden Chinese phrase
    response = '{"concept_hint": "完整答案如下", "explanation": "直接提交即可"}'
    decision = apply_guard(case, response)
    assert not decision.passed
    assert "forbidden_leak" in decision.failures


def test_guard_detects_incomplete_markers() -> None:
    case = _tutor_case(expected_markers=["explanation", "next_step"])
    # Only contains one of two expected markers
    response = '{"concept_hint": "variables", "explanation": "Variables are containers."}'
    decision = apply_guard(case, response)
    assert not decision.passed
    assert "incomplete_markers" in decision.failures


def test_guard_multiple_failures() -> None:
    case = _tutor_case(expected_markers=["explanation"], evidence_ids=["page-1-006"])
    response = "not json, missing grounding, also 完整答案 leak"
    decision = apply_guard(case, response)
    assert not decision.passed
    assert len(decision.failures) >= 2


def test_guard_failure_summary_ok() -> None:
    decision = GuardDecision(passed=True, failures=[])
    assert guard_failure_summary(decision) == "OK"


def test_guard_failure_summary_with_failures() -> None:
    decision = GuardDecision(passed=False, failures=["json_invalid", "grounding_failed"])
    summary = guard_failure_summary(decision)
    assert "json_invalid" in summary
    assert "grounding_failed" in summary


def test_guard_immutable_decision() -> None:
    decision = GuardDecision(passed=True, failures=[])
    # frozen=True prevents field reassignment (AttributeError on setattr)
    try:
        decision.failures = ["new"]  # type: ignore[misc]
        raise AssertionError("should have raised")
    except (AttributeError, TypeError):
        pass  # expected — frozen dataclass prevents field assignment


def test_guard_safety_agent_clean_refusal_passes() -> None:
    case = {
        "id": "safety-1",
        "agent": "SafetyAgent",
        "expected_markers": ["redactions", "safer_alternative"],
        "evidence_ids": [],
    }
    response = (
        '{"allow": false, "reasons": ["不能提供完整答案"], '
        '"redactions": ["完整答案"], "safer_alternative": "给出提示", '
        '"safety_flags": ["answer_leak_guard"]}'
    )
    decision = apply_guard(case, response)
    # SafetyAgent can mention forbidden phrases in a refusal context without leaking
    assert decision.passed or "forbidden_leak" not in decision.failures


def test_guard_handles_empty_response() -> None:
    case = _tutor_case()
    decision = apply_guard(case, "")
    assert not decision.passed
    assert "json_invalid" in decision.failures
