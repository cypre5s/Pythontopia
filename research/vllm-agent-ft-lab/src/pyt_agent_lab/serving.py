from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .scoring import AGENT_SCHEMA


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
    reasoning_parser: str = "qwen3"
    guided_decoding_backend: str | None = None


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
        "--reasoning-parser",
        config.reasoning_parser,
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
    if config.guided_decoding_backend:
        command.extend(["--guided-decoding-backend", config.guided_decoding_backend])
    return command


def build_agent_guided_json_extra_body(agent: str) -> dict[str, Any]:
    schema = AGENT_SCHEMA.get(agent)
    if schema is None:
        raise ValueError(f"unknown agent schema: {agent}")
    required = schema["required"]
    return {
        "guided_json": {
            "type": "object",
            "properties": {
                key: {"type": _json_schema_type(expected_type)}
                for key, expected_type in required.items()
            },
            "required": list(required),
            "additionalProperties": True,
        }
    }


def _json_schema_type(expected_type: Any) -> str:
    if expected_type is str:
        return "string"
    if expected_type is bool:
        return "boolean"
    if expected_type is list:
        return "array"
    if expected_type is dict:
        return "object"
    if expected_type in {int, float} or expected_type == (int, float):
        return "number"
    return "string"
