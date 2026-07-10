from pyt_agent_lab.promotion_gate import evaluate_promotion


def _suite(contract, schema, leak=0.0, repair=0.1, fallback=0.02):  # noqa: ANN001
    return {
        "raw": {
            "contract_pass_rate": contract,
            "schema_valid_rate": schema,
            "forbidden_leak_rate": leak,
        },
        "guard_repair_rate": repair,
        "fallback_rate": fallback,
        "safety_sensitive_forbidden_leak_rate": leak,
        "critical_slices": {"safety_sensitive": contract},
    }


def test_candidate_cannot_promote_without_full_eval_and_blind_review() -> None:
    champion = {"golden_50": _suite(0.7, 0.75), "stratified_500": _suite(0.65, 0.73)}
    candidate = {"golden_50": _suite(0.8, 0.85), "stratified_500": _suite(0.75, 0.8)}

    decision = evaluate_promotion(champion_suites=champion, candidate_suites=candidate)

    assert decision["eligible_for_full_5000"] is True
    assert decision["promoted"] is False
    assert decision["full_5000_completed"] is False


def test_perfect_golden_schema_may_tie_when_stratified_schema_improves() -> None:
    champion = {"golden_50": _suite(1.0, 1.0), "stratified_500": _suite(0.7, 0.8)}
    candidate = {"golden_50": _suite(1.0, 1.0), "stratified_500": _suite(0.8, 0.82)}

    decision = evaluate_promotion(champion_suites=champion, candidate_suites=candidate)

    assert decision["eligible_for_full_5000"] is True
    assert any(
        check["name"] == "golden_50:schema_non_regression" and check["passed"]
        for check in decision["checks"]
    )


def test_schema_regression_blocks_candidate() -> None:
    champion = {"golden_50": _suite(0.7, 0.8), "stratified_500": _suite(0.7, 0.8)}
    candidate = {"golden_50": _suite(0.8, 0.79), "stratified_500": _suite(0.8, 0.79)}

    decision = evaluate_promotion(champion_suites=champion, candidate_suites=candidate)

    assert decision["eligible_for_full_5000"] is False
    assert decision["promoted"] is False


def test_safety_sensitive_leak_blocks_candidate_even_when_overall_metrics_improve() -> None:
    champion = {"golden_50": _suite(0.7, 0.75), "stratified_500": _suite(0.7, 0.75)}
    candidate = {
        "golden_50": _suite(0.8, 0.85, leak=0.01),
        "stratified_500": _suite(0.8, 0.85, leak=0.01),
    }

    decision = evaluate_promotion(champion_suites=champion, candidate_suites=candidate)

    assert decision["eligible_for_full_5000"] is False
    assert any(
        check["name"].endswith("safety_sensitive_zero_leak") and not check["passed"]
        for check in decision["checks"]
    )


def test_overall_leak_may_match_champion_when_safety_sensitive_slice_is_clean() -> None:
    champion = {
        "golden_50": _suite(0.7, 0.75, leak=0.01),
        "stratified_500": _suite(0.7, 0.75, leak=0.01),
    }
    candidate = {
        "golden_50": _suite(0.8, 0.85, leak=0.01),
        "stratified_500": _suite(0.8, 0.85, leak=0.01),
    }
    for suite in candidate.values():
        suite["safety_sensitive_forbidden_leak_rate"] = 0.0

    decision = evaluate_promotion(champion_suites=champion, candidate_suites=candidate)

    assert decision["eligible_for_full_5000"] is True
    assert all(
        check["passed"]
        for check in decision["checks"]
        if check["name"].endswith("forbidden_leak_non_regression")
    )
