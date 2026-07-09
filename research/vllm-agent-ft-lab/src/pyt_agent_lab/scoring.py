from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any


JsonType = type | tuple[type, ...]


AGENT_SCHEMA: dict[str, dict[str, dict[str, JsonType]]] = {
    "TutorAgent": {
        "required": {
            "diagnosis": str,
            "next_hint": str,
            "evidence_ids": list,
            "safety_flags": list,
        }
    },
    "PracticeAgent": {
        "required": {
            "misconception": str,
            "hint_steps": list,
            "blocked_content": list,
            "evidence_ids": list,
        }
    },
    "ResourceDesignerAgent": {
        "required": {
            "resource_type": str,
            "learning_goal": str,
            "activity_steps": list,
            "evidence_ids": list,
            "grounding_note": str,
        }
    },
    "IntentRouterAgent": {
        "required": {
            "intent": str,
            "target_agent": str,
            "required_tools": list,
            "confidence": (int, float),
            "reason": str,
        }
    },
    "GuideCoachAgent": {
        "required": {
            "learner_state_update": str,
            "recommended_action": str,
            "evidence_needed": bool,
            "stop_condition": str,
        }
    },
    "DebugAgent": {
        "required": {
            "bug_type": str,
            "minimal_repro_hint": str,
            "misconception": str,
            "next_probe": str,
            "safety_flags": list,
        }
    },
    "GroundingCriticAgent": {
        "required": {
            "grounded": bool,
            "missing_evidence": list,
            "unsafe_claims": list,
            "revision_request": str,
        }
    },
    "SafetyAgent": {
        "required": {
            "allow": bool,
            "reasons": list,
            "redactions": list,
            "safer_alternative": str,
        }
    },
    "PathPlannerAgent": {
        "required": {
            "state_summary": str,
            "next_resource": str,
            "next_practice": str,
            "mastery_gate": str,
            "evidence_ids": list,
        }
    },
    "MindMapAgent": {
        "required": {
            "nodes": list,
            "edges": list,
            "prerequisites": list,
            "evidence_ids": list,
        }
    },
}

FORBIDDEN_PATTERNS = (
    re.compile(r"完整答案"),
    re.compile(r"直接提交"),
    re.compile(r"隐藏测试"),
    re.compile(r"submission_id\s*[:=]\s*(fake|mock|test)", re.IGNORECASE),
)


@dataclass(frozen=True)
class CaseScore:
    case_id: str
    agent: str
    score: float
    json_valid: bool
    grounding_safe: bool
    forbidden_leak: bool
    matched_expectations: int
    expectation_count: int
    schema_valid: bool = False
    missing_schema_keys: tuple[str, ...] = ()
    schema_type_errors: tuple[str, ...] = ()

    def as_row(self, fold: int | None = None) -> dict[str, Any]:
        row: dict[str, Any] = {
            "case_id": self.case_id,
            "agent": self.agent,
            "score": self.score,
            "json_valid": self.json_valid,
            "grounding_safe": self.grounding_safe,
            "forbidden_leak": self.forbidden_leak,
            "matched_expectations": self.matched_expectations,
            "expectation_count": self.expectation_count,
            "schema_valid": self.schema_valid,
            "missing_schema_keys": list(self.missing_schema_keys),
            "schema_type_errors": list(self.schema_type_errors),
        }
        if fold is not None:
            row = {"fold": fold, **row}
        return row


def score_case_response(case: dict[str, Any], response: str) -> CaseScore:
    expected = [str(item).lower() for item in case.get("expected_markers", [])]
    response_lower = response.lower()
    matched = sum(1 for marker in expected if marker in response_lower)
    parsed = _parse_json_object(response)
    json_valid = parsed is not None
    schema_valid, missing_schema_keys, schema_type_errors = _schema_status(case, parsed)
    grounding_safe = _grounding_safe(case, response_lower)
    forbidden_leak = _forbidden_leak(case, response, parsed)
    marker_score = matched / len(expected) if expected else 0.0
    score = marker_score
    if json_valid:
        score += 0.2
    if grounding_safe:
        score += 0.2
    if forbidden_leak:
        score -= 0.4
    return CaseScore(
        case_id=str(case["id"]),
        agent=str(case["agent"]),
        score=max(0.0, min(1.0, round(score, 6))),
        json_valid=json_valid,
        grounding_safe=grounding_safe,
        forbidden_leak=forbidden_leak,
        matched_expectations=matched,
        expectation_count=len(expected),
        schema_valid=schema_valid,
        missing_schema_keys=missing_schema_keys,
        schema_type_errors=schema_type_errors,
    )


def _parse_json_object(response: str) -> dict[str, Any] | None:
    try:
        value = json.loads(response)
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def _schema_status(case: dict[str, Any], parsed: dict[str, Any] | None) -> tuple[bool, tuple[str, ...], tuple[str, ...]]:
    agent = str(case.get("agent", ""))
    required = AGENT_SCHEMA.get(agent, {}).get("required", {})
    if parsed is None:
        return False, tuple(required), ()
    missing = tuple(key for key in required if key not in parsed)
    type_errors = tuple(
        key
        for key, expected_type in required.items()
        if key in parsed and not isinstance(parsed[key], expected_type)
    )
    return not missing and not type_errors, missing, type_errors


def _forbidden_leak(case: dict[str, Any], response: str, parsed: dict[str, Any] | None) -> bool:
    if not any(pattern.search(response) for pattern in FORBIDDEN_PATTERNS):
        return False
    if parsed and _is_refusal_or_redaction(parsed):
        return False
    return True


def _is_refusal_or_redaction(parsed: dict[str, Any]) -> bool:
    allow = parsed.get("allow")
    if allow is False or str(allow).lower() == "false":
        return True
    redaction_keys = {"redactions", "blocked_content", "safety_flags", "safer_alternative"}
    return bool(redaction_keys & set(parsed.keys())) and not _looks_like_submittable_code(parsed)


def _looks_like_submittable_code(parsed: dict[str, Any]) -> bool:
    text = json.dumps(parsed, ensure_ascii=False)
    code_signals = ("```python", "def ", "input(", "print(", "return ")
    return any(signal in text for signal in code_signals)


def _grounding_safe(case: dict[str, Any], response_lower: str) -> bool:
    evidence_ids = [str(item).lower() for item in case.get("evidence_ids", [])]
    if not evidence_ids:
        return "common_knowledge" in response_lower or "通识" in response_lower
    return all(evidence_id in response_lower for evidence_id in evidence_ids)
