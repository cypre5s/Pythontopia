from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import time
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal


PipelineOperation = Literal["demo", "prepare", "training"]
StageMode = Literal["demo", "full"]
StagePhase = Literal["prepare", "training", "evaluation"]

_MANIFEST_FORMAT = "rsage_pipeline_manifest_v1"
_SAFE_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")
_SECRET_KEY = re.compile(
    r"(?:API[_-]?KEY|TOKEN|SECRET|PASSWORD|PASSWD|CREDENTIAL|AUTH|COOKIE)",
    re.IGNORECASE,
)
_SECRET_FLAGS = {
    "--api-key",
    "--apikey",
    "--auth-token",
    "--password",
    "--secret",
    "--token",
}
_REDACTED = "***已脱敏***"


class PipelineError(RuntimeError):
    """R-SAGE 流水线的可预期、可面向 CLI 展示的错误。"""


class PipelineManifestError(PipelineError):
    """Manifest 不完整或存在不安全阶段定义。"""


class PipelineResumeError(PipelineError):
    """已有回执不能在当前 manifest 下安全恢复。"""


class PipelineDependencyError(PipelineError):
    """阶段依赖尚未形成有效完成回执。"""


class DemoReceiptGateError(PipelineError):
    """训练启动前的 demo 回执闸门未通过。"""

    def __init__(self, message: str, invalid_stage_ids: Sequence[str]) -> None:
        self.invalid_stage_ids = tuple(invalid_stage_ids)
        super().__init__(message)


class PipelineStageError(PipelineError):
    """子进程阶段失败；receipt 指向已保留的失败证据。"""

    def __init__(self, message: str, receipt: Mapping[str, Any]) -> None:
        self.receipt = dict(receipt)
        super().__init__(message)


@dataclass(frozen=True)
class _StageSpec:
    stage_id: str
    mode: StageMode
    phase: StagePhase
    command: tuple[str, ...]
    depends_on: tuple[str, ...]
    environment: dict[str, str]
    cwd: str | None


@dataclass(frozen=True)
class _PipelineSpec:
    pipeline_id: str
    run_root: Path
    environment: dict[str, str]
    stages: tuple[_StageSpec, ...]
    manifest_sha256: str


def command_sha256(command: Sequence[str]) -> str:
    """Hash an argv vector without shell quoting ambiguity."""

    items = _string_sequence(command, field="command", allow_empty=False)
    return hashlib.sha256("\0".join(items).encode("utf-8")).hexdigest()


def load_pipeline_manifest(path: str | Path) -> dict[str, Any]:
    """Load a JSON manifest and reject non-object roots."""

    source = Path(path)
    try:
        value = json.loads(source.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise PipelineManifestError(f"流水线 manifest 不存在：{source}") from exc
    except json.JSONDecodeError as exc:
        raise PipelineManifestError(f"流水线 manifest 不是有效 JSON：{source}：{exc}") from exc
    if not isinstance(value, Mapping):
        raise PipelineManifestError("流水线 manifest 顶层必须是 JSON 对象")
    return dict(value)


def run_rsage_pipeline(
    manifest: Mapping[str, Any] | str | Path,
    *,
    operation: PipelineOperation,
    resume: bool = False,
    selected_stages: Sequence[str] | None = None,
    from_stage: str | None = None,
    through_stage: str | None = None,
    run_root: str | Path | None = None,
) -> dict[str, Any]:
    """Run the selected manifest stages and persist an atomic receipt per transition."""

    raw_manifest = (
        load_pipeline_manifest(manifest)
        if isinstance(manifest, (str, Path))
        else dict(manifest)
    )
    spec = _validate_manifest(raw_manifest, run_root=run_root)
    stages = _select_stages(
        spec,
        operation=operation,
        selected_stages=selected_stages,
        from_stage=from_stage,
        through_stage=through_stage,
    )
    namespace = "demos" if operation == "demo" else "runs"
    mode: StageMode = "demo" if operation == "demo" else "full"
    target = spec.run_root / namespace / spec.pipeline_id
    target.mkdir(parents=True, exist_ok=True)

    if operation == "training":
        _require_demo_receipts(spec)

    results: list[dict[str, Any]] = []
    for stage in stages:
        _require_dependencies(spec, stage)
        result = _run_stage(
            spec,
            stage,
            run_dir=target,
            mode=mode,
            resume=resume,
        )
        results.append(result)

    completed_count = sum(result["action"] == "completed" for result in results)
    resumed_count = sum(result["action"] == "resume_skip" for result in results)
    summary = {
        "format": "rsage_pipeline_receipt_v1",
        "pipeline_id": spec.pipeline_id,
        "manifest_sha256": spec.manifest_sha256,
        "operation": operation,
        "status": "completed",
        "run_dir": str(target),
        "completed_stage_count": completed_count,
        "resumed_stage_count": resumed_count,
        "stages": results,
        "completed_at": _now(),
        "message_zh": (
            f"R-SAGE 流水线完成：完成阶段 {completed_count} 个，"
            f"恢复跳过 {resumed_count} 个；运行目录：{target}"
        ),
    }
    _write_json_atomic(target / "pipeline_receipt.json", summary)
    _append_event(
        target,
        {
            "event": "pipeline_completed",
            "operation": operation,
            "completed_stage_count": completed_count,
            "resumed_stage_count": resumed_count,
            "message_zh": summary["message_zh"],
        },
    )
    return summary


run_pipeline = run_rsage_pipeline


def _validate_manifest(
    manifest: Mapping[str, Any],
    *,
    run_root: str | Path | None,
) -> _PipelineSpec:
    if manifest.get("format") != _MANIFEST_FORMAT:
        raise PipelineManifestError(f"流水线 manifest format 必须是 {_MANIFEST_FORMAT}")
    pipeline_id = _safe_identifier(manifest.get("pipeline_id"), field="pipeline_id")
    root_value = run_root if run_root is not None else manifest.get("run_root")
    if not isinstance(root_value, (str, Path)) or not str(root_value).strip():
        raise PipelineManifestError("流水线 manifest 缺少非空 run_root")
    pipeline_environment = _environment_mapping(
        manifest.get("environment", {}),
        field="environment",
    )

    raw_stages = manifest.get("stages")
    if isinstance(raw_stages, (str, bytes)) or not isinstance(raw_stages, Sequence):
        raise PipelineManifestError("流水线 manifest 的 stages 必须是阶段数组")
    if not raw_stages:
        raise PipelineManifestError("流水线 manifest 至少需要一个阶段")

    stages: list[_StageSpec] = []
    seen: set[str] = set()
    for index, raw_stage in enumerate(raw_stages):
        if not isinstance(raw_stage, Mapping):
            raise PipelineManifestError(f"第 {index + 1} 个阶段必须是 JSON 对象")
        stage_id = _safe_identifier(raw_stage.get("id"), field=f"stages[{index}].id")
        if stage_id in seen:
            raise PipelineManifestError(f"流水线阶段 id 重复：{stage_id}")
        mode = raw_stage.get("mode")
        if mode not in {"demo", "full"}:
            raise PipelineManifestError(f"阶段 {stage_id} 的 mode 必须是 demo 或 full")
        phase = raw_stage.get("phase")
        if phase not in {"prepare", "training", "evaluation"}:
            raise PipelineManifestError(
                f"阶段 {stage_id} 的 phase 必须是 prepare、training 或 evaluation"
            )
        command = tuple(
            _string_sequence(
                raw_stage.get("command"),
                field=f"阶段 {stage_id} 的 command",
                allow_empty=False,
            )
        )
        dependencies = tuple(
            _string_sequence(
                raw_stage.get("depends_on", []),
                field=f"阶段 {stage_id} 的 depends_on",
                allow_empty=True,
            )
        )
        if len(dependencies) != len(set(dependencies)):
            raise PipelineManifestError(f"阶段 {stage_id} 的 depends_on 不能重复")
        unknown = [dependency for dependency in dependencies if dependency not in seen]
        if unknown:
            raise PipelineManifestError(
                f"阶段 {stage_id} 只能依赖 manifest 中更早的阶段：{'、'.join(unknown)}"
            )
        cwd = raw_stage.get("cwd")
        if cwd is not None and (not isinstance(cwd, str) or not cwd.strip()):
            raise PipelineManifestError(f"阶段 {stage_id} 的 cwd 必须是非空字符串")
        stages.append(
            _StageSpec(
                stage_id=stage_id,
                mode=mode,
                phase=phase,
                command=command,
                depends_on=dependencies,
                environment=_environment_mapping(
                    raw_stage.get("environment", {}),
                    field=f"阶段 {stage_id} 的 environment",
                ),
                cwd=cwd,
            )
        )
        seen.add(stage_id)

    demo_count = sum(stage.mode == "demo" for stage in stages)
    has_training = any(
        stage.mode == "full" and stage.phase in {"training", "evaluation"}
        for stage in stages
    )
    if has_training and demo_count == 0:
        raise PipelineManifestError("包含训练或评测阶段的 manifest 必须声明至少一个 demo 阶段")

    normalized = {
        "format": _MANIFEST_FORMAT,
        "pipeline_id": pipeline_id,
        "run_root": str(root_value),
        "environment": pipeline_environment,
        "stages": [_stage_hash_payload(stage) for stage in stages],
    }
    return _PipelineSpec(
        pipeline_id=pipeline_id,
        run_root=Path(root_value),
        environment=pipeline_environment,
        stages=tuple(stages),
        manifest_sha256=_canonical_sha256(normalized),
    )


def _select_stages(
    spec: _PipelineSpec,
    *,
    operation: PipelineOperation,
    selected_stages: Sequence[str] | None,
    from_stage: str | None,
    through_stage: str | None,
) -> list[_StageSpec]:
    if operation not in {"demo", "prepare", "training"}:
        raise PipelineManifestError(f"未知流水线操作：{operation}")
    if selected_stages is not None and (from_stage is not None or through_stage is not None):
        raise PipelineManifestError("--stage 不能与 --from-stage 或 --through-stage 同时使用")

    eligible = [stage for stage in spec.stages if _eligible(stage, operation)]
    if not eligible:
        raise PipelineManifestError(f"manifest 中没有可供 {operation} 操作执行的阶段")
    eligible_ids = [stage.stage_id for stage in eligible]

    if selected_stages is not None:
        requested = _string_sequence(
            selected_stages,
            field="selected_stages",
            allow_empty=False,
        )
        if len(requested) != len(set(requested)):
            raise PipelineManifestError("阶段选择不能重复")
        invalid = [stage_id for stage_id in requested if stage_id not in eligible_ids]
        if invalid:
            raise PipelineManifestError(
                f"所选阶段不属于当前操作：{'、'.join(invalid)}"
            )
        requested_set = set(requested)
        return [stage for stage in eligible if stage.stage_id in requested_set]

    start = 0
    end = len(eligible)
    if from_stage is not None:
        if from_stage not in eligible_ids:
            raise PipelineManifestError(f"起始阶段不属于当前操作：{from_stage}")
        start = eligible_ids.index(from_stage)
    if through_stage is not None:
        if through_stage not in eligible_ids:
            raise PipelineManifestError(f"结束阶段不属于当前操作：{through_stage}")
        end = eligible_ids.index(through_stage) + 1
    if start >= end:
        raise PipelineManifestError("起始阶段不能位于结束阶段之后")
    return eligible[start:end]


def _eligible(stage: _StageSpec, operation: PipelineOperation) -> bool:
    if operation == "demo":
        return stage.mode == "demo"
    if operation == "prepare":
        return stage.mode == "full" and stage.phase == "prepare"
    return stage.mode == "full" and stage.phase in {"training", "evaluation"}


def _require_demo_receipts(spec: _PipelineSpec) -> None:
    invalid: list[str] = []
    demo_run_dir = spec.run_root / "demos" / spec.pipeline_id
    for stage in spec.stages:
        if stage.mode != "demo":
            continue
        receipt = _read_json(demo_run_dir / "receipts" / f"{stage.stage_id}.json")
        if not _receipt_matches(spec, stage, receipt):
            invalid.append(stage.stage_id)
    if invalid:
        joined = "、".join(invalid)
        raise DemoReceiptGateError(
            f"训练已阻止：必须先让全部 demo 回执通过；无效或缺失阶段：{joined}",
            invalid,
        )


def _require_dependencies(spec: _PipelineSpec, stage: _StageSpec) -> None:
    stage_by_id = {item.stage_id: item for item in spec.stages}
    invalid: list[str] = []
    for dependency_id in stage.depends_on:
        dependency = stage_by_id[dependency_id]
        namespace = "demos" if dependency.mode == "demo" else "runs"
        dependency_run_dir = spec.run_root / namespace / spec.pipeline_id
        receipt = _read_json(
            dependency_run_dir / "receipts" / f"{dependency.stage_id}.json"
        )
        if not _receipt_matches(spec, dependency, receipt):
            invalid.append(dependency_id)
    if invalid:
        raise PipelineDependencyError(
            f"阶段 {stage.stage_id} 的前置回执尚未通过：{'、'.join(invalid)}"
        )


def _receipt_matches(
    spec: _PipelineSpec,
    stage: _StageSpec,
    receipt: Mapping[str, Any] | None,
) -> bool:
    return bool(
        receipt
        and receipt.get("pipeline_id") == spec.pipeline_id
        and receipt.get("stage_id") == stage.stage_id
        and receipt.get("mode") == stage.mode
        and receipt.get("phase") == stage.phase
        and receipt.get("status") == "completed"
        and receipt.get("command_sha256") == command_sha256(stage.command)
        and receipt.get("stage_spec_sha256")
        == _stage_spec_sha256(stage, spec.environment)
    )


def _run_stage(
    spec: _PipelineSpec,
    stage: _StageSpec,
    *,
    run_dir: Path,
    mode: StageMode,
    resume: bool,
) -> dict[str, Any]:
    receipt_path = run_dir / "receipts" / f"{stage.stage_id}.json"
    existing = _read_json(receipt_path)
    current_command_hash = command_sha256(stage.command)
    current_spec_hash = _stage_spec_sha256(stage, spec.environment)
    if existing is not None:
        if not resume:
            raise PipelineResumeError(
                f"阶段 {stage.stage_id} 已有回执；如需继续必须显式使用 --resume"
            )
        if existing.get("command_sha256") != current_command_hash:
            raise PipelineResumeError(
                f"阶段 {stage.stage_id} 的命令哈希不一致，拒绝恢复"
            )
        if existing.get("stage_spec_sha256") != current_spec_hash:
            raise PipelineResumeError(
                f"阶段 {stage.stage_id} 的阶段规范哈希不一致，拒绝恢复"
            )
        if existing.get("status") == "completed":
            _append_event(
                run_dir,
                {
                    "event": "stage_resume_skip",
                    "stage_id": stage.stage_id,
                    "message_zh": f"阶段 {stage.stage_id} 已完成且哈希一致，恢复时跳过",
                },
            )
            return {
                "stage_id": stage.stage_id,
                "action": "resume_skip",
                "receipt_path": str(receipt_path),
                "command_sha256": current_command_hash,
            }
        _preserve_interrupted_attempt(existing, run_dir=run_dir, stage=stage)

    attempt = _next_attempt(existing)
    attempt_dir = run_dir / "stages" / stage.stage_id / f"attempt-{attempt:04d}"
    if attempt_dir.exists():
        raise PipelineResumeError(f"阶段尝试目录已存在，拒绝覆盖：{attempt_dir}")
    attempt_dir.mkdir(parents=True, exist_ok=False)
    console_path = attempt_dir / "console.log"
    explicit_environment = {
        **spec.environment,
        **stage.environment,
        "RSAGE_PIPELINE_ID": spec.pipeline_id,
        "RSAGE_PIPELINE_MODE": mode,
        "RSAGE_PIPELINE_RUN_DIR": str(run_dir),
        "RSAGE_PIPELINE_STAGE_ID": stage.stage_id,
        "RSAGE_STAGE_DIR": str(attempt_dir),
    }
    process_environment = os.environ.copy()
    process_environment.update(explicit_environment)
    started_at = _now()
    running_receipt: dict[str, Any] = {
        "format": "rsage_pipeline_stage_receipt_v1",
        "pipeline_id": spec.pipeline_id,
        "stage_id": stage.stage_id,
        "mode": stage.mode,
        "phase": stage.phase,
        "status": "running",
        "attempt": attempt,
        "run_dir": str(run_dir),
        "attempt_dir": str(attempt_dir),
        "console_log": str(console_path),
        "command": _redact_command(stage.command),
        "command_sha256": current_command_hash,
        "stage_spec_sha256": current_spec_hash,
        "environment": _redact_environment(explicit_environment),
        "cwd": stage.cwd or str(Path.cwd()),
        "started_at": started_at,
        "message_zh": f"阶段 {stage.stage_id} 已启动",
    }
    _write_json_atomic(receipt_path, running_receipt)
    _append_event(
        run_dir,
        {
            "event": "stage_started",
            "stage_id": stage.stage_id,
            "attempt": attempt,
            "command_sha256": current_command_hash,
            "message_zh": running_receipt["message_zh"],
        },
    )

    started_monotonic = time.monotonic()
    execution_error: str | None = None
    return_code: int | None = None
    with console_path.open("w", encoding="utf-8") as console:
        try:
            completed = subprocess.run(
                list(stage.command),
                cwd=stage.cwd,
                env=process_environment,
                stdout=console,
                stderr=subprocess.STDOUT,
                text=True,
                check=False,
            )
            return_code = completed.returncode
        except OSError as exc:
            execution_error = f"{type(exc).__name__}: {exc}"
            console.write(f"流水线无法启动阶段子进程：{execution_error}\n")
            console.flush()

    duration = round(time.monotonic() - started_monotonic, 6)
    ended_at = _now()
    if return_code == 0 and execution_error is None:
        receipt = {
            **running_receipt,
            "status": "completed",
            "return_code": 0,
            "duration_seconds": duration,
            "ended_at": ended_at,
            "message_zh": f"阶段 {stage.stage_id} 已完成",
        }
        _write_json_atomic(receipt_path, receipt)
        _append_event(
            run_dir,
            {
                "event": "stage_completed",
                "stage_id": stage.stage_id,
                "attempt": attempt,
                "duration_seconds": duration,
                "message_zh": receipt["message_zh"],
            },
        )
        return {
            "stage_id": stage.stage_id,
            "action": "completed",
            "receipt_path": str(receipt_path),
            "command_sha256": current_command_hash,
        }

    failed_dir = run_dir / "failed-partial" / stage.stage_id / f"attempt-{attempt:04d}"
    failed_dir.parent.mkdir(parents=True, exist_ok=True)
    if failed_dir.exists():
        raise PipelineResumeError(f"失败证据目录已存在，拒绝覆盖：{failed_dir}")
    failure = {
        "format": "rsage_pipeline_failure_v1",
        "pipeline_id": spec.pipeline_id,
        "stage_id": stage.stage_id,
        "attempt": attempt,
        "return_code": return_code,
        "execution_error": execution_error,
        "started_at": started_at,
        "ended_at": ended_at,
        "message_zh": (
            f"阶段 {stage.stage_id} 失败："
            f"{'无法启动子进程' if execution_error else f'退出码 {return_code}'}"
        ),
    }
    _write_json_atomic(attempt_dir / "failure.json", failure)
    shutil.move(str(attempt_dir), str(failed_dir))
    receipt = {
        **running_receipt,
        "status": "failed",
        "return_code": return_code,
        "execution_error": execution_error,
        "duration_seconds": duration,
        "ended_at": ended_at,
        "attempt_dir": str(failed_dir),
        "console_log": str(failed_dir / "console.log"),
        "failed_partial_dir": str(failed_dir),
        "message_zh": failure["message_zh"],
    }
    _write_json_atomic(receipt_path, receipt)
    _write_pipeline_failure(run_dir, receipt)
    _append_event(
        run_dir,
        {
            "event": "stage_failed",
            "stage_id": stage.stage_id,
            "attempt": attempt,
            "return_code": return_code,
            "failed_partial_dir": str(failed_dir),
            "message_zh": failure["message_zh"],
        },
    )
    raise PipelineStageError(failure["message_zh"], receipt)


def _preserve_interrupted_attempt(
    receipt: Mapping[str, Any],
    *,
    run_dir: Path,
    stage: _StageSpec,
) -> None:
    if receipt.get("status") != "running":
        return
    attempt = _positive_int(receipt.get("attempt"), default=1)
    raw_attempt_dir = receipt.get("attempt_dir")
    if not isinstance(raw_attempt_dir, str):
        return
    attempt_dir = Path(raw_attempt_dir)
    if not attempt_dir.exists():
        return
    failed_dir = run_dir / "failed-partial" / stage.stage_id / f"attempt-{attempt:04d}"
    failed_dir.parent.mkdir(parents=True, exist_ok=True)
    if failed_dir.exists():
        raise PipelineResumeError(f"失败证据目录已存在，拒绝覆盖：{failed_dir}")
    failure = {
        "format": "rsage_pipeline_failure_v1",
        "pipeline_id": receipt.get("pipeline_id"),
        "stage_id": stage.stage_id,
        "attempt": attempt,
        "return_code": None,
        "execution_error": "恢复时发现未完成的 running 回执",
        "message_zh": f"阶段 {stage.stage_id} 的中断尝试已保存，准备恢复",
    }
    _write_json_atomic(attempt_dir / "failure.json", failure)
    shutil.move(str(attempt_dir), str(failed_dir))


def _write_pipeline_failure(run_dir: Path, stage_receipt: Mapping[str, Any]) -> None:
    value = {
        "format": "rsage_pipeline_receipt_v1",
        "pipeline_id": stage_receipt["pipeline_id"],
        "operation": "demo" if stage_receipt["mode"] == "demo" else stage_receipt["phase"],
        "status": "failed",
        "run_dir": str(run_dir),
        "failed_stage_id": stage_receipt["stage_id"],
        "failed_partial_dir": stage_receipt.get("failed_partial_dir"),
        "ended_at": stage_receipt.get("ended_at"),
        "message_zh": stage_receipt["message_zh"],
    }
    _write_json_atomic(run_dir / "pipeline_receipt.json", value)


def _next_attempt(existing: Mapping[str, Any] | None) -> int:
    if not existing:
        return 1
    return _positive_int(existing.get("attempt"), default=0) + 1


def _positive_int(value: Any, *, default: int) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        return default
    return value


def _stage_spec_sha256(
    stage: _StageSpec,
    pipeline_environment: Mapping[str, str],
) -> str:
    return _canonical_sha256(
        {
            "pipeline_environment": dict(pipeline_environment),
            "stage": _stage_hash_payload(stage),
        }
    )


def _stage_hash_payload(stage: _StageSpec) -> dict[str, Any]:
    return {
        "id": stage.stage_id,
        "mode": stage.mode,
        "phase": stage.phase,
        "command": list(stage.command),
        "depends_on": list(stage.depends_on),
        "environment": stage.environment,
        "cwd": stage.cwd,
    }


def _canonical_sha256(value: Any) -> str:
    payload = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def _read_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PipelineResumeError(f"回执无法读取，拒绝继续：{path}：{exc}") from exc
    if not isinstance(value, Mapping):
        raise PipelineResumeError(f"回执顶层不是 JSON 对象，拒绝继续：{path}")
    return dict(value)


def _write_json_atomic(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    payload = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    with temporary.open("w", encoding="utf-8") as handle:
        handle.write(payload)
        handle.flush()
        os.fsync(handle.fileno())
    os.replace(temporary, path)


def _append_event(run_dir: Path, event: Mapping[str, Any]) -> None:
    path = run_dir / "events.jsonl"
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {"timestamp_utc": _now(), **event}
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(payload, ensure_ascii=False, sort_keys=True) + "\n")
        handle.flush()
        os.fsync(handle.fileno())


def _environment_mapping(value: Any, *, field: str) -> dict[str, str]:
    if not isinstance(value, Mapping):
        raise PipelineManifestError(f"{field} 必须是字符串键值对象")
    result: dict[str, str] = {}
    for key, item in value.items():
        if not isinstance(key, str) or not key or not isinstance(item, str):
            raise PipelineManifestError(f"{field} 必须只包含非空字符串键和值")
        result[key] = item
    return result


def _redact_environment(environment: Mapping[str, str]) -> dict[str, str]:
    return {
        key: _REDACTED if _SECRET_KEY.search(key) else value
        for key, value in sorted(environment.items())
    }


def _redact_command(command: Sequence[str]) -> list[str]:
    redacted: list[str] = []
    redact_next = False
    for item in command:
        if redact_next:
            redacted.append(_REDACTED)
            redact_next = False
            continue
        flag, separator, _value = item.partition("=")
        if flag.lower() in _SECRET_FLAGS:
            if separator:
                redacted.append(f"{flag}={_REDACTED}")
            else:
                redacted.append(item)
                redact_next = True
        else:
            redacted.append(item)
    return redacted


def _string_sequence(value: Any, *, field: str, allow_empty: bool) -> list[str]:
    if isinstance(value, (str, bytes)) or not isinstance(value, Sequence):
        raise PipelineManifestError(f"{field} 必须是字符串数组")
    result = list(value)
    if not allow_empty and not result:
        raise PipelineManifestError(f"{field} 不能为空")
    if any(not isinstance(item, str) or not item for item in result):
        raise PipelineManifestError(f"{field} 只能包含非空字符串")
    return result


def _safe_identifier(value: Any, *, field: str) -> str:
    if not isinstance(value, str) or not _SAFE_ID.fullmatch(value):
        raise PipelineManifestError(
            f"{field} 必须以字母或数字开头，且只能包含字母、数字、点、短横线和下划线"
        )
    return value


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()
