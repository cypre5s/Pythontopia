#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from pyt_agent_lab.eval_pack_5000 import build_eval_pack_5000, write_eval_pack_jsonl, write_manifest


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the 5000-case Pythontopia fine-tuning evaluation pack.")
    parser.add_argument("--count", type=int, default=5000)
    parser.add_argument("--seed", type=int, default=20260706)
    parser.add_argument(
        "--output",
        default="research/vllm-agent-ft-lab/cases/domain_cases_5000_v1.jsonl",
        help="Output JSONL case file.",
    )
    parser.add_argument(
        "--manifest",
        default="research/vllm-agent-ft-lab/outputs/datasets/domain_cases_5000_v1_manifest.json",
        help="Output dataset manifest JSON.",
    )
    args = parser.parse_args()

    cases, manifest = build_eval_pack_5000(args.count, seed=args.seed)
    count = write_eval_pack_jsonl(cases, args.output)
    write_manifest(manifest, args.manifest)

    print(f"wrote {count} cases to {args.output}")
    print(f"wrote manifest to {args.manifest}")
    print("slice_counts:")
    for name, value in manifest["slice_counts"].items():
        print(f"  {name}: {value}")
    print("source_kind_counts:")
    for name, value in manifest["source_kind_counts"].items():
        print(f"  {name}: {value}")


if __name__ == "__main__":
    main()
