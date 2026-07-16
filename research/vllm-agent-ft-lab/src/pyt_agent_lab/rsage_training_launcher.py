from __future__ import annotations

import hashlib
import json
import os
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping, Sequence


_STAGE_NAMES = {
    "sft": "SFT",
    "gdpo_rpo": "GDPO+RPO",
}


def launch_training_command(
    *,
    command: Sequence[str],
    run_dir: str | Path,
    stage: str,
    environment: Mapping[str, str] | None = None,
) -> dict[str, Any]:
    if stage not in _STAGE_NAMES:
        raise ValueError(f"未知训练阶段：{stage}")
    if not command or any(not isinstance(item, str) or not item for item in command):
        raise ValueError("训练命令不能为空")
    target = Path(run_dir)
    target.mkdir(parents=True, exist_ok=True)
    display_name = _STAGE_NAMES[stage]
    command_list = list(command)
    command_hash = hashlib.sha256("\0".join(command_list).encode("utf-8")).hexdigest()
    started_at = _now()
    _append_event(
        target,
        {
            "event": "训练启动",
            "stage": stage,
            "command_sha256": command_hash,
            "message_zh": f"{display_name} 训练进程已启动",
        },
    )
    process_env = os.environ.copy()
    process_env.update(environment or {})
    start = time.monotonic()
    console_path = target / "console.log"
    with console_path.open("a", encoding="utf-8") as console:
        process = subprocess.Popen(  # noqa: S603 - 命令由已验证的内部计划构造，不使用 shell
            command_list,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            env=process_env,
        )
        assert process.stdout is not None
        for line in process.stdout:
            console.write(line)
            console.flush()
        return_code = process.wait()
    duration_seconds = round(time.monotonic() - start, 3)
    completed_at = _now()
    status = "completed" if return_code == 0 else "failed"
    receipt = {
        "format": "rsage_training_launch_receipt_v1",
        "stage": stage,
        "status": status,
        "return_code": return_code,
        "command": command_list,
        "command_sha256": command_hash,
        "started_at": started_at,
        "completed_at": completed_at,
        "duration_seconds": duration_seconds,
        "console_path": str(console_path),
        "message_zh": (
            f"{display_name} 训练命令执行完成"
            if return_code == 0
            else f"{display_name} 训练命令失败，已保留部分产物"
        ),
    }
    _write_json_atomic(target / "launch_receipt.json", receipt)
    if return_code == 0:
        _append_event(
            target,
            {
                "event": "训练完成",
                "stage": stage,
                "return_code": return_code,
                "message_zh": f"{display_name} 训练进程正常结束",
            },
        )
    else:
        failure = {
            **receipt,
            "message_zh": f"{display_name} 训练失败，已保留部分产物",
        }
        _write_json_atomic(target / "failure.json", failure)
        _append_event(
            target,
            {
                "event": "训练失败",
                "stage": stage,
                "return_code": return_code,
                "message_zh": failure["message_zh"],
            },
        )
    return receipt


def _append_event(target: Path, row: Mapping[str, Any]) -> None:
    record = {"timestamp_utc": _now(), **dict(row)}
    with (target / "events.jsonl").open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")


def _write_json_atomic(path: Path, value: Mapping[str, Any]) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(dict(value), ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")
