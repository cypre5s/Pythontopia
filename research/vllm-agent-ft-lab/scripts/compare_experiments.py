#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.experiment_compare import compare_summaries, load_summary, write_comparison_report


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare baseline and candidate experiment summaries.")
    parser.add_argument("--baseline-summary", required=True)
    parser.add_argument("--candidate-summary", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    comparison = compare_summaries(load_summary(args.baseline_summary), load_summary(args.candidate_summary))
    write_comparison_report(comparison, args.output_dir)
    print(comparison)


if __name__ == "__main__":
    main()
