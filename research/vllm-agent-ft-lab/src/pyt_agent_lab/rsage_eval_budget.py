"""Ordered evaluation-budget gates for the R-SAGE research pipeline."""

from __future__ import annotations

import hashlib
import json
import os
import sys
import tempfile
from collections.abc import Mapping, Sequence
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path
from typing import Literal

from .rsage_generalization_gate import GateDecision, SeedGateReport, aggregate_seed_gates


EvalStage = Literal[
    "golden50",
    "stratified500",
    "three_seed",
    "fresh_full5000_v2",
]
GateStateStatus = Literal["active", "ready_for_final", "rejected", "completed"]

GATE_ORDER: tuple[EvalStage, ...] = (
    "golden50",
    "stratified500",
    "three_seed",
    "fresh_full5000_v2",
)
EXPECTED_CASE_COUNTS: dict[EvalStage, int] = {
    "golden50": 50,
    "stratified500": 500,
    "three_seed": 500,
    "fresh_full5000_v2": 5000,
}
BASELINE_SCRIPT = "research/vllm-agent-ft-lab/scripts/run_baseline.py"


class EvaluationGateError(RuntimeError):
    """Base error for evaluation-budget state violations."""


class StageOrderError(EvaluationGateError):
    """Raised when a stage is attempted out of order."""


class CandidateRejectedError(EvaluationGateError):
    """Raised when work is attempted after a candidate was rejected."""


class FreshFull5000ManifestError(EvaluationGateError):
    """Raised when a dataset is not eligible for the one final evaluation."""


class Full5000BudgetConsumedError(EvaluationGateError):
    """Raised when another identity has already claimed the final budget."""


@dataclass(frozen=True)
class StageRecord:
    stage: EvalStage
    status: Literal["passed", "rejected"]
    case_count: int
    gate_status: str
    reason_codes: tuple[str, ...]
    artifact_sha256: str
    message_zh: str


@dataclass(frozen=True)
class EvaluationGateState:
    candidate_id: str
    adapter_sha256: str
    demo: bool
    status: GateStateStatus
    records: tuple[StageRecord, ...]
    rejection_reason_codes: tuple[str, ...]
    message_zh: str


@dataclass(frozen=True)
class PlannedEvaluationCommand:
    stage: EvalStage
    seed: int
    expected_case_count: int
    argv: tuple[str, ...]


def create_gate_state(
    *,
    candidate_id: str,
    adapter_sha256: str,
    demo: bool = False,
) -> EvaluationGateState:
    if not candidate_id.strip():
        raise ValueError("candidate_id must not be empty")
    if not adapter_sha256.strip():
        raise ValueError("adapter_sha256 must not be empty")
    return EvaluationGateState(
        candidate_id=candidate_id,
        adapter_sha256=adapter_sha256,
        demo=demo,
        status="active",
        records=(),
        rejection_reason_codes=(),
        message_zh="候选已登记，下一阶段为 golden50。",
    )


def next_stage(state: EvaluationGateState) -> EvalStage | None:
    if state.status in {"rejected", "completed"}:
        return None
    if len(state.records) >= len(GATE_ORDER):
        return None
    return GATE_ORDER[len(state.records)]


def record_evaluation_stage(
    state: EvaluationGateState,
    *,
    stage: EvalStage,
    case_count: int,
    gate: GateDecision,
    artifact_sha256: str,
    accepted: bool = True,
) -> EvaluationGateState:
    if state.status == "rejected":
        raise CandidateRejectedError("候选已被拒绝，不能继续评测")
    expected_stage = next_stage(state)
    if stage != expected_stage:
        raise StageOrderError(f"下一阶段必须是 {expected_stage}，不能记录 {stage}")
    if stage == "three_seed":
        raise ValueError("three_seed 必须通过 record_seed_stage 聚合三个种子")
    if stage == "fresh_full5000_v2":
        raise EvaluationGateError(
            "fresh_full5000_v2 必须通过 record_final_stage 校验 receipt 后登记"
        )
    expected_count = EXPECTED_CASE_COUNTS[stage]
    if case_count != expected_count:
        raise ValueError(f"{stage} 必须恰好包含 {expected_count} 条案例")

    rejected = gate.status == "hard_stop" or not accepted
    reasons = list(gate.reason_codes)
    if not accepted:
        reasons.append("CANDIDATE_REJECTED")
    reason_codes = tuple(dict.fromkeys(reasons))
    record = StageRecord(
        stage=stage,
        status="rejected" if rejected else "passed",
        case_count=case_count,
        gate_status=gate.status,
        reason_codes=reason_codes,
        artifact_sha256=artifact_sha256,
        message_zh=gate.message_zh if gate.status == "hard_stop" else "阶段门禁已通过。",
    )
    records = (*state.records, record)
    if rejected:
        return replace(
            state,
            status="rejected",
            records=records,
            rejection_reason_codes=reason_codes,
            message_zh="候选已被拒绝，后续评测预算冻结。",
        )
    status: GateStateStatus = "completed" if stage == "fresh_full5000_v2" else "active"
    return replace(
        state,
        status=status,
        records=records,
        message_zh=f"{stage} 已通过。",
    )


def record_seed_stage(
    state: EvaluationGateState,
    reports: Sequence[SeedGateReport],
    *,
    artifact_sha256: str,
) -> EvaluationGateState:
    """Aggregate registered seeds with the shared generalization gate."""

    decision = aggregate_seed_gates(reports)
    return _record_seed_decision(state, decision, artifact_sha256=artifact_sha256)


def record_final_stage(
    state: EvaluationGateState,
    *,
    case_count: int,
    gate: GateDecision,
    artifact_sha256: str,
    manifest: Mapping[str, object],
    receipt: Mapping[str, object],
) -> EvaluationGateState:
    """Record final evidence only after validating the matching receipt contract."""

    if state.status == "rejected":
        raise CandidateRejectedError("候选已被拒绝，不能登记 final")
    if next_stage(state) != "fresh_full5000_v2":
        raise StageOrderError("只有通过 50→500→三种子后才能登记 final")
    if case_count != EXPECTED_CASE_COUNTS["fresh_full5000_v2"]:
        raise ValueError("fresh_full5000_v2 必须恰好包含 5000 条案例")
    audit = validate_fresh_full5000_manifest(manifest)
    if receipt.get("format") != "rsage_fresh_full5000_receipt_v2":
        raise EvaluationGateError("final receipt format 无效")
    identity = receipt.get("identity")
    if not isinstance(identity, Mapping) or any(
        (
            identity.get("candidate_id") != state.candidate_id,
            identity.get("adapter_sha256") != state.adapter_sha256,
            identity.get("dataset_sha256") != audit["dataset_sha256"],
        )
    ):
        raise EvaluationGateError("final receipt 与候选、Adapter 或数据集身份不匹配")
    if state.demo:
        if (
            receipt.get("status") != "demo_only"
            or receipt.get("demo") is not True
            or receipt.get("consumes_final_budget") is not False
        ):
            raise EvaluationGateError("demo final 只能使用不消耗预算的 demo receipt")
    else:
        result_gate = receipt.get("result_gate")
        if (
            receipt.get("status") != "completed"
            or receipt.get("demo") is not False
            or receipt.get("consumes_final_budget") is not True
            or not isinstance(result_gate, Mapping)
            or result_gate.get("status") != gate.status
        ):
            raise EvaluationGateError("正式 final 必须使用已完成且门禁结果匹配的真实 receipt")

    rejected = gate.status == "hard_stop"
    record = StageRecord(
        stage="fresh_full5000_v2",
        status="rejected" if rejected else "passed",
        case_count=case_count,
        gate_status=gate.status,
        reason_codes=gate.reason_codes,
        artifact_sha256=artifact_sha256,
        message_zh=gate.message_zh,
    )
    return replace(
        state,
        status="rejected" if rejected else "completed",
        records=(*state.records, record),
        rejection_reason_codes=gate.reason_codes if rejected else (),
        message_zh=(
            "fresh_full5000_v2 触发硬停止，候选最终拒绝。"
            if rejected
            else "fresh_full5000_v2 已完成，评测预算关闭。"
        ),
    )


def _record_seed_decision(
    state: EvaluationGateState,
    decision: GateDecision,
    *,
    artifact_sha256: str,
) -> EvaluationGateState:
    if state.status == "rejected":
        raise CandidateRejectedError("候选已被拒绝，不能继续评测")
    expected_stage = next_stage(state)
    if expected_stage != "three_seed":
        raise StageOrderError(f"下一阶段必须是 {expected_stage}，不能记录 three_seed")
    record = StageRecord(
        stage="three_seed",
        status="rejected" if decision.status == "hard_stop" else "passed",
        case_count=EXPECTED_CASE_COUNTS["three_seed"],
        gate_status=decision.status,
        reason_codes=decision.reason_codes,
        artifact_sha256=artifact_sha256,
        message_zh=decision.message_zh,
    )
    records = (*state.records, record)
    if decision.status == "hard_stop":
        return replace(
            state,
            status="rejected",
            records=records,
            rejection_reason_codes=decision.reason_codes,
            message_zh="三种子晋级失败，最终评测预算冻结。",
        )
    return replace(
        state,
        status="ready_for_final",
        records=records,
        message_zh="三种子晋级通过，可申请 fresh_full5000_v2 最终评测。",
    )


def plan_next_commands(
    state: EvaluationGateState,
    *,
    cases_path: str | Path,
    run_root: str | Path,
    base_url: str,
    model: str,
    adapter_path: str | Path,
    final_manifest: Mapping[str, object] | None = None,
    python_executable: str = sys.executable,
) -> tuple[PlannedEvaluationCommand, ...]:
    """Plan commands around the existing baseline scorer and report builder."""

    stage = next_stage(state)
    if stage is None:
        if state.status == "rejected":
            raise CandidateRejectedError("候选已被拒绝，没有下一条评测命令")
        return ()
    if stage == "fresh_full5000_v2":
        if final_manifest is None:
            raise FreshFull5000ManifestError(
                "规划最终评测必须提供 fresh_full5000_v2 manifest"
            )
        validate_fresh_full5000_manifest(final_manifest)
    seeds = (42, 43, 44) if stage == "three_seed" else (20260716,)
    commands: list[PlannedEvaluationCommand] = []
    for seed in seeds:
        run_dir = Path(run_root) / f"{stage}-seed{seed}"
        argv = (
            python_executable,
            BASELINE_SCRIPT,
            "--cases",
            str(cases_path),
            "--base-url",
            base_url,
            "--model",
            model,
            "--adapter-path",
            str(adapter_path),
            "--folds",
            "1",
            "--seed",
            str(seed),
            "--run-dir",
            str(run_dir),
            "--prompt-variant",
            f"rsage-{stage}",
            "--decoding-mode",
            "json_schema",
        )
        commands.append(
            PlannedEvaluationCommand(
                stage=stage,
                seed=seed,
                expected_case_count=EXPECTED_CASE_COUNTS[stage],
                argv=argv,
            )
        )
    return tuple(commands)


def classify_full5000_manifest(manifest: Mapping[str, object]) -> dict[str, object]:
    """Classify legacy 5000 packs without treating them as final evidence."""

    format_name = str(manifest.get("format", ""))
    version = str(manifest.get("eval_pack_version", ""))
    source_path = str(manifest.get("source_path", ""))
    identity = " ".join((format_name, version, source_path)).lower()
    explicit_legacy = any(
        marker in identity
        for marker in (
            "domain_cases_5000_v1",
            "5000-v1",
            "5000_v1",
        )
    )
    known_v1_slices = {
        "new_question": 2000,
        "new_chapter_slice": 1000,
        "new_student_expression": 1500,
        "web_workshop_input": 500,
    }
    legacy_signature = (
        not format_name
        and not version
        and manifest.get("actual_count") == 5000
        and manifest.get("seed") == 20260706
        and manifest.get("slice_counts") == known_v1_slices
    )
    legacy = explicit_legacy or legacy_signature
    if legacy:
        return {
            "role": "historical_regression_only",
            "eligible_for_final": False,
            "format": format_name,
            "eval_pack_version": version,
            "message_zh": "旧 full5000 与历史训练源有重叠，只能用于历史回归，不能作为正式 final。",
        }
    if format_name == "fresh_full5000_v2":
        return {
            "role": "fresh_final_candidate",
            "eligible_for_final": True,
            "format": format_name,
            "eval_pack_version": version,
            "message_zh": "数据集声明为 fresh_full5000_v2，仍需通过零历史重叠审计。",
        }
    return {
        "role": "unknown",
        "eligible_for_final": False,
        "format": format_name,
        "eval_pack_version": version,
        "message_zh": "数据集版本未知，不能作为正式 final。",
    }


def validate_fresh_full5000_manifest(
    manifest: Mapping[str, object],
) -> dict[str, object]:
    """Require a fresh v2 manifest with explicit zero historical overlap."""

    classification = classify_full5000_manifest(manifest)
    if classification["role"] == "historical_regression_only":
        raise FreshFull5000ManifestError(
            "旧 full5000 只能作历史回归，不可用于正式 final"
        )
    if manifest.get("format") != "fresh_full5000_v2":
        raise FreshFull5000ManifestError(
            "正式 final 的 manifest format 必须是 fresh_full5000_v2"
        )
    if manifest.get("actual_count") != 5000:
        raise FreshFull5000ManifestError("fresh_full5000_v2 必须恰好包含 5000 条案例")

    dataset_sha256 = str(manifest.get("dataset_sha256", ""))
    if not _is_sha256(dataset_sha256):
        raise FreshFull5000ManifestError("fresh_full5000_v2 manifest 缺少有效 dataset_sha256")
    overlap_audit = manifest.get("overlap_audit")
    if not isinstance(overlap_audit, Mapping):
        raise FreshFull5000ManifestError("fresh_full5000_v2 manifest 缺少 overlap_audit")
    historical_overlap = overlap_audit.get("historical_eval_overlap_count")
    declared_zero = overlap_audit.get("declared_zero_historical_overlap")
    training_overlap = overlap_audit.get("training_overlap_count")
    if historical_overlap != 0 or declared_zero is not True:
        raise FreshFull5000ManifestError(
            "fresh_full5000_v2 manifest 必须声明并证明零历史重叠"
        )
    if training_overlap != 0:
        raise FreshFull5000ManifestError(
            "fresh_full5000_v2 与训练数据必须零重叠"
        )
    return {
        "format": "fresh_full5000_v2",
        "actual_count": 5000,
        "dataset_sha256": dataset_sha256,
        "historical_eval_overlap_count": 0,
        "declared_zero_historical_overlap": True,
        "training_overlap_count": 0,
        "eligible_for_final": True,
        "message_zh": "fresh_full5000_v2 已声明并通过零历史重叠审计。",
    }


def claim_full5000_receipt(
    state: EvaluationGateState,
    *,
    manifest: Mapping[str, object],
    command: Sequence[str],
    receipt_path: str | Path,
    demo: bool = False,
) -> dict[str, object]:
    """Atomically claim the only fresh-full5000 budget, or return an exact resume."""

    if state.status != "ready_for_final" or next_stage(state) != "fresh_full5000_v2":
        raise StageOrderError("只有通过 50→500→三种子后才能申请 fresh_full5000_v2")
    if state.demo and not demo:
        raise EvaluationGateError("demo 状态禁止申请真实 final receipt")
    audit = validate_fresh_full5000_manifest(manifest)
    command_sha256 = _sha256_json(list(command))
    identity = {
        "candidate_id": state.candidate_id,
        "adapter_sha256": state.adapter_sha256,
        "dataset_sha256": audit["dataset_sha256"],
        "command_sha256": command_sha256,
    }
    receipt = {
        "format": "rsage_fresh_full5000_receipt_v2",
        "stage": "fresh_full5000_v2",
        "status": "demo_only" if demo else "claimed",
        "identity": identity,
        "fresh_manifest_audit": audit,
        "legacy_full5000_role": "historical_regression_only",
        "consumes_final_budget": not demo,
        "demo": demo,
        "claimed_at": _now(),
        "message_zh": (
            "演示仅生成隔离凭据，不占用真实 fresh_full5000_v2 预算。"
            if demo
            else "已原子占用一次 fresh_full5000_v2 最终评测预算。"
        ),
    }
    if demo:
        return receipt

    target = Path(receipt_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    payload = _json_bytes(receipt)
    try:
        descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        existing = _read_json_object(target)
        if (
            existing.get("format") == "rsage_fresh_full5000_receipt_v2"
            and existing.get("identity") == identity
        ):
            return existing
        raise Full5000BudgetConsumedError(
            f"fresh_full5000_v2 最终预算已被占用：{target}"
        ) from None
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
    except BaseException:
        target.unlink(missing_ok=True)
        raise
    return receipt


def complete_full5000_receipt(
    receipt_path: str | Path,
    *,
    gate: GateDecision,
    result_manifest_sha256: str,
) -> dict[str, object]:
    """Complete the already-consumed final budget while preserving gate outcome."""

    if not _is_sha256(result_manifest_sha256):
        raise ValueError("result_manifest_sha256 must be a SHA-256 digest")
    target = Path(receipt_path)
    receipt = _read_json_object(target)
    if receipt.get("format") != "rsage_fresh_full5000_receipt_v2":
        raise EvaluationGateError("不是 R-SAGE fresh_full5000_v2 receipt")
    gate_summary = {
        "status": gate.status,
        "reason_codes": list(gate.reason_codes),
        "message_zh": gate.message_zh,
    }
    if receipt.get("status") == "completed":
        if (
            receipt.get("result_manifest_sha256") == result_manifest_sha256
            and receipt.get("result_gate") == gate_summary
        ):
            return receipt
        raise Full5000BudgetConsumedError("final receipt 已完成，结果身份不可更改")
    if receipt.get("status") != "claimed" or receipt.get("demo") is True:
        raise EvaluationGateError("只有真实且已 claimed 的 final receipt 可以完成")

    completed = {
        **receipt,
        "status": "completed",
        "outcome": "rejected" if gate.status == "hard_stop" else "passed",
        "result_manifest_sha256": result_manifest_sha256,
        "result_gate": gate_summary,
        "completed_at": _now(),
        "message_zh": (
            "fresh_full5000_v2 已完成，但泛化门禁硬停止。"
            if gate.status == "hard_stop"
            else "fresh_full5000_v2 已完成并通过门禁。"
        ),
    }
    _write_json_atomic(target, completed)
    return completed


def _is_sha256(value: str) -> bool:
    return len(value) == 64 and all(char in "0123456789abcdef" for char in value.lower())


def _sha256_json(value: object) -> str:
    payload = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def _json_bytes(value: Mapping[str, object]) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")


def _read_json_object(path: Path) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise EvaluationGateError(f"JSON 根节点必须是对象：{path}")
    return value


def _write_json_atomic(path: Path, value: Mapping[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="wb",
        dir=path.parent,
        prefix=f".{path.name}.",
        suffix=".tmp",
        delete=False,
    ) as handle:
        temporary = Path(handle.name)
        handle.write(_json_bytes(value))
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, path)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()
