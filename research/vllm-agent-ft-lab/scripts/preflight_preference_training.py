#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from transformers import AutoTokenizer

from pyt_agent_lab.training_quality import (
    run_preference_preflight,
    write_preference_preflight,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Run preference-data checks before GPU training.")
    parser.add_argument("--plan", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    plan = json.loads(Path(args.plan).read_text(encoding="utf-8"))
    tokenizer = AutoTokenizer.from_pretrained(plan["model_path"], trust_remote_code=False)
    report = run_preference_preflight(
        train_file=plan["train_file"],
        heldout_file=plan["heldout_file"],
        tokenizer=tokenizer,
        max_seq_length=int(plan["max_seq_length"]),
        changed_variables=(plan.get("quality_round") or {}).get("changed_variables"),
    )
    write_preference_preflight(report, args.output)
    print(json.dumps(report, ensure_ascii=False, sort_keys=True))
    if not report["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
