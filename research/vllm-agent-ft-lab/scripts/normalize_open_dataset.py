#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.dataset_normalization import normalize_ed_file


def main() -> None:
    parser = argparse.ArgumentParser(description="Normalize open tutoring datasets into lab JSONL.")
    parser.add_argument("--dataset", choices=["google-ed"], required=True)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--split", default="train")
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()

    if args.dataset == "google-ed":
        count = normalize_ed_file(args.input, args.output, split=args.split, limit=args.limit)
    else:
        raise ValueError(args.dataset)
    print(f"wrote {count} normalized rows to {args.output}")


if __name__ == "__main__":
    main()
