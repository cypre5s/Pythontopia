# Two-Day Optimization A/B Evaluation Hardening Design

## Goal

Give every optimization shipped on 2026-06-23 and 2026-06-24 a deterministic A/B gate. The modified path must be measurably and stably better than the baseline, not merely functionally correct in one happy-path test.

## Scope

Covered optimizations:

1. KC graph retrieval expansion vs strict KC/vector baseline.
2. Learning Evidence Graph vs resource-only learning loop baseline.
3. TutorBench-lite trajectory evaluation vs naive summary baseline.
4. Evidence personalization and Evidence Memory 2.0 vs raw recent-event memory.
5. Recommendation reranking with evidence memory vs mastery/raw-memory fallback.
6. AG-UI runtime events vs legacy `trace/token/result` SSE stream.
7. Eval-gated prompt optimization vs average-only prompt promotion.

## A/B Rules

Each A/B test must define:

- Baseline: the old or naive behavior, explicitly named in test code.
- Treatment: the modified implementation path.
- Metric: a numeric score, pass rate, recall/coverage value, or ranking improvement.
- Stability gate: treatment must win across multiple samples or scenarios. A single large win is not enough if other samples regress.
- Regression gate: any candidate with excessive regression rate or severe regression is rejected even when mean uplift is positive.

## Architecture

Keep A/B gates close to the subsystem they protect:

- RAG Python tests measure retrieval recall, irrelevant-hit control, and graph trace coverage.
- Graph Python optimizer tests measure mean uplift, regression rate, severe regression count, and sample win rate.
- Backend Java tests measure graph completeness, memory retention, recommendation rank, and TutorBench pass-rate uplift with Testcontainers data.
- Changelog records aggregate numbers and remaining risks.

The prompt optimizer needs a small production change: add a `min_sample_win_rate` policy gate and persist `sample_win_rate` in candidate summaries/artifacts. This directly implements the user's "stable better than baseline" requirement for automatic optimization.

## Acceptance

- A/B tests cover every listed optimization.
- The optimizer rejects a candidate that has positive mean uplift but wins too few samples.
- Existing full subsystem tests still pass.
- `CHANGELOG.md` records the new A/B gate and quantitative evidence.
