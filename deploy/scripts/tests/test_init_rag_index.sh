#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
tmp_root="$(mktemp -d)"
trap 'rm -rf "$tmp_root"' EXIT

mkdir -p "$tmp_root/deploy/scripts" "$tmp_root/bin"
cp "$repo_root/deploy/scripts/init_rag_index.sh" "$tmp_root/deploy/scripts/init_rag_index.sh"
cat > "$tmp_root/bin/curl" <<'CURL_STUB'
#!/usr/bin/env bash
set -euo pipefail
if [[ " $* " == *" /healthz "* ]]; then
  exit 0
fi
printf '%s\n' "$*" >> "$CURL_ARGS_FILE"
cat > "$CURL_CONFIG_FILE"
printf '{"indexed":true}'
CURL_STUB
chmod +x "$tmp_root/bin/curl"

if env -u PYT_RAG_ADMIN_TOKEN \
  PATH="$tmp_root/bin:$PATH" \
  CURL_ARGS_FILE="$tmp_root/args" \
  CURL_CONFIG_FILE="$tmp_root/config" \
  bash "$tmp_root/deploy/scripts/init_rag_index.sh" >/dev/null 2>&1; then
  echo "missing token should fail" >&2
  exit 1
fi
if [[ -e "$tmp_root/args" ]]; then
  echo "curl must not run without a token" >&2
  exit 1
fi

token="test-only-rag-token-0123456789abcdef"
PATH="$tmp_root/bin:$PATH" \
  CURL_ARGS_FILE="$tmp_root/args" \
  CURL_CONFIG_FILE="$tmp_root/config" \
  PYT_RAG_ADMIN_TOKEN="$token" \
  bash "$tmp_root/deploy/scripts/init_rag_index.sh" >/dev/null

if grep -Fq "$token" "$tmp_root/args"; then
  echo "token leaked into curl argv" >&2
  exit 1
fi
grep -Fq "$token" "$tmp_root/config"
echo "init_rag_index credential checks passed"
