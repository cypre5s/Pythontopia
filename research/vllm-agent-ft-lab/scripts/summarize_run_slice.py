#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.lora_evaluation import load_sft_source_case_ids
from pyt_agent_lab.run_slice import write_run_slice


def main() -> None:
    parser = argparse.ArgumentParser(description="Summarize a run on the case IDs present in an SFT split.")
    parser.add_argument("--metrics", required=True)
    parser.add_argument("--sft-split", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--limit", type=int, default=None)
    args = parser.parse_args()

    case_ids = load_sft_source_case_ids(args.sft_split)
    if args.limit is not None:
        case_ids = case_ids[: args.limit]
    summary = write_run_slice(args.metrics, case_ids, args.output_dir)
    print(summary)


if __name__ == "__main__":
    main()
