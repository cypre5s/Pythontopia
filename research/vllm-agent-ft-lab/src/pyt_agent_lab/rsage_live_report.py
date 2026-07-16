from __future__ import annotations

import json
import math
import os
import subprocess
import time
from datetime import datetime, timezone
from html import escape
from pathlib import Path
from typing import Any, Callable, Mapping


_CHART_NAMES = ("loss", "reward", "throughput", "gpu")
_COLORS = ("#2563eb", "#dc2626", "#059669", "#d97706", "#7c3aed", "#0891b2")


def append_event(
    run_dir: str | Path,
    *,
    event: str,
    message_zh: str,
    **fields: Any,
) -> dict[str, Any]:
    """Append one human-readable lifecycle event without rewriting prior rows."""
    if not event.strip():
        raise ValueError("事件名称不能为空")
    if not message_zh.strip():
        raise ValueError("中文事件说明不能为空")
    record = {
        **fields,
        "timestamp_utc": _now(),
        "event": event,
        "message_zh": message_zh,
    }
    _append_jsonl(Path(run_dir) / "events.jsonl", record)
    return record


def parse_trainer_log(path: str | Path) -> list[dict[str, Any]]:
    """Parse complete trainer JSONL rows while tolerating one in-flight final row."""
    rows, _ = _read_jsonl(Path(path), label="trainer_log.jsonl")
    return rows


def sample_gpu_metrics(
    *,
    run_command: Callable[..., Any] = subprocess.run,
) -> dict[str, Any]:
    """Collect a point-in-time GPU sample without making nvidia-smi mandatory."""
    timestamp = _now()
    command = [
        "nvidia-smi",
        "--query-gpu=index,utilization.gpu,memory.used,memory.total,temperature.gpu",
        "--format=csv,noheader,nounits",
    ]
    try:
        completed = run_command(
            command,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=True,
            timeout=5,
        )
    except FileNotFoundError:
        return {
            "timestamp_utc": timestamp,
            "available": False,
            "status": "nvidia_smi_missing",
            "message_zh": "未找到 nvidia-smi，已跳过 GPU 遥测",
            "gpus": [],
        }
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError) as exc:
        return {
            "timestamp_utc": timestamp,
            "available": False,
            "status": "nvidia_smi_error",
            "error_type": type(exc).__name__,
            "message_zh": "nvidia-smi 执行失败，已跳过本轮 GPU 遥测",
            "gpus": [],
        }

    gpus: list[dict[str, int | float]] = []
    for raw_line in str(completed.stdout).splitlines():
        if not raw_line.strip():
            continue
        fields = [item.strip() for item in raw_line.split(",")]
        if len(fields) != 5:
            continue
        try:
            gpu = {
                "index": int(float(fields[0])),
                "utilization_pct": float(fields[1]),
                "memory_used_mib": float(fields[2]),
                "memory_total_mib": float(fields[3]),
                "temperature_c": float(fields[4]),
            }
        except ValueError:
            continue
        if all(_finite_number(value) is not None for value in gpu.values()):
            gpus.append(gpu)

    if not gpus:
        return {
            "timestamp_utc": timestamp,
            "available": False,
            "status": "nvidia_smi_invalid_output",
            "message_zh": "nvidia-smi 未返回可解析的 GPU 数据，已跳过本轮 GPU 遥测",
            "gpus": [],
        }
    return {
        "timestamp_utc": timestamp,
        "available": True,
        "status": "ok",
        "message_zh": f"已采集 {len(gpus)} 张 GPU 的遥测数据",
        "gpus": gpus,
    }


def write_live_charts(
    run_dir: str | Path,
    *,
    trainer_rows: list[dict[str, Any]],
    gpu_samples: list[dict[str, Any]],
) -> dict[str, Path]:
    """Regenerate all dependency-free SVG charts using same-directory atomic writes."""
    target = Path(run_dir) / "charts"
    target.mkdir(parents=True, exist_ok=True)
    chart_series = {
        "loss": (
            "训练与验证损失",
            "损失",
            _trainer_series(
                trainer_rows,
                (
                    ("训练损失", "loss"),
                    ("验证损失", "eval_loss"),
                ),
            ),
        ),
        "reward": (
            "偏好奖励",
            "奖励 / 准确率",
            _trainer_series(
                trainer_rows,
                (
                    ("chosen 奖励", "rewards/chosen"),
                    ("rejected 奖励", "rewards/rejected"),
                    ("奖励准确率", "rewards/accuracies"),
                    ("奖励间隔", "rewards/margins"),
                ),
            ),
        ),
        "throughput": (
            "训练吞吐量",
            "每秒处理量",
            _trainer_series(
                trainer_rows,
                (
                    ("训练样本/秒", "train_samples_per_second"),
                    ("训练步数/秒", "train_steps_per_second"),
                    ("验证样本/秒", "eval_samples_per_second"),
                    ("验证步数/秒", "eval_steps_per_second"),
                ),
            ),
        ),
        "gpu": (
            "GPU 实时遥测",
            "百分比 / 温度",
            _gpu_series(gpu_samples),
        ),
    }
    outputs: dict[str, Path] = {}
    for chart_name in _CHART_NAMES:
        title, y_label, series = chart_series[chart_name]
        path = target / f"{chart_name}.svg"
        _atomic_write_text(path, _line_chart_svg(title, y_label, series))
        outputs[chart_name] = path
    return outputs


def poll_once(
    run_dir: str | Path,
    *,
    gpu_sampler: Callable[[], Mapping[str, Any]] = sample_gpu_metrics,
) -> dict[str, Any]:
    """Read current logs once and refresh telemetry, charts, snapshots, and journal."""
    target = Path(run_dir)
    target.mkdir(parents=True, exist_ok=True)
    trainer_rows, partial_tail = _read_jsonl(target / "trainer_log.jsonl", label="trainer_log.jsonl")
    gpu_sample = _safe_gpu_sample(gpu_sampler)
    _append_jsonl(target / "gpu_metrics.jsonl", gpu_sample)
    gpu_samples, _ = _read_jsonl(target / "gpu_metrics.jsonl", label="gpu_metrics.jsonl")
    chart_paths = write_live_charts(target, trainer_rows=trainer_rows, gpu_samples=gpu_samples)

    latest = trainer_rows[-1] if trainer_rows else {}
    snapshot = {
        "timestamp_utc": _now(),
        "format": "rsage_live_progress_v1",
        "trainer_rows": len(trainer_rows),
        "latest_step": latest.get("step"),
        "latest_epoch": latest.get("epoch"),
        "gpu_available": bool(gpu_sample.get("available")),
        "gpu_status": gpu_sample.get("status", "unknown"),
        "partial_trainer_line_ignored": partial_tail,
        "charts": {name: str(path.relative_to(target)) for name, path in chart_paths.items()},
        "message_zh": f"已解析 {len(trainer_rows)} 条训练日志并刷新 {len(_CHART_NAMES)} 张图表",
    }
    _append_jsonl(target / "progress_snapshots.jsonl", snapshot)
    _write_experiment_journal(
        target / "EXPERIMENT_LOG.md",
        trainer_rows=trainer_rows,
        gpu_sample=gpu_sample,
        snapshot=snapshot,
    )

    event_message = str(snapshot["message_zh"])
    if not gpu_sample.get("available"):
        event_message += f"；{gpu_sample.get('message_zh', 'GPU 遥测不可用')}"
    append_event(
        target,
        event="监控轮询",
        message_zh=event_message,
        trainer_rows=len(trainer_rows),
        latest_step=latest.get("step"),
        gpu_status=gpu_sample.get("status", "unknown"),
        partial_trainer_line_ignored=partial_tail,
    )
    return snapshot


def watch_training(
    run_dir: str | Path,
    *,
    interval_seconds: float = 10.0,
    max_polls: int | None = None,
    gpu_sampler: Callable[[], Mapping[str, Any]] = sample_gpu_metrics,
    sleep_fn: Callable[[float], Any] = time.sleep,
) -> list[dict[str, Any]]:
    """Poll until interrupted or until max_polls has been reached."""
    if interval_seconds < 0:
        raise ValueError("轮询间隔不能为负数")
    if max_polls is not None and max_polls <= 0:
        raise ValueError("max_polls 必须为正整数")
    results: list[dict[str, Any]] = []
    while max_polls is None or len(results) < max_polls:
        results.append(poll_once(run_dir, gpu_sampler=gpu_sampler))
        if max_polls is not None and len(results) >= max_polls:
            break
        sleep_fn(interval_seconds)
    return results


def _safe_gpu_sample(gpu_sampler: Callable[[], Mapping[str, Any]]) -> dict[str, Any]:
    try:
        sample = dict(gpu_sampler())
    except Exception as exc:  # noqa: BLE001 - watcher must record telemetry failures and continue
        return {
            "timestamp_utc": _now(),
            "available": False,
            "status": "gpu_sampler_error",
            "error_type": type(exc).__name__,
            "message_zh": "GPU 遥测采样器异常，已继续刷新训练图表",
            "gpus": [],
        }
    sample.setdefault("timestamp_utc", _now())
    sample.setdefault("available", False)
    sample.setdefault("status", "unknown")
    sample.setdefault("message_zh", "GPU 遥测状态未知")
    sample.setdefault("gpus", [])
    return sample


def _read_jsonl(path: Path, *, label: str) -> tuple[list[dict[str, Any]], bool]:
    if not path.exists():
        return [], False
    content = path.read_text(encoding="utf-8")
    has_unterminated_tail = bool(content) and not content.endswith(("\n", "\r"))
    rows: list[dict[str, Any]] = []
    lines = content.splitlines()
    ignored_partial_tail = False
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as exc:
            if has_unterminated_tail and line_number == len(lines):
                ignored_partial_tail = True
                continue
            raise ValueError(f"{label} 第 {line_number} 行不是有效 JSON") from exc
        if not isinstance(value, dict):
            raise ValueError(f"{label} 第 {line_number} 行必须是 JSON 对象")
        rows.append(value)
    return rows, ignored_partial_tail


def _append_jsonl(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(dict(value), ensure_ascii=False, sort_keys=True) + "\n"
    with path.open("a", encoding="utf-8") as handle:
        handle.write(payload)


def _atomic_write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    try:
        temporary.write_text(content, encoding="utf-8")
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def _trainer_series(
    rows: list[dict[str, Any]],
    metrics: tuple[tuple[str, str], ...],
) -> list[tuple[str, str, list[tuple[float, float]]]]:
    series: list[tuple[str, str, list[tuple[float, float]]]] = []
    for metric_index, (label, key) in enumerate(metrics):
        points: list[tuple[float, float]] = []
        for row_index, row in enumerate(rows, start=1):
            y_value = _finite_number(row.get(key))
            if y_value is None:
                continue
            step = _finite_number(row.get("step"))
            points.append((step if step is not None else float(row_index), y_value))
        if points:
            series.append((label, _COLORS[metric_index % len(_COLORS)], points))
    return series


def _gpu_series(samples: list[dict[str, Any]]) -> list[tuple[str, str, list[tuple[float, float]]]]:
    values: dict[tuple[int, str], list[tuple[float, float]]] = {}
    labels = {
        "utilization_pct": "利用率 (%)",
        "memory_pct": "显存使用率 (%)",
        "temperature_c": "温度 (°C)",
    }
    for sample_index, sample in enumerate(samples, start=1):
        gpus = sample.get("gpus")
        if not sample.get("available") or not isinstance(gpus, list):
            continue
        for gpu in gpus:
            if not isinstance(gpu, dict):
                continue
            gpu_index_value = _finite_number(gpu.get("index"))
            gpu_index = int(gpu_index_value) if gpu_index_value is not None else 0
            utilization = _finite_number(gpu.get("utilization_pct"))
            temperature = _finite_number(gpu.get("temperature_c"))
            memory_used = _finite_number(gpu.get("memory_used_mib"))
            memory_total = _finite_number(gpu.get("memory_total_mib"))
            memory_pct = (
                memory_used / memory_total * 100.0
                if memory_used is not None and memory_total is not None and memory_total > 0
                else None
            )
            for key, value in (
                ("utilization_pct", utilization),
                ("memory_pct", memory_pct),
                ("temperature_c", temperature),
            ):
                if value is not None:
                    values.setdefault((gpu_index, key), []).append((float(sample_index), value))
    series = []
    for series_index, ((gpu_index, key), points) in enumerate(sorted(values.items())):
        series.append(
            (
                f"GPU {gpu_index} {labels[key]}",
                _COLORS[series_index % len(_COLORS)],
                points,
            )
        )
    return series


def _line_chart_svg(
    title: str,
    y_label: str,
    series: list[tuple[str, str, list[tuple[float, float]]]],
) -> str:
    width = 900
    height = 460
    left = 72
    right = 30
    top = 68
    bottom = 70
    plot_width = width - left - right
    plot_height = height - top - bottom
    all_points = [point for _, _, points in series for point in points]
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text x="{left}" y="34" font-family="sans-serif" font-size="22" fill="#111827">{escape(title)}</text>',
        f'<line x1="{left}" y1="{height - bottom}" x2="{width - right}" y2="{height - bottom}" stroke="#6b7280"/>',
        f'<line x1="{left}" y1="{top}" x2="{left}" y2="{height - bottom}" stroke="#6b7280"/>',
    ]
    if not all_points:
        parts.append(
            f'<text x="{width / 2:.1f}" y="{height / 2:.1f}" text-anchor="middle" '
            'font-family="sans-serif" font-size="18" fill="#6b7280">暂无可绘制数据</text>'
        )
    else:
        x_values = [point[0] for point in all_points]
        y_values = [point[1] for point in all_points]
        x_min, x_max = min(x_values), max(x_values)
        y_min, y_max = min(y_values), max(y_values)
        if x_min == x_max:
            x_min -= 0.5
            x_max += 0.5
        if y_min == y_max:
            padding = max(abs(y_min) * 0.1, 0.5)
        else:
            padding = (y_max - y_min) * 0.1
        y_min -= padding
        y_max += padding

        def project(point: tuple[float, float]) -> tuple[float, float]:
            x, y = point
            return (
                left + (x - x_min) / (x_max - x_min) * plot_width,
                top + (y_max - y) / (y_max - y_min) * plot_height,
            )

        for tick in range(5):
            ratio = tick / 4
            y = top + ratio * plot_height
            value = y_max - ratio * (y_max - y_min)
            parts.extend(
                [
                    f'<line x1="{left}" y1="{y:.2f}" x2="{width - right}" y2="{y:.2f}" stroke="#e5e7eb"/>',
                    f'<text x="{left - 9}" y="{y + 4:.2f}" text-anchor="end" '
                    f'font-family="sans-serif" font-size="11" fill="#4b5563">{value:.3g}</text>',
                ]
            )
        for label, color, points in series:
            coordinates = " ".join(f"{x:.2f},{y:.2f}" for x, y in map(project, points))
            parts.append(
                f'<polyline fill="none" stroke="{color}" stroke-width="2.5" points="{coordinates}"/>'
            )
            for raw_point in points:
                x, y = project(raw_point)
                parts.append(
                    f'<circle cx="{x:.2f}" cy="{y:.2f}" r="3" fill="{color}">'
                    f'<title>{escape(label)}: x={raw_point[0]:.4g}, y={raw_point[1]:.4g}</title></circle>'
                )
        parts.extend(
            [
                f'<text x="{left}" y="{height - bottom + 22}" font-family="sans-serif" '
                f'font-size="11" fill="#4b5563">{x_min:.4g}</text>',
                f'<text x="{width - right}" y="{height - bottom + 22}" text-anchor="end" '
                f'font-family="sans-serif" font-size="11" fill="#4b5563">{x_max:.4g}</text>',
            ]
        )
    for index, (label, color, _) in enumerate(series):
        legend_x = left + (index % 3) * 250
        legend_y = height - 28 + (index // 3) * 16
        parts.extend(
            [
                f'<line x1="{legend_x}" y1="{legend_y}" x2="{legend_x + 18}" '
                f'y2="{legend_y}" stroke="{color}" stroke-width="3"/>',
                f'<text x="{legend_x + 24}" y="{legend_y + 4}" font-family="sans-serif" '
                f'font-size="12" fill="#374151">{escape(label)}</text>',
            ]
        )
    parts.extend(
        [
            f'<text x="{left + plot_width / 2:.2f}" y="{height - 45}" text-anchor="middle" '
            'font-family="sans-serif" font-size="12" fill="#374151">训练步数 / 采样序号</text>',
            f'<text x="20" y="{top + plot_height / 2:.2f}" '
            f'transform="rotate(-90 20,{top + plot_height / 2:.2f})" text-anchor="middle" '
            f'font-family="sans-serif" font-size="12" fill="#374151">{escape(y_label)}</text>',
            "</svg>",
        ]
    )
    return "\n".join(parts) + "\n"


def _write_experiment_journal(
    path: Path,
    *,
    trainer_rows: list[dict[str, Any]],
    gpu_sample: Mapping[str, Any],
    snapshot: Mapping[str, Any],
) -> None:
    latest = trainer_rows[-1] if trainer_rows else {}
    metric_labels = (
        ("训练损失", "loss"),
        ("验证损失", "eval_loss"),
        ("chosen 奖励", "rewards/chosen"),
        ("rejected 奖励", "rewards/rejected"),
        ("奖励准确率", "rewards/accuracies"),
        ("训练样本/秒", "train_samples_per_second"),
        ("训练步数/秒", "train_steps_per_second"),
        ("学习率", "learning_rate"),
    )
    lines = [
        "# RSAGE 实验实时记录",
        "",
        "> 本文件由只读训练监控器原子更新；模型、优化器与 checkpoint 不会被修改。",
        "",
        "## 当前状态",
        "",
        "| 项目 | 当前值 |",
        "| --- | --- |",
        f"| 更新时间（UTC） | {_display(snapshot.get('timestamp_utc'))} |",
        f"| 已解析训练日志 | {len(trainer_rows)} 条 |",
        f"| 最新训练步数 | {_display(latest.get('step'))} |",
        f"| 最新 epoch | {_display(latest.get('epoch'))} |",
        f"| GPU 遥测 | {_display(gpu_sample.get('message_zh'))} |",
        f"| 末尾半行处理 | {'已忽略正在写入的半行' if snapshot.get('partial_trainer_line_ignored') else '无需处理'} |",
        "",
        "## 最新训练指标",
        "",
        "| 指标 | 值 |",
        "| --- | ---: |",
    ]
    metric_count = 0
    for label, key in metric_labels:
        if key in latest:
            lines.append(f"| {label} | {_display(latest.get(key))} |")
            metric_count += 1
    if metric_count == 0:
        lines.append("| 暂无训练指标 | — |")

    lines.extend(
        [
            "",
            "## 实时图表",
            "",
            "- [训练与验证损失](charts/loss.svg)",
            "- [偏好奖励](charts/reward.svg)",
            "- [训练吞吐量](charts/throughput.svg)",
            "- [GPU 遥测](charts/gpu.svg)",
            "",
            "## 增量产物",
            "",
            "- `trainer_log.jsonl`：训练器原始增量日志",
            "- `events.jsonl`：中文生命周期事件（只追加）",
            "- `progress_snapshots.jsonl`：监控进度快照（只追加）",
            "- `gpu_metrics.jsonl`：GPU 遥测快照（只追加）",
            "",
        ]
    )
    _atomic_write_text(path, "\n".join(lines))


def _display(value: Any) -> str:
    number = _finite_number(value)
    if number is not None:
        if number.is_integer():
            return str(int(number))
        return f"{number:.6g}"
    if value is None or value == "":
        return "—"
    return str(value).replace("|", "\\|").replace("\n", " ")


def _finite_number(value: Any) -> float | None:
    if isinstance(value, bool):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")
