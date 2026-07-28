#!/usr/bin/env bash
set -euo pipefail

CURRENT_CONFIG="${1:-}"
TARGET_CONFIG="${2:-}"
VERIFIER="${3:-}"

if [ ! -x "${VERIFIER}" ]; then
  echo "[FAIL] 双协议 Nginx 校验器不可执行：${VERIFIER}" >&2
  exit 1
fi

if "${VERIFIER}" "${TARGET_CONFIG}" >&2; then
  echo "${TARGET_CONFIG}"
  exit 0
fi

echo "[WARN] 目标 release 的 Nginx 配置不满足当前 HTTP + HTTPS 契约，保留主机现有入口。" >&2
if "${VERIFIER}" "${CURRENT_CONFIG}" >&2; then
  echo "${CURRENT_CONFIG}"
  exit 0
fi

echo "[FAIL] 目标 release 与主机现有 Nginx 配置均不满足 HTTP + HTTPS 契约。" >&2
exit 1
