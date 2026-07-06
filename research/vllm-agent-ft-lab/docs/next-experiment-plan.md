# Next Experiment Plan

## Goal

Move from local baseline to a measurable improvement experiment without touching Pythontopia web frontend/backend.

## Experiment E1: Prompt/Protocol Patch

- Status: completed on both the cleaned 140-case set and the 1000-case set.
- Current main result: `runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z`.
- Compared with `runs/baseline-1000-v1-conc4-20260702T100600Z`, E1 improved score by +0.034583, JSON by +0.294000, grounding by +0.141000, and reduced forbidden leak by -0.007000.
- Purpose: estimate how much can be gained without training.

## Experiment E2: Domain Evaluation Expansion

- Status: expanded to about 1000 generated domain cases.
- Current generated set: `cases/domain_cases_1000_v1.jsonl`.
- Next, manually review and refine a smaller benchmark slice from the 1000 cases for final competition claims.
- Cover TutorAgent, PracticeAgent, ResourceDesignerAgent, IntentRouterAgent, GuideCoachAgent, DebugAgent, GroundingCriticAgent, SafetyAgent, PathPlannerAgent, MindMapAgent.
- Add chapter/KC coverage for pack 43.
- Add adversarial rows for forbidden full-answer requests and fake evidence IDs.

## Experiment E3: Data Import

- Normalize large open tutoring data into `outputs/datasets/normalized/*.jsonl`.
- Keep source and license/terms notes per row.
- Use open data for training candidates, not for final Pythontopia claims.

## Experiment E4: LoRA Candidate

- Status: 1000-scale QLoRA training completed.
- Adapter: `outputs/adapters/e1-protocol-patch-v1-1000-v1-qlora-2epoch`.
- Training data: 780 train / 195 heldout clean E1 responses from the 1000-case run.
- Heldout token metrics improved from epoch 1 to epoch 2: eval loss 0.290419 -> 0.241026, token accuracy 0.907434 -> 0.917043.
- Train a small LoRA on:
  - structured JSON output,
  - evidence ID discipline,
  - hint-not-answer behavior,
  - transition-graph tutoring moves.
- Prioritize examples that repair the observed 140-case baseline gaps: Markdown-to-JSON conversion, explicit `common_knowledge` marking, and DebugAgent stepwise diagnosis without leaking full answers.
- Use clean E1 1000-case responses as seed SFT targets, but hold out a manually reviewed set so the adapter is not only learning the local scoring contract.
- Serve via vLLM LoRA if supported for this architecture; otherwise evaluate merged or PEFT-loaded model offline.

## Experiment E5: Adapter Gain Test

- Status: offline heldout PEFT evaluation completed.
- Adapter run: `runs/adapter-offline-heldout-195-20260702T142200Z`.
- Compared with the raw baseline on the same 195 heldout source cases, adapter deltas are score +0.020940, JSON +0.194872, grounding +0.153846, forbidden leak -0.015385.
- Compared with the E1 prompt on the same heldout source cases, adapter is lower on score, JSON, and grounding, but has lower forbidden leak.
- Produce:
  - aggregate table,
  - fold charts,
  - per-agent deltas,
  - failure taxonomy.
- Next: validate vLLM LoRA serving if compatible with Qwen3.5 and the local adapter.
- Only claim adapter replacement for E1 after adapter generation matches or beats the E1 prompt.

## Experiment E6: Grounding Module

- E1 improved grounding by +0.141 on 1000 cases, below the strict +0.20 gate.
- Add a second-stage grounding repair or verifier pass focused on the 8 missing-evidence cases and remaining no-evidence/common-knowledge failures.
- Measure whether grounding can pass the strict gate without increasing forbidden leak.

Current story gate implementation:

- `scripts/compare_experiments.py`
- Current 1000-case baseline: `runs/baseline-1000-v1-conc4-20260702T100600Z`.
- Current 1000-case candidate: `runs/e1-protocol-patch-v1-1000-v1-conc4-20260702T103600Z`.
- Requires JSON valid rate delta >= +0.25, grounding safe delta >= +0.20, and candidate forbidden leak rate <= 0.02.
- The 1000-case E1 run improves all core metrics but does not pass the strict story gate because grounding delta is +0.141.
