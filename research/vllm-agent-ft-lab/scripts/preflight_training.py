#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.training_plan import build_training_plan, detect_training_dependencies, write_preflight_report, write_training_plan


def main() -> None:
    parser = argparse.ArgumentParser(description="Write and validate a QLoRA training plan for the local Qwen3.5 lab.")
    parser.add_argument("--model-path", required=True)
    parser.add_argument("--train-file", required=True)
    parser.add_argument("--heldout-file", required=True)
    parser.add_argument("--adapter-output", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--method", default="qlora_4bit")
    args = parser.parse_args()

    plan = build_training_plan(
        model_path=args.model_path,
        train_file=args.train_file,
        heldout_file=args.heldout_file,
        output_dir=args.adapter_output,
        method=args.method,
    )
    write_training_plan(plan, f"{args.output_dir}/training_plan.json")
    report = write_preflight_report(plan, detect_training_dependencies(args.method), args.output_dir)
    print(report["dependency_status"])


if __name__ == "__main__":
    main()
