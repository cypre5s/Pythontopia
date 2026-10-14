from __future__ import annotations

import json
import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from typing import Any

from .baseline import (
    BaselineResult,
    OpenAICompatClient,
    ResponseJsonlWriter,
    build_case_messages,
)
from .experiment_logging import ExperimentLogger
from .fallback import build_fallback_response
from .guard import apply_guard, guard_failure_summary
from .reporting import aggregate_fold_metrics, build_report
from .scoring import score_case_response

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class GroupEndpoint:
    label: str
    base_url: str
    api_key: str
    model: str


@dataclass(frozen=True)
class AbcEvalConfig:
    output_dir: Path
    cases: list[dict[str, Any]]
    group_a: GroupEndpoint
    group_b: GroupEndpoint
    seed: int = 20260708
    concurrency: int = 1
    progress_every: int = 10
    timeout_seconds: int = 180
    prompt_variant: str | None = None


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------


def run_abc_groups(config: AbcEvalConfig) -> dict[str, Path]:
    """Run the same cases through all three pipelines.

    Returns a dict mapping group key to its run_dir.
    """
    config.output_dir.mkdir(parents=True, exist_ok=True)

    # ---- Groups A & B run in parallel ------------------------------------
    a_dir = config.output_dir / "group_a"
    b_dir = config.output_dir / "group_b"

    a_client = OpenAICompatClient(
        base_url=config.group_a.base_url,
        api_key=config.group_a.api_key,
        model=config.group_a.model,
        timeout=config.timeout_seconds,
    )
    b_client = OpenAICompatClient(
        base_url=config.group_b.base_url,
        api_key=config.group_b.api_key,
        model=config.group_b.model,
        timeout=config.timeout_seconds,
    )

    a_logger = ExperimentLogger(
        a_dir, total_cases=len(config.cases), progress_every=config.progress_every
    )
    b_logger = ExperimentLogger(
        b_dir, total_cases=len(config.cases), progress_every=config.progress_every
    )

    a_writer = ResponseJsonlWriter(a_dir / "responses.jsonl")
    b_writer = ResponseJsonlWriter(b_dir / "responses.jsonl")

    progress = _ProgressTracker(len(config.cases), config)

    if config.concurrency <= 1:
        # Sequential interleaved: one case at a time through both pipelines
        a_results, b_results = _run_sequential(
            config, a_client, b_client, a_logger, b_logger, a_writer, b_writer, progress
        )
    else:
        # Parallel: A and B run concurrently in separate thread pools
        a_results, b_results = _run_parallel(
            config, a_client, b_client, a_logger, b_logger, a_writer, b_writer, progress
        )

    # ---- Finalise A & B --------------------------------------------------
    _finalise_run(a_dir, a_logger, a_results)
    _finalise_run(b_dir, b_logger, b_results)

    # ---- Group C: Group B + Guard + Fallback  ----------------------------
    c_dir = config.output_dir / "group_c_guarded"
    _run_group_c(config, b_results, c_dir, progress)

    progress.finish()

    return {"A": a_dir, "B": b_dir, "C": c_dir}


# ---------------------------------------------------------------------------
# Internal: sequential execution
# ---------------------------------------------------------------------------


def _run_sequential(
    config: AbcEvalConfig,
    a_client: OpenAICompatClient,
    b_client: OpenAICompatClient,
    a_logger: ExperimentLogger,
    b_logger: ExperimentLogger,
    a_writer: ResponseJsonlWriter,
    b_writer: ResponseJsonlWriter,
    progress: "_ProgressTracker",
) -> tuple[list[BaselineResult], list[BaselineResult]]:
    """Run A and B in parallel per case, one case at a time."""
    a_results: list[BaselineResult] = []
    b_results: list[BaselineResult] = []

    for case in config.cases:
        with ThreadPoolExecutor(max_workers=2) as pool:
            a_future = pool.submit(_run_one_case, case, a_client, a_logger, config.prompt_variant)
            b_future = pool.submit(_run_one_case, case, b_client, b_logger, config.prompt_variant)
            a_result = a_future.result()
            b_result = b_future.result()

        a_writer.append(a_result)
        b_writer.append(b_result)
        a_results.append(a_result)
        b_results.append(b_result)
        progress.tick(a_result.score.score, b_result.score.score)

    return a_results, b_results


# ---------------------------------------------------------------------------
# Internal: parallel execution
# ---------------------------------------------------------------------------


def _run_parallel(
    config: AbcEvalConfig,
    a_client: OpenAICompatClient,
    b_client: OpenAICompatClient,
    a_logger: ExperimentLogger,
    b_logger: ExperimentLogger,
    a_writer: ResponseJsonlWriter,
    b_writer: ResponseJsonlWriter,
    progress: "_ProgressTracker",
) -> tuple[list[BaselineResult], list[BaselineResult]]:
    from concurrent.futures import ThreadPoolExecutor, as_completed

    a_results_by_idx: dict[int, BaselineResult] = {}
    b_results_by_idx: dict[int, BaselineResult] = {}

    def _run(idx_case: tuple[int, dict[str, Any]], client: OpenAICompatClient, logger: ExperimentLogger) -> tuple[int, str, BaselineResult]:
        idx, case = idx_case
        result = _run_one_case(case, client, logger, config.prompt_variant)
        return idx, "unknown", result

    with ThreadPoolExecutor(max_workers=config.concurrency) as executor:
        a_futures = {
            executor.submit(_run_one_case, case, a_client, a_logger, config.prompt_variant): idx
            for idx, case in enumerate(config.cases)
        }
        b_futures = {
            executor.submit(_run_one_case, case, b_client, b_logger, config.prompt_variant): idx
            for idx, case in enumerate(config.cases)
        }

        for future in as_completed(list(a_futures) + list(b_futures)):
            result = future.result()
            idx = a_futures.get(future) if future in a_futures else b_futures.get(future)
            if future in a_futures:
                a_results_by_idx[idx] = result
                a_writer.append(result)
            else:
                b_results_by_idx[idx] = result
                b_writer.append(result)

            a_score = a_results_by_idx.get(idx)
            b_score = b_results_by_idx.get(idx)
            if a_score is not None and b_score is not None:
                progress.tick(a_score.score.score, b_score.score.score)

    return (
        [a_results_by_idx[i] for i in range(len(config.cases))],
        [b_results_by_idx[i] for i in range(len(config.cases))],
    )


# ---------------------------------------------------------------------------
# Group C: Group B + Guard + Fallback
# ---------------------------------------------------------------------------


def _run_group_c(
    config: AbcEvalConfig,
    b_results: list[BaselineResult],
    c_dir: Path,
    progress: "_ProgressTracker",
) -> None:
    """Reuses Group B's LoRA responses, applies guard, falls back on failure."""
    c_logger = ExperimentLogger(
        c_dir, total_cases=len(b_results), progress_every=config.progress_every
    )
    guard_log_path = c_dir / "guard_log.jsonl"
    c_dir.mkdir(parents=True, exist_ok=True)

    c_results: list[BaselineResult] = []

    for b_result in b_results:
        case = b_result.case
        raw_response = b_result.response
        decision = apply_guard(case, raw_response)

        if decision.passed:
            final_response = raw_response
            guard_applied = False
        else:
            final_response = build_fallback_response(
                case,
                str(case.get("agent", "")),
                decision.failures,
            )
            guard_applied = True

        score = score_case_response(case, final_response)
        c_logger.event(
            "case_started",
            {"case_id": case["id"], "agent": case["agent"], "group": "C"},
        )
        c_logger.metric(
            {
                "case_id": case["id"],
                "agent": case["agent"],
                "score": score.score,
                "json_valid": score.json_valid,
                "schema_valid": score.schema_valid,
                "grounding_safe": score.grounding_safe,
                "forbidden_leak": score.forbidden_leak,
                "missing_schema_keys": list(score.missing_schema_keys),
                "schema_type_errors": list(score.schema_type_errors),
                "matched_expectations": score.matched_expectations,
                "expectation_count": score.expectation_count,
                "guard_passed": decision.passed,
                "guard_failures": guard_failure_summary(decision),
                "guard_applied": guard_applied,
                "latency_ms": b_result.latency_ms,
                "response_chars": len(final_response),
            }
        )
        c_logger.event(
            "case_finished",
            {
                "case_id": case["id"],
                "agent": case["agent"],
                "group": "C",
                "score": score.score,
                "guard_passed": decision.passed,
            },
        )

        c_results.append(
            BaselineResult(case=case, response=final_response, latency_ms=b_result.latency_ms, score=score)
        )

        # Write guard log
        with open(guard_log_path, "a", encoding="utf-8") as handle:
            handle.write(
                json.dumps(
                    {
                        "case_id": case["id"],
                        "agent": case["agent"],
                        "guard_passed": decision.passed,
                        "guard_failures": decision.failures,
                        "guard_applied": guard_applied,
                        "score_before": (
                            decision.score.as_row() if decision.score else None
                        ),
                        "score_after": score.as_row(),
                    },
                    ensure_ascii=False,
                    sort_keys=True,
                )
                + "\n"
            )

        progress.tick_c(score.score, guard_applied)

    # Write C responses
    from .baseline import write_results

    write_results(c_results, c_dir / "responses.jsonl")
    _finalise_run(c_dir, c_logger, c_results)


# ---------------------------------------------------------------------------
# Finalise a run: summary + charts
# ---------------------------------------------------------------------------


def _finalise_run(
    run_dir: Path,
    logger: ExperimentLogger,
    results: list[BaselineResult],
) -> None:
    rows = [r.score.as_row(fold=0) for r in results]
    summary = aggregate_fold_metrics(rows)
    build_report(summary, run_dir)


# ---------------------------------------------------------------------------
# Single-case runner (mirrors baseline._run_one_case)
# ---------------------------------------------------------------------------


def _run_one_case(
    case: dict[str, Any],
    client: OpenAICompatClient,
    logger: ExperimentLogger,
    prompt_variant: str | None = None,
) -> BaselineResult:
    logger.event("case_started", {"case_id": case["id"], "agent": case["agent"]})
    started = time.perf_counter()
    response = client.chat(
        build_case_messages(case, prompt_variant=prompt_variant),
        max_tokens=int(case.get("max_tokens", 512)),
    )
    latency_ms = int((time.perf_counter() - started) * 1000)
    score = score_case_response(case, response)
    row = {
        **score.as_row(),
        "latency_ms": latency_ms,
        "response_chars": len(response),
    }
    logger.metric(row)
    logger.event(
        "case_finished",
        {"case_id": case["id"], "agent": case["agent"], "score": score.score},
    )
    return BaselineResult(case=case, response=response, latency_ms=latency_ms, score=score)


# ---------------------------------------------------------------------------
# Progress tracker (live terminal output)
# ---------------------------------------------------------------------------


class _ProgressTracker:
    def __init__(self, total: int, config: AbcEvalConfig) -> None:
        self.total = total
        self.config = config
        self._lock = threading.Lock()
        self._a_complete = 0
        self._b_complete = 0
        self._c_complete = 0
        self._c_guarded = 0
        self._started = time.perf_counter()
        self._a_score_sum = 0.0
        self._b_score_sum = 0.0
        self._c_score_sum = 0.0

    def tick(self, a_score: float, b_score: float) -> None:
        with self._lock:
            self._a_complete += 1
            self._b_complete += 1
            self._a_score_sum += a_score
            self._b_score_sum += b_score
        self._print()

    def tick_c(self, c_score: float, guarded: bool) -> None:
        with self._lock:
            self._c_complete += 1
            self._c_score_sum += c_score
            if guarded:
                self._c_guarded += 1
        self._print()

    def finish(self) -> None:
        self._print()
        elapsed = time.perf_counter() - self._started
        print(f"\nDone in {elapsed:.1f}s  |  "
              f"A mean={self._a_score_sum / max(1, self._a_complete):.3f}  "
              f"B mean={self._b_score_sum / max(1, self._b_complete):.3f}  "
              f"C mean={self._c_score_sum / max(1, self._c_complete):.3f}  "
              f"C fallbacks={self._c_guarded}",
              file=sys.stderr)

    def _print(self) -> None:
        with self._lock:
            a, b, c = self._a_complete, self._b_complete, self._c_complete
        elapsed = time.perf_counter() - self._started
        throughput = (a + b) / elapsed if elapsed > 0 else 0
        eta = (self.total - max(a, b)) / throughput if throughput > 0 and max(a, b) < self.total else 0
        print(
            f"\rCases: {max(a, b)}/{self.total} | "
            f"A: {a}✓ | B: {b}✓ | C: {c}✓ {self._c_guarded}↻ | "
            f"Elapsed: {elapsed:.0f}s | ETA: {eta:.0f}s  ",
            end="",
            file=sys.stderr,
            flush=True,
        )
