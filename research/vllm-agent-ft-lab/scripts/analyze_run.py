#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from pyt_agent_lab.run_analysis import build_extended_summary, load_metrics, write_additional_charts, write_extended_summary


def main() -> None:
    parser = argparse.ArgumentParser(description="Build extended reliability charts for a vLLM evaluation run.")
    parser.add_argument("--run-dir", required=True)
    args = parser.parse_args()

    run_dir = Path(args.run_dir)
    rows = load_metrics(run_dir / "metrics.csv")
    summary = build_extended_summary(rows)
    write_extended_summary(summary, run_dir / "extended_summary.json")
    write_additional_charts(summary, run_dir / "charts")
    print(run_dir / "extended_summary.json")
    print(run_dir / "charts")


if __name__ == "__main__":
    main()
