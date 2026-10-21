#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
INSTALLER="${ROOT_DIR}/deploy/scripts/install_release.sh"
PREFLIGHT="${ROOT_DIR}/deploy/scripts/preflight_ecs.sh"
COMPOSE_FILE="${ROOT_DIR}/deploy/docker-compose.production.yml"
FAILURES=0

require_fixed() {
  local file="$1"
  local description="$2"
  local expected="$3"
  if grep -Fq "${expected}" "${file}"; then
    echo "[PASS] ${description}"
  else
    echo "[FAIL] ${description}" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_fixed "${INSTALLER}" \
  "安装前校验独立路由执行密钥强度" \
  "check_env_secret PYT_ROUTE_EXECUTION_SECRET 32"
require_fixed "${COMPOSE_FILE}" \
  "生产 Compose 对路由执行密钥 fail fast" \
  'PYT_ROUTE_EXECUTION_SECRET: ${PYT_ROUTE_EXECUTION_SECRET:?缺少 PYT_ROUTE_EXECUTION_SECRET}'
require_fixed "${INSTALLER}" \
  "安装前校验 AI 密钥加密主密钥强度" \
  "check_env_secret PYT_ADMIN_ENCRYPTION_KEY 32"
require_fixed "${PREFLIGHT}" \
  "ECS 预检检查 AI 密钥加密主密钥强度" \
  "check_secret_strength PYT_ADMIN_ENCRYPTION_KEY 32 fail"
require_fixed "${COMPOSE_FILE}" \
  "生产 Compose 对 AI 密钥加密主密钥 fail fast" \
  'PYT_ADMIN_ENCRYPTION_KEY: ${PYT_ADMIN_ENCRYPTION_KEY:?缺少 PYT_ADMIN_ENCRYPTION_KEY}'

if [ "${FAILURES}" -ne 0 ]; then
  echo "[RESULT] ${FAILURES} 项发布密钥契约未满足" >&2
  exit 1
fi

echo "[RESULT] 发布密钥契约通过"
