#!/usr/bin/env bash
# Pythontopia: 通过 pythontopia-rag 重建课件向量索引。
# 需要 PYT_RAG_ADMIN_TOKEN 与 pythontopia-rag 服务已经 healthy。
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  # shellcheck source=/dev/null
  source .env
fi

TOKEN="${PYT_RAG_ADMIN_TOKEN:-}"
unset PYT_RAG_ADMIN_TOKEN
if [[ ${#TOKEN} -lt 32 || "$TOKEN" == *$'\n'* || "$TOKEN" == *$'\r'* ]]; then
  echo "[init_rag_index] PYT_RAG_ADMIN_TOKEN must be configured with at least 32 characters" >&2
  exit 1
fi
RAG_URL="${PYT_RAG_URL:-http://127.0.0.1:18092}"

echo "[init_rag_index] 等待 pythontopia-rag healthy..."
for i in $(seq 1 60); do
  if curl -fsS "${RAG_URL}/healthz" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "[init_rag_index] 重建索引..."
if ! response="$(printf 'X-Internal-Token: %s\n' "$TOKEN" | curl -fsS -X POST "${RAG_URL}/rag/index/rebuild" -H @-)"; then
  echo "[init_rag_index] 调用失败，请检查 token 与 rag 服务" >&2
  exit 1
fi
echo "[init_rag_index] $response"
