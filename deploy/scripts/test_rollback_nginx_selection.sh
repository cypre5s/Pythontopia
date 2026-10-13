#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SELECTOR="${SCRIPT_DIR}/select_rollback_nginx_config.sh"
VERIFIER="${SCRIPT_DIR}/verify_nginx_dual_protocol.sh"
FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "${FIXTURE_DIR}"' EXIT

CURRENT_DUAL="${FIXTURE_DIR}/current-dual.conf"
TARGET_DUAL="${FIXTURE_DIR}/target-dual.conf"
TARGET_INSECURE="${FIXTURE_DIR}/target-insecure.conf"
LEGACY_HTTP="${FIXTURE_DIR}/legacy-http.conf"

cp "${SCRIPT_DIR}/../nginx/pythontopia.conf" "${CURRENT_DUAL}"
cp "${SCRIPT_DIR}/../nginx/pythontopia.conf" "${TARGET_DUAL}"
sed '/return 308 https:\/\/47\.120\.29\.62\$request_uri;/d' "${TARGET_DUAL}" >"${TARGET_INSECURE}"
sed \
  -e '/443 ssl default_server;/d' \
  -e '/ssl_certificate /d' \
  -e '/ssl_certificate_key /d' \
  -e '/ssl_protocols /d' \
  "${CURRENT_DUAL}" >"${LEGACY_HTTP}"

selected="$("${SELECTOR}" "${CURRENT_DUAL}" "${LEGACY_HTTP}" "${VERIFIER}")"
if [ "${selected}" != "${CURRENT_DUAL}" ]; then
  echo "[FAIL] 旧版 HTTP-only 配置应保留当前安全入口。" >&2
  exit 1
fi

selected="$("${SELECTOR}" "${LEGACY_HTTP}" "${TARGET_DUAL}" "${VERIFIER}")"
if [ "${selected}" != "${TARGET_DUAL}" ]; then
  echo "[FAIL] 目标版本满足 HTTP→HTTPS 契约时应使用目标配置。" >&2
  exit 1
fi

selected="$("${SELECTOR}" "${CURRENT_DUAL}" "${TARGET_INSECURE}" "${VERIFIER}")"
if [ "${selected}" != "${CURRENT_DUAL}" ]; then
  echo "[FAIL] 目标版本未强制 HTTPS 时应保留当前安全入口。" >&2
  exit 1
fi

if "${SELECTOR}" "${LEGACY_HTTP}" "${LEGACY_HTTP}" "${VERIFIER}" >/dev/null 2>&1; then
  echo "[FAIL] 当前与目标均不满足 HTTP→HTTPS 契约时必须拒绝回滚入口切换。" >&2
  exit 1
fi

echo "[PASS] 回滚 Nginx 配置选择保留 HTTP→HTTPS 安全入口"
