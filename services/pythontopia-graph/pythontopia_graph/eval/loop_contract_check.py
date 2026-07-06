"""Executable gate for the frozen Agentic loop contract document."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from pythontopia_graph.schemas.loop_contract import (
    AgentToolCall,
    EvalRecord,
    EventEnvelope,
    LoopCheckpoint,
    LoopResult,
)


@dataclass(frozen=True)
class LoopContractCheckSummary:
    ok: bool
    schema_version: str
    checked_models: list[str]
    errors: list[str]


_MODEL_FIELDS = {
    "event_envelope_required_fields": EventEnvelope,
    "checkpoint_required_fields": LoopCheckpoint,
    "agent_tool_call_required_fields": AgentToolCall,
    "loop_result_required_fields": LoopResult,
    "eval_record_required_fields": EvalRecord,
}


def _as_set(value: Any) -> set[str]:
    if not isinstance(value, list):
        return set()
    return {str(item) for item in value}


def check_loop_contract(contract_path: Path) -> LoopContractCheckSummary:
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    errors: list[str] = []

    if contract.get("schema_version") != "loop-contract.v1":
        errors.append("schema_version must be loop-contract.v1")

    for contract_key, model in _MODEL_FIELDS.items():
        documented = _as_set(contract.get(contract_key))
        actual = set(model.model_fields)
        missing = sorted(actual - documented)
        extra = sorted(documented - actual)
        if missing:
            errors.append(f"{contract_key} missing model fields: {', '.join(missing)}")
        if extra:
            errors.append(f"{contract_key} has unknown fields: {', '.join(extra)}")

    loop_types = _as_set(contract.get("loop_types"))
    expected_loop_types = {"LEARNER", "DEBUG", "RESOURCE", "WORLD", "IMPROVE"}
    if loop_types != expected_loop_types:
        errors.append(f"loop_types mismatch: expected {sorted(expected_loop_types)}, got {sorted(loop_types)}")

    gates = contract.get("gates", {})
    if not isinstance(gates, dict):
        errors.append("gates must be an object")
    else:
        if gates.get("schema_validity") != 1.0:
            errors.append("gates.schema_validity must be 1.0")
        for key in ("trace_id_required", "policy_version_required", "evidence_refs_required"):
            if gates.get(key) is not True:
                errors.append(f"gates.{key} must be true")

    terminal_mapping = contract.get("agui_terminal_mapping", {})
    if not isinstance(terminal_mapping, dict) or terminal_mapping.get("RUN_FINISHED.result") != "LoopResult":
        errors.append("agui_terminal_mapping.RUN_FINISHED.result must map to LoopResult")

    return LoopContractCheckSummary(
        ok=not errors,
        schema_version=str(contract.get("schema_version", "")),
        checked_models=[model.__name__ for model in _MODEL_FIELDS.values()],
        errors=errors,
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate docs/contracts/loop_contract_check.json")
    parser.add_argument("contract_path", type=Path)
    args = parser.parse_args(argv)

    summary = check_loop_contract(args.contract_path)
    if summary.ok:
        print(f"OK {summary.schema_version}: {', '.join(summary.checked_models)}")
        return 0
    print("FAILED loop contract check")
    for error in summary.errors:
        print(f"- {error}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
