#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json

from pyt_agent_lab.model_inventory import inspect_model_dir, recommend_model


def main() -> None:
    parser = argparse.ArgumentParser(description="Inspect local Qwen3.5 candidates and print vLLM serve config.")
    parser.add_argument("model_dirs", nargs="+")
    parser.add_argument("--gpu-memory-gb", type=float, default=8.0)
    args = parser.parse_args()

    infos = [inspect_model_dir(path) for path in args.model_dirs]
    recommendation = recommend_model(args.model_dirs, int(args.gpu_memory_gb * 1024**3))
    print(
        json.dumps(
            {
                "models": [
                    {
                        "path": str(info.path),
                        "exists": info.exists,
                        "complete": info.complete,
                        "model_type": info.model_type,
                        "quantization_method": info.quantization_method,
                        "weight_gib": round(info.weight_bytes / 1024**3, 3),
                        "missing_files": info.missing_files,
                        "notes": list(info.notes),
                    }
                    for info in infos
                ],
                "recommended": {
                    "path": str(recommendation.model_dir),
                    "serve_command": recommendation.serve.shell_command(),
                    "rationale": list(recommendation.rationale),
                },
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
