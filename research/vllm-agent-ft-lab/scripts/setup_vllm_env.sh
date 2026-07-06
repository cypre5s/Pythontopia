#!/usr/bin/env bash
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UV="${UV:-/home/cypress/.local/bin/uv}"

if [[ ! -x "$UV" ]]; then
  echo "uv not found at $UV" >&2
  exit 2
fi

cd "$LAB_ROOT"
"$UV" venv --python python3.10 .venv
"$UV" pip install --python .venv/bin/python --torch-backend cu128 \
  "vllm==0.17.1" \
  "torch==2.10.0" "torchvision==0.25.0" "torchaudio==2.10.0" \
  "transformers>=4.57,<5" \
  "accelerate>=1.8,<2" \
  "datasets>=3.6,<5" \
  "peft>=0.15,<1" \
  "trl>=0.18,<1" \
  "pandas>=2.2,<3" \
  "matplotlib>=3.9,<4"
