#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from pyt_agent_lab.baseline import load_cases
from pyt_agent_lab.eval_pack_5000 import (
    select_stratified_eval_subset,
    write_eval_pack_jsonl,
    write_manifest,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build a deterministic stratified subset from the 5000-case eval pack.")
    parser.add_argument("--cases", default="research/vllm-agent-ft-lab/cases/domain_cases_5000_v1.jsonl")
    parser.add_argument("--count", type=int, default=500)
    parser.add_argument("--seed", type=int, default=20260709)
    parser.add_argument(
        "--output",
        default="research/vllm-agent-ft-lab/cases/domain_cases_5000_v1_stratified500.jsonl",
    )
    parser.add_argument(
        "--manifest",
        default="research/vllm-agent-ft-lab/outputs/datasets/domain_cases_5000_v1_stratified500_manifest.json",
    )
    args = parser.parse_args()

    cases = load_cases(args.cases)
    subset, manifest = select_stratified_eval_subset(cases, args.count, seed=args.seed)
    count = write_eval_pack_jsonl(subset, Path(args.output))
    write_manifest(manifest, Path(args.manifest))

    print(f"loaded {len(cases)} cases from {args.cases}")
    print(f"wrote {count} stratified cases to {args.output}")
    print(f"wrote manifest to {args.manifest}")
    print("agent_counts:")
    for name, value in manifest["agent_counts"].items():
        print(f"  {name}: {value}")
    print("slice_counts:")
    for name, value in manifest["slice_counts"].items():
        print(f"  {name}: {value}")


if __name__ == "__main__":
    main()
