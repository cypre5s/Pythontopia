#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pyt_agent_lab.lora_training import load_training_plan
from pyt_agent_lab.rsage_training_launcher import launch_training_command
from pyt_agent_lab.rsage_training_plan import (
    build_training_command,
    validate_training_plan_hashes,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="启动并留存 R-SAGE specialist SFT 训练进程。")
    parser.add_argument(
        "--plan",
        default="research/vllm-agent-ft-lab/outputs/rsage/plans/f100-seed42/sft-plan.json",
        help="SFT 训练计划 JSON。",
    )
    parser.add_argument("--max-steps", type=int, default=None, help="仅执行指定正整数步。")
    parser.add_argument("--run-dir", help="启动日志目录；默认与 Adapter 输出目录相同。")
    parser.add_argument("--demo", action="store_true", help="执行真实单步 GPU 训练并验证 Adapter 可读取。")
    args = parser.parse_args()

    plan_path = Path(args.plan)
    if args.demo and args.plan == parser.get_default("plan"):
        plan_path = Path("research/vllm-agent-ft-lab/outputs/rsage/demos/plans/f100-seed42/sft-plan.json")
    plan = load_training_plan(plan_path)
    if plan.get("method") != "qlora_4bit" or plan.get("stage") != "rsage_specialist_sft":
        raise ValueError("计划不是 R-SAGE specialist SFT 训练计划")
    validate_training_plan_hashes(plan)
    max_steps = 1 if args.demo else args.max_steps
    command = build_training_command("sft", plan_path, max_steps=max_steps)
    run_dir = Path(args.run_dir) if args.run_dir else Path(plan["output_dir"])
    receipt = launch_training_command(
        command=command,
        run_dir=run_dir,
        stage="sft",
        environment={
            "PYTHONUNBUFFERED": "1",
            "HF_HUB_DISABLE_PROGRESS_BARS": "1",
            "TRANSFORMERS_VERBOSITY": "error",
        },
    )
    if receipt["status"] != "completed":
        raise RuntimeError(f"SFT 子进程退出码为 {receipt['return_code']}，请查看 {run_dir / 'console.log'}")
    verification = _verify_adapter(Path(plan["output_dir"]))
    (run_dir / "adapter_reload_receipt.json").write_text(
        json.dumps(verification, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(verification["message_zh"], flush=True)


def _verify_adapter(path: Path) -> dict[str, object]:
    from peft import PeftConfig

    config = PeftConfig.from_pretrained(path)
    weight_files = sorted(item.name for item in path.glob("adapter_model.*"))
    if not weight_files:
        raise ValueError("训练结束后未发现 Adapter 权重文件")
    return {
        "format": "rsage_adapter_reload_receipt_v1",
        "adapter_path": str(path),
        "peft_type": str(config.peft_type),
        "weight_files": weight_files,
        "message_zh": f"SFT Adapter 配置与权重已重新读取：{path}",
    }


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001 - 命令行统一输出中文失败原因
        raise SystemExit(f"R-SAGE SFT 启动失败：{exc}") from None
