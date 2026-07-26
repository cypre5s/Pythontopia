#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from pyt_agent_lab.rsage_training_plan import (
    build_rsage_training_plans,
    write_rsage_training_plans,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="冻结 R-SAGE 35%/70%/100% 学习曲线训练计划。")
    parser.add_argument(
        "--curated-dir",
        default="research/vllm-agent-ft-lab/outputs/rsage/data/curated",
        help="整理后数据目录。",
    )
    parser.add_argument(
        "--model-path",
        default="/home/cypress/.cache/modelscope/Qwen/Qwen3.5-4B",
        help="本地完整 Qwen3.5-4B 模型目录。",
    )
    parser.add_argument(
        "--adapter-root",
        default="research/vllm-agent-ft-lab/outputs/rsage/adapters",
        help="各训练阶段隔离输出根目录。",
    )
    parser.add_argument(
        "--initial-adapter-path",
        default="research/vllm-agent-ft-lab/outputs/adapters/e1-protocol-patch-v1-1000-v1-dpo-v2-len512",
        help="冻结 champion Adapter；SFT specialist 从其副本继续训练。",
    )
    parser.add_argument(
        "--output-dir",
        default="research/vllm-agent-ft-lab/outputs/rsage/plans",
        help="冻结计划输出根目录。",
    )
    parser.add_argument("--seed", type=int, default=42, help="训练随机种子。")
    parser.add_argument(
        "--fractions",
        default="0.35,0.70,1.0",
        help="逗号分隔的学习曲线数据比例。",
    )
    parser.add_argument("--max-seq-length", type=int, default=384)
    parser.add_argument("--beta", type=float, default=0.1)
    parser.add_argument("--lambda-sft", type=float, default=0.2)
    parser.add_argument("--demo", action="store_true", help="对小型整理数据冻结一份可运行演示计划。")
    args = parser.parse_args()

    curated_dir = Path(args.curated_dir)
    output_dir = Path(args.output_dir)
    fractions = _parse_fractions(args.fractions)
    if args.demo:
        if args.curated_dir == parser.get_default("curated_dir"):
            curated_dir = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/curation")
        if args.output_dir == parser.get_default("output_dir"):
            output_dir = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/plans")
        if args.adapter_root == parser.get_default("adapter_root"):
            args.adapter_root = "research/vllm-agent-ft-lab/outputs/rsage/demos/adapters"
        fractions = [1.0]

    for fraction in fractions:
        label = {0.35: "f035", 0.70: "f070", 1.0: "f100"}[fraction]
        plans = build_rsage_training_plans(
            curated_dir=curated_dir,
            model_path=args.model_path,
            adapter_root=args.adapter_root,
            seed=args.seed,
            initial_adapter_path=args.initial_adapter_path,
            max_seq_length=args.max_seq_length,
            beta=args.beta,
            lambda_sft=args.lambda_sft,
            data_fraction=fraction,
        )
        paths = write_rsage_training_plans(plans, output_dir / f"{label}-seed{args.seed}")
        print(
            f"已冻结 {label} 学习曲线计划：SFT {plans['sft']['subset_counts']['selected']} 条，"
            f"GDPO+RPO {plans['gdpo_rpo']['subset_counts']['selected']} 条",
            flush=True,
        )
        print(f"SFT 计划：{paths['sft']}", flush=True)
        print(f"GDPO+RPO 计划：{paths['gdpo_rpo']}", flush=True)


def _parse_fractions(value: str) -> list[float]:
    try:
        fractions = [float(item.strip()) for item in value.split(",") if item.strip()]
    except ValueError as exc:
        raise ValueError("学习曲线比例必须是数字") from exc
    if not fractions or any(item not in {0.35, 0.70, 1.0} for item in fractions):
        raise ValueError("学习曲线比例只能包含 0.35、0.70、1.0")
    if len(fractions) != len(set(fractions)):
        raise ValueError("学习曲线比例不能重复")
    return fractions


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001 - 命令行统一输出中文失败原因
        raise SystemExit(f"R-SAGE 训练计划构建失败：{exc}") from None
