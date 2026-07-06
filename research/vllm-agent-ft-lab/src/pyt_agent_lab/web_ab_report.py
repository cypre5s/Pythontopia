from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .experiment_compare import compare_summaries


@dataclass(frozen=True)
class WebAbInput:
    baseline_summary: dict[str, Any]
    adapter_summary: dict[str, Any]
    repaired_summary: dict[str, Any]
    serving_models: list[str]


def build_web_ab_report(data: WebAbInput) -> dict[str, Any]:
    raw = compare_summaries(data.baseline_summary, data.adapter_summary)
    repaired = compare_summaries(data.baseline_summary, data.repaired_summary)
    adapter_model = data.serving_models[-1] if data.serving_models else "unknown"
    recommendation = (
        "enable_fine_tuned_provider_with_grounding_guard"
        if repaired["story_gate_passed"]
        else "keep_shadow_mode_and_collect_more_failures"
    )
    return {
        "ab": {
            "raw_adapter_vs_baseline": raw,
            "repaired_vs_baseline": repaired,
        },
        "serving": {
            "models": data.serving_models,
            "adapter_model": adapter_model,
        },
        "web_embedding": {
            "entrypoint": "services/pythontopia-graph/pythontopia_graph/llm/provider.py",
            "feature_flag": "PYT_FINE_TUNED_LLM_ENABLED",
            "fallback_required": True,
            "scope": "structured_json_agent_calls_only",
        },
        "recommendation": recommendation,
    }


def write_web_ab_report(report: dict[str, Any], output_dir: str | Path) -> dict[str, Path]:
    target = Path(output_dir)
    target.mkdir(parents=True, exist_ok=True)
    json_path = target / "web_ab_report.json"
    markdown_path = target / "web_ab_report.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    markdown_path.write_text(_to_markdown(report), encoding="utf-8")
    return {"json": json_path, "markdown": markdown_path}


def _to_markdown(report: dict[str, Any]) -> str:
    raw = report["ab"]["raw_adapter_vs_baseline"]
    repaired = report["ab"]["repaired_vs_baseline"]
    lines = [
        "# Fine-Tuned LLM Web A/B Report",
        "",
        "## Recommendation",
        "",
        f"- `{report['recommendation']}`",
        "",
        "## Serving",
        "",
        f"- Adapter model: `{report['serving']['adapter_model']}`",
        f"- Models: `{', '.join(report['serving']['models'])}`",
        "",
        "## A/B Metrics",
        "",
        "| Candidate | Score Δ | JSON Δ | Grounding Δ | Leak Δ | Gate |",
        "|---|---:|---:|---:|---:|---|",
        _metric_row("Raw adapter", raw),
        _metric_row("Adapter + grounding guard", repaired),
        "",
        "## Web Embedding",
        "",
        f"- Entrypoint: `{report['web_embedding']['entrypoint']}`",
        f"- Feature flag: `{report['web_embedding']['feature_flag']}`",
        f"- Scope: `{report['web_embedding']['scope']}`",
        "- Fallback: required; the Web Agent must keep running when local vLLM is down.",
        "",
    ]
    return "\n".join(lines)


def _metric_row(name: str, comparison: dict[str, Any]) -> str:
    deltas = comparison["deltas"]
    return (
        f"| {name} | {deltas['score_mean']:+.6f} | {deltas['json_valid_rate']:+.6f} | "
        f"{deltas['grounding_safe_rate']:+.6f} | {deltas['forbidden_leak_rate']:+.6f} | "
        f"{'PASS' if comparison['story_gate_passed'] else 'HOLD'} |"
    )
