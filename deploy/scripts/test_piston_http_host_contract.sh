#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPTS=(
  "${ROOT_DIR}/deploy/scripts/preflight_ecs.sh"
  "${ROOT_DIR}/deploy/scripts/install_release.sh"
)
source "${ROOT_DIR}/deploy/scripts/piston_url_contract.sh"

expect_allowed() {
  local url="$1"
  is_private_piston_http_url "$url" || {
    echo "[FAIL] expected private Piston HTTP URL to pass: ${url}" >&2
    exit 1
  }
}

expect_rejected() {
  local url="$1"
  if is_private_piston_http_url "$url"; then
    echo "[FAIL] expected Piston HTTP URL to be rejected: ${url}" >&2
    exit 1
  fi
}

for script in "${SCRIPTS[@]}"; do
  grep -Fq 'source "${SCRIPT_DIR}/piston_url_contract.sh"' "${script}" || {
    echo "[FAIL] $(basename "${script}") must load the shared Piston URL contract" >&2
    exit 1
  }
  grep -Fq 'is_private_piston_http_url "${piston_url}"' "${script}" || {
    echo "[FAIL] $(basename "${script}") must use the shared Piston URL contract" >&2
    exit 1
  }
  echo "[PASS] $(basename "${script}") loads the shared Piston URL contract"
done

expect_allowed 'http://piston:2000/api/v2/execute'
expect_allowed 'http://127.0.0.1:2000'
expect_allowed 'http://10.2.3.4:2000'
expect_allowed 'http://172.31.255.254:2000'
expect_allowed 'http://192.168.1.20:2000'
expect_allowed 'http://169.254.10.20:2000'
expect_allowed 'http://[::1]:2000'
expect_allowed 'http://[fd12:3456::1]:2000'
expect_allowed 'http://[fe80::1]:2000'
expect_rejected 'http://piston-attacker.example.com:2000'
expect_rejected 'http://piston-evil:2000'
expect_rejected 'http://piston.example.com:2000'
expect_rejected 'http://localhost:2000'
expect_rejected 'http://attacker.internal:2000'
expect_rejected 'http://8.8.8.8:2000'
expect_rejected 'http://172.32.0.1:2000'
expect_rejected 'http://10.not-an-ip:2000'
expect_rejected 'http://[2001:db8::1]:2000'
expect_rejected 'http://[fe80::1%25eth0]:2000'
expect_rejected 'http://piston:bad-port'
echo '[PASS] private and local-literal Piston HTTP hosts are classified correctly'
