#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.blind_review import export_blind_review_pack, load_responses


def main() -> None:
    parser = argparse.ArgumentParser(description="Export anonymized manual blind-review sheets from an eval responses file.")
    parser.add_argument("--responses", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--sample-size", type=int, default=120)
    parser.add_argument("--seed", type=int, default=20260706)
    args = parser.parse_args()

    responses = load_responses(args.responses)
    outputs = export_blind_review_pack(
        responses,
        args.output_dir,
        sample_size=args.sample_size,
        seed=args.seed,
    )
    for label, path in outputs.items():
        print(f"{label}: {path}")


if __name__ == "__main__":
    main()
