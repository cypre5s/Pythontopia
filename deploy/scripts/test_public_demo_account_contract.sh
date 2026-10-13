#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INSTALLER="${ROOT_DIR}/deploy/scripts/install_release.sh"
LOGIN_PAGE="${ROOT_DIR}/frontend/src/pages/LoginPage.vue"
PUBLIC_ACCOUNT_SCRIPT="${ROOT_DIR}/deploy/scripts/ensure_public_demo_account.sh"

if grep -Fq 'ensure_public_demo_account.sh' "${INSTALLER}"; then
  echo "[FAIL] production release must not create or reset a shared demo account" >&2
  exit 1
fi

if [[ -e "${PUBLIC_ACCOUNT_SCRIPT}" ]]; then
  echo "[FAIL] shared public account reset script must be removed" >&2
  exit 1
fi

if grep -Eq 'demo-stu-01|P@ssw0rd1|fillAndSubmitDemo|DEMO_USERNAME|DEMO_PASSWORD' "${LOGIN_PAGE}"; then
  echo "[FAIL] Web login page must not expose fixed shared credentials" >&2
  exit 1
fi

grep -Fq '还没有账号？创建新账号' "${LOGIN_PAGE}"
echo "[PASS] production and Web login do not expose a shared demo account"
