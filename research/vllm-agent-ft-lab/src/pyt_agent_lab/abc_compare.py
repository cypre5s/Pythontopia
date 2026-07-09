from __future__ import annotations

import csv
import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any

# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

METRICS = (
    "score_mean",
    "json_valid_rate",
    "grounding_safe_rate",
    "forbidden_leak_rate",
)
GROUP_LABELS = {"A": "External LLM", "B": "Qwen LoRA", "C": "Qwen LoRA + Guard"}
GROUP_COLORS = {"A": "#64748b", "B": "#2563eb", "C": "#16a34a"}


def load_group_summary(run_dir_or_path: str | Path) -> dict[str, Any]:
    p = Path(run_dir_or_path)
    if p.is_dir():
        p = p / "summary.json"
    return json.loads(p.read_text(encoding="utf-8"))


def compare_three(
    summary_a: dict[str, Any],
    summary_b: dict[str, Any],
    summary_c: dict[str, Any],
) -> dict[str, Any]:
    """Build a 3-way comparison data structure."""
    a = summary_a["overall"]
    b = summary_b["overall"]
    c = summary_c["overall"]

    def _pick(values: dict[str, float]) -> dict[str, float]:
        return {m: round(float(values.get(m, 0.0)), 6) for m in METRICS}

    base = _pick(a)
    deltas_b = {m: round(_pick(b)[m] - base[m], 6) for m in METRICS}
    deltas_c = {m: round(_pick(c)[m] - base[m], 6) for m in METRICS}
    guard_effect = {m: round(_pick(c)[m] - _pick(b)[m], 6) for m in METRICS}

    ab_test = {
        "candidate_label": "Qwen LoRA + Guard + Fallback",
        "baseline_label": "External LLM",
        "deltas": deltas_c,
        "verdict": _ab_verdict(deltas_c),
    }

    return {
        "group_a": {"label": GROUP_LABELS["A"], "metrics": _pick(a)},
        "group_b": {"label": GROUP_LABELS["B"], "metrics": _pick(b)},
        "group_c": {"label": GROUP_LABELS["C"], "metrics": _pick(c)},
        "deltas_b_vs_a": deltas_b,
        "deltas_c_vs_a": deltas_c,
        "guard_effect": guard_effect,
        "ab_test": ab_test,
    }


def write_abc_report(
    comparison: dict[str, Any],
    output_dir: str | Path,
) -> dict[str, Path]:
    """Write comparison JSON, markdown, and all SVG charts."""
    target = Path(output_dir)
    charts_dir = target / "charts"
    charts_dir.mkdir(parents=True, exist_ok=True)

    json_path = target / "abc_comparison.json"
    md_path = target / "abc_comparison.md"

    json_path.write_text(
        json.dumps(comparison, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    md_path.write_text(_to_markdown(comparison), encoding="utf-8")

    _write_3way_bars(charts_dir / "metrics_3way_bars.svg", comparison)
    _write_delta_bars(charts_dir / "deltas_vs_external.svg", comparison)
    _write_guard_effect_bars(charts_dir / "guard_effect.svg", comparison)

    return {"json": json_path, "markdown": md_path, "charts_dir": charts_dir}


# ---------------------------------------------------------------------------
# Per-agent comparison from metrics CSV files
# ---------------------------------------------------------------------------


def compare_by_agent(
    metrics_a_path: str | Path,
    metrics_b_path: str | Path,
    metrics_c_path: str | Path,
    charts_dir: str | Path,
) -> dict[str, Any]:
    """Build per-agent breakdown and heatmap chart."""
    a_rows = _load_metrics(metrics_a_path)
    b_rows = _load_metrics(metrics_b_path)
    c_rows = _load_metrics(metrics_c_path)

    a_by_agent = _group_by_agent(a_rows)
    b_by_agent = _group_by_agent(b_rows)
    c_by_agent = _group_by_agent(c_rows)

    all_agents = sorted(set(a_by_agent) | set(b_by_agent) | set(c_by_agent))

    result: dict[str, Any] = {}
    for agent in all_agents:
        result[agent] = {
            "A": _summarize(a_by_agent.get(agent, [])),
            "B": _summarize(b_by_agent.get(agent, [])),
            "C": _summarize(c_by_agent.get(agent, [])),
        }

    charts = Path(charts_dir)
    charts.mkdir(parents=True, exist_ok=True)
    _write_agent_heatmap(charts / "per_agent_heatmap.svg", result, all_agents)

    return result


# ---------------------------------------------------------------------------
# SVG charts
# ---------------------------------------------------------------------------


def _write_3way_bars(path: Path, comparison: dict[str, Any]) -> None:
    width = 820
    height = 400
    margin_left = 80
    margin_right = 160
    margin_top = 54
    margin_bottom = 82

    labels = ["Score", "JSON Valid", "Grounding", "1 - Leak"]
    keys = ["score_mean", "json_valid_rate", "grounding_safe_rate", "forbidden_leak_rate"]
    groups = ["group_a", "group_b", "group_c"]
    colors = [GROUP_COLORS["A"], GROUP_COLORS["B"], GROUP_COLORS["C"]]
    group_names = [GROUP_LABELS["A"], GROUP_LABELS["B"], GROUP_LABELS["C"]]

    plot_w = width - margin_left - margin_right
    plot_h = height - margin_top - margin_bottom
    n_groups = len(labels)
    n_bars = len(groups)
    group_w = plot_w / max(1, n_groups)
    bar_w = min(36, group_w / (n_bars + 0.8))

    max_val = 1.05
    values: dict[str, list[float]] = {}
    for gi, gk in enumerate(groups):
        m = comparison[gk]["metrics"]
        vals = [float(m.get(k, 0)) for k in keys]
        # Invert forbidden_leak_rate to "1 - leak" for consistent "higher is better"
        vals[3] = 1.0 - vals[3]
        values[gk] = vals
        max_val = max(max_val, max(vals))

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text x="{margin_left}" y="32" font-family="Arial" font-size="20" fill="#111827">A / B / C 3-Way Comparison</text>',
        f'<line x1="{margin_left}" y1="{height - margin_bottom}" x2="{width - margin_right}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
        f'<line x1="{margin_left}" y1="{margin_top}" x2="{margin_left}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
    ]

    for li, label in enumerate(labels):
        gx = margin_left + li * group_w
        cx = gx + group_w / 2
        parts.append(
            f'<text x="{cx:.1f}" y="{height - 48}" text-anchor="middle" font-family="Arial" font-size="12" fill="#374151">{_esc(label)}</text>'
        )
        start_x = gx + (group_w - bar_w * n_bars) / 2
        for bi, gk in enumerate(groups):
            v = values[gk][li]
            bh = (v / max_val) * plot_h if max_val > 0 else 0
            x = start_x + bi * bar_w
            y = height - margin_bottom - bh
            parts.append(
                f'<rect x="{x:.1f}" y="{y:.1f}" width="{bar_w * 0.82:.1f}" height="{bh:.1f}" '
                f'fill="{colors[bi]}"><title>{group_names[bi]}: {v:.4f}</title></rect>'
            )
            if bh > 14:
                parts.append(
                    f'<text x="{x + bar_w * 0.41:.1f}" y="{max(y - 5, 48):.1f}" '
                    f'text-anchor="middle" font-family="Arial" font-size="10" fill="#111827">{v:.3f}</text>'
                )

    # Legend
    for bi in range(len(groups)):
        lx = width - margin_right + 20
        ly = margin_top + bi * 26
        parts.append(f'<rect x="{lx}" y="{ly - 12}" width="12" height="12" fill="{colors[bi]}"/>')
        parts.append(
            f'<text x="{lx + 18}" y="{ly - 2}" font-family="Arial" font-size="12" fill="#374151">{_esc(group_names[bi])}</text>'
        )

    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def _write_delta_bars(path: Path, comparison: dict[str, Any]) -> None:
    """B vs A and C vs A delta chart."""
    width = 720
    height = 380
    margin_left = 80
    margin_right = 150
    margin_top = 54
    margin_bottom = 82

    labels = ["Score", "JSON Valid", "Grounding", "Forbidden Leak"]
    keys = ["score_mean", "json_valid_rate", "grounding_safe_rate", "forbidden_leak_rate"]

    deltas_b = [comparison["deltas_b_vs_a"].get(k, 0.0) for k in keys]
    deltas_c = [comparison["deltas_c_vs_a"].get(k, 0.0) for k in keys]

    plot_w = width - margin_left - margin_right
    plot_h = height - margin_top - margin_bottom
    n = len(labels)
    group_w = plot_w / max(1, n)
    bar_w = min(42, group_w / 2.6)

    all_vals = deltas_b + deltas_c
    abs_max = max(abs(v) for v in all_vals) if all_vals else 0.1
    abs_max = max(abs_max, 0.05)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text x="{margin_left}" y="32" font-family="Arial" font-size="20" fill="#111827">Deltas vs External LLM (Baseline)</text>',
        f'<line x1="{margin_left}" y1="{height - margin_bottom}" x2="{width - margin_right}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
        f'<line x1="{margin_left}" y1="{margin_top}" x2="{margin_left}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
    ]

    zero_y = height - margin_bottom - plot_h / 2

    # Zero line
    parts.append(
        f'<line x1="{margin_left}" y1="{zero_y:.1f}" x2="{width - margin_right}" y2="{zero_y:.1f}" stroke="#9ca3af" stroke-dasharray="4,4"/>'
    )

    for li, label in enumerate(labels):
        gx = margin_left + li * group_w
        cx = gx + group_w / 2
        parts.append(
            f'<text x="{cx:.1f}" y="{height - 48}" text-anchor="middle" font-family="Arial" font-size="12" fill="#374151">{_esc(label)}</text>'
        )
        for bi, (deltas, color, name) in enumerate(
            [
                (deltas_b, GROUP_COLORS["B"], "B vs A (LoRA)"),
                (deltas_c, GROUP_COLORS["C"], "C vs A (Guarded)"),
            ]
        ):
            v = deltas[li]
            bh = (abs(v) / abs_max) * (plot_h / 2) if abs_max > 0 else 0
            x = gx + group_w * 0.15 + bi * bar_w
            if v >= 0:
                y = zero_y - bh
            else:
                y = zero_y
            parts.append(
                f'<rect x="{x:.1f}" y="{y:.1f}" width="{bar_w * 0.78:.1f}" height="{bh:.1f}" '
                f'fill="{color}"><title>{name}: {v:+.4f}</title></rect>'
            )
            label_y = y - 6 if v >= 0 else y + bh + 14
            if abs(v) > 0.001:
                parts.append(
                    f'<text x="{x + bar_w * 0.39:.1f}" y="{label_y:.1f}" '
                    f'text-anchor="middle" font-family="Arial" font-size="10" fill="#111827">{v:+.3f}</text>'
                )

    # Legend
    for bi, (name, color) in enumerate(
        [("B vs A (LoRA)", GROUP_COLORS["B"]), ("C vs A (Guarded)", GROUP_COLORS["C"])]
    ):
        lx = width - margin_right + 14
        ly = margin_top + bi * 26
        parts.append(f'<rect x="{lx}" y="{ly - 12}" width="12" height="12" fill="{color}"/>')
        parts.append(
            f'<text x="{lx + 18}" y="{ly - 2}" font-family="Arial" font-size="12" fill="#374151">{_esc(name)}</text>'
        )

    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def _write_guard_effect_bars(path: Path, comparison: dict[str, Any]) -> None:
    """B → C improvement from guard + fallback."""
    width = 660
    height = 360
    margin_left = 80
    margin_right = 120
    margin_top = 54
    margin_bottom = 82

    labels = ["Score", "JSON Valid", "Grounding", "Forbidden Leak"]
    keys = ["score_mean", "json_valid_rate", "grounding_safe_rate", "forbidden_leak_rate"]
    effect = [comparison["guard_effect"].get(k, 0.0) for k in keys]

    plot_w = width - margin_left - margin_right
    plot_h = height - margin_top - margin_bottom
    n = len(labels)
    group_w = plot_w / max(1, n)
    bar_w = min(48, group_w * 0.55)

    abs_max = max(abs(v) for v in effect) if effect else 0.1
    abs_max = max(abs_max, 0.02)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text x="{margin_left}" y="32" font-family="Arial" font-size="18" fill="#111827">Guard + Fallback Effect (B → C)</text>',
        f'<line x1="{margin_left}" y1="{height - margin_bottom}" x2="{width - margin_right}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
        f'<line x1="{margin_left}" y1="{margin_top}" x2="{margin_left}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
    ]

    zero_y = height - margin_bottom - plot_h / 2
    parts.append(
        f'<line x1="{margin_left}" y1="{zero_y:.1f}" x2="{width - margin_right}" y2="{zero_y:.1f}" stroke="#9ca3af" stroke-dasharray="4,4"/>'
    )

    for li, label in enumerate(labels):
        gx = margin_left + li * group_w
        cx = gx + group_w / 2
        parts.append(
            f'<text x="{cx:.1f}" y="{height - 48}" text-anchor="middle" font-family="Arial" font-size="12" fill="#374151">{_esc(label)}</text>'
        )
        v = effect[li]
        bh = (abs(v) / abs_max) * (plot_h / 2) if abs_max > 0 else 0
        x = gx + (group_w - bar_w) / 2
        color = "#16a34a" if v >= 0 else "#dc2626"
        if v >= 0:
            y = zero_y - bh
        else:
            y = zero_y
        parts.append(
            f'<rect x="{x:.1f}" y="{y:.1f}" width="{bar_w:.1f}" height="{bh:.1f}" '
            f'fill="{color}"><title>Guard effect: {v:+.4f}</title></rect>'
        )
        label_y = y - 6 if v >= 0 else y + bh + 14
        if abs(v) > 0.0001:
            parts.append(
                f'<text x="{x + bar_w / 2:.1f}" y="{label_y:.1f}" '
                f'text-anchor="middle" font-family="Arial" font-size="11" fill="#111827">{v:+.4f}</text>'
            )

    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def _write_agent_heatmap(
    path: Path,
    by_agent: dict[str, Any],
    agents: list[str],
) -> None:
    """Per-agent score comparison as a compact grouped bar chart."""
    if not agents:
        return

    width = max(680, 80 * len(agents) + 200)
    height = 380
    margin_left = 100
    margin_right = 140
    margin_top = 48
    margin_bottom = 40

    plot_w = width - margin_left - margin_right
    plot_h = height - margin_top - margin_bottom
    n = len(agents)
    group_w = plot_w / max(1, n)
    bar_w = min(18, group_w / 3.6)
    colors = [GROUP_COLORS["A"], GROUP_COLORS["B"], GROUP_COLORS["C"]]

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text x="{margin_left}" y="28" font-family="Arial" font-size="18" fill="#111827">Score by Agent</text>',
        f'<line x1="{margin_left}" y1="{height - margin_bottom}" x2="{width - margin_right}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
        f'<line x1="{margin_left}" y1="{margin_top}" x2="{margin_left}" y2="{height - margin_bottom}" stroke="#6b7280"/>',
    ]

    all_scores = []
    for agent in agents:
        for gk in ["A", "B", "C"]:
            s = by_agent.get(agent, {}).get(gk, {})
            all_scores.append(float(s.get("score_mean", 0)))
    max_val = max(all_scores) if all_scores else 1.0

    for ai, agent in enumerate(agents):
        gx = margin_left + ai * group_w
        cx = gx + group_w / 2
        # Rotate agent names
        parts.append(
            f'<text x="{cx:.1f}" y="{height - 12}" text-anchor="end" '
            f'transform="rotate(-35 {cx:.1f} {height - 12})" '
            f'font-family="Arial" font-size="10" fill="#374151">{_esc(agent)}</text>'
        )
        start_x = gx + (group_w - bar_w * 3) / 2
        for gi, gk in enumerate(["A", "B", "C"]):
            s = by_agent.get(agent, {}).get(gk, {})
            v = float(s.get("score_mean", 0))
            bh = (v / max_val) * plot_h if max_val > 0 else 0
            x = start_x + gi * bar_w
            y = height - margin_bottom - bh
            parts.append(
                f'<rect x="{x:.1f}" y="{y:.1f}" width="{bar_w * 0.82:.1f}" height="{bh:.1f}" '
                f'fill="{colors[gi]}"><title>{agent} {gk}: {v:.4f}</title></rect>'
            )

    # Legend
    for gi, gk in enumerate(["A", "B", "C"]):
        lx = width - margin_right + 14
        ly = margin_top + gi * 22
        parts.append(f'<rect x="{lx}" y="{ly - 12}" width="12" height="12" fill="{colors[gi]}"/>')
        parts.append(
            f'<text x="{lx + 18}" y="{ly - 2}" font-family="Arial" font-size="11" fill="#374151">{_esc(GROUP_LABELS[gk])}</text>'
        )

    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


# ---------------------------------------------------------------------------
# Markdown report
# ---------------------------------------------------------------------------


def _to_markdown(comparison: dict[str, Any]) -> str:
    lines = [
        "# A / B / C Workshop Task Evaluation Report",
        "",
        "## Groups",
        "",
        "| Group | Description |",
        "|---|---|",
        f"| A | {GROUP_LABELS['A']} |",
        f"| B | {GROUP_LABELS['B']} |",
        f"| C | {GROUP_LABELS['C']} |",
        "",
        "## Overall Metrics",
        "",
        "| Metric | A (External) | B (LoRA) | C (LoRA+Guard) |",
        "|---|---:|---:|---:|",
    ]
    for metric in METRICS:
        a_val = comparison["group_a"]["metrics"].get(metric, 0)
        b_val = comparison["group_b"]["metrics"].get(metric, 0)
        c_val = comparison["group_c"]["metrics"].get(metric, 0)
        lines.append(f"| {metric} | {a_val:.4f} | {b_val:.4f} | {c_val:.4f} |")

    lines.extend(
        [
            "",
            "## Deltas vs External LLM (A)",
            "",
            "| Metric | B vs A | C vs A |",
            "|---|---:|---:|",
        ]
    )
    for metric in METRICS:
        b_d = comparison["deltas_b_vs_a"].get(metric, 0)
        c_d = comparison["deltas_c_vs_a"].get(metric, 0)
        lines.append(f"| {metric} | {b_d:+.4f} | {c_d:+.4f} |")

    lines.extend(
        [
            "",
            "## Guard Effect (B → C)",
            "",
            "| Metric | Delta |",
            "|---|---:|",
        ]
    )
    for metric in METRICS:
        d = comparison["guard_effect"].get(metric, 0)
        lines.append(f"| {metric} | {d:+.4f} |")

    ab = comparison["ab_test"]
    lines.extend(
        [
            "",
            "## A/B Test: C (Qwen LoRA + Guard) vs A (External LLM)",
            "",
            f"- **Verdict**: `{ab['verdict']}`",
            "",
            "| Metric | Delta |",
            "|---|---:|",
        ]
    )
    for metric in METRICS:
        d = ab["deltas"].get(metric, 0)
        lines.append(f"| {metric} | {d:+.4f} |")

    lines.extend(
        [
            "",
            "## Charts",
            "",
            "- `charts/metrics_3way_bars.svg` — A vs B vs C grouped comparison",
            "- `charts/deltas_vs_external.svg` — B and C deltas vs external LLM baseline",
            "- `charts/guard_effect.svg` — guard + fallback improvement (B → C)",
            "- `charts/per_agent_heatmap.svg` — per-agent score breakdown",
            "",
        ]
    )
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _ab_verdict(deltas: dict[str, float]) -> str:
    """Classify the A/B result of C vs A."""
    score_d = deltas.get("score_mean", 0)
    json_d = deltas.get("json_valid_rate", 0)
    grounding_d = deltas.get("grounding_safe_rate", 0)
    leak_d = deltas.get("forbidden_leak_rate", 0)

    if score_d >= 0 and json_d >= 0 and grounding_d >= 0 and leak_d <= 0:
        if score_d > 0.02:
            return "candidate_wins"
        return "candidate_parity"
    if score_d < -0.05:
        return "baseline_wins"
    if json_d < -0.05 or grounding_d < -0.05:
        return "candidate_behind_reliability"
    if leak_d > 0.02:
        return "candidate_safety_risk"
    return "mixed"


def _load_metrics(path: str | Path) -> list[dict[str, Any]]:
    p = Path(path)
    if not p.exists():
        return []
    with p.open(encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def _group_by_agent(rows: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[str(row.get("agent", "unknown"))].append(row)
    return dict(grouped)


def _summarize(rows: list[dict[str, Any]]) -> dict[str, float]:
    if not rows:
        return {
            "score_mean": 0.0,
            "json_valid_rate": 0.0,
            "grounding_safe_rate": 0.0,
            "forbidden_leak_rate": 0.0,
            "case_count": 0,
        }
    n = len(rows)
    scores = [_f(row.get("score")) for row in rows]
    return {
        "score_mean": round(sum(scores) / n, 6),
        "json_valid_rate": round(sum(1 for r in rows if _truthy(r.get("json_valid"))) / n, 6),
        "grounding_safe_rate": round(sum(1 for r in rows if _truthy(r.get("grounding_safe"))) / n, 6),
        "forbidden_leak_rate": round(sum(1 for r in rows if _truthy(r.get("forbidden_leak"))) / n, 6),
        "case_count": n,
    }


def _f(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def _truthy(value: Any) -> bool:
    if isinstance(value, str):
        return value.lower() in {"1", "true", "yes"}
    return bool(value)


def _esc(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
