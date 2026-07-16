from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path
from types import SimpleNamespace

from pyt_agent_lab.rsage_live_report import (
    append_event,
    parse_trainer_log,
    poll_once,
    sample_gpu_metrics,
    watch_training,
    write_live_charts,
)


LAB_ROOT = Path(__file__).resolve().parents[1]


def _write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    path.write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows),
        encoding="utf-8",
    )


def _gpu_unavailable() -> dict[str, object]:
    return {
        "timestamp_utc": "2026-07-16T00:00:00+00:00",
        "available": False,
        "status": "nvidia_smi_missing",
        "message_zh": "未找到 nvidia-smi，已跳过 GPU 遥测",
        "gpus": [],
    }


def test_append_event_preserves_existing_rows_and_writes_readable_chinese(tmp_path: Path) -> None:
    events_path = tmp_path / "events.jsonl"
    original = '{"event":"训练启动","message_zh":"训练已启动"}\n'
    events_path.write_text(original, encoding="utf-8")

    append_event(tmp_path, event="监控启动", message_zh="实时训练监控已启动")
    append_event(
        tmp_path,
        event="监控轮询",
        message_zh="已刷新训练曲线",
        trainer_rows=2,
    )

    raw = events_path.read_text(encoding="utf-8")
    rows = [json.loads(line) for line in raw.splitlines()]
    assert raw.startswith(original), "事件日志必须只追加，不能重写既有训练事件"
    assert [row["event"] for row in rows] == ["训练启动", "监控启动", "监控轮询"]
    assert rows[-1]["trainer_rows"] == 2
    assert "已刷新训练曲线" in raw, "中文事件必须以可读文本写入，不能转义成 Unicode 编码"
    assert "\\u5df2" not in raw


def test_parse_trainer_log_keeps_metric_names_and_ignores_growing_partial_tail(tmp_path: Path) -> None:
    log_path = tmp_path / "trainer_log.jsonl"
    log_path.write_text(
        json.dumps(
            {
                "step": 1,
                "epoch": 0.25,
                "loss": 1.2,
                "rewards/chosen": 0.4,
                "train_samples_per_second": 1.5,
            }
        )
        + "\n"
        + json.dumps({"step": 2, "eval_loss": 0.9, "rewards/accuracies": 0.75})
        + "\n"
        + '{"step": 3, "loss":',
        encoding="utf-8",
    )

    rows = parse_trainer_log(log_path)

    assert len(rows) == 2, "训练器正在追加的最后半行不应让监控进程崩溃"
    assert rows[0]["rewards/chosen"] == 0.4
    assert rows[1] == {"step": 2, "eval_loss": 0.9, "rewards/accuracies": 0.75}


def test_sample_gpu_metrics_parses_nvidia_smi_and_reports_missing_command() -> None:
    def successful_run(command: list[str], **kwargs: object) -> SimpleNamespace:
        assert command[0] == "nvidia-smi"
        assert kwargs["check"] is True
        return SimpleNamespace(stdout="0, 42, 4096, 8192, 65\n")

    sample = sample_gpu_metrics(run_command=successful_run)

    assert sample["available"] is True
    assert sample["status"] == "ok"
    assert sample["gpus"] == [
        {
            "index": 0,
            "utilization_pct": 42.0,
            "memory_used_mib": 4096.0,
            "memory_total_mib": 8192.0,
            "temperature_c": 65.0,
        }
    ]
    assert sample["message_zh"] == "已采集 1 张 GPU 的遥测数据"

    def missing_run(command: list[str], **kwargs: object) -> SimpleNamespace:
        raise FileNotFoundError(command[0])

    missing = sample_gpu_metrics(run_command=missing_run)
    assert missing["available"] is False
    assert missing["status"] == "nvidia_smi_missing"
    assert "未找到 nvidia-smi" in str(missing["message_zh"])


def test_write_live_charts_atomically_replaces_all_four_svg_files(tmp_path: Path) -> None:
    charts_dir = tmp_path / "charts"
    charts_dir.mkdir()
    old_loss = charts_dir / "loss.svg"
    old_loss.write_text("旧图表，不应残留", encoding="utf-8")
    trainer_rows = [
        {
            "step": 1,
            "loss": 1.2,
            "rewards/chosen": 0.4,
            "rewards/rejected": -0.2,
            "train_samples_per_second": 1.5,
        },
        {
            "step": 2,
            "loss": 0.8,
            "eval_loss": 0.9,
            "rewards/chosen": 0.7,
            "rewards/rejected": -0.1,
            "rewards/accuracies": 0.75,
            "train_samples_per_second": 1.8,
        },
    ]
    gpu_samples = [
        {
            "timestamp_utc": "2026-07-16T00:00:00+00:00",
            "available": True,
            "gpus": [
                {
                    "index": 0,
                    "utilization_pct": 42.0,
                    "memory_used_mib": 4096.0,
                    "memory_total_mib": 8192.0,
                    "temperature_c": 65.0,
                }
            ],
        }
    ]

    outputs = write_live_charts(tmp_path, trainer_rows=trainer_rows, gpu_samples=gpu_samples)

    assert set(outputs) == {"loss", "reward", "throughput", "gpu"}
    for name in outputs:
        svg = charts_dir / f"{name}.svg"
        assert svg.read_text(encoding="utf-8").startswith("<svg"), f"{name} 图表应为有效 SVG"
    assert "旧图表" not in old_loss.read_text(encoding="utf-8")
    assert not list(charts_dir.glob("*.tmp")), "原子替换后不应残留临时图表"


def test_poll_once_keeps_reporting_when_gpu_is_unavailable_and_updates_journal(tmp_path: Path) -> None:
    _write_jsonl(
        tmp_path / "trainer_log.jsonl",
        [
            {"step": 1, "epoch": 0.25, "loss": 1.2},
            {"step": 2, "epoch": 0.5, "loss": 0.8, "eval_loss": 0.9},
        ],
    )
    (tmp_path / "events.jsonl").write_text(
        json.dumps({"event": "训练启动", "message_zh": "训练进程已启动"}, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    first = poll_once(tmp_path, gpu_sampler=_gpu_unavailable)

    assert first["trainer_rows"] == 2
    assert first["latest_step"] == 2
    assert first["gpu_available"] is False
    assert all(
        (tmp_path / "charts" / name).exists()
        for name in ("loss.svg", "reward.svg", "throughput.svg", "gpu.svg")
    )
    journal = (tmp_path / "EXPERIMENT_LOG.md").read_text(encoding="utf-8")
    assert journal.startswith("# RSAGE 实验实时记录")
    assert "最新训练步数 | 2" in journal
    assert "未找到 nvidia-smi" in journal
    assert "charts/loss.svg" in journal
    assert len((tmp_path / "gpu_metrics.jsonl").read_text(encoding="utf-8").splitlines()) == 1
    assert len((tmp_path / "progress_snapshots.jsonl").read_text(encoding="utf-8").splitlines()) == 1

    with (tmp_path / "trainer_log.jsonl").open("a", encoding="utf-8") as handle:
        handle.write(json.dumps({"step": 3, "epoch": 0.75, "loss": 0.6}) + "\n")
    second = poll_once(tmp_path, gpu_sampler=_gpu_unavailable)

    assert second["trainer_rows"] == 3
    assert "最新训练步数 | 3" in (tmp_path / "EXPERIMENT_LOG.md").read_text(encoding="utf-8")
    events = [json.loads(line) for line in (tmp_path / "events.jsonl").read_text(encoding="utf-8").splitlines()]
    assert [event["event"] for event in events] == ["训练启动", "监控轮询", "监控轮询"]
    assert all(event["message_zh"] for event in events)
    assert len((tmp_path / "progress_snapshots.jsonl").read_text(encoding="utf-8").splitlines()) == 2


def test_watch_training_stops_at_bound_and_sleeps_only_between_polls(tmp_path: Path) -> None:
    _write_jsonl(tmp_path / "trainer_log.jsonl", [{"step": 1, "loss": 1.0}])
    sleeps: list[float] = []

    results = watch_training(
        tmp_path,
        interval_seconds=0.25,
        max_polls=2,
        gpu_sampler=_gpu_unavailable,
        sleep_fn=sleeps.append,
    )

    assert len(results) == 2
    assert sleeps == [0.25], "有界 watcher 只应在相邻轮询之间等待"


def test_demo_cli_grows_synthetic_log_and_exits_with_reports(tmp_path: Path) -> None:
    env = os.environ.copy()
    env["PYTHONPATH"] = str(LAB_ROOT / "src")
    completed = subprocess.run(
        [
            sys.executable,
            str(LAB_ROOT / "scripts" / "watch_rsage_training.py"),
            "--run-dir",
            str(tmp_path),
            "--demo",
        ],
        cwd=LAB_ROOT,
        env=env,
        text=True,
        capture_output=True,
        check=False,
    )

    assert completed.returncode == 0, completed.stderr
    assert "演示完成" in completed.stdout
    assert len(parse_trainer_log(tmp_path / "trainer_log.jsonl")) == 3
    assert (tmp_path / "EXPERIMENT_LOG.md").exists()
    assert (tmp_path / "charts" / "gpu.svg").exists()
