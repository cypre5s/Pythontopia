from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .schema_registry import SCHEMA_VERSION_V1, schema_for_agent


@dataclass(frozen=True)
class VllmLoraServeConfig:
    vllm_bin: Path
    model_dir: Path
    adapter_name: str
    adapter_dir: Path
    served_model_name: str
    host: str = "0.0.0.0"
    port: int = 18094
    gpu_memory_utilization: float = 0.74
    max_model_len: int = 1024
    max_num_seqs: int = 1
    cpu_offload_gb: int = 0
    max_lora_rank: int = 8
    api_key: str = "local-dev-token"
    reasoning_parser: str | None = None


def build_vllm_lora_serve_command(config: VllmLoraServeConfig) -> list[str]:
    command = [
        str(config.vllm_bin),
        "serve",
        str(config.model_dir),
        "--served-model-name",
        config.served_model_name,
        "--host",
        config.host,
        "--port",
        str(config.port),
        "--gpu-memory-utilization",
        str(config.gpu_memory_utilization),
        "--max-model-len",
        str(config.max_model_len),
        "--dtype",
        "auto",
        "--cpu-offload-gb",
        str(config.cpu_offload_gb),
        "--language-model-only",
        "--max-num-seqs",
        str(config.max_num_seqs),
        "--enforce-eager",
        "--enable-lora",
        "--lora-modules",
        f"{config.adapter_name}={config.adapter_dir}",
        "--max-loras",
        "1",
        "--max-cpu-loras",
        "1",
        "--max-lora-rank",
        str(config.max_lora_rank),
        "--api-key",
        config.api_key,
    ]
    if config.reasoning_parser:
        command.extend(["--reasoning-parser", config.reasoning_parser])
    return command


def build_agent_structured_json_extra_body(
    agent: str,
    schema_version: str = SCHEMA_VERSION_V1,
    *,
    evidence_ids: list[str | int] | None = None,
) -> dict[str, Any]:
    json_schema = deepcopy(schema_for_agent(agent, schema_version))
    if evidence_ids:
        _bind_evidence_allowlist(json_schema, evidence_ids)
    return {
        "response_format": {
            "type": "json_schema",
            "json_schema": {
                "name": f"{agent}Response",
                "schema": json_schema,
                "strict": True,
            },
        }
    }


def _bind_evidence_allowlist(
    node: Any, evidence_ids: list[str | int]
) -> None:
    """Specialize evidence arrays without changing the semantic IR contract.

    The model still selects evidence; constrained decoding only prevents values
    outside the request-local allowlist.  This is intentionally separate from
    guard repair so inference-time gains remain observable.
    """
    if isinstance(node, dict):
        properties = node.get("properties")
        if isinstance(properties, dict):
            for field in ("evidence_ids", "evidence_refs", "evidence_page_ids"):
                value = properties.get(field)
                if not isinstance(value, dict):
                    continue
                items = value.get("items")
                if isinstance(items, dict):
                    items["enum"] = list(dict.fromkeys(evidence_ids))
                    value["minItems"] = max(1, int(value.get("minItems", 0)))
                    required = node.setdefault("required", [])
                    if isinstance(required, list) and field not in required:
                        required.append(field)
        for value in node.values():
            _bind_evidence_allowlist(value, evidence_ids)
    elif isinstance(node, list):
        for value in node:
            _bind_evidence_allowlist(value, evidence_ids)
