#!/usr/bin/env python3
"""Runner script for 100-case A/B/C eval. Avoids shell piping issues."""
from __future__ import annotations

import os
import sys
from pathlib import Path

# Ensure stdout/stderr are unbuffered
sys.stdout.reconfigure(line_buffering=True)
sys.stderr.reconfigure(line_buffering=True)

from pyt_agent_lab.abc_compare import compare_by_agent, compare_three, load_group_summary, write_abc_report
from pyt_agent_lab.abc_eval import AbcEvalConfig, GroupEndpoint, run_abc_groups
from pyt_agent_lab.baseline import load_cases

cases = load_cases("research/vllm-agent-ft-lab/cases/domain_cases_100.jsonl")
output_dir = Path("research/vllm-agent-ft-lab/outputs/abc-eval/run-100-001")

config = AbcEvalConfig(
    output_dir=output_dir,
    cases=cases,
    group_a=GroupEndpoint(
        label="External LLM",
        base_url="https://api.deepseek.com/v1",
        api_key=os.environ["PYT_OPENAI_API_KEY"],
        model="deepseek-v4-flash",
    ),
    group_b=GroupEndpoint(
        label="Qwen LoRA",
        base_url="http://127.0.0.1:18094/v1",
        api_key="local-dev-token",
        model="pythontopia-qwen35-e1-lora",
    ),
    concurrency=1,
    progress_every=10,
    timeout_seconds=300,
)

print(f"Starting 100-case A/B/C eval", flush=True)
print(f"  A: {config.group_a.model} @ {config.group_a.base_url}", flush=True)
print(f"  B: {config.group_b.model} @ {config.group_b.base_url}", flush=True)
print(f"  C: {config.group_b.model} + Guard + Fallback", flush=True)

run_dirs = run_abc_groups(config)

print(f"\nBuilding comparison report...", flush=True)
sa = load_group_summary(run_dirs["A"])
sb = load_group_summary(run_dirs["B"])
sc = load_group_summary(run_dirs["C"])
comparison = compare_three(sa, sb, sc)
comp_dir = output_dir / "comparison"
write_abc_report(comparison, comp_dir)
compare_by_agent(
    run_dirs["A"] / "metrics.csv",
    run_dirs["B"] / "metrics.csv",
    run_dirs["C"] / "metrics.csv",
    comp_dir / "charts",
)

verdict = comparison["ab_test"]["verdict"]
print(f"\nA/B verdict (C vs A): {verdict}", flush=True)
print(f"  Score delta:    {comparison['deltas_c_vs_a']['score_mean']:+.4f}", flush=True)
print(f"  JSON delta:     {comparison['deltas_c_vs_a']['json_valid_rate']:+.4f}", flush=True)
print(f"  Grounding delta:{comparison['deltas_c_vs_a']['grounding_safe_rate']:+.4f}", flush=True)
print(f"  Leak delta:     {comparison['deltas_c_vs_a']['forbidden_leak_rate']:+.4f}", flush=True)
print(f"  Guard effect:   {comparison['guard_effect']['score_mean']:+.4f}", flush=True)
print(f"\nOutput: {output_dir}", flush=True)
print(f"Done.", flush=True)
