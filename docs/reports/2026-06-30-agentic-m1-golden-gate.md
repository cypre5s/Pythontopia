# Agentic M1 Golden Gate And Replay Report

Date: 2026-06-30

## Scope

M1 covers the learner/debug part of the 14-week agentic plan:

- learner loop replay from runtime checkpoints,
- debug loop replay from failed submission -> Bug Mine -> retry/recovery,
- offline golden regression gate,
- canary-5 metadata skeleton without live traffic.

## Implemented

- Backend `LoopRuntimeService.restoreReplayPlan()` builds a replay plan from existing checkpoint, eval result, and agent run artifacts.
- Backend `GET /api/loop/runs/{id}/replay` exposes the owner-scoped replay plan.
- Frontend E2E now verifies a learner replay plan after writing runtime artifacts.
- Graph service now includes `pythontopia_graph.eval.m1_golden_gate`.
- M1 golden data lives in `pythontopia_graph/eval/golden/agentic_m1_golden.jsonl`, separate from generic RAG eval datasets.

## Golden Gate Result

Dataset: `agentic-m1-golden-v1`

| Category | Cases |
|---|---:|
| learner | 3 |
| debug | 3 |
| route | 2 |
| safety | 2 |
| leakage | 2 |

Result: PASS, 12/12 cases.

Canary skeleton:

- policy version: `agentic_loop_v1`
- traffic fraction: `0.05`
- rollout stage: `CANARY_METADATA_ONLY`
- real traffic enabled: `false`
- one variable only: `true`

## Verification

| Command | Result |
|---|---|
| `python3 -m pytest -q tests/test_m1_golden_gate.py tests/test_eval_runner.py tests/test_agentic_ab.py tests/test_loop_contract.py` | passed: 43 tests |
| `python3 -m pythontopia_graph.eval.m1_golden_gate` | passed: 12/12 golden cases |
| `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopControllerTest,LoopRunServiceTest test` | passed |
| `npm run typecheck` | passed |
| `PYT_E2E_REGISTER=1 npx playwright test e2e/agenticDeepLoop.spec.ts --retries=0` | passed: 29 tests / 1.4m |

## Residual Risk

This is still an offline/local gate. No real online LLM replay or live canary traffic was enabled in M1.
