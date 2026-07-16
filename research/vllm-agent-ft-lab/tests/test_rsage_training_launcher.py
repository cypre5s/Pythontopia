from __future__ import annotations

import json
import sys
from pathlib import Path

from pyt_agent_lab.rsage_training_launcher import launch_training_command


def test_launcher_preserves_chinese_console_events_and_success_receipt(tmp_path: Path) -> None:
    result = launch_training_command(
        command=[sys.executable, "-c", "print('一步训练完成')"],
        run_dir=tmp_path,
        stage="sft",
    )

    assert result["status"] == "completed"
    assert result["return_code"] == 0
    assert "一步训练完成" in (tmp_path / "console.log").read_text(encoding="utf-8")
    events = [json.loads(line) for line in (tmp_path / "events.jsonl").read_text(encoding="utf-8").splitlines()]
    assert [row["event"] for row in events] == ["训练启动", "训练完成"]
    assert all(row["message_zh"] for row in events)
    assert json.loads((tmp_path / "launch_receipt.json").read_text(encoding="utf-8"))["status"] == "completed"


def test_launcher_archives_failed_partial_and_fails_closed(tmp_path: Path) -> None:
    result = launch_training_command(
        command=[sys.executable, "-c", "print('显存检查失败'); raise SystemExit(7)"],
        run_dir=tmp_path,
        stage="gdpo_rpo",
    )

    assert result["status"] == "failed"
    assert result["return_code"] == 7
    assert (tmp_path / "failure.json").exists()
    failure = json.loads((tmp_path / "failure.json").read_text(encoding="utf-8"))
    assert failure["message_zh"] == "GDPO+RPO 训练失败，已保留部分产物"
