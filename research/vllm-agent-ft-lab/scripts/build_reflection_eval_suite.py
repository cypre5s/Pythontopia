#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pyt_agent_lab.reflection_preferences import build_reflection_eval_cases, jsonl_text
from pyt_agent_lab.v2_preferences import load_jsonl


def main() -> None:
    parser = argparse.ArgumentParser(description="Build protected ReflectionAgent v2 cases.")
    parser.add_argument("--source", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    rows = build_reflection_eval_cases(load_jsonl([args.source]))
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(jsonl_text(rows), encoding="utf-8")
    print(json.dumps({"source": args.source, "output": args.output, "count": len(rows)}))


if __name__ == "__main__":
    main()
