import json
from pathlib import Path

from pyt_agent_lab.model_inventory import inspect_model_dir, recommend_model


def write_model_dir(
    root: Path,
    name: str,
    shard_sizes: list[int],
    missing: set[str] | None = None,
    sparse: bool = False,
    quantization: str | None = None,
) -> Path:
    model_dir = root / name
    model_dir.mkdir(parents=True)
    config = {"model_type": "qwen3_5", "architectures": ["Qwen3_5ForConditionalGeneration"]}
    if quantization:
        config["quantization_config"] = {"quant_method": quantization}
    (model_dir / "config.json").write_text(json.dumps(config), encoding="utf-8")
    weight_map = {}
    missing = missing or set()
    for idx, size in enumerate(shard_sizes, start=1):
        shard = f"model-{idx:05d}-of-{len(shard_sizes):05d}.safetensors"
        weight_map[f"layer.{idx}.weight"] = shard
        if shard not in missing:
            path = model_dir / shard
            if sparse:
                with path.open("wb") as handle:
                    handle.truncate(size)
            else:
                path.write_bytes(b"x" * size)
    (model_dir / "model.safetensors.index.json").write_text(
        json.dumps({"weight_map": weight_map}),
        encoding="utf-8",
    )
    return model_dir


def test_inspect_model_dir_detects_complete_indexed_safetensors(tmp_path: Path) -> None:
    model_dir = write_model_dir(tmp_path, "Qwen3.5-4B", [11, 13])

    info = inspect_model_dir(model_dir)

    assert info.exists
    assert info.complete
    assert info.model_type == "qwen3_5"
    assert info.quantization_method is None
    assert info.weight_bytes == 24
    assert info.missing_files == []


def test_inspect_model_dir_reports_missing_shards(tmp_path: Path) -> None:
    model_dir = write_model_dir(tmp_path, "Qwen3.5-4B-AWQ", [11, 13], {"model-00002-of-00002.safetensors"})

    info = inspect_model_dir(model_dir)

    assert info.exists
    assert not info.complete
    assert info.missing_files == ["model-00002-of-00002.safetensors"]


def test_recommend_model_prefers_largest_serviceable_qwen35(tmp_path: Path) -> None:
    gib = 1024**3
    small = write_model_dir(tmp_path, "Qwen3.5-2B", [2 * gib], sparse=True)
    large = write_model_dir(tmp_path, "Qwen3.5-4B", [5 * gib, 4 * gib], sparse=True)
    awq = write_model_dir(tmp_path, "Qwen3.5-4B-AWQ", [5 * gib], sparse=True, quantization="awq")
    write_model_dir(tmp_path, "Qwen3.5-9B-AWQ", [100], {"model-00001-of-00001.safetensors"})

    recommendation = recommend_model([small, large, awq], gpu_memory_bytes=8 * 1024**3)

    assert recommendation.model_dir == awq
    assert recommendation.serve.max_model_len == 2048
    assert recommendation.serve.cpu_offload_gb == 0
    assert "--language-model-only" in recommendation.serve.extra_args
