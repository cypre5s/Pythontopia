#!/usr/bin/env bash
set -euo pipefail

"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/agentic-m3-system-evolution-gate.sh" nightly-improve
