#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-all}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRAPH_DIR="$ROOT_DIR/services/pythontopia-graph"
DATASET="$GRAPH_DIR/pythontopia_graph/eval/golden/agentic_m3_system_evolution.jsonl"
REPORT="$ROOT_DIR/docs/reports/2026-06-30-agentic-m3-system-evolution-gate.md"

run_gate() {
  local label="$1"
  echo "[agentic-m3] $label"
  (
    cd "$GRAPH_DIR"
    python3 -m pythontopia_graph.eval.m3_system_evolution_gate "$DATASET" --markdown "$REPORT"
  )
}

case "$MODE" in
  offline-eval)
    run_gate "offline agent eval"
    ;;
  nightly-improve)
    run_gate "nightly improve loop dry-run"
    ;;
  canary-dry-run)
    run_gate "canary rollout dry-run"
    ;;
  all)
    run_gate "offline agent eval"
    run_gate "nightly improve loop dry-run"
    run_gate "canary rollout dry-run"
    ;;
  *)
    echo "Usage: $0 [offline-eval|nightly-improve|canary-dry-run|all]" >&2
    exit 2
    ;;
esac
