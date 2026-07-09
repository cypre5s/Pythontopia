from __future__ import annotations

import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any


def aggregate_fold_metrics(rows: list[dict[str, Any]]) -> dict[str, Any]:
    folds: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        folds[str(row["fold"])].append(row)
    fold_summary = {fold: _summarize(items) for fold, items in sorted(folds.items(), key=lambda item: int(item[0]))}
    return {
        "overall": _summarize(rows),
        "folds": fold_summary,
    }


def build_report(summary: dict[str, Any], out_dir: str | Path) -> None:
    target = Path(out_dir)
    charts_dir = target / "charts"
    charts_dir.mkdir(parents=True, exist_ok=True)
    (target / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")

    folds = summary["folds"]
    labels = list(folds.keys())
    score_values = [folds[label]["score_mean"] for label in labels]
    json_rates = [folds[label].get("json_valid_rate", 0.0) for label in labels]
    schema_rates = [folds[label].get("schema_valid_rate", 0.0) for label in labels]
    grounding_rates = [folds[label].get("grounding_safe_rate", 0.0) for label in labels]
    _write_bar_svg(charts_dir / "score_by_fold.svg", "Score by fold", labels, [("score", score_values)])
    _write_bar_svg(
        charts_dir / "rates_by_fold.svg",
        "Validation rates by fold",
        labels,
        [("json", json_rates), ("schema", schema_rates), ("grounding", grounding_rates)],
    )


def _summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    scores = [float(row.get("score", 0.0)) for row in rows]
    return {
        "case_count": len(rows),
        "score_mean": _mean(scores),
        "score_std": _std(scores),
        "json_valid_rate": _rate(rows, "json_valid"),
        "schema_valid_rate": _rate(rows, "schema_valid"),
        "grounding_safe_rate": _rate(rows, "grounding_safe"),
        "forbidden_leak_rate": _rate(rows, "forbidden_leak"),
    }


def _mean(values: list[float]) -> float:
    if not values:
        return 0.0
    return round(sum(values) / len(values), 6)


def _std(values: list[float]) -> float:
    if len(values) <= 1:
        return 0.0
    mean = sum(values) / len(values)
    variance = sum((value - mean) ** 2 for value in values) / (len(values) - 1)
    return round(math.sqrt(variance), 6)


def _rate(rows: list[dict[str, Any]], key: str) -> float:
    if not rows:
        return 0.0
    return round(sum(1 for row in rows if _truthy(row.get(key))) / len(rows), 6)


def _truthy(value: Any) -> bool:
    if isinstance(value, str):
        return value.lower() in {"1", "true", "yes"}
    return bool(value)


def _write_bar_svg(path: Path, title: str, labels: list[str], series: list[tuple[str, list[float]]]) -> None:
    width = 720
    height = 360
    margin = 52
    plot_width = width - margin * 2
    plot_height = height - margin * 2
    max_value = max([1.0] + [value for _, values in series for value in values])
    group_width = plot_width / max(1, len(labels))
    bar_width = group_width / (len(series) + 1)
    colors = ["#2563eb", "#16a34a", "#dc2626", "#9333ea"]
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text x="{margin}" y="30" font-family="Arial" font-size="20" fill="#111827">{_escape(title)}</text>',
        f'<line x1="{margin}" y1="{height - margin}" x2="{width - margin}" y2="{height - margin}" stroke="#6b7280"/>',
        f'<line x1="{margin}" y1="{margin}" x2="{margin}" y2="{height - margin}" stroke="#6b7280"/>',
    ]
    for label_index, label in enumerate(labels):
        group_x = margin + label_index * group_width
        parts.append(
            f'<text x="{group_x + group_width / 2}" y="{height - 20}" text-anchor="middle" '
            f'font-family="Arial" font-size="12" fill="#374151">{_escape(label)}</text>'
        )
        for series_index, (name, values) in enumerate(series):
            value = values[label_index]
            bar_height = 0 if max_value == 0 else (value / max_value) * plot_height
            x = group_x + (series_index + 0.5) * bar_width
            y = height - margin - bar_height
            parts.append(
                f'<rect x="{x:.2f}" y="{y:.2f}" width="{bar_width * 0.78:.2f}" height="{bar_height:.2f}" '
                f'fill="{colors[series_index % len(colors)]}"><title>{_escape(name)}: {value:.3f}</title></rect>'
            )
            parts.append(
                f'<text x="{x + bar_width * 0.39:.2f}" y="{max(y - 6, 48):.2f}" text-anchor="middle" '
                f'font-family="Arial" font-size="11" fill="#111827">{value:.2f}</text>'
            )
    for series_index, (name, _) in enumerate(series):
        legend_x = width - margin - 130
        legend_y = margin + series_index * 22
        parts.append(f'<rect x="{legend_x}" y="{legend_y - 12}" width="12" height="12" fill="{colors[series_index % len(colors)]}"/>')
        parts.append(f'<text x="{legend_x + 18}" y="{legend_y - 2}" font-family="Arial" font-size="12" fill="#374151">{_escape(name)}</text>')
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def _escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
