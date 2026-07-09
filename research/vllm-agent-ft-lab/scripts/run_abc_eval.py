#!/usr/bin/env python3
"""Run the same workshop task cases through three pipelines:

  Group A — External LLM
  Group B — Qwen LoRA (local vLLM)
  Group C — Qwen LoRA + Guard + Fallback

Produces three standard run directories plus a 3-way comparison report.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from pathlib import Path

from pyt_agent_lab.abc_compare import (
    compare_by_agent,
    compare_three,
    load_group_summary,
    write_abc_report,
)
from pyt_agent_lab.abc_eval import AbcEvalConfig, GroupEndpoint, run_abc_groups
from pyt_agent_lab.baseline import load_cases


def main() -> None:
    parser = argparse.ArgumentParser(
        description="A/B/C workshop task evaluation: External LLM vs Qwen LoRA vs Qwen LoRA + Guard"
    )
    # ---- Cases -----------------------------------------------------------
    parser.add_argument(
        "--cases",
        default="research/vllm-agent-ft-lab/cases/domain_cases_1000_v1.jsonl",
        help="JSONL case file to evaluate across all three groups.",
    )

    # ---- Group A (External LLM) ------------------------------------------
    parser.add_argument(
        "--a-base-url",
        required=True,
        help="OpenAI-compatible base URL for external LLM (Group A).",
    )
    parser.add_argument(
        "--a-api-key",
        default="local-dev-token",
        help="API key for external LLM endpoint.",
    )
    parser.add_argument(
        "--a-model",
        default="deepseek-chat",
        help="Model name for external LLM.",
    )

    # ---- Group B (Qwen LoRA) ---------------------------------------------
    parser.add_argument(
        "--b-base-url",
        required=True,
        help="OpenAI-compatible base URL for Qwen LoRA vLLM (Group B).",
    )
    parser.add_argument(
        "--b-api-key",
        default="local-dev-token",
        help="API key for Qwen LoRA endpoint.",
    )
    parser.add_argument(
        "--b-model",
        default="pythontopia-qwen35-e1-lora",
        help="Model name for Qwen LoRA.",
    )

    # ---- Execution -------------------------------------------------------
    parser.add_argument("--concurrency", type=int, default=1)
    parser.add_argument("--progress-every", type=int, default=10)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    parser.add_argument("--seed", type=int, default=20260708)
    parser.add_argument("--prompt-variant", default=None)

    # ---- Output ----------------------------------------------------------
    parser.add_argument(
        "--output-dir",
        help="Parent directory for all three run dirs and comparison report.",
    )

    args = parser.parse_args()

    output_dir = Path(
        args.output_dir
        or f"research/vllm-agent-ft-lab/outputs/abc-eval/{_stamp()}"
    )
    cases = load_cases(args.cases)
    print(f"Loaded {len(cases)} cases from {args.cases}")

    config = AbcEvalConfig(
        output_dir=output_dir,
        cases=cases,
        group_a=GroupEndpoint(
            label="External LLM",
            base_url=args.a_base_url,
            api_key=args.a_api_key,
            model=args.a_model,
        ),
        group_b=GroupEndpoint(
            label="Qwen LoRA",
            base_url=args.b_base_url,
            api_key=args.b_api_key,
            model=args.b_model,
        ),
        seed=args.seed,
        concurrency=args.concurrency,
        progress_every=args.progress_every,
        timeout_seconds=args.timeout_seconds,
        prompt_variant=args.prompt_variant,
    )

    print(f"Output directory: {output_dir}")
    print(f"Group A: {config.group_a.model} @ {config.group_a.base_url}")
    print(f"Group B: {config.group_b.model} @ {config.group_b.base_url}")
    print(f"Group C: {config.group_b.model} + Guard + Fallback")
    print(f"Concurrency: {config.concurrency}")
    print()

    # ---- Run -------------------------------------------------------------
    run_dirs = run_abc_groups(config)

    # ---- Compare ---------------------------------------------------------
    print("\nBuilding comparison report ...")
    summary_a = load_group_summary(run_dirs["A"])
    summary_b = load_group_summary(run_dirs["B"])
    summary_c = load_group_summary(run_dirs["C"])

    comparison = compare_three(summary_a, summary_b, summary_c)
    comp_dir = output_dir / "comparison"
    write_abc_report(comparison, comp_dir)

    # Per-agent breakdown
    compare_by_agent(
        run_dirs["A"] / "metrics.csv",
        run_dirs["B"] / "metrics.csv",
        run_dirs["C"] / "metrics.csv",
        comp_dir / "charts",
    )

    # ---- Summary ---------------------------------------------------------
    verdict = comparison["ab_test"]["verdict"]
    print(f"\nA/B verdict (C vs A): {verdict}")
    print(f"  Score delta:    {comparison['deltas_c_vs_a']['score_mean']:+.4f}")
    print(f"  JSON delta:     {comparison['deltas_c_vs_a']['json_valid_rate']:+.4f}")
    print(f"  Grounding delta:{comparison['deltas_c_vs_a']['grounding_safe_rate']:+.4f}")
    print(f"  Leak delta:     {comparison['deltas_c_vs_a']['forbidden_leak_rate']:+.4f}")
    print(f"  Guard effect:   {comparison['guard_effect']['score_mean']:+.4f}")
    print(f"\nOutput: {output_dir}")
    print(f"  A: {run_dirs['A']}")
    print(f"  B: {run_dirs['B']}")
    print(f"  C: {run_dirs['C']}")
    print(f"  Comparison: {comp_dir}")


def _stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


if __name__ == "__main__":
    main()
