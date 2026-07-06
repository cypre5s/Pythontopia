# E1 Protocol Patch V1 on V2 Domain Set: 2026-07-02

## Question

Can a paper-inspired protocol layer improve local Qwen3.5 Agent behavior before LoRA fine-tuning?

## Intervention

`protocol_patch_v1` appends a strict Agent output contract to the system prompt:

- return exactly one valid JSON object,
- no Markdown fences or prose wrappers,
- preserve the requested Agent fields,
- include all required evidence IDs when evidence is required,
- use `evidence_ids: []` and `grounding_note: "common_knowledge"` when no page evidence is required,
- keep tutoring responses hint-oriented and avoid directly submittable answers.

This is a prompt/protocol candidate, not a weight update.

## Data

- Dataset: `cases/domain_cases_140_v2.jsonl`.
- Cases: 140.
- Folds: 5.
- Fold seed: `20260702`.
- Difference from v1: no-evidence cases no longer ask the user to cite a `page-*` ID.

## Runs

| Run | Path |
|---|---|
| baseline-v2 | `runs/baseline-v2-20260702T091400Z` |
| E1 protocol patch v1 v2 | `runs/e1-protocol-patch-v1-v2-20260702T093000Z` |
| comparison | `outputs/comparisons/e1-protocol-patch-v1-v2-vs-baseline-v2` |

## Overall Results

| Metric | Baseline V2 | E1 V2 | Delta |
|---|---:|---:|---:|
| score mean | 0.975714 | 1.000000 | +0.024286 |
| JSON valid rate | 0.600000 | 1.000000 | +0.400000 |
| grounding safe rate | 0.642857 | 1.000000 | +0.357143 |
| forbidden leak rate | 0.000000 | 0.000000 | +0.000000 |

Story gate: passed.

## Response Audit

| Audit Metric | Baseline V2 | E1 V2 |
|---|---:|---:|
| clean count | 80 / 140 | 140 / 140 |
| clean rate | 0.571429 | 1.000000 |
| invalid JSON flags | 56 | 0 |
| Markdown fence flags | 56 | 0 |
| missing evidence ID flags | 8 | 0 |
| page mention without evidence contract flags | 0 | 0 |

The v2 audit removes the v1 ambiguity where no-evidence cases still asked for page citations.

## Agent Breakdown

Baseline V2:

| Agent | Cases | Mean | JSON | Grounding | Leak |
|---|---:|---:|---:|---:|---:|
| DebugAgent | 14 | 0.914 | 1.000 | 0.714 | 0.000 |
| GroundingCriticAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| GuideCoachAgent | 14 | 1.000 | 0.000 | 1.000 | 0.000 |
| IntentRouterAgent | 14 | 0.943 | 1.000 | 0.000 | 0.000 |
| MindMapAgent | 14 | 0.929 | 0.000 | 0.714 | 0.000 |
| PathPlannerAgent | 14 | 1.000 | 0.000 | 0.000 | 0.000 |
| PracticeAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| ResourceDesignerAgent | 14 | 0.971 | 0.000 | 1.000 | 0.000 |
| SafetyAgent | 14 | 1.000 | 1.000 | 0.000 | 0.000 |
| TutorAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |

E1 V2:

| Agent | Cases | Mean | JSON | Grounding | Leak |
|---|---:|---:|---:|---:|---:|
| DebugAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| GroundingCriticAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| GuideCoachAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| IntentRouterAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| MindMapAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| PathPlannerAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| PracticeAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| ResourceDesignerAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| SafetyAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |
| TutorAgent | 14 | 1.000 | 1.000 | 1.000 | 0.000 |

## Interpretation

E1 gives a real measured uplift on a cleaned local domain set, but the result should be framed as protocol control, not model fine-tuning. The model already knows much of the content; the failure was mostly Agent contract discipline: strict JSON, evidence IDs, no Markdown wrappers, and explicit common-knowledge marking.

This is enough to start a story draft:

- local Qwen3.5-4B-AWQ can run under vLLM on the laptop;
- raw model behavior is content-strong but protocol-fragile;
- an eval-gated protocol module converts fragile Agent outputs into clean auditable artifacts;
- next, the protocol module should be distilled into LoRA/SFT data so the behavior becomes part of the model adapter rather than only prompt scaffolding.

## Remaining Risk

- The 140-case set is generated, not manually reviewed.
- E1 may be partially optimized to the local scoring contract.
- No LoRA has been trained yet.
- A held-out hand-reviewed set and at least one external tutoring set slice are still needed before final competition claims.
