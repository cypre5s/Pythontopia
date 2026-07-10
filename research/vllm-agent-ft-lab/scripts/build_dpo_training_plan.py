#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pyt_agent_lab.preference_training_plan import (
    build_dpo_training_plan,
    build_orpo_training_plan,
    write_preference_training_plan,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build a reproducible DPO v2 training plan.")
    parser.add_argument("--model-path", required=True)
    parser.add_argument("--sft-adapter-path", required=True)
    parser.add_argument("--train-file", required=True)
    parser.add_argument("--heldout-file", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--plan-output", required=True)
    parser.add_argument("--max-seq-length", type=int, default=768)
    parser.add_argument("--method", choices=["dpo", "orpo"], default="dpo")
    parser.add_argument("--beta", type=float, default=None)
    parser.add_argument("--learning-rate", type=float, default=None)
    parser.add_argument("--max-grad-norm", type=float, default=None)
    parser.add_argument("--seed", type=int, default=20260710)
    parser.add_argument("--champion-adapter-path", default=None)
    parser.add_argument("--changed-variable", default=None)
    args = parser.parse_args()

    common_args = {
        "model_path": args.model_path,
        "sft_adapter_path": args.sft_adapter_path,
        "train_file": args.train_file,
        "heldout_file": args.heldout_file,
        "output_dir": args.output_dir,
        "max_seq_length": args.max_seq_length,
        "seed": args.seed,
        "champion_adapter_path": args.champion_adapter_path,
    }
    if args.method == "orpo":
        orpo_args = dict(common_args)
        if args.beta is not None:
            orpo_args["beta"] = args.beta
        if args.learning_rate is not None:
            orpo_args["learning_rate"] = args.learning_rate
        if args.max_grad_norm is not None:
            orpo_args["max_grad_norm"] = args.max_grad_norm
        if args.changed_variable is not None:
            orpo_args["changed_variable"] = args.changed_variable
        plan = build_orpo_training_plan(**orpo_args)
    else:
        if args.changed_variable is not None:
            common_args["changed_variable"] = args.changed_variable
        plan = build_dpo_training_plan(**common_args)
    write_preference_training_plan(plan, args.plan_output)
    print(json.dumps({"plan_output": str(Path(args.plan_output)), "plan": plan}, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
