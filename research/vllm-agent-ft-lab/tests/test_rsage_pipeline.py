from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

import pytest

from pyt_agent_lab.rsage_pipeline import (
    DemoReceiptGateError,
    PipelineManifestError,
    PipelineResumeError,
    PipelineStageError,
    command_sha256,
    run_rsage_pipeline,
)


def _append_command(path: Path, value: str, *, return_code: int = 0) -> list[str]:
    script = (
        "from pathlib import Path; "
        f"path = Path({str(path)!r}); "
        "path.parent.mkdir(parents=True, exist_ok=True); "
        f"path.open('a', encoding='utf-8').write({(value + chr(10))!r}); "
        f"print({('阶段输出：' + value)!r}); "
        f"raise SystemExit({return_code})"
    )
    return [sys.executable, "-c", script]


def _environment_command(path: Path, key: str) -> list[str]:
    script = (
        "import os; from pathlib import Path; "
        f"path = Path({str(path)!r}); "
        "path.parent.mkdir(parents=True, exist_ok=True); "
        f"path.write_text(os.environ[{key!r}], encoding='utf-8')"
    )
    return [sys.executable, "-c", script]


def _stage(
    stage_id: str,
    command: list[str],
    *,
    mode: str = "full",
    phase: str = "prepare",
    depends_on: list[str] | None = None,
    demo_gates: list[str] | None = None,
    environment: dict[str, str] | None = None,
) -> dict[str, Any]:
    stage = {
        "id": stage_id,
        "mode": mode,
        "phase": phase,
        "command": command,
        "depends_on": depends_on or [],
        "environment": environment or {},
    }
    if demo_gates is not None:
        stage["demo_gates"] = demo_gates
    return stage


def _manifest(tmp_path: Path, stages: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "format": "rsage_pipeline_manifest_v1",
        "pipeline_id": "unit-pipeline",
        "run_root": str(tmp_path / "pipeline-runs"),
        "environment": {"PYTHONUNBUFFERED": "1"},
        "stages": stages,
    }


def _receipt_path(tmp_path: Path, namespace: str, stage_id: str) -> Path:
    return (
        tmp_path
        / "pipeline-runs"
        / namespace
        / "unit-pipeline"
        / "receipts"
        / f"{stage_id}.json"
    )


def test_runs_manifest_stages_in_order_and_writes_atomic_receipts(tmp_path: Path) -> None:
    order_path = tmp_path / "order.txt"
    manifest = _manifest(
        tmp_path,
        [
            _stage("case-demo", _append_command(order_path, "case"), mode="demo"),
            _stage(
                "vote-demo",
                _append_command(order_path, "vote"),
                mode="demo",
                depends_on=["case-demo"],
            ),
            _stage(
                "curation-demo",
                _append_command(order_path, "curation"),
                mode="demo",
                depends_on=["vote-demo"],
            ),
        ],
    )

    summary = run_rsage_pipeline(manifest, operation="demo")

    assert order_path.read_text(encoding="utf-8").splitlines() == [
        "case",
        "vote",
        "curation",
    ]
    assert summary["status"] == "completed"
    assert summary["completed_stage_count"] == 3
    for stage in manifest["stages"]:
        receipt = json.loads(
            _receipt_path(tmp_path, "demos", stage["id"]).read_text(encoding="utf-8")
        )
        assert receipt["status"] == "completed"
        assert receipt["command_sha256"] == command_sha256(stage["command"])
        assert not list(_receipt_path(tmp_path, "demos", stage["id"]).parent.glob("*.tmp"))


def test_resume_skips_completed_stages_with_the_same_command_hash(tmp_path: Path) -> None:
    calls_path = tmp_path / "calls.txt"
    manifest = _manifest(
        tmp_path,
        [
            _stage("prepare-data", _append_command(calls_path, "prepare-data")),
            _stage(
                "prepare-plan",
                _append_command(calls_path, "prepare-plan"),
                depends_on=["prepare-data"],
            ),
        ],
    )
    run_rsage_pipeline(manifest, operation="prepare")

    summary = run_rsage_pipeline(manifest, operation="prepare", resume=True)

    assert calls_path.read_text(encoding="utf-8").splitlines() == [
        "prepare-data",
        "prepare-plan",
    ]
    assert summary["resumed_stage_count"] == 2
    assert [stage["action"] for stage in summary["stages"]] == [
        "resume_skip",
        "resume_skip",
    ]


def test_resume_fails_closed_when_a_completed_stage_command_changes(tmp_path: Path) -> None:
    calls_path = tmp_path / "calls.txt"
    manifest = _manifest(
        tmp_path,
        [_stage("prepare-data", _append_command(calls_path, "original"))],
    )
    run_rsage_pipeline(manifest, operation="prepare")
    changed = _manifest(
        tmp_path,
        [_stage("prepare-data", _append_command(calls_path, "changed"))],
    )

    with pytest.raises(PipelineResumeError, match="命令哈希不一致"):
        run_rsage_pipeline(changed, operation="prepare", resume=True)

    assert calls_path.read_text(encoding="utf-8").splitlines() == ["original"]


def test_resume_fails_closed_when_explicit_environment_changes(tmp_path: Path) -> None:
    calls_path = tmp_path / "calls.txt"
    manifest = _manifest(
        tmp_path,
        [_stage("prepare-data", _append_command(calls_path, "original"))],
    )
    run_rsage_pipeline(manifest, operation="prepare")
    changed = _manifest(
        tmp_path,
        [_stage("prepare-data", _append_command(calls_path, "original"))],
    )
    changed["environment"]["SAFE_FLAG"] = "changed"

    with pytest.raises(PipelineResumeError, match="阶段规范哈希不一致"):
        run_rsage_pipeline(changed, operation="prepare", resume=True)

    assert calls_path.read_text(encoding="utf-8").splitlines() == ["original"]


def test_failed_stage_is_preserved_under_failed_partial_and_stops_pipeline(
    tmp_path: Path,
) -> None:
    calls_path = tmp_path / "calls.txt"
    manifest = _manifest(
        tmp_path,
        [
            _stage("will-fail", _append_command(calls_path, "failed", return_code=7)),
            _stage(
                "must-not-run",
                _append_command(calls_path, "unexpected"),
                depends_on=["will-fail"],
            ),
        ],
    )

    with pytest.raises(PipelineStageError, match="阶段 will-fail 失败") as raised:
        run_rsage_pipeline(manifest, operation="prepare")

    receipt = raised.value.receipt
    failed_dir = Path(receipt["failed_partial_dir"])
    assert receipt["status"] == "failed"
    assert receipt["return_code"] == 7
    assert "阶段输出：failed" in (failed_dir / "console.log").read_text(encoding="utf-8")
    assert json.loads((failed_dir / "failure.json").read_text(encoding="utf-8"))[
        "stage_id"
    ] == "will-fail"
    assert calls_path.read_text(encoding="utf-8").splitlines() == ["failed"]


def test_demo_and_full_artifacts_use_different_namespaces(tmp_path: Path) -> None:
    demo_mode_path = tmp_path / "demo-mode.txt"
    full_mode_path = tmp_path / "full-mode.txt"
    manifest = _manifest(
        tmp_path,
        [
            _stage(
                "same-purpose-demo",
                _environment_command(demo_mode_path, "RSAGE_PIPELINE_MODE"),
                mode="demo",
            ),
            _stage(
                "same-purpose-full",
                _environment_command(full_mode_path, "RSAGE_PIPELINE_MODE"),
            ),
        ],
    )

    demo_summary = run_rsage_pipeline(manifest, operation="demo")
    full_summary = run_rsage_pipeline(manifest, operation="prepare")

    assert demo_mode_path.read_text(encoding="utf-8") == "demo"
    assert full_mode_path.read_text(encoding="utf-8") == "full"
    assert "/demos/" in demo_summary["run_dir"]
    assert "/runs/" in full_summary["run_dir"]
    assert _receipt_path(tmp_path, "demos", "same-purpose-demo").exists()
    assert _receipt_path(tmp_path, "runs", "same-purpose-full").exists()


def test_training_is_blocked_until_every_current_demo_receipt_passes(tmp_path: Path) -> None:
    calls_path = tmp_path / "calls.txt"
    training_path = tmp_path / "training.txt"
    manifest = _manifest(
        tmp_path,
        [
            _stage("case-demo", _append_command(calls_path, "case"), mode="demo"),
            _stage("vote-demo", _append_command(calls_path, "vote"), mode="demo"),
            _stage(
                "sft-training",
                _append_command(training_path, "train"),
                phase="training",
                demo_gates=["vote-demo"],
            ),
        ],
    )
    run_rsage_pipeline(
        manifest,
        operation="demo",
        selected_stages=["case-demo"],
    )

    with pytest.raises(DemoReceiptGateError, match="全部 demo 回执通过") as raised:
        run_rsage_pipeline(manifest, operation="training")

    assert raised.value.invalid_stage_ids == ("vote-demo",)
    assert not training_path.exists()

    run_rsage_pipeline(
        manifest,
        operation="demo",
        selected_stages=["vote-demo"],
        resume=True,
    )
    summary = run_rsage_pipeline(manifest, operation="training")

    assert training_path.read_text(encoding="utf-8").splitlines() == ["train"]
    assert summary["completed_stage_count"] == 1


def test_unmapped_trivial_demo_cannot_unlock_a_training_stage(tmp_path: Path) -> None:
    manifest = _manifest(
        tmp_path,
        [
            _stage("unrelated-demo", _append_command(tmp_path / "demo.txt", "demo"), mode="demo"),
            _stage(
                "sft-training",
                _append_command(tmp_path / "training.txt", "train"),
                phase="training",
            ),
        ],
    )

    with pytest.raises(PipelineManifestError, match="demo_gates"):
        run_rsage_pipeline(manifest, operation="demo")


def test_training_rejects_a_demo_receipt_with_tampered_stage_identity(
    tmp_path: Path,
) -> None:
    training_path = tmp_path / "training.txt"
    manifest = _manifest(
        tmp_path,
        [
            _stage("required-demo", _append_command(tmp_path / "demo.txt", "demo"), mode="demo"),
            _stage(
                "sft-training",
                _append_command(training_path, "train"),
                phase="training",
                demo_gates=["required-demo"],
            ),
        ],
    )
    run_rsage_pipeline(manifest, operation="demo")
    receipt_path = _receipt_path(tmp_path, "demos", "required-demo")
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    receipt["stage_id"] = "another-demo"
    receipt_path.write_text(json.dumps(receipt, ensure_ascii=False), encoding="utf-8")

    with pytest.raises(DemoReceiptGateError) as raised:
        run_rsage_pipeline(manifest, operation="training")

    assert raised.value.invalid_stage_ids == ("required-demo",)
    assert not training_path.exists()


def test_receipt_captures_only_explicit_environment_and_redacts_secrets(
    tmp_path: Path,
) -> None:
    observed_path = tmp_path / "observed-secret.txt"
    manifest = _manifest(
        tmp_path,
        [
            _stage(
                "redaction",
                _environment_command(observed_path, "TEACHER_API_KEY"),
                environment={
                    "TEACHER_API_KEY": "super-secret-value",
                    "SAFE_FLAG": "visible",
                },
            )
        ],
    )

    run_rsage_pipeline(manifest, operation="prepare")

    receipt_path = _receipt_path(tmp_path, "runs", "redaction")
    receipt_text = receipt_path.read_text(encoding="utf-8")
    receipt = json.loads(receipt_text)
    assert observed_path.read_text(encoding="utf-8") == "super-secret-value"
    assert receipt["environment"] == {
        "PYTHONUNBUFFERED": "1",
        "RSAGE_PIPELINE_ID": "unit-pipeline",
        "RSAGE_PIPELINE_MODE": "full",
        "RSAGE_PIPELINE_RUN_DIR": receipt["run_dir"],
        "RSAGE_PIPELINE_STAGE_ID": "redaction",
        "RSAGE_STAGE_DIR": receipt["attempt_dir"],
        "SAFE_FLAG": "visible",
        "TEACHER_API_KEY": "***已脱敏***",
    }
    assert "super-secret-value" not in receipt_text
    assert "PATH" not in receipt["environment"]


def test_chinese_cli_runs_a_manifest_demo(tmp_path: Path) -> None:
    marker = tmp_path / "cli-demo.txt"
    manifest = _manifest(
        tmp_path,
        [_stage("cli-demo", _append_command(marker, "cli"), mode="demo")],
    )
    manifest_path = tmp_path / "pipeline.json"
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False),
        encoding="utf-8",
    )
    project_root = Path(__file__).resolve().parents[1]
    environment = os.environ.copy()
    environment["PYTHONPATH"] = str(project_root / "src")

    completed = subprocess.run(
        [
            sys.executable,
            str(project_root / "scripts" / "run_rsage_pipeline.py"),
            "--manifest",
            str(manifest_path),
            "--demo",
        ],
        cwd=project_root.parent.parent,
        env=environment,
        text=True,
        capture_output=True,
        check=False,
    )

    assert completed.returncode == 0, completed.stderr
    assert "R-SAGE 流水线完成" in completed.stdout
    assert "完成阶段 1 个" in completed.stdout
    assert marker.read_text(encoding="utf-8").splitlines() == ["cli"]


def test_cli_help_uses_chinese_labels_and_lists_safe_run_modes() -> None:
    project_root = Path(__file__).resolve().parents[1]
    environment = os.environ.copy()
    environment["PYTHONPATH"] = str(project_root / "src")

    completed = subprocess.run(
        [
            sys.executable,
            str(project_root / "scripts" / "run_rsage_pipeline.py"),
            "--help",
        ],
        cwd=project_root.parent.parent,
        env=environment,
        text=True,
        capture_output=True,
        check=False,
    )

    assert completed.returncode == 0
    assert completed.stderr == ""
    assert completed.stdout.startswith("用法：")
    assert "usage:" not in completed.stdout
    assert "--demo" in completed.stdout
    assert "--prepare-only" in completed.stdout
    assert "--start-training" in completed.stdout
    assert "--resume" in completed.stdout
