#!/usr/bin/env bash
set -euo pipefail

candidate="${1:-}"
normalized="$(printf '%s' "$candidate" | tr '[:upper:]' '[:lower:]')"
byte_length="$(printf '%s' "$candidate" | wc -c)"

is_placeholder=0
case "$normalized" in
  "" | "pytopia-rag-admin" | *replace-with* | *replace_with* | *change-me* | *changeme* | *placeholder* | *your-token*)
    is_placeholder=1
    ;;
esac

if ((is_placeholder == 1 || byte_length < 32)); then
  command -v openssl >/dev/null 2>&1 || {
    printf 'openssl is required to generate a local internal token\n' >&2
    exit 1
  }
  openssl rand -hex 32
  exit 0
fi

printf '%s\n' "$candidate"
