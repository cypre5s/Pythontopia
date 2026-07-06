#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
from pathlib import Path

from pyt_agent_lab.baseline import OpenAICompatClient, load_cases, run_cases, write_results
from pyt_agent_lab.experiment_logging import ExperimentLogger
from pyt_agent_lab.folds import kfold
from pyt_agent_lab.reporting import aggregate_fold_metrics, build_report


def main() -> None:
    parser = argparse.ArgumentParser(description="Run Pythontopia agent baseline through an OpenAI-compatible vLLM endpoint.")
    parser.add_argument("--cases", default="research/vllm-agent-ft-lab/cases/baseline_cases.jsonl")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--api-key", default="local-dev-token")
    parser.add_argument("--model", default="pythontopia-qwen35-baseline")
    parser.add_argument("--folds", type=int, default=1)
    parser.add_argument("--seed", type=int, default=20260702)
    parser.add_argument("--run-dir")
    parser.add_argument("--prompt-variant", default="baseline")
    parser.add_argument("--concurrency", type=int, default=1)
    args = parser.parse_args()

    run_dir = Path(args.run_dir or f"research/vllm-agent-ft-lab/runs/baseline-{_stamp()}")
    logger = ExperimentLogger(run_dir)
    client = OpenAICompatClient(args.base_url, args.api_key, args.model)
    cases = load_cases(args.cases)

    all_results = []
    if args.folds > 1:
        folds = kfold(cases, args.folds, args.seed)
        for fold in folds:
            all_results.extend(
                run_cases(
                    fold.validation,
                    client,
                    logger,
                    fold=fold.index,
                    prompt_variant=args.prompt_variant,
                    concurrency=args.concurrency,
                )
            )
    else:
        all_results.extend(run_cases(cases, client, logger, fold=0, prompt_variant=args.prompt_variant, concurrency=args.concurrency))

    write_results(all_results, run_dir / "responses.jsonl")
    metrics = list(csv.DictReader((run_dir / "metrics.csv").open(encoding="utf-8")))
    summary = aggregate_fold_metrics(metrics)
    build_report(summary, run_dir)
    print(run_dir)
    print(summary)


def _stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


if __name__ == "__main__":
    main()
