#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pyt_agent_lab.code_plan import jsonl_text, upgrade_code_lab_cases
from pyt_agent_lab.v2_preferences import load_jsonl


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Upgrade only CodeLab cases to v2.1 CodePlan in a protected suite."
    )
    parser.add_argument("--source", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    rows = upgrade_code_lab_cases(load_jsonl([args.source]))
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(jsonl_text(rows), encoding="utf-8")
    code_plan_count = sum(
        row.get("schema_version") == "agent-schema-v2.1" for row in rows
    )
    print(
        json.dumps(
            {
                "source": args.source,
                "output": args.output,
                "count": len(rows),
                "code_plan_count": code_plan_count,
            }
        )
    )


if __name__ == "__main__":
    main()
