#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json

from pyt_agent_lab.code_plan_preferences import (
    build_code_plan_preference_dataset,
    split_and_write_code_plan_preferences,
)
from pyt_agent_lab.v2_preferences import load_jsonl


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build uncontaminated CodePlan v2.1 chosen/rejected data."
    )
    parser.add_argument("--source", required=True)
    parser.add_argument("--protected", action="append", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--target-count", type=int, default=240)
    parser.add_argument("--heldout-ratio", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=20260713)
    args = parser.parse_args()
    rows = build_code_plan_preference_dataset(
        source_cases=load_jsonl([args.source]),
        protected_cases=load_jsonl(args.protected),
        target_count=args.target_count,
        seed=args.seed,
    )
    manifest = split_and_write_code_plan_preferences(
        rows,
        args.output_dir,
        heldout_ratio=args.heldout_ratio,
        seed=args.seed,
        source_path=args.source,
        protected_paths=args.protected,
    )
    print(json.dumps(manifest, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
