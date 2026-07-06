# E1 Protocol Patch V1 on 1000-Case Domain Set: 2026-07-02

## Question

Does the paper-inspired protocol module still improve local Qwen3.5 Agent behavior when the evaluation set expands from 140 to about 1000 cases?

## Data

- Case file: `cases/domain_cases_1000_v1.jsonl`.
- Case count: 1000.
- Agent coverage: 10 Agents x 100 cases each.
- Chapter coverage: chapters 1-7, with 142 or 143 cases per chapter.
- Prompt uniqueness: 1000 unique user prompts.
- No-evidence cases: 300.
- Forbidden-answer-risk cases: 66.
- Data profile: `outputs/datasets/domain_cases_1000_v1_profile.json`.

The cases add learner profiles, learning signals, classroom scenes, misconceptions, and task-focus variants so the 1000 rows are not just repeated copies of the 140-row set.

## Method

- Serving model: `models/QuantTrio-Qwen3.5-4B-AWQ`.
- Served name: `pythontopia-qwen35-baseline`.
- Runtime: vLLM `0.17.1`, max model length 1024, `MAX_NUM_SEQS=4`, `GPU_MEMORY_UTILIZATION=0.82`.
- Evaluation: 5 folds, seed `20260702`, 200 validation cases per fold.
- Runner: `scripts/run_baseline.py --concurrency 4`.

## Runs

| Run | Path |
|---|---|
| Baseline | `runs/baseline-1000-v1-conc4-20260702T100600Z` |
| E1 protocol patch | `runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z` |
| Comparison | `outputs/comparisons/e1-protocol-patch-v1-1000-v1-vs-baseline-1000-v1` |

## Results

| Metric | Baseline | E1 | Delta |
|---|---:|---:|---:|
| Mean score | 0.962467 | 0.997050 | +0.034583 |
| JSON valid rate | 0.686000 | 0.980000 | +0.294000 |
| Grounding safe rate | 0.822000 | 0.963000 | +0.141000 |
| Forbidden leak rate | 0.014000 | 0.007000 | -0.007000 |

Audit:

| Audit Metric | Baseline | E1 |
|---|---:|---:|
| Clean count | 612 / 1000 | 975 / 1000 |
| Clean rate | 0.612000 | 0.975000 |
| Invalid JSON flags | 314 | 20 |
| Markdown fence flags | 314 | 0 |
| Missing evidence flags | 112 | 8 |

Charts:

- Baseline fold charts: `runs/baseline-1000-v1-conc4-20260702T100600Z/charts/`.
- E1 fold charts: `runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z/charts/`.
- Baseline-vs-E1 chart: `outputs/comparisons/e1-protocol-patch-v1-1000-v1-vs-baseline-1000-v1/charts/baseline_vs_candidate.svg`.

## Interpretation

E1 produces a real improvement on the 1000-case set. The strongest gain is structural reliability: JSON-valid output rises by +29.4 points, audit cleanliness rises by +36.3 points, and forbidden leakage falls by 0.7 points.

The strict story gate currently remains closed because the pre-set grounding-delta threshold is +0.20 and this run reaches +0.141. That means the safe story is not "grounding is solved." The safe story is: a compact protocol layer makes the local Agent brain much more governable, while grounding still needs a second module.

## Follow-Up

- Distill the 975 clean E1 responses into SFT data.
- Train a QLoRA adapter on original prompts without the protocol patch text.
- Evaluate the adapter as a generation model before claiming weight-level improvement.
