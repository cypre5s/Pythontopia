from __future__ import annotations

import csv
import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


def load_metrics(path: str | Path) -> list[dict[str, Any]]:
    with Path(path).open(encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def build_extended_summary(rows: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "overall": _summarize(rows),
        "by_fold": _group_and_summarize(rows, "fold", numeric_keys=True),
        "by_agent": _group_and_summarize(rows, "agent"),
        "by_noise_profile": _group_and_summarize(rows, "_noise_profile"),
    }


def write_extended_summary(summary: dict[str, Any], path: str | Path) -> None:
    Path(path).write_text(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")


def write_additional_charts(summary: dict[str, Any], charts_dir: str | Path) -> None:
    target = Path(charts_dir)
    target.mkdir(parents=True, exist_ok=True)
    _write_grouped_bar_svg(
        target / "rates_by_noise_profile.svg",
        title="Reliability rates by beginner-noise profile",
        labels=list(summary["by_noise_profile"].keys()),
        series=[
            ("json", [item["json_valid_rate"] for item in summary["by_noise_profile"].values()]),
            ("grounding", [item["grounding_safe_rate"] for item in summary["by_noise_profile"].values()]),
            ("no leak", [1.0 - item["forbidden_leak_rate"] for item in summary["by_noise_profile"].values()]),
        ],
        value_format="{:.2f}",
    )
    _write_grouped_bar_svg(
        target / "error_counts_by_agent.svg",
        title="Error counts by agent",
        labels=list(summary["by_agent"].keys()),
        series=[
            ("json invalid", [item["json_invalid_count"] for item in summary["by_agent"].values()]),
            ("grounding false", [item["grounding_false_count"] for item in summary["by_agent"].values()]),
            ("forbidden leak", [item["forbidden_leak_count"] for item in summary["by_agent"].values()]),
        ],
        value_format="{:.0f}",
    )
    _write_grouped_bar_svg(
        target / "latency_by_fold.svg",
        title="Latency by fold",
        labels=list(summary["by_fold"].keys()),
        series=[
            ("mean ms", [item["latency_ms_mean"] for item in summary["by_fold"].values()]),
            ("p95 ms", [item["latency_ms_p95"] for item in summary["by_fold"].values()]),
        ],
        value_format="{:.0f}",
    )


def _group_and_summarize(rows: list[dict[str, Any]], key: str, numeric_keys: bool = False) -> dict[str, Any]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        group_key = _noise_profile(row.get("case_id", "")) if key == "_noise_profile" else str(row.get(key, "unknown"))
        grouped[group_key].append(row)
    sort_key = (lambda item: int(item[0])) if numeric_keys else (lambda item: item[0])
    return {group: _summarize(items) for group, items in sorted(grouped.items(), key=sort_key)}


def _summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    scores = [_float(row.get("score")) for row in rows]
    latency = [_float(row.get("latency_ms")) for row in rows if row.get("latency_ms") not in {None, ""}]
    json_invalid_count = sum(not _truthy(row.get("json_valid")) for row in rows)
    grounding_false_count = sum(not _truthy(row.get("grounding_safe")) for row in rows)
    forbidden_leak_count = sum(_truthy(row.get("forbidden_leak")) for row in rows)
    count = len(rows)
    return {
        "case_count": count,
        "score_mean": _round(_mean(scores)),
        "score_std": _round(_std(scores)),
        "json_valid_rate": _round((count - json_invalid_count) / count) if count else 0.0,
        "json_invalid_count": json_invalid_count,
        "grounding_safe_rate": _round((count - grounding_false_count) / count) if count else 0.0,
        "grounding_false_count": grounding_false_count,
        "forbidden_leak_rate": _round(forbidden_leak_count / count) if count else 0.0,
        "forbidden_leak_count": forbidden_leak_count,
        "latency_ms_mean": _round(_mean(latency), digits=2),
        "latency_ms_p50": _round(_percentile(latency, 50), digits=2),
        "latency_ms_p95": _round(_percentile(latency, 95), digits=2),
        "latency_ms_max": _round(max(latency), digits=2) if latency else 0.0,
    }


def _noise_profile(case_id: str) -> str:
    if "-beginner-" not in case_id:
        return "clean"
    return case_id.split("-beginner-", 1)[1]


def _truthy(value: Any) -> bool:
    if isinstance(value, str):
        return value.lower() in {"1", "true", "yes"}
    return bool(value)


def _float(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def _mean(values: Iterable[float]) -> float:
    items = list(values)
    return sum(items) / len(items) if items else 0.0


def _std(values: list[float]) -> float:
    if len(values) <= 1:
        return 0.0
    mean = sum(values) / len(values)
    variance = sum((value - mean) ** 2 for value in values) / (len(values) - 1)
    return math.sqrt(variance)


def _percentile(values: list[float], percentile: int) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    rank = (len(ordered) - 1) * (percentile / 100)
    lower = math.floor(rank)
    upper = math.ceil(rank)
    if lower == upper:
        return ordered[int(rank)]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (rank - lower)


def _round(value: float, digits: int = 6) -> float:
    return round(value, digits)


def _write_grouped_bar_svg(
    path: Path,
    title: str,
    labels: list[str],
    series: list[tuple[str, list[float]]],
    value_format: str,
) -> None:
    width = max(760, 92 * max(1, len(labels)) + 180)
    height = 420
    margin_left = 72
    margin_right = 152
    margin_bottom = 98
    margin_top = 58
    plot_width = width - margin_left - margin_right
    plot_height = height - margin_top - margin_bottom
    max_value = max([1.0] + [value for _, values in series for value in values])
    group_width = plot_width / max(1, len(labels))
    bar_width = min(28, group_width / (len(series) + 1))
    colors = ["#2563eb", "#16a34a", "#dc2626", "#9333ea"]
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text x="{margin_left}" y="34" font-family="Arial" font-size="20" fill="#111827">{_escape(title)}</text>',
        f'<line x1="{margin_left}" y1="{height - margin_bottom}" x2="{width - margin_right}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
        f'<line x1="{margin_left}" y1="{margin_top}" x2="{margin_left}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
    ]
    for label_index, label in enumerate(labels):
        group_x = margin_left + label_index * group_width
        label_x = group_x + group_width / 2
        parts.append(
            f'<text x="{label_x:.2f}" y="{height - 62}" text-anchor="end" transform="rotate(-32 {label_x:.2f} {height - 62})" '
            f'font-family="Arial" font-size="11" fill="#374151">{_escape(label)}</text>'
        )
        start_x = group_x + (group_width - bar_width * len(series)) / 2
        for series_index, (name, values) in enumerate(series):
            value = values[label_index] if label_index < len(values) else 0.0
            bar_height = 0.0 if max_value == 0 else (value / max_value) * plot_height
            x = start_x + series_index * bar_width
            y = height - margin_bottom - bar_height
            parts.append(
                f'<rect x="{x:.2f}" y="{y:.2f}" width="{bar_width * 0.82:.2f}" height="{bar_height:.2f}" '
                f'fill="{colors[series_index % len(colors)]}"><title>{_escape(name)}: {value_format.format(value)}</title></rect>'
            )
            if bar_height > 14:
                parts.append(
                    f'<text x="{x + bar_width * 0.41:.2f}" y="{max(y - 5, 50):.2f}" text-anchor="middle" '
                    f'font-family="Arial" font-size="10" fill="#111827">{_escape(value_format.format(value))}</text>'
                )
    for series_index, (name, _) in enumerate(series):
        legend_x = width - margin_right + 20
        legend_y = margin_top + series_index * 24
        parts.append(f'<rect x="{legend_x}" y="{legend_y - 12}" width="12" height="12" fill="{colors[series_index % len(colors)]}"/>')
        parts.append(f'<text x="{legend_x + 18}" y="{legend_y - 2}" font-family="Arial" font-size="12" fill="#374151">{_escape(name)}</text>')
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def _escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
