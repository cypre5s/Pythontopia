#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f deploy/.env ]]; then
  echo 'Copy deploy/.env.example to deploy/.env and set independent random secrets first.' >&2
  exit 1
fi
exec docker compose --env-file deploy/.env -f deploy/compose.yml up --build -d "$@"
