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

require_http_redirect_block() {
  local http_block
  http_block="$(awk '
    /^server[[:space:]]*\{/ { server++ }
    server == 1 { print }
    server == 1 && /^}/ { exit }
  ' "${CONFIG_PATH}")"
  if ! grep -Eq '^[[:space:]]*listen[[:space:]]+80[[:space:]]+default_server;' <<<"${http_block}" \
      || ! grep -Eq '^[[:space:]]*listen[[:space:]]+\[::\]:80[[:space:]]+default_server;' <<<"${http_block}" \
      || ! grep -Eq 'location[[:space:]]+\^~[[:space:]]+/\.well-known/acme-challenge/' <<<"${http_block}" \
      || ! grep -Eq 'root[[:space:]]+/var/www/certbot;' <<<"${http_block}" \
      || ! grep -Eq 'return[[:space:]]+308[[:space:]]+https://47\.120\.29\.62\$request_uri;' <<<"${http_block}"; then
    echo "[FAIL] HTTP 入口必须保留 ACME，并将其余请求固定跳转到 HTTPS" >&2
    FAILURES=$((FAILURES + 1))
    return
  fi
  if grep -Eq 'proxy_pass[[:space:]]' <<<"${http_block}"; then
    echo "[FAIL] HTTP 入口不得代理应用/API 请求" >&2
    FAILURES=$((FAILURES + 1))
  else
    echo "[PASS] HTTP 入口保留 ACME，其余请求固定跳转 HTTPS 且不代理应用"
  fi
}

require_classroom_join_limit_block() {
  local join_block
  join_block="$(awk '
    /location[[:space:]]+=[[:space:]]+\/api\/classrooms\/join[[:space:]]*\{/ { capture=1 }
    capture { print }
    capture && /^[[:space:]]+}/ { exit }
  ' "${CONFIG_PATH}")"
  if grep -Eq 'limit_req[[:space:]]+zone=pythontopia_classroom_join[[:space:]]+burst=5[[:space:]]+nodelay;' <<<"${join_block}" \
      && grep -Eq 'limit_req_status[[:space:]]+429;' <<<"${join_block}"; then
    echo "[PASS] 班级加入入口超限返回 429"
  else
    echo "[FAIL] 班级加入入口超限返回 429" >&2
    FAILURES=$((FAILURES + 1))
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
require_pattern "班级加入码按来源 IP 限速" \
  'limit_req_zone[[:space:]]+\$binary_remote_addr[[:space:]]+zone=pythontopia_classroom_join:10m[[:space:]]+rate=5r/m;'
require_classroom_join_limit_block
require_pattern "HTTP 使用永久重定向" \
  'return[[:space:]]+308[[:space:]]+https://47\.120\.29\.62\$request_uri;'
require_http_redirect_block

if [ "${FAILURES}" -ne 0 ]; then
  echo "[RESULT] ${FAILURES} 项失败" >&2
  exit 1
fi

echo "[RESULT] HTTP→HTTPS 安全入口配置契约通过"
