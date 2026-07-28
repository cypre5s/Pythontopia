#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
FRONTEND_NGINX="${ROOT_DIR}/frontend/nginx.conf"
BACKEND_CONFIG="${ROOT_DIR}/backend/src/main/resources/application.yml"
INSTALLER="${ROOT_DIR}/deploy/scripts/install_release.sh"
FAILURES=0

require_pattern() {
  local file="$1"
  local description="$2"
  local pattern="$3"
  if grep -Eq "${pattern}" "${file}"; then
    echo "[PASS] ${description}"
  else
    echo "[FAIL] ${description}" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_pattern "${FRONTEND_NGINX}" \
  "前端 Nginx 从上游请求头解析外部协议" \
  'map[[:space:]]+\$http_x_forwarded_proto[[:space:]]+\$pythontopia_forwarded_proto[[:space:]]*\{'
require_pattern "${FRONTEND_NGINX}" \
  "API 代理保留外层 HTTPS 协议" \
  'proxy_set_header[[:space:]]+X-Forwarded-Proto[[:space:]]+\$pythontopia_forwarded_proto;'
require_pattern "${BACKEND_CONFIG}" \
  "Spring 使用框架级转发头处理" \
  '^[[:space:]]+forward-headers-strategy:[[:space:]]+framework[[:space:]]*$'
require_pattern "${INSTALLER}" \
  "发布终验覆盖 HTTPS 公网入口" \
  'curl.*--resolve[[:space:]]+47\.120\.29\.62:443:127\.0\.0\.1.*https://47\.120\.29\.62/healthz'

if [ "${FAILURES}" -ne 0 ]; then
  echo "[RESULT] ${FAILURES} 项 HTTPS 转发链路契约未满足" >&2
  exit 1
fi

echo "[RESULT] HTTPS 转发链路契约通过"
