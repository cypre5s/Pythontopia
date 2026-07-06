from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


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


def build_vllm_lora_serve_command(config: VllmLoraServeConfig) -> list[str]:
    return [
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
