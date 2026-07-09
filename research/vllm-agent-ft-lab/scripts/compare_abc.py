#!/usr/bin/env python3
"""Generate a 3-way A/B/C comparison report from three existing run directories.

Use this to re-generate charts and reports after a run, or to compare
runs from different experiments.
"""
from __future__ import annotations

import argparse
from pathlib import Path

from pyt_agent_lab.abc_compare import (
    compare_by_agent,
    compare_three,
    load_group_summary,
    write_abc_report,
)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Compare three experiment run directories (A/B/C) and produce charts."
    )
    parser.add_argument(
        "--a-summary",
        required=True,
        help="Path to Group A summary.json (external LLM).",
    )
    parser.add_argument(
        "--b-summary",
        required=True,
        help="Path to Group B summary.json (Qwen LoRA).",
    )
    parser.add_argument(
        "--c-summary",
        required=True,
        help="Path to Group C summary.json (Qwen LoRA + Guard).",
    )
    parser.add_argument(
        "--a-metrics",
        help="Path to Group A metrics.csv for per-agent breakdown.",
    )
    parser.add_argument(
        "--b-metrics",
        help="Path to Group B metrics.csv for per-agent breakdown.",
    )
    parser.add_argument(
        "--c-metrics",
        help="Path to Group C metrics.csv for per-agent breakdown.",
    )
    parser.add_argument(
        "--output-dir",
        required=True,
        help="Directory to write comparison report and charts.",
    )
    args = parser.parse_args()

    summary_a = load_group_summary(args.a_summary)
    summary_b = load_group_summary(args.b_summary)
    summary_c = load_group_summary(args.c_summary)

    comparison = compare_three(summary_a, summary_b, summary_c)
    paths = write_abc_report(comparison, args.output_dir)

    # Per-agent breakdown (requires metrics CSVs)
    if args.a_metrics and args.b_metrics and args.c_metrics:
        compare_by_agent(
            args.a_metrics,
            args.b_metrics,
            args.c_metrics,
            Path(args.output_dir) / "charts",
        )

    verdict = comparison["ab_test"]["verdict"]
    print(f"A/B verdict (C vs A): {verdict}")
    print(f"Comparison written to: {args.output_dir}")
    for key, p in paths.items():
        if isinstance(p, Path):
            print(f"  {key}: {p}")


if __name__ == "__main__":
    main()
