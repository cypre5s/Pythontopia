#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.response_audit import load_response_records
from pyt_agent_lab.sft_dataset import build_sft_examples, split_sft_examples, write_sft_dataset


def main() -> None:
    parser = argparse.ArgumentParser(description="Distill clean protocol-patch responses into SFT/LoRA chat data.")
    parser.add_argument("--responses", required=True)
    parser.add_argument("--source-run", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--heldout-ratio", type=float, default=0.2)
    parser.add_argument("--seed", type=int, default=20260702)
    args = parser.parse_args()

    records = load_response_records(args.responses)
    examples = build_sft_examples(records, source_run=args.source_run)
    split = split_sft_examples(examples, heldout_ratio=args.heldout_ratio, seed=args.seed)
    manifest = write_sft_dataset(split, args.output_dir)
    print(manifest)


if __name__ == "__main__":
    main()
