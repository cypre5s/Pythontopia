#!/usr/bin/env bash
set -euo pipefail

candidate="${1:-}"
backend_pid="${2:-}"
normalized="$(printf '%s' "$candidate" | tr '[:upper:]' '[:lower:]')"
byte_length="$(printf '%s' "$candidate" | wc -c)"

is_placeholder=0
case "$normalized" in
  "" | "pytopia-rag-admin" | *replace-with* | *replace_with* | *change-me* | *changeme* | *placeholder* | *your-token*)
    is_placeholder=1
    ;;
esac

if [[ -n "$backend_pid" ]]; then
  if [[ ! "$backend_pid" =~ ^[0-9]+$ || ! -r "/proc/$backend_pid/environ" ]]; then
    printf 'cannot read the running backend environment; stop it before starting dev services\n' >&2
    exit 1
  fi
  candidate="$(tr '\0' '\n' < "/proc/$backend_pid/environ" | sed -n 's/^PYT_RAG_ADMIN_TOKEN=//p' | head -n 1)"
  normalized="$(printf '%s' "$candidate" | tr '[:upper:]' '[:lower:]')"
  byte_length="$(printf '%s' "$candidate" | wc -c)"
  case "$normalized" in
    "" | "pytopia-rag-admin" | *replace-with* | *replace_with* | *change-me* | *changeme* | *placeholder* | *your-token*)
      is_placeholder=1
      ;;
    *) is_placeholder=0 ;;
  esac
  if ((is_placeholder == 1 || byte_length < 32)); then
    printf 'running backend has no reusable strong internal token; stop it before starting dev services\n' >&2
    exit 1
  fi
  printf '%s\n' "$candidate"
  exit 0
fi

if ((is_placeholder == 1 || byte_length < 32)); then
  command -v openssl >/dev/null 2>&1 || {
    printf 'openssl is required to generate a local internal token\n' >&2
    exit 1
  }
  openssl rand -hex 32
  exit 0
fi

printf '%s\n' "$candidate"
