#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESOLVER="$SCRIPT_DIR/resolve_dev_jwt_secret.sh"

assert_generated_secret() {
  local input="$1" resolved
  resolved="$("$RESOLVER" "$input")"
  [[ "$resolved" =~ ^[0-9a-f]{64}$ ]]
  [[ "$resolved" != "$input" ]]
}

assert_generated_secret ""
assert_generated_secret "replace-with-local-secret-at-least-32-chars"
assert_generated_secret "short-secret"

strong_secret="local-only-strong-secret-0123456789abcdef"
resolved="$("$RESOLVER" "$strong_secret")"
[[ "$resolved" == "$strong_secret" ]]

printf 'resolve_dev_jwt_secret: PASS\n'
