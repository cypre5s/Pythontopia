#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pyt_agent_lab.web_ab_report import WebAbInput, build_web_ab_report, write_web_ab_report


def main() -> None:
    parser = argparse.ArgumentParser(description="Build a Web embedding A/B report for the fine-tuned Pythontopia Agent adapter.")
    parser.add_argument("--baseline-summary", required=True)
    parser.add_argument("--adapter-summary", required=True)
    parser.add_argument("--repaired-summary", required=True)
    parser.add_argument("--models-json", required=True, help="Path to a saved /v1/models JSON response, or a JSON list of model ids.")
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    report = build_web_ab_report(
        WebAbInput(
            baseline_summary=_read_json(args.baseline_summary),
            adapter_summary=_read_json(args.adapter_summary),
            repaired_summary=_read_json(args.repaired_summary),
            serving_models=_read_models(args.models_json),
        )
    )
    outputs = write_web_ab_report(report, args.output_dir)
    print(json.dumps({key: str(value) for key, value in outputs.items()}, ensure_ascii=False, indent=2))


def _read_json(path: str | Path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def _read_models(value: str) -> list[str]:
    path = Path(value)
    raw = path.read_text(encoding="utf-8") if path.exists() else value
    parsed = json.loads(raw)
    if isinstance(parsed, list):
        return [str(item) for item in parsed]
    return [str(item["id"]) for item in parsed.get("data", []) if isinstance(item, dict) and item.get("id")]


if __name__ == "__main__":
    main()
