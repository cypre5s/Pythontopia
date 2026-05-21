#!/usr/bin/env bash
# Pythontopia: 通过 pythontopia-rag 重建课件向量索引。
# 需要 PYT_RAG_ADMIN_TOKEN 与 pythontopia-rag 服务已经 healthy。
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck source=/dev/null
  source .env
  set +a
fi

TOKEN="${PYT_RAG_ADMIN_TOKEN:-pytopia-rag-admin}"
RAG_URL="${PYT_RAG_URL:-http://127.0.0.1:18092}"

echo "[init_rag_index] 等待 pythontopia-rag healthy..."
for i in $(seq 1 60); do
  if curl -fsS "${RAG_URL}/healthz" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "[init_rag_index] 重建索引..."
response="$(curl -fsS -X POST "${RAG_URL}/rag/index/rebuild" -H "X-Internal-Token: ${TOKEN}" || true)"
if [[ -z "$response" ]]; then
  echo "[init_rag_index] 调用失败，请检查 token 与 rag 服务" >&2
  exit 1
fi
echo "[init_rag_index] $response"
