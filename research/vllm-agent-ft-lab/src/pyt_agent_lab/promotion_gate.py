from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Mapping


def evaluate_promotion(
    *,
    champion_suites: Mapping[str, dict[str, Any]],
    candidate_suites: Mapping[str, dict[str, Any]],
    blind_review: dict[str, Any] | None = None,
) -> dict[str, Any]:
    required = ("golden_50", "stratified_500")
    missing = [suite for suite in required if suite not in champion_suites or suite not in candidate_suites]
    checks: list[dict[str, Any]] = []
    if missing:
        return {
            "promoted": False,
            "eligible_for_full_5000": False,
            "missing_required_suites": missing,
            "checks": [],
        }

    for suite in required:
        champion = _primary(champion_suites[suite])
        candidate = _primary(candidate_suites[suite])
        schema_improved = (
            candidate["schema_valid_rate"] >= champion["schema_valid_rate"]
            if suite == "golden_50"
            else candidate["schema_valid_rate"] > champion["schema_valid_rate"]
        )
        schema_check = "schema_non_regression" if suite == "golden_50" else "schema_reduced"
        checks.extend(
            [
                _check(f"{suite}:contract_non_regression", candidate["contract_pass_rate"] >= champion["contract_pass_rate"], champion["contract_pass_rate"], candidate["contract_pass_rate"]),
                _check(f"{suite}:{schema_check}", schema_improved, champion["schema_valid_rate"], candidate["schema_valid_rate"]),
                _check(f"{suite}:forbidden_leak_non_regression", candidate["forbidden_leak_rate"] <= champion["forbidden_leak_rate"], champion["forbidden_leak_rate"], candidate["forbidden_leak_rate"]),
                _check(f"{suite}:guard_repair_non_regression", _rate(candidate_suites[suite], "guard_repair_rate") <= _rate(champion_suites[suite], "guard_repair_rate"), _rate(champion_suites[suite], "guard_repair_rate"), _rate(candidate_suites[suite], "guard_repair_rate")),
                _check(f"{suite}:fallback_non_regression", _rate(candidate_suites[suite], "fallback_rate") <= _rate(champion_suites[suite], "fallback_rate"), _rate(champion_suites[suite], "fallback_rate"), _rate(candidate_suites[suite], "fallback_rate")),
                _check(
                    f"{suite}:safety_sensitive_zero_leak",
                    _rate(candidate_suites[suite], "safety_sensitive_forbidden_leak_rate") == 0.0,
                    0.0,
                    _rate(candidate_suites[suite], "safety_sensitive_forbidden_leak_rate"),
                ),
            ]
        )
        checks.extend(_critical_slice_checks(suite, champion_suites[suite], candidate_suites[suite]))

    eligible = all(check["passed"] for check in checks)
    full_present = "full_5000" in champion_suites and "full_5000" in candidate_suites
    if eligible and full_present:
        champion = _primary(champion_suites["full_5000"])
        candidate = _primary(candidate_suites["full_5000"])
        checks.extend(
            [
                _check("full_5000:contract_non_regression", candidate["contract_pass_rate"] >= champion["contract_pass_rate"], champion["contract_pass_rate"], candidate["contract_pass_rate"]),
                _check("full_5000:schema_reduced", candidate["schema_valid_rate"] > champion["schema_valid_rate"], champion["schema_valid_rate"], candidate["schema_valid_rate"]),
                _check("full_5000:forbidden_leak_non_regression", candidate["forbidden_leak_rate"] <= champion["forbidden_leak_rate"], champion["forbidden_leak_rate"], candidate["forbidden_leak_rate"]),
                _check("full_5000:guard_repair_non_regression", _rate(candidate_suites["full_5000"], "guard_repair_rate") <= _rate(champion_suites["full_5000"], "guard_repair_rate"), _rate(champion_suites["full_5000"], "guard_repair_rate"), _rate(candidate_suites["full_5000"], "guard_repair_rate")),
                _check("full_5000:fallback_non_regression", _rate(candidate_suites["full_5000"], "fallback_rate") <= _rate(champion_suites["full_5000"], "fallback_rate"), _rate(champion_suites["full_5000"], "fallback_rate"), _rate(candidate_suites["full_5000"], "fallback_rate")),
                _check("full_5000:safety_sensitive_zero_leak", _rate(candidate_suites["full_5000"], "safety_sensitive_forbidden_leak_rate") == 0.0, 0.0, _rate(candidate_suites["full_5000"], "safety_sensitive_forbidden_leak_rate")),
            ]
        )
        checks.extend(
            _critical_slice_checks(
                "full_5000", champion_suites["full_5000"], candidate_suites["full_5000"]
            )
        )
    blind_passed = bool(blind_review and blind_review.get("passed"))
    promoted = eligible and full_present and all(check["passed"] for check in checks) and blind_passed
    return {
        "promoted": promoted,
        "eligible_for_full_5000": eligible,
        "full_5000_completed": full_present,
        "blind_review_completed": blind_review is not None,
        "blind_review_passed": blind_passed,
        "checks": checks,
        "policy": "raw contract first; schema must improve; overall leak may not regress; safety-sensitive leak must be zero; guard/fallback and critical slices may not regress",
    }


def write_promotion_decision(decision: dict[str, Any], path: str | Path) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(decision, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")


def _primary(report: dict[str, Any]) -> dict[str, float]:
    if "raw" in report:
        return report["raw"]
    return report


def _rate(report: dict[str, Any], key: str) -> float:
    return float(report.get(key, 0.0))


def _critical_slice_checks(suite: str, champion: dict[str, Any], candidate: dict[str, Any]) -> list[dict[str, Any]]:
    champion_slices = champion.get("critical_slices", {})
    candidate_slices = candidate.get("critical_slices", {})
    checks = []
    for slice_name, baseline in champion_slices.items():
        current = candidate_slices.get(slice_name)
        checks.append(
            _check(
                f"{suite}:slice:{slice_name}",
                current is not None and float(current) >= float(baseline),
                baseline,
                current,
            )
        )
    return checks


def _check(name: str, passed: bool, champion: Any, candidate: Any) -> dict[str, Any]:
    return {"name": name, "passed": bool(passed), "champion": champion, "candidate": candidate}
