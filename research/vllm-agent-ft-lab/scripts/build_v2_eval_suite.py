#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pyt_agent_lab.v2_preferences import build_v2_eval_cases, load_jsonl


def main() -> None:
    parser = argparse.ArgumentParser(description="Filter a protected eval suite and optionally project it to v2 IR.")
    parser.add_argument("--source", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--agent", action="append", default=[])
    parser.add_argument(
        "--preserve-schema",
        action="store_true",
        help="Only filter by agent; keep the source prompt and schema version unchanged.",
    )
    args = parser.parse_args()
    source_rows = load_jsonl([args.source])
    if args.preserve_schema:
        selected = set(args.agent)
        rows = [row for row in source_rows if not selected or str(row.get("agent")) in selected]
    else:
        rows = build_v2_eval_cases(
            source_rows,
            **({"agents": tuple(args.agent)} if args.agent else {}),
        )
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    print(json.dumps({"source": args.source, "output": args.output, "count": len(rows)}))


if __name__ == "__main__":
    main()
