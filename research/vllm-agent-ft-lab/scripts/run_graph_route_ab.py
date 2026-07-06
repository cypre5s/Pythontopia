#!/usr/bin/env python3
from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any

import httpx


REPO_ROOT = Path(__file__).resolve().parents[3]
GRAPH_ROOT = REPO_ROOT / "services" / "pythontopia-graph"
if str(GRAPH_ROOT) not in sys.path:
    sys.path.insert(0, str(GRAPH_ROOT))

from pyt_agent_lab.graph_route_ab import (  # noqa: E402
    build_graph_route_ab_report,
    default_evidence_hits,
    default_graph_route_cases,
    summarize_graph_route_results,
    write_graph_route_ab_report,
)
from pythontopia_graph.llm.provider import provider_from_env, provider_label  # noqa: E402
from pythontopia_graph.main import app  # noqa: E402
from pythontopia_graph.nodes.evidence_fetcher import RagHit, StaticEvidenceFetcher  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run a live Graph route A/B through /agents/resource/produce."
    )
    parser.add_argument("--base-url", default="http://127.0.0.1:18094/v1")
    parser.add_argument("--api-key", default="local-dev-token")
    parser.add_argument("--baseline-model", default="pythontopia-qwen35-awq-base")
    parser.add_argument("--candidate-model", default="pythontopia-qwen35-e1-lora")
    parser.add_argument("--internal-token", default="local-dev-token")
    parser.add_argument(
        "--output-dir",
        default="research/vllm-agent-ft-lab/outputs/web-ab/graph-route-live-v1",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=180.0,
        help="Per-route request timeout. Local vLLM first-token latency can be high on 8 GB VRAM.",
    )
    return parser.parse_args()


@contextmanager
def patched_env(values: dict[str, str]):
    previous: dict[str, str | None] = {key: os.environ.get(key) for key in values}
    os.environ.update(values)
    try:
        yield
    finally:
        for key, value in previous.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


async def main() -> int:
    args = parse_args()
    baseline_results = await _run_variant(
        variant="baseline_base_model",
        base_url=args.base_url,
        api_key=args.api_key,
        baseline_model=args.baseline_model,
        candidate_model=args.candidate_model,
        internal_token=args.internal_token,
        fine_tuned_enabled=False,
        timeout_seconds=args.timeout_seconds,
    )
    candidate_results = await _run_variant(
        variant="candidate_fine_tuned_provider",
        base_url=args.base_url,
        api_key=args.api_key,
        baseline_model=args.baseline_model,
        candidate_model=args.candidate_model,
        internal_token=args.internal_token,
        fine_tuned_enabled=True,
        timeout_seconds=args.timeout_seconds,
    )
    baseline_summary = summarize_graph_route_results(
        variant="baseline_base_model",
        results=baseline_results,
    )
    candidate_summary = summarize_graph_route_results(
        variant="candidate_fine_tuned_provider",
        results=candidate_results,
    )
    report = build_graph_route_ab_report(
        baseline_summary=baseline_summary,
        candidate_summary=candidate_summary,
        model_info={
            "base_url": args.base_url,
            "baseline_model": args.baseline_model,
            "candidate_model": args.candidate_model,
        },
    )
    paths = write_graph_route_ab_report(report, args.output_dir)
    raw_path = Path(args.output_dir) / "graph_route_ab_raw.jsonl"
    raw_path.parent.mkdir(parents=True, exist_ok=True)
    raw_path.write_text(
        "\n".join(
            json.dumps(result, ensure_ascii=False, sort_keys=True)
            for result in baseline_results + candidate_results
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"wrote {paths['json']}")
    print(f"wrote {paths['markdown']}")
    print(f"wrote {raw_path}")
    print(f"recommendation={report['recommendation']}")
    return 0 if report["comparison"]["gate_passed"] else 2


async def _run_variant(
    *,
    variant: str,
    base_url: str,
    api_key: str,
    baseline_model: str,
    candidate_model: str,
    internal_token: str,
    fine_tuned_enabled: bool,
    timeout_seconds: float,
) -> list[dict[str, Any]]:
    env = {
        "PYT_RAG_ADMIN_TOKEN": internal_token,
        "PYT_LLM_PROVIDER": "openai_compat",
        "OPENAI_BASE_URL": base_url,
        "OPENAI_API_KEY": api_key,
        "OPENAI_MODEL": baseline_model,
        "OPENAI_MODEL_FAST": baseline_model,
        "PYT_FINE_TUNED_LLM_ENABLED": "true" if fine_tuned_enabled else "false",
        "PYT_FINE_TUNED_BASE_URL": base_url,
        "PYT_FINE_TUNED_API_KEY": api_key,
        "PYT_FINE_TUNED_MODEL": candidate_model,
        "NO_PROXY": "*",
        "no_proxy": "*",
    }
    with patched_env(env):
        provider = provider_from_env()
        app.state.llm_provider = provider
        app.state.evidence_fetcher = StaticEvidenceFetcher(
            [RagHit(**hit) for hit in default_evidence_hits()]
        )
        app.state.valid_kc_codes = {
            code
            for hit in default_evidence_hits()
            for code in hit.get("kc_codes", [])
            if isinstance(code, str)
        }
        print(f"{variant}: {provider_label()}")
        transport = httpx.ASGITransport(app=app)
        results: list[dict[str, Any]] = []
        async with httpx.AsyncClient(
            transport=transport,
            base_url="http://graph-route-ab.local",
            timeout=timeout_seconds,
        ) as client:
            for case in default_graph_route_cases():
                start = time.perf_counter()
                result: dict[str, Any] = {
                    "case_id": case.case_id,
                    "variant": variant,
                    "http_status": 0,
                    "elapsed_ms": 0,
                    "body": None,
                    "error": None,
                }
                try:
                    response = await client.post(
                        "/agents/resource/produce",
                        headers={"X-Internal-Token": internal_token},
                        json=case.request,
                    )
                    result["http_status"] = response.status_code
                    result["body"] = response.json()
                except Exception as exc:  # noqa: BLE001 - keep per-case evidence.
                    result["error"] = f"{type(exc).__name__}: {exc}"
                result["elapsed_ms"] = round((time.perf_counter() - start) * 1000)
                results.append(result)
                print(
                    f"{variant}: {case.case_id} "
                    f"http={result['http_status']} elapsed_ms={result['elapsed_ms']}"
                )
        return results


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
