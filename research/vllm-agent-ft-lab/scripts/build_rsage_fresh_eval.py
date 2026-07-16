#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from pyt_agent_lab.rsage_fresh_eval import (
    build_fresh_eval_pack,
    select_fresh_stratified_subset,
    write_fresh_eval_pack,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="构造并冻结与历史训练零重叠的 R-SAGE fresh-full5000-v2 评测集。")
    parser.add_argument("--course-dir", default="data/course-pack-43")
    parser.add_argument("--count", type=int, default=5000)
    parser.add_argument("--subset-count", type=int, default=500)
    parser.add_argument("--seed", type=int, default=20260716)
    parser.add_argument(
        "--output",
        default="research/vllm-agent-ft-lab/cases/fresh_full5000_v2.jsonl",
    )
    parser.add_argument(
        "--manifest",
        default="research/vllm-agent-ft-lab/outputs/datasets/fresh_full5000_v2_manifest.json",
    )
    parser.add_argument(
        "--subset-output",
        default="research/vllm-agent-ft-lab/cases/fresh_full5000_v2_stratified500.jsonl",
    )
    parser.add_argument(
        "--subset-manifest",
        default="research/vllm-agent-ft-lab/outputs/datasets/fresh_full5000_v2_stratified500_manifest.json",
    )
    parser.add_argument("--historical", action="append", default=[], help="需要审计隔离的历史 JSON/JSONL，可重复。")
    parser.add_argument("--demo", action="store_true", help="生成 40 条 full 与 10 条分层演示集。")
    args = parser.parse_args()

    count = args.count
    subset_count = args.subset_count
    output = Path(args.output)
    manifest_path = Path(args.manifest)
    subset_output = Path(args.subset_output)
    subset_manifest_path = Path(args.subset_manifest)
    if args.demo:
        count = 40
        subset_count = 10
        if args.output == parser.get_default("output"):
            output = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/fresh-eval/fresh40.jsonl")
        if args.manifest == parser.get_default("manifest"):
            manifest_path = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/fresh-eval/fresh40-manifest.json")
        if args.subset_output == parser.get_default("subset_output"):
            subset_output = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/fresh-eval/stratified10.jsonl")
        if args.subset_manifest == parser.get_default("subset_manifest"):
            subset_manifest_path = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/fresh-eval/stratified10-manifest.json")

    historical_paths = [Path(value) for value in args.historical] or _default_historical_paths()
    cases, manifest = build_fresh_eval_pack(args.course_dir, target_count=count, seed=args.seed)
    written = write_fresh_eval_pack(
        cases,
        manifest,
        cases_path=output,
        manifest_path=manifest_path,
        historical_paths=historical_paths,
    )
    subset, subset_manifest = select_fresh_stratified_subset(
        cases,
        target_count=subset_count,
        seed=args.seed,
    )
    subset_written = write_fresh_eval_pack(
        subset,
        subset_manifest,
        cases_path=subset_output,
        manifest_path=subset_manifest_path,
        historical_paths=historical_paths,
    )
    print(written["message_zh"], flush=True)
    print(written["freshness_audit"]["message_zh"], flush=True)
    print(f"完整评测文件：{output}", flush=True)
    print(subset_written["message_zh"], flush=True)
    print(f"缩减 90% 的中间评测文件：{subset_output}", flush=True)


def _default_historical_paths() -> list[Path]:
    roots = [
        Path("research/vllm-agent-ft-lab/cases"),
        Path("research/vllm-agent-ft-lab/outputs/sft"),
        Path("research/vllm-agent-ft-lab/outputs/preferences"),
    ]
    candidates: set[Path] = set()
    for root in roots:
        if not root.exists():
            continue
        if root.name == "cases":
            candidates.update(root.glob("*.jsonl"))
        else:
            candidates.update(root.glob("**/train.jsonl"))
    return sorted(
        path
        for path in candidates
        if path.name not in {"fresh_full5000_v2.jsonl", "fresh_full5000_v2_stratified500.jsonl"}
    )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001 - 命令行统一输出中文失败原因
        raise SystemExit(f"R-SAGE fresh 评测集构建失败：{exc}") from None
