#!/usr/bin/env bash
set -euo pipefail

candidate="${1:-}"
normalized="$(printf '%s' "$candidate" | tr '[:upper:]' '[:lower:]')"
byte_length="$(printf '%s' "$candidate" | wc -c)"

is_placeholder=0
case "$normalized" in
  "" | \
  "replace-with-local-secret-at-least-32-chars" | \
  "replace-with-32-char-secret-please" | \
  "pytopia-teacher" | \
  "pytopia-rag-admin" | \
  "pythontopia-route-secret" | \
  *replace-with* | \
  *replace_with* | \
  *change-me* | \
  *changeme* | \
  *placeholder* | \
  *your-secret*)
    is_placeholder=1
    ;;
esac

if ((is_placeholder == 1 || byte_length < 32)); then
  command -v openssl >/dev/null 2>&1 || {
    printf 'openssl is required to generate a local JWT secret\n' >&2
    exit 1
  }
  openssl rand -hex 32
  exit 0
fi

printf '%s\n' "$candidate"
