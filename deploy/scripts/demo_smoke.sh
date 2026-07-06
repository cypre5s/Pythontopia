#!/usr/bin/env bash
# Pythontopia: 演示前最小冒烟。检查 4 项核心服务 healthcheck + 1 个课程包接口可达。
set -euo pipefail

cd "$(dirname "$0")/.."

API_BASE="${PYT_API_BASE:-http://127.0.0.1:18080}"
RAG_URL="${PYT_RAG_URL:-http://127.0.0.1:18092}"
GRAPH_URL="${PYT_GRAPH_URL:-http://127.0.0.1:18091}"
FRONTEND_URL="${PYT_FRONTEND_URL:-http://127.0.0.1:8080}"

check() {
  local name="$1"
  local url="$2"
  printf "[demo_smoke] %s -> %s ... " "$name" "$url"
  if curl -fsS "$url" -o /dev/null; then
    echo "ok"
  else
    echo "FAIL"
    return 1
  fi
}

check "backend"  "${API_BASE}/healthz"
check "graph"    "${GRAPH_URL}/healthz"
check "rag"      "${RAG_URL}/healthz"
check "frontend" "${FRONTEND_URL}/healthz"

if curl -fsS "${API_BASE}/api/course/pack" -o /dev/null; then
  echo "[demo_smoke] course pack 43 可读"
else
  echo "[demo_smoke] course pack 43 读取失败" >&2
  exit 1
fi

echo "[demo_smoke] 演示链路冒烟通过。"
