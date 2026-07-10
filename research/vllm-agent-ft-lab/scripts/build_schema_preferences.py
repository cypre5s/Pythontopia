#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.schema_preferences import (
    build_schema_preference_dataset,
    load_cases,
    load_failure_records,
    split_and_write_schema_preferences,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build uncontaminated preference pairs targeted at observed schema failures.")
    parser.add_argument("--source-cases", required=True)
    parser.add_argument("--protected", action="append", required=True)
    parser.add_argument("--failure-responses", action="append", default=[])
    parser.add_argument("--target-count", type=int, default=300)
    parser.add_argument("--heldout-ratio", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=20260710)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    protected = load_cases(args.protected)
    failures = load_failure_records(args.failure_responses)
    examples = build_schema_preference_dataset(
        source_cases=load_cases([args.source_cases]),
        protected_cases=protected,
        failure_records=failures,
        target_count=args.target_count,
        seed=args.seed,
    )
    manifest = split_and_write_schema_preferences(
        examples,
        args.output_dir,
        heldout_ratio=args.heldout_ratio,
        seed=args.seed,
        protected_paths=args.protected,
        source_path=args.source_cases,
    )
    print(manifest)


if __name__ == "__main__":
    main()
