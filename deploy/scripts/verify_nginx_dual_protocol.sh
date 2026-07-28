#!/usr/bin/env bash
set -euo pipefail

CONFIG_PATH="${1:-deploy/nginx/pythontopia.conf}"
FAILURES=0

if [ ! -f "${CONFIG_PATH}" ]; then
  echo "[FAIL] Nginx 配置不存在：${CONFIG_PATH}" >&2
  exit 1
fi

require_pattern() {
  local description="$1"
  local pattern="$2"
  if grep -Eq "${pattern}" "${CONFIG_PATH}"; then
    echo "[PASS] ${description}"
  else
    echo "[FAIL] ${description}" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

forbid_pattern() {
  local description="$1"
  local pattern="$2"
  if grep -Eq "${pattern}" "${CONFIG_PATH}"; then
    echo "[FAIL] ${description}" >&2
    FAILURES=$((FAILURES + 1))
  else
    echo "[PASS] ${description}"
  fi
}

require_pattern "IPv4 HTTP 监听 80" \
  '^[[:space:]]*listen[[:space:]]+80[[:space:]]+default_server;'
require_pattern "IPv6 HTTP 监听 80" \
  '^[[:space:]]*listen[[:space:]]+\[::\]:80[[:space:]]+default_server;'
require_pattern "IPv4 HTTPS 监听 443" \
  '^[[:space:]]*listen[[:space:]]+443[[:space:]]+ssl[[:space:]]+default_server;'
require_pattern "IPv6 HTTPS 监听 443" \
  '^[[:space:]]*listen[[:space:]]+\[::\]:443[[:space:]]+ssl[[:space:]]+default_server;'
require_pattern "使用目标 IP 的 Let’s Encrypt 完整证书链" \
  '^[[:space:]]*ssl_certificate[[:space:]]+/etc/letsencrypt/live/47\.120\.29\.62/fullchain\.pem;'
require_pattern "使用目标 IP 的 Let’s Encrypt 私钥" \
  '^[[:space:]]*ssl_certificate_key[[:space:]]+/etc/letsencrypt/live/47\.120\.29\.62/privkey\.pem;'
require_pattern "仅启用 TLS 1.2 与 TLS 1.3" \
  '^[[:space:]]*ssl_protocols[[:space:]]+TLSv1\.2[[:space:]]+TLSv1\.3;'
require_pattern "保留 ACME HTTP-01 路由" \
  'location[[:space:]]+\^~[[:space:]]+/\.well-known/acme-challenge/'
require_pattern "ACME 使用持久 webroot" \
  '^[[:space:]]*root[[:space:]]+/var/www/certbot;'
forbid_pattern "HTTP 不强制跳转 HTTPS" \
  'return[[:space:]]+30(1|2|7|8)[[:space:]]+https://'
forbid_pattern "双协议模式不启用 HSTS" \
  'Strict-Transport-Security'

if [ "${FAILURES}" -ne 0 ]; then
  echo "[RESULT] ${FAILURES} 项失败" >&2
  exit 1
fi

echo "[RESULT] HTTP + HTTPS Nginx 配置契约通过"
