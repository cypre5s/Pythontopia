#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.golden_adapter import build_expected_baseline, load_jsonl, select_golden_cases, write_jsonl


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the 50-case adapter golden set and expected baseline.")
    parser.add_argument("--cases", default="research/vllm-agent-ft-lab/cases/domain_cases_5000_v1.jsonl")
    parser.add_argument("--golden-output", default="research/vllm-agent-ft-lab/cases/golden_adapter_50_v1.jsonl")
    parser.add_argument("--baseline-output", default="research/vllm-agent-ft-lab/outputs/golden/adapter_baseline_50_v1.jsonl")
    args = parser.parse_args()

    golden_cases = select_golden_cases(load_jsonl(args.cases), per_agent=5)
    baseline = build_expected_baseline(golden_cases)
    write_jsonl(golden_cases, args.golden_output)
    write_jsonl(baseline, args.baseline_output)


if __name__ == "__main__":
    main()
