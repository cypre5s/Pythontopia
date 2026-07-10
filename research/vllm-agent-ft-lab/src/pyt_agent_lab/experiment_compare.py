from __future__ import annotations

import json
from pathlib import Path
from typing import Any


METRICS = (
    "contract_pass_rate",
    "score_mean",
    "json_valid_rate",
    "schema_valid_rate",
    "grounding_safe_rate",
    "forbidden_leak_rate",
)


def compare_summaries(baseline: dict[str, Any], candidate: dict[str, Any]) -> dict[str, Any]:
    base = baseline["overall"]
    cand = candidate["overall"]
    deltas = {metric: round(float(cand.get(metric, 0.0)) - float(base.get(metric, 0.0)), 6) for metric in METRICS}
    return {
        "baseline": {metric: float(base.get(metric, 0.0)) for metric in METRICS},
        "candidate": {metric: float(cand.get(metric, 0.0)) for metric in METRICS},
        "deltas": deltas,
        "story_gate_passed": _story_gate_from_deltas(deltas, cand),
    }


def story_gate_passed(comparison: dict[str, Any]) -> bool:
    return bool(comparison.get("story_gate_passed"))


def write_comparison_report(comparison: dict[str, Any], out_dir: str | Path) -> None:
    target = Path(out_dir)
    charts = target / "charts"
    charts.mkdir(parents=True, exist_ok=True)
    (target / "comparison.json").write_text(json.dumps(comparison, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    _write_comparison_svg(charts / "baseline_vs_candidate.svg", comparison)


def load_summary(path: str | Path) -> dict[str, Any]:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def _story_gate_from_deltas(deltas: dict[str, float], candidate: dict[str, Any]) -> bool:
    return (
        deltas["contract_pass_rate"] > 0
        and deltas["schema_valid_rate"] >= 0
        and deltas["grounding_safe_rate"] >= 0
        and float(candidate.get("forbidden_leak_rate", 1.0)) == 0.0
    )


def _write_comparison_svg(path: Path, comparison: dict[str, Any]) -> None:
    width = 760
    height = 380
    margin = 64
    labels = list(METRICS)
    max_value = max(1.0, *(comparison["baseline"].values()), *(comparison["candidate"].values()))
    group_width = (width - 2 * margin) / len(labels)
    bar_width = group_width * 0.32
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#fff"/>',
        '<text x="64" y="32" font-family="Arial" font-size="20" fill="#111827">Baseline vs Candidate</text>',
        f'<line x1="{margin}" y1="{height - margin}" x2="{width - margin}" y2="{height - margin}" stroke="#6b7280"/>',
        f'<line x1="{margin}" y1="{margin}" x2="{margin}" y2="{height - margin}" stroke="#6b7280"/>',
    ]
    plot_height = height - 2 * margin
    for idx, label in enumerate(labels):
        x0 = margin + idx * group_width + group_width * 0.18
        for offset, key, color in ((0, "baseline", "#64748b"), (1, "candidate", "#16a34a")):
            value = comparison[key][label]
            h = value / max_value * plot_height
            x = x0 + offset * bar_width
            y = height - margin - h
            parts.append(f'<rect x="{x:.2f}" y="{y:.2f}" width="{bar_width * 0.86:.2f}" height="{h:.2f}" fill="{color}"><title>{key} {label}: {value:.3f}</title></rect>')
            parts.append(f'<text x="{x + bar_width * 0.43:.2f}" y="{max(y - 6, 52):.2f}" text-anchor="middle" font-family="Arial" font-size="11" fill="#111827">{value:.2f}</text>')
        parts.append(f'<text x="{x0 + bar_width:.2f}" y="{height - 24}" text-anchor="middle" font-family="Arial" font-size="11" fill="#374151">{label}</text>')
    parts.extend(
        [
            '<rect x="590" y="54" width="12" height="12" fill="#64748b"/>',
            '<text x="608" y="65" font-family="Arial" font-size="12" fill="#374151">baseline</text>',
            '<rect x="590" y="76" width="12" height="12" fill="#16a34a"/>',
            '<text x="608" y="87" font-family="Arial" font-size="12" fill="#374151">candidate</text>',
            "</svg>",
        ]
    )
    path.write_text("\n".join(parts), encoding="utf-8")
