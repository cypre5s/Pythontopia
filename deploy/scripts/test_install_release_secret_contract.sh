#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
INSTALLER="${ROOT_DIR}/deploy/scripts/install_release.sh"
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

if [ "${FAILURES}" -ne 0 ]; then
  echo "[RESULT] ${FAILURES} 项发布密钥契约未满足" >&2
  exit 1
fi

echo "[RESULT] 发布密钥契约通过"
