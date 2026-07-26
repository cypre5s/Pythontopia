#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.v2_preferences import (
    build_v2_preference_dataset,
    load_jsonl,
    split_and_write_v2_preferences,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build protected v2 semantic IR preferences.")
    parser.add_argument("--source", required=True)
    parser.add_argument("--protected", action="append", required=True)
    parser.add_argument("--target-count", type=int, default=400)
    parser.add_argument("--heldout-ratio", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=20260711)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--agent", action="append", default=[])
    parser.add_argument(
        "--failure-directed",
        action="store_true",
        help="Use per-Agent rejection axes derived from real raw failure categories.",
    )
    args = parser.parse_args()
    rows = build_v2_preference_dataset(
        source_cases=load_jsonl([args.source]),
        protected_cases=load_jsonl(args.protected),
        target_count=args.target_count,
        seed=args.seed,
        failure_directed=args.failure_directed,
        **({"agents": tuple(args.agent)} if args.agent else {}),
    )
    manifest = split_and_write_v2_preferences(
        rows,
        args.output_dir,
        heldout_ratio=args.heldout_ratio,
        seed=args.seed,
        source_path=args.source,
        protected_paths=args.protected,
    )
    print(manifest)


if __name__ == "__main__":
    main()
