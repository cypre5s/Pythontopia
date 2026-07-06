from pathlib import Path

from pyt_agent_lab.serving import VllmLoraServeConfig, build_vllm_lora_serve_command


def test_build_vllm_lora_serve_command_exposes_static_adapter_model() -> None:
    config = VllmLoraServeConfig(
        vllm_bin=Path("/lab/.venv/bin/vllm"),
        model_dir=Path("/models/qwen35-awq"),
        adapter_name="pythontopia-e1-lora",
        adapter_dir=Path("/adapters/e1"),
        served_model_name="pythontopia-qwen35-base",
        host="127.0.0.1",
        port=18094,
        max_model_len=1024,
        max_num_seqs=1,
        gpu_memory_utilization=0.72,
        cpu_offload_gb=4,
        api_key="local-token",
    )

    command = build_vllm_lora_serve_command(config)

    assert command[:3] == ["/lab/.venv/bin/vllm", "serve", "/models/qwen35-awq"]
    assert "--enable-lora" in command
    assert command[command.index("--lora-modules") + 1] == "pythontopia-e1-lora=/adapters/e1"
    assert command[command.index("--served-model-name") + 1] == "pythontopia-qwen35-base"
    assert command[command.index("--max-lora-rank") + 1] == "8"
    assert command[command.index("--api-key") + 1] == "local-token"


def test_build_vllm_lora_serve_command_keeps_adapter_and_base_names_distinct() -> None:
    config = VllmLoraServeConfig(
        vllm_bin=Path("vllm"),
        model_dir=Path("base"),
        adapter_name="adapter-model",
        adapter_dir=Path("adapter"),
        served_model_name="base-model",
    )

    command = build_vllm_lora_serve_command(config)

    assert command.count("adapter-model=adapter") == 1
    assert "adapter-model" not in command[command.index("--served-model-name") + 1]
