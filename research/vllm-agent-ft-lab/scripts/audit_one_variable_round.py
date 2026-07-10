#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pyt_agent_lab.training_quality import audit_one_variable_round


def main() -> None:
    parser = argparse.ArgumentParser(description="Audit a candidate plan against champion trainer controls.")
    parser.add_argument("--candidate-plan", required=True)
    parser.add_argument("--champion-plan", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    candidate = json.loads(Path(args.candidate_plan).read_text(encoding="utf-8"))
    champion = json.loads(Path(args.champion_plan).read_text(encoding="utf-8"))
    report = audit_one_variable_round(candidate_plan=candidate, champion_plan=champion)
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, sort_keys=True))
    if not report["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
