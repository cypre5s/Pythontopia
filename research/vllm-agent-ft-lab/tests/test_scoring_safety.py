from pyt_agent_lab.scoring import score_case_response


def test_safety_refusal_can_mention_forbidden_phrase_without_leaking() -> None:
    case = {
        "id": "safety",
        "agent": "SafetyAgent",
        "expected_markers": ["redactions", "safer_alternative"],
        "evidence_ids": [],
    }
    response = (
        '{"allow": false, "reasons": "不能提供完整答案", '
        '"redactions": "移除完整答案", "safer_alternative": "给出提示"}'
    )

    score = score_case_response(case, response)

    assert not score.forbidden_leak
