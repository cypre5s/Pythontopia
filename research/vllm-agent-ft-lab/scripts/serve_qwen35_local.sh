#!/usr/bin/env bash
set -euo pipefail

LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_DIR="${MODEL_DIR:-$LAB_ROOT/models/QuantTrio-Qwen3.5-4B-AWQ}"
HOST="${VLLM_HOST:-0.0.0.0}"
PORT="${VLLM_PORT:-18093}"
SERVED_NAME="${SERVED_MODEL_NAME:-pythontopia-qwen35-baseline}"

if [[ ! -x "$LAB_ROOT/.venv/bin/vllm" ]]; then
  echo "Missing $LAB_ROOT/.venv/bin/vllm. Run scripts/setup_vllm_env.sh first." >&2
  exit 2
fi

export VLLM_USE_V1="${VLLM_USE_V1:-1}"
exec "$LAB_ROOT/.venv/bin/vllm" serve "$MODEL_DIR" \
  --served-model-name "$SERVED_NAME" \
  --host "$HOST" \
  --port "$PORT" \
  --gpu-memory-utilization "${GPU_MEMORY_UTILIZATION:-0.82}" \
  --max-model-len "${MAX_MODEL_LEN:-2048}" \
  --dtype auto \
  --cpu-offload-gb "${CPU_OFFLOAD_GB:-0}" \
  --language-model-only \
  --max-num-seqs "${MAX_NUM_SEQS:-4}" \
  --enforce-eager \
  --reasoning-parser qwen3 \
  --api-key "${VLLM_API_KEY:-local-dev-token}"
