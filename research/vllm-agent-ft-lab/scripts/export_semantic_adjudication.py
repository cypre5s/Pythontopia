#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.blind_review import load_responses
from pyt_agent_lab.semantic_adjudication import export_paired_semantic_adjudication


def main() -> None:
    parser = argparse.ArgumentParser(description="Export anonymized paired GuideCoach semantic review.")
    parser.add_argument("--model", action="append", required=True, metavar="NAME=RESPONSES_JSONL")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--seed", type=int, default=20260712)
    args = parser.parse_args()
    models = {}
    for value in args.model:
        name, separator, path = value.partition("=")
        if not separator or not name or not path:
            parser.error("--model must use NAME=RESPONSES_JSONL")
        models[name] = load_responses(path)
    outputs = export_paired_semantic_adjudication(models, args.output_dir, seed=args.seed)
    for name, path in outputs.items():
        print(f"{name}: {path}")


if __name__ == "__main__":
    main()
