#!/usr/bin/env python3
from __future__ import annotations

import argparse
from collections import Counter

from pyt_agent_lab.beginner_noise_cases import (
    augment_cases_with_beginner_noise,
    load_cases_jsonl,
    write_beginner_noise_cases_jsonl,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build novice-expression variants of Pythontopia Agent eval cases.")
    parser.add_argument("--input", required=True, help="Source JSONL domain cases.")
    parser.add_argument("--output", required=True, help="Output JSONL with beginner-noise prompts.")
    parser.add_argument("--seed", type=int, default=20260703)
    args = parser.parse_args()

    cases = load_cases_jsonl(args.input)
    augmented = augment_cases_with_beginner_noise(cases, seed=args.seed)
    count = write_beginner_noise_cases_jsonl(cases, args.output, seed=args.seed)
    profile_counts = Counter(case["beginner_noise_profile"] for case in augmented)

    print(f"wrote {count} beginner-noise cases to {args.output}")
    for profile, profile_count in sorted(profile_counts.items()):
        print(f"{profile}: {profile_count}")


if __name__ == "__main__":
    main()
