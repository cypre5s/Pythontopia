from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def extract_training_curve(state: dict[str, Any]) -> dict[str, list[dict[str, float]]]:
    train_loss: list[dict[str, float]] = []
    eval_loss: list[dict[str, float]] = []
    for item in state.get("log_history", []):
        step = int(item.get("step", 0))
        if "loss" in item:
            train_loss.append(
                {
                    "step": step,
                    "loss": float(item["loss"]),
                    "mean_token_accuracy": float(item.get("mean_token_accuracy", 0.0)),
                }
            )
        if "eval_loss" in item:
            eval_loss.append(
                {
                    "step": step,
                    "eval_loss": float(item["eval_loss"]),
                    "eval_mean_token_accuracy": float(item.get("eval_mean_token_accuracy", 0.0)),
                }
            )
    return {"train_loss": train_loss, "eval_loss": eval_loss}


def write_training_curve_report(state_path: str | Path, output_dir: str | Path) -> dict[str, list[dict[str, float]]]:
    state = json.loads(Path(state_path).read_text(encoding="utf-8"))
    curve = extract_training_curve(state)
    target = Path(output_dir)
    charts_dir = target / "charts"
    charts_dir.mkdir(parents=True, exist_ok=True)
    (target / "training_curve.json").write_text(json.dumps(curve, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    _write_loss_svg(charts_dir / "training_loss.svg", curve)
    return curve


def extract_training_curve_from_jsonl_rows(
    rows: list[dict[str, Any]]
) -> dict[str, list[dict[str, float]]]:
    train_loss: list[dict[str, float]] = []
    for item in rows:
        if "loss" not in item:
            continue
        train_loss.append(
            {
                "step": int(item.get("step", 0)),
                "loss": float(item["loss"]),
                "mean_token_accuracy": float(item.get("mean_token_accuracy", 0.0)),
            }
        )
    return {"train_loss": train_loss, "eval_loss": []}


def write_training_jsonl_curve_report(
    log_path: str | Path, output_dir: str | Path
) -> dict[str, list[dict[str, float]]]:
    rows = [
        json.loads(line)
        for line in Path(log_path).read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    curve = extract_training_curve_from_jsonl_rows(rows)
    target = Path(output_dir)
    charts_dir = target / "charts"
    charts_dir.mkdir(parents=True, exist_ok=True)
    (target / "training_curve.json").write_text(
        json.dumps(curve, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    _write_loss_svg(charts_dir / "training_loss.svg", curve)
    return curve


def _write_loss_svg(path: Path, curve: dict[str, list[dict[str, float]]]) -> None:
    width = 820
    height = 420
    margin = 64
    train = curve["train_loss"]
    evals = curve["eval_loss"]
    all_steps = [point["step"] for point in train] + [point["step"] for point in evals]
    all_losses = [point["loss"] for point in train] + [point["eval_loss"] for point in evals]
    max_step = max(all_steps) if all_steps else 1
    max_loss = max([1.0] + all_losses)
    plot_w = width - margin * 2
    plot_h = height - margin * 2

    def point(step: float, loss: float) -> tuple[float, float]:
        x = margin + (step / max_step) * plot_w
        y = height - margin - (loss / max_loss) * plot_h
        return x, y

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        '<text x="64" y="34" font-family="Arial" font-size="20" fill="#111827">QLoRA Training Loss</text>',
        f'<line x1="{margin}" y1="{height - margin}" x2="{width - margin}" y2="{height - margin}" stroke="#6b7280"/>',
        f'<line x1="{margin}" y1="{margin}" x2="{margin}" y2="{height - margin}" stroke="#6b7280"/>',
    ]
    if train:
        train_points = " ".join(f"{x:.2f},{y:.2f}" for x, y in (point(item["step"], item["loss"]) for item in train))
        parts.append(f'<polyline fill="none" stroke="#2563eb" stroke-width="2" points="{train_points}"/>')
    for item in evals:
        x, y = point(item["step"], item["eval_loss"])
        parts.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="5" fill="#dc2626"><title>eval step {item["step"]}: {item["eval_loss"]:.3f}</title></circle>')
        parts.append(
            f'<text x="{x:.2f}" y="{max(y - 10, 48):.2f}" text-anchor="middle" '
            f'font-family="Arial" font-size="11" fill="#111827">{item["eval_loss"]:.3f}</text>'
        )
    parts.extend(
        [
            '<rect x="620" y="58" width="12" height="12" fill="#2563eb"/>',
            '<text x="638" y="69" font-family="Arial" font-size="12" fill="#374151">train loss</text>',
            '<circle cx="626" cy="88" r="5" fill="#dc2626"/>',
            '<text x="638" y="92" font-family="Arial" font-size="12" fill="#374151">heldout eval loss</text>',
            f'<text x="{width / 2:.2f}" y="{height - 18}" text-anchor="middle" font-family="Arial" font-size="12" fill="#374151">step</text>',
            '<text x="22" y="210" transform="rotate(-90 22,210)" text-anchor="middle" font-family="Arial" font-size="12" fill="#374151">loss</text>',
            "</svg>",
        ]
    )
    path.write_text("\n".join(parts), encoding="utf-8")
