#!/usr/bin/env bash
# Pythontopia 本地一键启动：Postgres + Redis + Backend + Graph + RAG + Frontend
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$ROOT_DIR/deploy"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:8080}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] missing command: $1" >&2
    exit 1
  fi
}

wait_http_ok() {
  local url="$1"
  local retries="${2:-90}"
  local interval="${3:-2}"
  for _ in $(seq 1 "$retries"); do
    local code
    code=$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)
    if [[ "$code" == "200" ]]; then
      return 0
    fi
    sleep "$interval"
  done
  return 1
}

require_cmd docker
require_cmd curl

if [[ ! -f "$DEPLOY_DIR/.env" ]]; then
  if [[ -f "$DEPLOY_DIR/.env.example" ]]; then
    cp "$DEPLOY_DIR/.env.example" "$DEPLOY_DIR/.env"
    echo "[INFO] created $DEPLOY_DIR/.env from .env.example — edit LLM keys if needed"
  else
    echo "[ERROR] missing $DEPLOY_DIR/.env" >&2
    exit 1
  fi
fi

cd "$DEPLOY_DIR"

BUILD_FLAG="${PYT_START_BUILD:-0}"
if [[ "${1:-}" == "--build" ]]; then
  BUILD_FLAG=1
fi

if [[ "$BUILD_FLAG" == "1" ]]; then
  PROXY="${PYT_BUILD_PROXY:-http://127.0.0.1:7892}"
  if curl -sf --connect-timeout 3 -x "$PROXY" https://registry.npmmirror.com/ -o /dev/null 2>/dev/null; then
    echo "[INFO] rebuild with proxy ($PROXY) via deploy/scripts/build-with-proxy.sh ..."
    bash scripts/build-with-proxy.sh
  else
    echo "[WARN] proxy $PROXY unavailable — skip rebuild, starting existing images"
    echo "[WARN] to rebuild: start Clash/7892 then run: PYT_START_BUILD=1 ./start.sh"
    docker compose up -d
  fi
else
  echo "[INFO] starting Pythontopia stack (docker compose up -d, no rebuild)..."
  docker compose up -d
fi

echo "[INFO] waiting for core health endpoints..."
wait_http_ok "http://127.0.0.1:18080/healthz" 120 2 || {
  echo "[ERROR] backend not healthy on :18080" >&2
  docker compose ps >&2
  docker compose logs backend --tail 80 >&2 || true
  exit 1
}
wait_http_ok "http://127.0.0.1:18091/healthz" 120 2 || {
  echo "[ERROR] pythontopia-graph not healthy on :18091" >&2
  docker compose logs pythontopia-graph --tail 80 >&2 || true
  exit 1
}
wait_http_ok "http://127.0.0.1:18092/healthz" 120 2 || {
  echo "[ERROR] pythontopia-rag not healthy on :18092" >&2
  docker compose logs pythontopia-rag --tail 80 >&2 || true
  exit 1
}
wait_http_ok "http://127.0.0.1:8080/healthz" 90 2 || {
  echo "[WARN] frontend /healthz not 200 yet — nginx may still be starting"
}

echo ""
echo "[OK] Pythontopia is up"
echo "  Frontend:  $FRONTEND_URL"
echo "  Backend:   http://127.0.0.1:18080/healthz"
echo "  Graph:     http://127.0.0.1:18091/healthz"
echo "  RAG:       http://127.0.0.1:18092/healthz"
echo "  Postgres:  127.0.0.1:15432"
echo "  Redis:     127.0.0.1:16379"
echo ""
echo "Optional demo setup:"
echo "  cd deploy && bash scripts/init_rag_index.sh"
echo "  cd deploy && bash scripts/seed_demo_classroom.sh"
echo "  cd deploy && bash scripts/demo_smoke.sh"
