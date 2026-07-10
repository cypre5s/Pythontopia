#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
from pathlib import Path

from pyt_agent_lab.baseline import OpenAICompatClient, ResponseJsonlWriter, load_cases, load_completed_response_case_ids, run_cases
from pyt_agent_lab.experiment_logging import ExperimentLogger
from pyt_agent_lab.folds import kfold
from pyt_agent_lab.reporting import aggregate_fold_metrics, build_report
from pyt_agent_lab.training_quality import complete_evaluation_manifest, initialize_evaluation_manifest


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
    parser.add_argument("--progress-every", type=int, default=25, help="Append a progress snapshot every N metric rows; set 0 to disable.")
    parser.add_argument("--resume", action="store_true", help="Skip case IDs already present in responses.jsonl and append new responses.")
    args = parser.parse_args()

    run_dir = Path(args.run_dir or f"research/vllm-agent-ft-lab/runs/baseline-{_stamp()}")
    client = OpenAICompatClient(args.base_url, args.api_key, args.model)
    cases = load_cases(args.cases)
    initialize_evaluation_manifest(
        run_dir=run_dir,
        cases_path=args.cases,
        endpoint=args.base_url,
        model=args.model,
        prompt_variant=args.prompt_variant,
        seed=args.seed,
        folds=args.folds,
        concurrency=args.concurrency,
    )
    responses_path = run_dir / "responses.jsonl"
    completed_ids = load_completed_response_case_ids(responses_path) if args.resume else set()
    if completed_ids:
        cases = [case for case in cases if str(case["id"]) not in completed_ids]
    logger = ExperimentLogger(run_dir, total_cases=len(cases) + len(completed_ids), progress_every=args.progress_every)
    response_writer = ResponseJsonlWriter(responses_path)

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
                    on_result=response_writer.append,
                )
            )
    else:
        all_results.extend(
            run_cases(
                cases,
                client,
                logger,
                fold=0,
                prompt_variant=args.prompt_variant,
                concurrency=args.concurrency,
                on_result=response_writer.append,
            )
        )

    metrics = _dedupe_metric_rows(list(csv.DictReader((run_dir / "metrics.csv").open(encoding="utf-8"))))
    summary = aggregate_fold_metrics(metrics)
    build_report(summary, run_dir)
    complete_evaluation_manifest(run_dir, summary)
    print(run_dir)
    print(summary)


def _stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def _dedupe_metric_rows(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    latest_by_case: dict[str, dict[str, str]] = {}
    order: list[str] = []
    for row in rows:
        case_id = row.get("case_id", "")
        if case_id not in latest_by_case:
            order.append(case_id)
        latest_by_case[case_id] = row
    return [latest_by_case[case_id] for case_id in order if case_id]


if __name__ == "__main__":
    main()
