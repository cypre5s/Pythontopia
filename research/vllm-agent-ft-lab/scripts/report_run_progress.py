#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from pyt_agent_lab.progress_report import load_progress_snapshots, write_progress_report


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate realtime progress charts and tables for a running eval.")
    parser.add_argument("--run-dir", required=True)
    parser.add_argument("--output-dir")
    args = parser.parse_args()

    run_dir = Path(args.run_dir)
    output_dir = Path(args.output_dir) if args.output_dir else run_dir
    snapshots = load_progress_snapshots(run_dir / "progress_snapshots.jsonl")
    outputs = write_progress_report(snapshots, output_dir)
    for label, path in outputs.items():
        print(f"{label}: {path}")


if __name__ == "__main__":
    main()
