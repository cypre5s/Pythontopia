#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pyt_agent_lab.rsage_live_report import append_event, poll_once, watch_training


def main() -> int:
    parser = argparse.ArgumentParser(
        description="增量监控 RSAGE 训练日志，并刷新中文实验记录、GPU 遥测与 SVG 图表。"
    )
    parser.add_argument("--run-dir", required=True, help="包含 trainer_log.jsonl 的训练输出目录。")
    parser.add_argument("--interval-seconds", type=float, default=10.0, help="轮询间隔，默认 10 秒。")
    parser.add_argument("--max-polls", type=int, default=None, help="最多轮询次数；省略时持续运行。")
    parser.add_argument("--demo", action="store_true", help="逐行生成三条合成训练日志并立即退出。")
    args = parser.parse_args()

    run_dir = Path(args.run_dir).expanduser().resolve()
    run_dir.mkdir(parents=True, exist_ok=True)
    if args.demo:
        return _run_demo(run_dir)

    append_event(run_dir, event="监控启动", message_zh="RSAGE 实时训练监控已启动")
    try:
        results = watch_training(
            run_dir,
            interval_seconds=args.interval_seconds,
            max_polls=args.max_polls,
        )
    except KeyboardInterrupt:
        append_event(run_dir, event="监控停止", message_zh="用户中断了 RSAGE 实时训练监控")
        print("监控已停止；已有日志和图表均已保留。")
        return 130
    append_event(
        run_dir,
        event="监控完成",
        message_zh=f"RSAGE 实时训练监控已完成 {len(results)} 次轮询",
        poll_count=len(results),
    )
    print(json.dumps(results[-1] if results else {}, ensure_ascii=False, sort_keys=True))
    return 0


def _run_demo(run_dir: Path) -> int:
    trainer_log = run_dir / "trainer_log.jsonl"
    if trainer_log.exists() and trainer_log.stat().st_size > 0:
        raise ValueError("--demo 需要空的 run-dir，避免污染真实训练日志")
    synthetic_rows = [
        {
            "step": 1,
            "epoch": 0.25,
            "loss": 1.18,
            "rewards/chosen": 0.22,
            "rewards/rejected": -0.18,
            "rewards/accuracies": 0.5,
            "train_samples_per_second": 1.2,
        },
        {
            "step": 2,
            "epoch": 0.5,
            "loss": 0.91,
            "rewards/chosen": 0.48,
            "rewards/rejected": -0.12,
            "rewards/accuracies": 0.75,
            "train_samples_per_second": 1.45,
        },
        {
            "step": 3,
            "epoch": 0.75,
            "loss": 0.72,
            "eval_loss": 0.79,
            "rewards/chosen": 0.65,
            "rewards/rejected": -0.08,
            "rewards/accuracies": 1.0,
            "train_samples_per_second": 1.61,
        },
    ]
    append_event(run_dir, event="演示启动", message_zh="开始生成逐步增长的合成训练日志")
    latest: dict[str, object] = {}
    for row in synthetic_rows:
        with trainer_log.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
        latest = poll_once(run_dir)
    append_event(
        run_dir,
        event="演示完成",
        message_zh="合成训练日志演示完成，实时报告已生成",
        trainer_rows=len(synthetic_rows),
    )
    print(f"演示完成：{json.dumps(latest, ensure_ascii=False, sort_keys=True)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
