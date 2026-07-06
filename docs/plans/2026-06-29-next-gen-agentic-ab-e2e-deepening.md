# Next-Gen Agentic A/B + E2E Deepening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prove the next-generation Agentic loop work against the pasted report by closing remaining M0/M1 evidence gaps, adding repeated A/B evidence, and expanding browser E2E coverage beyond 20 scenarios.

**Architecture:** Keep the existing Spring Boot + Vue + FastAPI graph split. Add deterministic offline A/B gates in the graph eval layer, browser E2E checks that exercise real backend endpoints through Playwright, and documentation that maps report requirements to current evidence. Do not create a standalone demo runtime.

**Tech Stack:** Python/Pydantic/Pytest in `services/pythontopia-graph`, Vue/Playwright in `frontend`, Spring Boot/Maven for API verification, Markdown docs and `CHANGELOG.md` for auditable delivery.

---

## Requirement Audit

| Report requirement | Current evidence | Status before this plan | Required action |
|---|---|---|---|
| Freeze internal schema: `EventEnvelope`, `LoopCheckpoint`, `AgentToolCall`, `LoopResult`, `EvalRecord` | `services/pythontopia-graph/pythontopia_graph/schemas/loop_contract.py`, `docs/contracts/loop_contract_check.json` | Complete | Keep tests and extend eval records where A/B reports emit policy evidence. |
| AG-UI terminal result carries validated loop result | `sse_agui.run_finished_loop_result()` and `tests/test_loop_contract.py` | Complete | Add browser/API E2E checks that AG-UI contract endpoints remain visible through product flows. |
| Resource loop uses generate-review-revise-publish style evidence chain | `ResourceGraph` dispatch and resource artifact audit fields | Partial | Add A/B tests proving the looped policy beats legacy single-shot resource decisions across repeated samples. |
| Student loop uses read/micro-check/submission/mastery decision routing | `ResourceWorkshopService`, compact resource rail, `studentLearningLoop.spec.ts` | Partial | Add more API/UI E2E cases for pass, remediate, swap resource, replan, read evidence, and no-card compactness. |
| Debug loop prevents answer leakage and requires transfer understanding | Existing DebugAgent/Bug Mine tests, but no new report-level A/B proof in this batch | Partial | Cover in A/B dataset with leakage and transfer metrics; add browser/API checks for failed submission -> debug surfaces. |
| World loop binds quest/world update to KC and evidence | Existing quest/classroom/world APIs; no report-level world loop contract proof in this batch | Partial | Add E2E checks for bootstrap, town hub, quest evidence, classroom quest completion, and no farm mainline wording. |
| Improve loop gates candidate policies before canary | Existing prompt optimization module | Partial | Add repeated A/B report with strict gates: mean uplift, per-run win rate, regression rate, stability. |
| Startup endpoint source of truth: `POST /api/game/bootstrap` | Backend has both `/api/game/bootstrap` and legacy `/api/world/session` | Partial | Add E2E/API checks that bootstrap is preferred and legacy remains compatibility-only. |
| At least 5 closed-loop browser golden paths | Existing `studentLearningLoop` and teacher flow cover 4 tests total | Incomplete | Add 20+ new Playwright tests, designed as stable API+UI E2E against the real stack. |
| Prove effectiveness with A/B, multiple stable runs better than original | Existing eval optimizer exists, but no dedicated report proof for next-gen loop vs baseline | Incomplete | Add deterministic A/B harness, dataset, CLI/script output, tests, and docs report. |

## Task 1: Add Repeated Agentic A/B Harness

**Files:**
- Create: `services/pythontopia-graph/pythontopia_graph/eval/agentic_ab.py`
- Create: `services/pythontopia-graph/tests/test_agentic_ab.py`
- Create: `docs/reports/2026-06-29-agentic-ab-evaluation.md`

**Steps:**
1. Write failing pytest cases for a repeated baseline-vs-treatment comparison.
2. Require the report to expose sample count, repeat count, per-run deltas, stability rate, regression rate, loop-type summaries, and gate decisions.
3. Implement deterministic sample scoring for legacy single-shot policy and next-gen agentic loop policy.
4. Verify candidate passes only when every run beats baseline and regressions stay below threshold.
5. Generate/update the Markdown A/B report with exact command and expected metrics.

## Task 2: Add 20+ Browser E2E Scenarios

**Files:**
- Create: `frontend/e2e/agenticDeepLoop.spec.ts`

**Steps:**
1. Add shared authenticated API helpers that use real `/api/auth`, `/api/game/bootstrap`, `/api/workshop/resources`, `/api/submissions`, `/api/loop/runs`, `/api/learner/*`, `/api/classrooms/*`, and browser navigation.
2. Add at least 20 Playwright tests, balancing fast API E2E checks with selected UI checks to keep runtime practical.
3. Cover five groups: runtime contract, student learning loop, debug/submission loop, world/quest loop, improve/eval observability.
4. Run the spec against `./dev.sh` with `PYT_E2E_REGISTER=1`.

## Task 3: Changelog and Verification

**Files:**
- Modify: `CHANGELOG.md`

**Steps:**
1. Add a new top changelog entry after A/B and E2E are implemented.
2. Run targeted graph tests first, then targeted frontend E2E.
3. Run full graph/RAG/frontend/backend verification as far as the local environment allows.
4. Run `git diff --check`.
5. Commit only files touched for this task.
