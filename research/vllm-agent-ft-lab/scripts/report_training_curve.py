#!/usr/bin/env python3
from __future__ import annotations

import argparse

from pyt_agent_lab.training_report import write_training_curve_report


def main() -> None:
    parser = argparse.ArgumentParser(description="Export QLoRA training loss/eval-loss curves from a Trainer state file.")
    parser.add_argument("--trainer-state", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    report = write_training_curve_report(args.trainer_state, args.output_dir)
    print({"train_points": len(report["train_loss"]), "eval_points": len(report["eval_loss"])})


if __name__ == "__main__":
    main()
