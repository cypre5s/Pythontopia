#!/usr/bin/env python3
from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[3]
GRAPH_ROOT = REPO_ROOT / "services" / "pythontopia-graph"
if str(GRAPH_ROOT) not in sys.path:
    sys.path.insert(0, str(GRAPH_ROOT))

from pyt_agent_lab.graph_route_ab import default_evidence_hits, default_graph_route_cases  # noqa: E402
from pythontopia_graph.agents.handout_agent import HandoutAgent  # noqa: E402
from pythontopia_graph.agents.mind_map_agent import MindMapAgent  # noqa: E402
from pythontopia_graph.agents.practice_agent import PracticeAgent  # noqa: E402
from pythontopia_graph.llm.provider import provider_from_env, provider_label  # noqa: E402
from pythontopia_graph.nodes.evidence_fetcher import EvidenceBundle, RagHit  # noqa: E402
from pythontopia_graph.schemas.resource import ResourceProduceRequest  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Inspect raw fine-tuned responses for real graph Agent prompts."
    )
    parser.add_argument("--base-url", default="http://127.0.0.1:18094/v1")
    parser.add_argument("--api-key", default="local-dev-token")
    parser.add_argument("--baseline-model", default="pythontopia-qwen35-awq-base")
    parser.add_argument("--candidate-model", default="pythontopia-qwen35-e1-lora")
    parser.add_argument(
        "--output",
        default="research/vllm-agent-ft-lab/outputs/web-ab/graph-route-live-v1/graph_agent_prompt_diagnostics.jsonl",
    )
    return parser.parse_args()


async def main() -> int:
    args = parse_args()
    os.environ.update(
        {
            "PYT_LLM_PROVIDER": "openai_compat",
            "OPENAI_BASE_URL": args.base_url,
            "OPENAI_API_KEY": args.api_key,
            "OPENAI_MODEL": args.baseline_model,
            "OPENAI_MODEL_FAST": args.baseline_model,
            "PYT_FINE_TUNED_LLM_ENABLED": "true",
            "PYT_FINE_TUNED_BASE_URL": args.base_url,
            "PYT_FINE_TUNED_API_KEY": args.api_key,
            "PYT_FINE_TUNED_MODEL": args.candidate_model,
            "NO_PROXY": "*",
            "no_proxy": "*",
        }
    )
    provider = provider_from_env()
    hits = [RagHit(**hit) for hit in default_evidence_hits()]
    rows: list[dict[str, Any]] = []
    for case in default_graph_route_cases():
        request = ResourceProduceRequest.model_validate(case.request)
        evidence = EvidenceBundle(
            chunks=[
                hit
                for hit in hits
                if any(code in hit.kc_codes for code in request.kc_codes)
            ]
        )
        agent = _agent_for(request.type, provider)
        messages = agent._prompt(request, evidence)  # noqa: SLF001 - diagnostic script.
        response = await provider.chat(messages)
        rows.append(
            {
                "case_id": case.case_id,
                "resource_type": request.type,
                "provider_label": provider_label(),
                "message_roles": [message.get("role") for message in messages],
                "content_json": response.content_json,
                "content_text": response.content_text,
            }
        )
        print(
            f"{case.case_id}: json_keys="
            f"{sorted(response.content_json.keys()) if response.content_json else []}"
        )
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(json.dumps(row, ensure_ascii=False, sort_keys=True) for row in rows) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {output}")
    return 0


def _agent_for(resource_type: str, provider: Any) -> Any:
    if resource_type == "HANDOUT":
        return HandoutAgent(provider)
    if resource_type == "MINDMAP":
        return MindMapAgent(provider)
    if resource_type == "PRACTICE":
        return PracticeAgent(provider)
    raise ValueError(f"unsupported diagnostic resource type: {resource_type}")


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
