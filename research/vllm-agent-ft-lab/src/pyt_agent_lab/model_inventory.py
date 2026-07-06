from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from pathlib import Path


GIB = 1024**3


@dataclass(frozen=True)
class ServeConfig:
    model: str
    served_model_name: str = "pythontopia-qwen35-baseline"
    host: str = "0.0.0.0"
    port: int = 18093
    gpu_memory_utilization: float = 0.82
    max_model_len: int = 2048
    dtype: str = "auto"
    cpu_offload_gb: int = 0
    extra_args: tuple[str, ...] = field(default_factory=tuple)

    def to_argv(self) -> list[str]:
        args = [
            "vllm",
            "serve",
            self.model,
            "--served-model-name",
            self.served_model_name,
            "--host",
            self.host,
            "--port",
            str(self.port),
            "--gpu-memory-utilization",
            str(self.gpu_memory_utilization),
            "--max-model-len",
            str(self.max_model_len),
            "--dtype",
            self.dtype,
        ]
        if self.cpu_offload_gb > 0:
            args.extend(["--cpu-offload-gb", str(self.cpu_offload_gb)])
        args.extend(self.extra_args)
        return args

    def shell_command(self) -> str:
        return " ".join(_quote(arg) for arg in self.to_argv())


@dataclass(frozen=True)
class ModelInfo:
    path: Path
    exists: bool
    complete: bool
    model_type: str | None
    architectures: tuple[str, ...]
    quantization_method: str | None
    weight_bytes: int
    weight_files: tuple[str, ...]
    missing_files: list[str]
    notes: tuple[str, ...] = ()


@dataclass(frozen=True)
class ModelRecommendation:
    model_dir: Path
    info: ModelInfo
    serve: ServeConfig
    rationale: tuple[str, ...]


def inspect_model_dir(path: str | Path) -> ModelInfo:
    model_dir = Path(path)
    if not model_dir.exists():
        return ModelInfo(model_dir, False, False, None, (), None, 0, (), ["<dir>"], ("directory_missing",))

    config = _read_json(model_dir / "config.json")
    model_type = config.get("model_type") if isinstance(config, dict) else None
    architectures = tuple(config.get("architectures", [])) if isinstance(config, dict) else ()
    quantization = config.get("quantization_config", {}) if isinstance(config, dict) else {}
    quantization_method = quantization.get("quant_method") if isinstance(quantization, dict) else None

    weight_files = _indexed_weight_files(model_dir)
    if not weight_files:
        weight_files = tuple(sorted(p.name for p in model_dir.glob("*.safetensors")))

    missing = [name for name in weight_files if not (model_dir / name).exists()]
    weight_bytes = sum((model_dir / name).stat().st_size for name in weight_files if (model_dir / name).exists())
    notes: list[str] = []
    if not weight_files:
        notes.append("no_safetensors_weights")
    if missing:
        notes.append("missing_weight_shards")
    complete = bool(weight_files) and not missing and weight_bytes > 0
    return ModelInfo(model_dir, True, complete, model_type, architectures, quantization_method, weight_bytes, weight_files, missing, tuple(notes))


def recommend_model(model_dirs: list[str | Path], gpu_memory_bytes: int) -> ModelRecommendation:
    candidates = [inspect_model_dir(path) for path in model_dirs]
    complete_qwen35 = [
        info for info in candidates if info.complete and (info.model_type == "qwen3_5" or "qwen3.5" in info.path.name.lower())
    ]
    if not complete_qwen35:
        inspected = ", ".join(str(info.path) for info in candidates) or "<none>"
        raise ValueError(f"no complete qwen3.5 model found in: {inspected}")

    conservative_gpu_budget = int(gpu_memory_bytes * 0.72)
    directly_serviceable = [info for info in complete_qwen35 if info.weight_bytes <= conservative_gpu_budget]
    if directly_serviceable:
        info = max(directly_serviceable, key=lambda item: item.weight_bytes)
    else:
        info = max(complete_qwen35, key=lambda item: item.weight_bytes)
    cpu_offload_gb = 0 if info in directly_serviceable else _cpu_offload_for(info.weight_bytes, gpu_memory_bytes)
    extra_args = ["--language-model-only", "--reasoning-parser", "qwen3"]
    if cpu_offload_gb == 0:
        extra_args.extend(["--max-num-seqs", "4"])
    serve = ServeConfig(
        model=str(info.path),
        cpu_offload_gb=cpu_offload_gb,
        extra_args=tuple(extra_args),
    )
    rationale = [
        f"selected largest complete qwen3.5 candidate: {info.path}",
        f"weights: {info.weight_bytes / GIB:.2f} GiB; gpu memory: {gpu_memory_bytes / GIB:.2f} GiB",
        "uses language-model-only to avoid multimodal profiling overhead on an 8GB laptop GPU",
    ]
    if info.quantization_method:
        rationale.append(f"quantization: {info.quantization_method}")
    if cpu_offload_gb:
        rationale.append(f"enables CPU offload ({cpu_offload_gb} GiB) because raw weights exceed a conservative VRAM budget")
    else:
        rationale.append("avoids CPU weight offload because vLLM Qwen3.5 hybrid KV currently failed with offload on this machine")
    return ModelRecommendation(info.path, info, serve, tuple(rationale))


def _indexed_weight_files(model_dir: Path) -> tuple[str, ...]:
    index_path = model_dir / "model.safetensors.index.json"
    index = _read_json(index_path)
    if not isinstance(index, dict):
        return ()
    weight_map = index.get("weight_map")
    if not isinstance(weight_map, dict):
        return ()
    return tuple(sorted({str(name) for name in weight_map.values()}))


def _read_json(path: Path) -> dict:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def _cpu_offload_for(weight_bytes: int, gpu_memory_bytes: int) -> int:
    conservative_gpu_budget = int(gpu_memory_bytes * 0.62)
    if weight_bytes <= conservative_gpu_budget:
        return 0
    shortfall = weight_bytes - conservative_gpu_budget
    return max(4, math.ceil(shortfall / GIB) + 2)


def _quote(value: str) -> str:
    if all(char.isalnum() or char in "-_./:=," for char in value):
        return value
    return "'" + value.replace("'", "'\"'\"'") + "'"
