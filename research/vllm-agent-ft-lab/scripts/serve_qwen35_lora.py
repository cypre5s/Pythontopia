#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

from pyt_agent_lab.serving import VllmLoraServeConfig, build_vllm_lora_serve_command


def main() -> None:
    parser = argparse.ArgumentParser(description="Serve the Pythontopia Qwen3.5 LoRA adapter with vLLM.")
    parser.add_argument("--dry-run-json", action="store_true", help="Print the argv vector instead of execing vLLM.")
    args = parser.parse_args()

    lab_root = Path(__file__).resolve().parents[1]
    config = VllmLoraServeConfig(
        vllm_bin=Path(os.environ.get("VLLM_BIN", lab_root / ".venv/bin/vllm")),
        model_dir=Path(os.environ.get("MODEL_DIR", lab_root / "models/QuantTrio-Qwen3.5-4B-AWQ")),
        adapter_name=os.environ.get("LORA_MODEL_NAME", "pythontopia-qwen35-e1-lora"),
        adapter_dir=Path(
            os.environ.get(
                "LORA_ADAPTER_DIR",
                lab_root / "outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch",
            )
        ),
        served_model_name=os.environ.get("SERVED_MODEL_NAME", "pythontopia-qwen35-awq-base"),
        host=os.environ.get("VLLM_HOST", "0.0.0.0"),
        port=int(os.environ.get("VLLM_PORT", "18094")),
        gpu_memory_utilization=float(os.environ.get("GPU_MEMORY_UTILIZATION", "0.74")),
        max_model_len=int(os.environ.get("MAX_MODEL_LEN", "1024")),
        max_num_seqs=int(os.environ.get("MAX_NUM_SEQS", "1")),
        cpu_offload_gb=int(os.environ.get("CPU_OFFLOAD_GB", "0")),
        api_key=os.environ.get("VLLM_API_KEY", "local-dev-token"),
    )
    command = build_vllm_lora_serve_command(config)
    if args.dry_run_json:
        print(json.dumps(command, ensure_ascii=False, indent=2))
        return
    env = dict(os.environ)
    env.setdefault("VLLM_USE_V1", "1")
    os.execvpe(command[0], command, env)


if __name__ == "__main__":
    main()
