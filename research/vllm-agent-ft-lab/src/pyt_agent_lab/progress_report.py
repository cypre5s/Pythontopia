from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def load_progress_snapshots(path: str | Path) -> list[dict[str, Any]]:
    source = Path(path)
    if not source.exists():
        return []
    return [json.loads(line) for line in source.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_progress_report(snapshots: list[dict[str, Any]], output_dir: str | Path) -> dict[str, str]:
    target = Path(output_dir)
    charts_dir = target / "charts"
    charts_dir.mkdir(parents=True, exist_ok=True)
    table_path = target / "progress_table.md"
    summary_path = target / "progress_summary.json"

    table_path.write_text(_progress_table(snapshots), encoding="utf-8")
    summary_path.write_text(json.dumps(_latest_summary(snapshots), ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    _write_line_svg(
        charts_dir / "progress_completion.svg",
        "Completed cases over time",
        snapshots,
        y_key="completed_cases",
        y_label="cases",
    )
    _write_line_svg(
        charts_dir / "progress_quality_rates.svg",
        "Quality rates over time",
        snapshots,
        y_key="score_mean",
        y_label="score",
        extra_keys=["json_valid_rate", "grounding_safe_rate"],
    )
    _write_line_svg(
        charts_dir / "progress_latency.svg",
        "Mean latency over time",
        snapshots,
        y_key="latency_ms_mean",
        y_label="ms",
    )
    return {
        "table": str(table_path),
        "summary": str(summary_path),
        "charts": str(charts_dir),
    }


def _latest_summary(snapshots: list[dict[str, Any]]) -> dict[str, Any]:
    if not snapshots:
        return {"snapshot_count": 0}
    latest = dict(snapshots[-1])
    latest["snapshot_count"] = len(snapshots)
    return latest


def _progress_table(snapshots: list[dict[str, Any]]) -> str:
    if not snapshots:
        return "# Progress Table\n\nNo progress snapshots found.\n"
    rows = [
        "# Progress Table",
        "",
        "| snapshot | completed | total | score | json | grounding | leak | latency mean ms | eta sec |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for index, item in enumerate(snapshots, start=1):
        rows.append(
            "| {index} | {completed} | {total} | {score:.4f} | {json_rate:.4f} | {grounding:.4f} | "
            "{leak:.4f} | {latency:.0f} | {eta} |".format(
                index=index,
                completed=int(item.get("completed_cases") or 0),
                total=int(item.get("total_cases") or 0),
                score=float(item.get("score_mean") or 0.0),
                json_rate=float(item.get("json_valid_rate") or 0.0),
                grounding=float(item.get("grounding_safe_rate") or 0.0),
                leak=float(item.get("forbidden_leak_rate") or 0.0),
                latency=float(item.get("latency_ms_mean") or 0.0),
                eta="" if item.get("eta_seconds") is None else int(float(item["eta_seconds"])),
            )
        )
    rows.append("")
    return "\n".join(rows)


def _write_line_svg(
    path: Path,
    title: str,
    snapshots: list[dict[str, Any]],
    *,
    y_key: str,
    y_label: str,
    extra_keys: list[str] | None = None,
) -> None:
    keys = [y_key] + (extra_keys or [])
    width = 760
    height = 380
    margin_left = 70
    margin_right = 34
    margin_top = 54
    margin_bottom = 58
    plot_width = width - margin_left - margin_right
    plot_height = height - margin_top - margin_bottom
    values = [[_float(item.get(key)) for item in snapshots] for key in keys]
    max_value = max([1.0] + [value for series in values for value in series])
    colors = ["#2563eb", "#16a34a", "#9333ea", "#dc2626"]
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text x="{margin_left}" y="32" font-family="Arial" font-size="20" fill="#111827">{_escape(title)}</text>',
        f'<line x1="{margin_left}" y1="{height - margin_bottom}" x2="{width - margin_right}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
        f'<line x1="{margin_left}" y1="{margin_top}" x2="{margin_left}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
        f'<text x="18" y="{margin_top + 18}" font-family="Arial" font-size="12" fill="#374151">{_escape(y_label)}</text>',
    ]
    if not snapshots:
        parts.append("</svg>")
        path.write_text("\n".join(parts), encoding="utf-8")
        return
    for series_index, key in enumerate(keys):
        point_values = values[series_index]
        points = []
        for index, value in enumerate(point_values):
            x = margin_left + (plot_width * index / max(1, len(point_values) - 1))
            y = height - margin_bottom - (plot_height * value / max_value if max_value else 0)
            points.append(f"{x:.2f},{y:.2f}")
        parts.append(
            f'<polyline fill="none" stroke="{colors[series_index % len(colors)]}" stroke-width="2.5" points="{" ".join(points)}"/>'
        )
        legend_x = margin_left + series_index * 160
        legend_y = height - 18
        parts.append(f'<rect x="{legend_x}" y="{legend_y - 12}" width="12" height="12" fill="{colors[series_index % len(colors)]}"/>')
        parts.append(f'<text x="{legend_x + 18}" y="{legend_y - 2}" font-family="Arial" font-size="12" fill="#374151">{_escape(key)}</text>')
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def _float(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def _escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
