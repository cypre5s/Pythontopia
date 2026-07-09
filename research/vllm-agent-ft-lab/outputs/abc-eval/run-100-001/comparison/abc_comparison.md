# A / B / C Workshop Task Evaluation Report

## Groups

| Group | Description |
|---|---|
| A | External LLM |
| B | Qwen LoRA |
| C | Qwen LoRA + Guard |

## Overall Metrics

| Metric | A (External) | B (LoRA) | C (LoRA+Guard) |
|---|---:|---:|---:|
| score_mean | 0.8560 | 0.9768 | 0.9593 |
| json_valid_rate | 0.4200 | 1.0000 | 1.0000 |
| grounding_safe_rate | 0.5200 | 0.6200 | 1.0000 |
| forbidden_leak_rate | 0.0700 | 0.0000 | 0.0000 |

## Deltas vs External LLM (A)

| Metric | B vs A | C vs A |
|---|---:|---:|
| score_mean | +0.1208 | +0.1033 |
| json_valid_rate | +0.5800 | +0.5800 |
| grounding_safe_rate | +0.1000 | +0.4800 |
| forbidden_leak_rate | -0.0700 | -0.0700 |

## Guard Effect (B → C)

| Metric | Delta |
|---|---:|
| score_mean | -0.0175 |
| json_valid_rate | +0.0000 |
| grounding_safe_rate | +0.3800 |
| forbidden_leak_rate | +0.0000 |

## A/B Test: C (Qwen LoRA + Guard) vs A (External LLM)

- **Verdict**: `candidate_wins`

| Metric | Delta |
|---|---:|
| score_mean | +0.1033 |
| json_valid_rate | +0.5800 |
| grounding_safe_rate | +0.4800 |
| forbidden_leak_rate | -0.0700 |

## Charts

- `charts/metrics_3way_bars.svg` — A vs B vs C grouped comparison
- `charts/deltas_vs_external.svg` — B and C deltas vs external LLM baseline
- `charts/guard_effect.svg` — guard + fallback improvement (B → C)
- `charts/per_agent_heatmap.svg` — per-agent score breakdown
