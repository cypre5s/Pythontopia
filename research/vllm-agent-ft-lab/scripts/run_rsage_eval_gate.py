#!/usr/bin/env python3
"""Plan R-SAGE evaluation stages and run an isolated fixture gate demo."""

from __future__ import annotations

import argparse
import json
from dataclasses import asdict
from pathlib import Path

from pyt_agent_lab.rsage_eval_budget import (
    claim_full5000_receipt,
    create_gate_state,
    plan_next_commands,
    record_evaluation_stage,
    record_final_stage,
    record_seed_stage,
)
from pyt_agent_lab.rsage_generalization_gate import SeedGateReport, evaluate_checkpoint


UNIFORM_ACTION_COUNTS = {
    "READ": 10,
    "REVIEW": 10,
    "TRACE": 10,
    "SCAFFOLD": 10,
    "PRACTICE": 10,
    "ASK": 10,
    "CHECK": 10,
}


def main() -> None:
    parser = argparse.ArgumentParser(
        description="规划 50→500→三种子→一次 fresh_full5000_v2 的 R-SAGE 评测门禁。"
    )
    parser.add_argument(
        "--output-dir",
        default="research/vllm-agent-ft-lab/outputs/rsage/eval-gate",
        help="状态与命令计划输出目录。",
    )
    parser.add_argument(
        "--receipt-path",
        default="research/vllm-agent-ft-lab/outputs/rsage/eval-gate/fresh-full5000-receipt.json",
        help="真实 final 的唯一 durable receipt；demo 永远不会写入该路径。",
    )
    parser.add_argument("--candidate-id", default="rsage-candidate")
    parser.add_argument("--adapter-sha256", help="正式模式必填的 Adapter SHA-256。")
    parser.add_argument("--adapter-path", default="outputs/adapters/rsage-candidate")
    parser.add_argument("--base-url", default="http://127.0.0.1:8000/v1")
    parser.add_argument("--model", default="pythontopia-rsage-candidate")
    parser.add_argument(
        "--cases",
        default="research/vllm-agent-ft-lab/cases/golden_adapter_50_v1.jsonl",
        help="正式模式首次 golden50 的案例路径。",
    )
    parser.add_argument(
        "--demo",
        action="store_true",
        help="用 fixture evaluator 走完整门禁，不启动模型且不消耗真实 receipt。",
    )
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    if args.demo:
        _run_demo(output_dir=output_dir, receipt_path=Path(args.receipt_path))
        return
    if not args.adapter_sha256:
        parser.error("正式模式必须提供 --adapter-sha256")
    _plan_initial_gate(
        output_dir=output_dir,
        candidate_id=args.candidate_id,
        adapter_sha256=args.adapter_sha256,
        adapter_path=args.adapter_path,
        cases_path=args.cases,
        base_url=args.base_url,
        model=args.model,
    )


def _run_demo(*, output_dir: Path, receipt_path: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    pass_gate = evaluate_checkpoint(action_counts=UNIFORM_ACTION_COUNTS)
    state = create_gate_state(
        candidate_id="rsage-demo-candidate",
        adapter_sha256="a" * 64,
        demo=True,
    )
    state = record_evaluation_stage(
        state,
        stage="golden50",
        case_count=50,
        gate=pass_gate,
        artifact_sha256="b" * 64,
    )
    state = record_evaluation_stage(
        state,
        stage="stratified500",
        case_count=500,
        gate=pass_gate,
        artifact_sha256="c" * 64,
    )
    state = record_seed_stage(
        state,
        [
            SeedGateReport(42, 0.05, pass_gate),
            SeedGateReport(43, 0.04, pass_gate),
            SeedGateReport(44, -0.01, pass_gate),
        ],
        artifact_sha256="d" * 64,
    )
    manifest = _demo_fresh_manifest()
    commands = plan_next_commands(
        state,
        cases_path=output_dir / "fixture-fresh-full5000-v2.jsonl",
        run_root=output_dir / "fixture-runs",
        base_url="fixture://no-network",
        model="fixture-rsage-candidate",
        adapter_path=output_dir / "fixture-adapter",
        final_manifest=manifest,
        python_executable="python3",
    )
    receipt = claim_full5000_receipt(
        state,
        manifest=manifest,
        command=commands[0].argv,
        receipt_path=receipt_path,
        demo=True,
    )
    state = record_final_stage(
        state,
        case_count=5000,
        gate=pass_gate,
        artifact_sha256="e" * 64,
        manifest=manifest,
        receipt=receipt,
    )

    _write_json(output_dir / "fresh-full5000-v2-manifest.json", manifest)
    _write_json(
        output_dir / "planned-final-command.json",
        [asdict(command) for command in commands],
    )
    _write_json(output_dir / "demo-full5000-receipt.json", receipt)
    _write_json(output_dir / "gate-state.json", asdict(state))
    print("R-SAGE fixture 门禁已完成：50→500→seeds 42/43/44→fresh_full5000_v2。")
    print(f"真实 receipt 未消耗：{receipt_path}")


def _plan_initial_gate(
    *,
    output_dir: Path,
    candidate_id: str,
    adapter_sha256: str,
    adapter_path: str,
    cases_path: str,
    base_url: str,
    model: str,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    state = create_gate_state(
        candidate_id=candidate_id,
        adapter_sha256=adapter_sha256,
    )
    commands = plan_next_commands(
        state,
        cases_path=cases_path,
        run_root=output_dir / "runs",
        base_url=base_url,
        model=model,
        adapter_path=adapter_path,
    )
    _write_json(output_dir / "gate-state.json", asdict(state))
    _write_json(
        output_dir / "planned-next-command.json",
        [asdict(command) for command in commands],
    )
    print("已冻结 golden50 首阶段命令；评分与报告继续由 run_baseline.py 负责。")


def _demo_fresh_manifest() -> dict[str, object]:
    return {
        "format": "fresh_full5000_v2",
        "actual_count": 5000,
        "dataset_sha256": "f" * 64,
        "overlap_audit": {
            "historical_eval_overlap_count": 0,
            "declared_zero_historical_overlap": True,
            "training_overlap_count": 0,
        },
        "fixture_only": True,
        "message_zh": "演示 manifest，仅验证门禁控制流，不代表真实 final 数据。",
    }


def _write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001 - CLI 统一输出中文失败原因
        raise SystemExit(f"R-SAGE 评测门禁失败：{exc}") from None
