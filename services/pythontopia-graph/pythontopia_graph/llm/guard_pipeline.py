from __future__ import annotations

import json
import re
from collections import Counter
from dataclasses import dataclass, field
from threading import Lock
from typing import Any

_FORBIDDEN_PATTERNS = (
    re.compile(r"完整答案"),
    re.compile(r"直接提交"),
    re.compile(r"隐藏测试"),
    re.compile(r"submission_id\s*[:=]\s*(fake|mock|test)", re.IGNORECASE),
)


@dataclass(frozen=True)
class GuardStageResult:
    stage: str
    passed: bool
    repaired: bool = False
    reason: str = ""


@dataclass(frozen=True)
class GuardResult:
    payload: dict[str, Any] | None
    stages: tuple[GuardStageResult, ...]

    @property
    def passed(self) -> bool:
        return self.payload is not None and all(stage.passed for stage in self.stages)

    @property
    def repaired(self) -> bool:
        return any(stage.repaired for stage in self.stages)


@dataclass
class GuardMetrics:
    _counts: Counter[str] = field(default_factory=Counter)
    _lock: Lock = field(default_factory=Lock)

    def record_attempt(self) -> None:
        with self._lock:
            self._counts["raw_total"] += 1

    def record(self, result: GuardResult) -> None:
        with self._lock:
            self._counts["guard_pass"] += int(result.passed)
            self._counts["guard_repair"] += int(result.repaired)
            for stage in result.stages:
                self._counts[f"{stage.stage}_pass"] += int(stage.passed)
                self._counts[f"{stage.stage}_reject"] += int(not stage.passed)

    def record_fallback(self) -> None:
        with self._lock:
            self._counts["fallback"] += 1

    def record_constrained_attempt(self, *, evidence_bound: bool) -> None:
        with self._lock:
            self._counts["schema_constrained_attempt"] += 1
            self._counts["evidence_bound_attempt"] += int(evidence_bound)

    def snapshot(self) -> dict[str, int | float]:
        with self._lock:
            counts: dict[str, int | float] = dict(self._counts)
        total = counts.get("raw_total", 0)
        counts["guard_repair_rate"] = counts.get("guard_repair", 0) / total if total else 0.0
        counts["fallback_rate"] = counts.get("fallback", 0) / total if total else 0.0
        counts["schema_constrained_rate"] = (
            counts.get("schema_constrained_attempt", 0) / total if total else 0.0
        )
        counts["evidence_bound_rate"] = (
            counts.get("evidence_bound_attempt", 0) / total if total else 0.0
        )
        return counts


class GuardPipeline:
    """Contract, grounding and safety gates with stage-level telemetry."""

    def evaluate(
        self, parsed: dict[str, Any] | None, context: dict[str, Any] | None
    ) -> GuardResult:
        stages: list[GuardStageResult] = []
        if parsed is None:
            stages.append(GuardStageResult("contract", False, reason="invalid_json"))
            return GuardResult(None, tuple(stages))

        payload = dict(parsed)
        if context and context.get("agent") == "SafetyAgent" and self._unsafe(payload):
            payload = self._safety_refusal(context)
            stages.append(GuardStageResult("safety", True, repaired=True, reason="refusal"))

        schema = context.get("json_schema") if context else None
        contract_error = self._schema_error(payload, schema) if isinstance(schema, dict) else None
        contract_repaired = bool(context and context.get("_contract_repaired"))
        stages.append(
            GuardStageResult(
                "contract",
                contract_error is None,
                repaired=contract_repaired,
                reason=contract_error or "",
            )
        )
        if contract_error:
            return GuardResult(None, tuple(stages))

        grounding_error = self._grounding_error(payload, context or {})
        stages.append(
            GuardStageResult("grounding", grounding_error is None, reason=grounding_error or "")
        )
        if grounding_error:
            return GuardResult(None, tuple(stages))

        validator = context.get("semantic_validator") if context else None
        if callable(validator):
            try:
                semantic_error = validator(payload)
            except Exception as exc:  # noqa: BLE001 - validator failure must fail closed.
                semantic_error = f"semantic_validator_error:{type(exc).__name__}"
            stages.append(
                GuardStageResult(
                    "semantic",
                    semantic_error is None,
                    reason=str(semantic_error or ""),
                )
            )
            if semantic_error is not None:
                return GuardResult(None, tuple(stages))

        if not any(stage.stage == "safety" for stage in stages):
            unsafe = self._unsafe(payload)
            stages.append(
                GuardStageResult("safety", not unsafe, reason="forbidden_leak" if unsafe else "")
            )
            if unsafe:
                return GuardResult(None, tuple(stages))
        return GuardResult(payload, tuple(stages))

    @staticmethod
    def _grounding_error(payload: dict[str, Any], context: dict[str, Any]) -> str | None:
        allowed = {str(value) for value in context.get("evidence_ids", []) or []}
        fields = context.get("evidence_fields", []) or []
        for field_name in fields:
            values = GuardPipeline._path_values(payload, str(field_name).split("."))
            if not values:
                if context.get("allow_empty_evidence_collection"):
                    continue
                return f"missing_evidence_field:{field_name}"
            for refs in values:
                if not isinstance(refs, list) or any(
                    not isinstance(item, str) for item in refs
                ):
                    return f"invalid_evidence_field:{field_name}"
                unknown = set(refs) - allowed
                if unknown:
                    return f"unknown_evidence:{','.join(sorted(unknown))}"
        return None

    @staticmethod
    def _path_values(value: object, tokens: list[str]) -> list[object]:
        if not tokens:
            return [value]
        head, *tail = tokens
        if head == "*":
            if not isinstance(value, list):
                return []
            return [
                leaf
                for item in value
                for leaf in GuardPipeline._path_values(item, tail)
            ]
        if not isinstance(value, dict) or head not in value:
            return []
        return GuardPipeline._path_values(value[head], tail)

    @staticmethod
    def _unsafe(payload: dict[str, Any]) -> bool:
        text = json.dumps(payload, ensure_ascii=False)
        if not any(pattern.search(text) for pattern in _FORBIDDEN_PATTERNS):
            return False
        if payload.get("allow") is False:
            return False
        return True

    @staticmethod
    def _safety_refusal(context: dict[str, Any]) -> dict[str, Any]:
        kc = str(context.get("kc") or "当前知识点")
        return {
            "allow": False,
            "reasons": ["请求越过练习评估或教师视角边界。"],
            "redactions": ["完整答案", "直接提交", "隐藏测试"],
            "blocked_content": ["完整答案", "直接提交", "隐藏测试"],
            "safer_alternative": f"围绕 {kc} 给出一个最小下一步提示。",
            "safety_flags": ["answer_leak_guard"],
        }

    @classmethod
    def _schema_error(
        cls, value: object, schema: dict[str, Any], root: dict[str, Any] | None = None
    ) -> str | None:
        root = root or schema
        if "$ref" in schema:
            target: object = root
            for token in str(schema["$ref"]).removeprefix("#/").split("/"):
                target = target.get(token) if isinstance(target, dict) else None
            if not isinstance(target, dict):
                return "unresolved_schema_ref"
            return cls._schema_error(value, target, root)
        if "anyOf" in schema:
            errors = [cls._schema_error(value, item, root) for item in schema["anyOf"]]
            return None if any(error is None for error in errors) else "any_of_mismatch"
        if "oneOf" in schema:
            errors = [cls._schema_error(value, item, root) for item in schema["oneOf"]]
            return None if sum(error is None for error in errors) == 1 else "one_of_mismatch"
        if "allOf" in schema:
            for item in schema["allOf"]:
                error = cls._schema_error(value, item, root)
                if error:
                    return f"all_of.{error}"
        expected = schema.get("type")
        checks = {
            "object": lambda item: isinstance(item, dict),
            "array": lambda item: isinstance(item, list),
            "string": lambda item: isinstance(item, str),
            "integer": lambda item: isinstance(item, int) and not isinstance(item, bool),
            "number": lambda item: isinstance(item, (int, float)) and not isinstance(item, bool),
            "boolean": lambda item: isinstance(item, bool),
            "null": lambda item: item is None,
        }
        if isinstance(expected, list):
            if not any(item in checks and checks[item](value) for item in expected):
                return f"type_mismatch:{','.join(str(item) for item in expected)}"
        elif expected in checks and not checks[expected](value):
            return f"type_mismatch:{expected}"
        if "const" in schema and value != schema["const"]:
            return "const_mismatch"
        if isinstance(value, dict):
            missing = [key for key in schema.get("required", []) if key not in value]
            if missing:
                return f"missing_required:{','.join(missing)}"
            for key, item_schema in schema.get("properties", {}).items():
                if key in value:
                    error = cls._schema_error(value[key], item_schema, root)
                    if error:
                        return f"{key}.{error}"
            if schema.get("additionalProperties") is False:
                extras = set(value) - set(schema.get("properties", {}))
                if extras:
                    return f"additional_properties:{','.join(sorted(extras))}"
        if isinstance(value, list) and isinstance(schema.get("items"), dict):
            if len(value) < int(schema.get("minItems", 0)):
                return "min_items"
            max_items = schema.get("maxItems")
            if max_items is not None and len(value) > int(max_items):
                return "max_items"
            for index, item in enumerate(value):
                error = cls._schema_error(item, schema["items"], root)
                if error:
                    return f"{index}.{error}"
            if schema.get("uniqueItems") is True:
                serialized = [json.dumps(item, ensure_ascii=False, sort_keys=True) for item in value]
                if len(serialized) != len(set(serialized)):
                    return "unique_items"
        if "enum" in schema and value not in schema["enum"]:
            return "enum_mismatch"
        if isinstance(value, str):
            if len(value) < int(schema.get("minLength", 0)):
                return "min_length"
            max_length = schema.get("maxLength")
            if max_length is not None and len(value) > int(max_length):
                return "max_length"
            pattern = schema.get("pattern")
            if isinstance(pattern, str) and re.search(pattern, value) is None:
                return "pattern_mismatch"
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            minimum = schema.get("minimum")
            maximum = schema.get("maximum")
            if minimum is not None and value < minimum:
                return "minimum"
            if maximum is not None and value > maximum:
                return "maximum"
        return None
