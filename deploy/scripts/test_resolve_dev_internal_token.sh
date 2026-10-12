#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESOLVER="$SCRIPT_DIR/resolve_dev_internal_token.sh"

assert_generated_token() {
  local input="$1" resolved
  resolved="$("$RESOLVER" "$input")"
  [[ "$resolved" =~ ^[0-9a-f]{64}$ ]]
  [[ "$resolved" != "$input" ]]
}

assert_generated_token ""
assert_generated_token "pytopia-rag-admin"
assert_generated_token "replace-with-a-local-internal-token-value"
assert_generated_token "short-token"

strong_token="local-only-strong-internal-token-0123456789"
resolved="$("$RESOLVER" "$strong_token")"
[[ "$resolved" == "$strong_token" ]]

printf 'resolve_dev_internal_token: PASS\n'
