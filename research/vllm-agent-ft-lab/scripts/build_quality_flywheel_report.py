#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from pyt_agent_lab.quality_flywheel import write_quality_flywheel_report


def main() -> None:
    parser = argparse.ArgumentParser(description="Build layered contract metrics, paired comparison, and failure matrices.")
    parser.add_argument("--model", action="append", required=True, metavar="NAME=RESPONSES_JSONL")
    parser.add_argument("--cases", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--prompt-variant", default="baseline")
    parser.add_argument("--temperature", type=float, default=0.0)
    parser.add_argument("--seed", type=int, default=20260708)
    args = parser.parse_args()
    models: dict[str, Path] = {}
    for value in args.model:
        name, separator, path = value.partition("=")
        if not separator or not name or not path:
            parser.error("--model must use NAME=RESPONSES_JSONL")
        models[name] = Path(path)
    report = write_quality_flywheel_report(
        models,
        args.output_dir,
        cases_path=args.cases,
        prompt_variant=args.prompt_variant,
        temperature=args.temperature,
        seed=args.seed,
    )
    for name, summary in report["models"].items():
        print(name, summary)


if __name__ == "__main__":
    main()
