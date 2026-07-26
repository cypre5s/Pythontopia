from pathlib import Path

from pyt_agent_lab.serving import (
    VllmLoraServeConfig,
    build_agent_structured_json_extra_body,
    build_vllm_lora_serve_command,
)


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


def test_build_vllm_lora_serve_command_does_not_emit_removed_guided_decoding_flag() -> None:
    config = VllmLoraServeConfig(
        vllm_bin=Path("vllm"),
        model_dir=Path("base"),
        adapter_name="adapter-model",
        adapter_dir=Path("adapter"),
        served_model_name="base-model",
    )

    command = build_vllm_lora_serve_command(config)

    assert "--guided-decoding-backend" not in command


def test_build_vllm_lora_serve_command_does_not_emit_reasoning_parser_by_default() -> None:
    config = VllmLoraServeConfig(
        vllm_bin=Path("vllm"),
        model_dir=Path("base"),
        adapter_name="adapter-model",
        adapter_dir=Path("adapter"),
        served_model_name="base-model",
    )

    command = build_vllm_lora_serve_command(config)

    assert "--reasoning-parser" not in command


def test_build_vllm_lora_serve_command_can_emit_reasoning_parser_explicitly() -> None:
    config = VllmLoraServeConfig(
        vllm_bin=Path("vllm"),
        model_dir=Path("base"),
        adapter_name="adapter-model",
        adapter_dir=Path("adapter"),
        served_model_name="base-model",
        reasoning_parser="qwen3",
    )

    command = build_vllm_lora_serve_command(config)

    assert command[command.index("--reasoning-parser") + 1] == "qwen3"


def test_build_agent_structured_json_extra_body_uses_openai_json_schema_format() -> None:
    extra_body = build_agent_structured_json_extra_body("TutorAgent")

    response_format = extra_body["response_format"]
    assert response_format["type"] == "json_schema"
    assert response_format["json_schema"]["name"] == "TutorAgentResponse"
    schema = response_format["json_schema"]["schema"]
    assert schema["type"] == "object"
    assert schema["required"] == ["diagnosis", "next_hint", "evidence_ids", "safety_flags"]
    assert schema["properties"]["diagnosis"]["type"] == "string"
    assert schema["properties"]["evidence_ids"]["type"] == "array"


def test_build_agent_structured_json_extra_body_binds_nested_v2_evidence_allowlist() -> None:
    extra_body = build_agent_structured_json_extra_body(
        "ResourceDesignerAgent.handout",
        "agent-schema-v2",
        evidence_ids=["page-1-006", "page-1-009", "page-1-006"],
    )

    schema = extra_body["response_format"]["json_schema"]["schema"]
    section = schema["$defs"]["HandoutSectionIR"]
    assert section["properties"]["evidence_refs"]["items"]["enum"] == [
        "page-1-006",
        "page-1-009",
    ]
    assert section["properties"]["evidence_refs"]["minItems"] == 1
    assert "evidence_refs" in section["required"]


def test_evidence_binding_does_not_mutate_registry_schema() -> None:
    build_agent_structured_json_extra_body(
        "ResourceDesignerAgent.handout",
        "agent-schema-v2",
        evidence_ids=["page-1-006"],
    )

    unbound = build_agent_structured_json_extra_body(
        "ResourceDesignerAgent.handout", "agent-schema-v2"
    )
    schema = unbound["response_format"]["json_schema"]["schema"]
    assert "enum" not in schema["$defs"]["HandoutSectionIR"]["properties"]["evidence_refs"]["items"]
