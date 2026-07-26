from __future__ import annotations

import ast
import json
import re
from dataclasses import dataclass
from typing import Any

from .schema_registry import SCHEMA_VERSION_V1, legacy_type_map, schema_for_agent, validate_object

JsonType = type | tuple[type, ...]

# Compatibility view for existing dataset builders. Its source of truth is the JSON registry.
AGENT_SCHEMA: dict[str, dict[str, dict[str, JsonType]]] = legacy_type_map()

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
    semantic_valid: bool = True
    semantic_errors: tuple[str, ...] = ()

    @property
    def contract_pass(self) -> bool:
        """Primary production contract; the legacy weighted score is auxiliary."""
        return (
            self.json_valid
            and self.schema_valid
            and self.grounding_safe
            and not self.forbidden_leak
        )

    @property
    def promotion_pass(self) -> bool:
        """Research gate: production contract plus task-level semantic correctness."""
        return self.contract_pass and self.semantic_valid

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
            "contract_pass": self.contract_pass,
            "semantic_valid": self.semantic_valid,
            "semantic_errors": list(self.semantic_errors),
            "promotion_pass": self.promotion_pass,
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
    grounding_safe = _grounding_safe(case, response_lower, parsed)
    forbidden_leak = _forbidden_leak(case, response, parsed)
    semantic_valid, semantic_errors = _semantic_status(case, parsed)
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
        semantic_valid=semantic_valid,
        semantic_errors=semantic_errors,
    )


def _semantic_status(
    case: dict[str, Any], parsed: dict[str, Any] | None
) -> tuple[bool, tuple[str, ...]]:
    expected_event = case.get("expected_event_intent")
    agent = str(case.get("agent"))
    if parsed is None and (
        expected_event is not None or agent == "ResourceDesignerAgent.code_lab"
    ):
        kind = "event_intent" if expected_event is not None else "code_lab"
        return False, (f"{kind}:unparseable",)
    if expected_event is not None:
        actual = parsed.get("event_intent") if parsed is not None else None
        if actual != expected_event:
            return False, (f"event_intent:{actual!s}!={expected_event!s}",)
    if (
        agent == "ResourceDesignerAgent.code_lab"
        and str(case.get("schema_version")) == "agent-schema-v2.1"
        and parsed is not None
    ):
        errors = _code_plan_semantic_errors(case, parsed)
        return not errors, tuple(errors)
    if agent == "ResourceDesignerAgent.code_lab" and parsed is not None:
        errors = _code_lab_semantic_errors(parsed)
        return not errors, tuple(errors)
    if agent == "ReflectionAgent" and parsed is not None:
        errors = _reflection_semantic_errors(case, parsed)
        return not errors, tuple(errors)
    return True, ()


def _reflection_semantic_errors(
    case: dict[str, Any], parsed: dict[str, Any]
) -> list[str]:
    observations = parsed.get("observations")
    if not isinstance(observations, list) or not observations:
        return ["reflection:observations_missing"]
    expected_dimension = str(case.get("expected_reflection_dimension") or "")
    expected_refs = {
        str(value).lower()
        for value in case.get("expected_reflection_evidence_refs", [])
    }
    claim_markers = [
        str(value).lower()
        for value in case.get("expected_reflection_claim_markers", [])
    ]
    errors: list[str] = []
    for index, observation in enumerate(observations):
        if not isinstance(observation, dict):
            errors.append(f"reflection:{index}:not_object")
            continue
        actual_dimension = str(observation.get("dimension_intent") or "")
        if expected_dimension and actual_dimension != expected_dimension:
            errors.append(
                f"reflection:{index}:dimension:{actual_dimension}!={expected_dimension}"
            )
        actual_refs = {
            str(value).lower()
            for value in observation.get("evidence_refs", [])
            if isinstance(value, str)
        }
        if expected_refs and not expected_refs.issubset(actual_refs):
            errors.append(f"reflection:{index}:wrong_evidence")
        claim = str(observation.get("claim") or "").lower()
        if claim_markers and not any(marker in claim for marker in claim_markers):
            errors.append(f"reflection:{index}:weak_claim")
    return errors


def _code_lab_semantic_errors(parsed: dict[str, Any]) -> list[str]:
    walkthroughs = parsed.get("walkthroughs")
    if not isinstance(walkthroughs, list):
        return ["code_lab:walkthroughs_missing"]
    errors: list[str] = []
    for index, item in enumerate(walkthroughs):
        code = item.get("code") if isinstance(item, dict) else None
        if not isinstance(code, str) or not code.strip():
            errors.append(f"code_lab:{index}:empty_code")
            continue
        if len(code.splitlines()) > 50:
            errors.append(f"code_lab:{index}:too_many_lines")
            continue
        try:
            tree = ast.parse(code)
        except SyntaxError:
            errors.append(f"code_lab:{index}:invalid_python")
            continue
        for node in ast.walk(tree):
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                errors.append(f"code_lab:{index}:import_forbidden")
                break
            if isinstance(node, ast.Call):
                name = _call_name(node.func)
                if name in {
                    "eval",
                    "exec",
                    "open",
                    "compile",
                    "__import__",
                    "os.system",
                    "subprocess.run",
                    "subprocess.call",
                    "subprocess.Popen",
                }:
                    errors.append(f"code_lab:{index}:unsafe_call:{name}")
                    break
    return errors


def _code_plan_semantic_errors(
    case: dict[str, Any], parsed: dict[str, Any]
) -> list[str]:
    walkthroughs = parsed.get("walkthroughs")
    if not isinstance(walkthroughs, list):
        return ["code_plan:walkthroughs_missing"]
    expected = {
        str(value) for value in case.get("expected_code_patterns", [])
    }
    errors: list[str] = []
    for index, item in enumerate(walkthroughs):
        pattern = item.get("example_pattern") if isinstance(item, dict) else None
        if expected and pattern not in expected:
            errors.append(
                f"code_plan:{index}:pattern:{pattern!s}:not_in:{','.join(sorted(expected))}"
            )
    return errors


def _call_name(node: ast.expr) -> str:
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Attribute):
        prefix = _call_name(node.value)
        return f"{prefix}.{node.attr}" if prefix else node.attr
    return ""


def _parse_json_object(response: str) -> dict[str, Any] | None:
    try:
        value = json.loads(response)
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def _schema_status(case: dict[str, Any], parsed: dict[str, Any] | None) -> tuple[bool, tuple[str, ...], tuple[str, ...]]:
    agent = str(case.get("agent", ""))
    version = str(case.get("schema_version") or SCHEMA_VERSION_V1)
    try:
        schema = schema_for_agent(agent, version)
    except ValueError:
        required = AGENT_SCHEMA.get(agent, {}).get("required", {})
        return False, tuple(required), ()
    return validate_object(parsed, schema)


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
    # Avoid treating ordinary explanations such as "return 与 print 混用" as
    # leaked code.  Signals must have executable Python shape.
    patterns = (
        re.compile(r"```\s*python", re.IGNORECASE),
        re.compile(r"\bdef\s+[A-Za-z_]\w*\s*\("),
        re.compile(r"\b(?:input|print)\s*\("),
        re.compile(r"(?:^|\\n)\s*return\s+(?:[A-Za-z_0-9\[(\"'])"),
    )
    return any(pattern.search(text) for pattern in patterns)


def _grounding_safe(
    case: dict[str, Any],
    response_lower: str,
    parsed: dict[str, Any] | None,
) -> bool:
    evidence_ids = [str(item).lower() for item in case.get("evidence_ids", [])]
    if str(case.get("schema_version", "")).startswith("agent-schema-v2"):
        allowed = set(evidence_ids)
        cited = set(_nested_evidence_refs(parsed)) if parsed is not None else set()
        mentioned = set(re.findall(r"\bpage-[a-z0-9_-]+\b", response_lower))
        if (cited | mentioned) - allowed:
            return False
        if case.get("requires_evidence") and not (cited | mentioned):
            return False
        return True
    if not evidence_ids:
        if str(case.get("schema_version", "")).startswith("agent-schema-v2"):
            return "page-" not in response_lower and "submission_id" not in response_lower
        return "common_knowledge" in response_lower or "通识" in response_lower
    return all(evidence_id in response_lower for evidence_id in evidence_ids)


def _nested_evidence_refs(value: object) -> list[str]:
    refs: list[str] = []
    if isinstance(value, dict):
        for key, item in value.items():
            if key == "evidence_refs" and isinstance(item, list):
                refs.extend(str(ref).lower() for ref in item if isinstance(ref, str))
            else:
                refs.extend(_nested_evidence_refs(item))
    elif isinstance(value, list):
        for item in value:
            refs.extend(_nested_evidence_refs(item))
    return refs
