#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pyt_agent_lab.preference_dataset import (
    build_preference_examples,
    split_preference_examples,
    write_preference_dataset,
)
from pyt_agent_lab.response_audit import load_response_records


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build DPO/ORPO preference data from clean chosen and audited bad rejected responses."
    )
    parser.add_argument("--chosen-responses", required=True)
    parser.add_argument("--rejected-responses", required=True)
    parser.add_argument("--chosen-source", required=True)
    parser.add_argument("--rejected-source", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--heldout-ratio", type=float, default=0.2)
    parser.add_argument("--seed", type=int, default=20260707)
    args = parser.parse_args()

    chosen_records = load_response_records(args.chosen_responses)
    rejected_records = load_response_records(args.rejected_responses)
    examples = build_preference_examples(
        chosen_records=chosen_records,
        rejected_records=rejected_records,
        chosen_source=args.chosen_source,
        rejected_source=args.rejected_source,
    )
    split = split_preference_examples(
        examples, heldout_ratio=args.heldout_ratio, seed=args.seed
    )
    manifest = write_preference_dataset(split, args.output_dir)
    manifest.update(
        {
            "chosen_responses": args.chosen_responses,
            "rejected_responses": args.rejected_responses,
            "heldout_ratio": args.heldout_ratio,
            "seed": args.seed,
        }
    )
    Path(args.output_dir, "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    print(json.dumps(manifest, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
