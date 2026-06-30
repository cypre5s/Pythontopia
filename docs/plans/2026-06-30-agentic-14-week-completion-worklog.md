# Agentic 14-Week Completion Worklog

> Updated: 2026-06-30 20:46
> Source spec: pasted "Pythontopia 下一代 Agentic 系统可执行推进报告"
> Rule for this worklog: every phase must record scope, tests, changelog entry, and local commit before it is treated as complete.

## Delivery Rules

- No subagent parallel work.
- Work in the current dirty tree, but stage and commit only task-owned files.
- Use TDD for behavior changes: failing test first, then minimal implementation, then verification.
- Each phase ends with:
  - targeted tests for touched backend/graph/frontend code,
  - browser or runtime acceptance when the phase changes user-visible loop behavior,
  - `git diff --check`,
  - `CHANGELOG.md` update,
  - local commit with only phase-owned files.

## Phase Map

| Phase | Plan Window | Completion Bar | Current Status |
|---|---:|---|---|
| M0 Baseline Freeze | 2 weeks | startup/API/AG-UI/schema/run trace are consistent and covered by contract tests and golden browser paths | Complete |
| M1 Learner + Debug Loops | 4 weeks | learner/debug loops can restore, replay, use golden sets, and pass offline regression gates | Complete |
| M2 Resource + World Loops | 4 weeks | resource review chain and quest/world updates are evidence-bound and feedback-aware | Complete |
| M3 System Evolution Loop | 4 weeks | trace to eval to failure cluster to policy candidate to canary to rollback is automated | Complete |

## M0 Baseline Freeze

### Done In Current Working Tree

- Candidate-output A/B scorer for five loop types.
- Runtime artifact persistence tables:
  - `policy_registry`
  - `loop_checkpoint`
  - `agent_run`
  - `agent_tool_call`
  - `loop_eval_result`
- Owner-scoped runtime service/repository and HTTP endpoints for checkpoint/tool-call/agent-run/eval-result/snapshot.
- Stronger workshop evidence gates:
  - micro quiz must match real resource content rather than KC-code-only text,
  - operation evidence needs a formal passed training submission.
- Browser E2E includes runtime artifact restore plus learning/debug/resource/world/improve loop coverage.
- Executable loop contract checker validates the JSON checklist against the five Pydantic models and AG-UI terminal mapping.
- New loop runs now require nonblank `trace_id` at both `LoopRunService` and database constraint level.

### Still Needed Before M0 Complete

- None.

### Verification Log

| Time | Command | Result |
|---|---|---|
| 2026-06-30 15:41 | `python3 -m pytest -q tests/test_agentic_ab.py tests/test_orchestrator_real_dispatch.py` | passed: 20 tests |
| 2026-06-30 15:42 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopControllerTest,ResourceWorkshopServiceTest,LoopRunServiceTest test` | passed: exit 0, Testcontainers/Flyway v182 covered |
| 2026-06-30 15:41 | `npm run typecheck` | passed: `vue-tsc --noEmit` exit 0 |
| 2026-06-30 15:46 | `PYT_E2E_REGISTER=1 npx playwright test e2e/agenticDeepLoop.spec.ts --retries=0` | passed: 29 tests / 1.4m |
| 2026-06-30 15:41 | `git diff --check` | passed |
| 2026-06-30 15:48 | `python3 -m pytest -q tests/test_loop_contract.py::test_loop_contract_checker_returns_actionable_summary` | RED: failed with missing `pythontopia_graph.eval.loop_contract_check` |
| 2026-06-30 15:48 | `python3 -m pytest -q tests/test_loop_contract.py` | passed: 7 tests after checker implementation |
| 2026-06-30 15:48 | `python3 -m pythontopia_graph.eval.loop_contract_check ../../docs/contracts/loop_contract_check.json` | passed: `OK loop-contract.v1` |
| 2026-06-30 15:48 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopRunServiceTest#criticVerdictsTokensAndTraceArePersistedExactly test` | RED: empty trace accepted before service gate |
| 2026-06-30 15:49 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopRunServiceTest#databaseRejectsNewLoopRunWithoutTraceId test` | RED: DB accepted empty trace before V183 |
| 2026-06-30 15:50 | same two focused Maven tests | passed after service gate + V183 |
| 2026-06-30 15:53 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopRunServiceTest test` | passed: 9 tests, Flyway v183 |
| 2026-06-30 15:56 | `python3 -m pytest -q tests/test_loop_contract.py tests/test_agentic_ab.py tests/test_orchestrator_real_dispatch.py` | passed: 27 tests |
| 2026-06-30 15:56 | `python3 -m pythontopia_graph.eval.loop_contract_check ../../docs/contracts/loop_contract_check.json` | passed: `OK loop-contract.v1` |
| 2026-06-30 15:56 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopControllerTest,ResourceWorkshopServiceTest,LoopRunServiceTest test` | passed: exit 0, Testcontainers/Flyway v183 covered |
| 2026-06-30 15:56 | `npm run typecheck` | passed: `vue-tsc --noEmit` exit 0 |
| 2026-06-30 16:00 | `PYT_E2E_REGISTER=1 npx playwright test e2e/agenticDeepLoop.spec.ts --retries=0` | passed: 29 tests / 2.0m, after fresh dev restart and v183 migration |
| 2026-06-30 16:03 | `git diff --check` | passed |

### Commit Log

| Phase | Commit | Notes |
|---|---|---|
| M0 foundation | `a7216f3f` | Runtime persistence, scorer A/B, stronger evidence gate, browser restore test |
| M0 contract/trace gate | `a3b9ab2e` | Executable contract checker and mandatory trace_id for new loop runs |

## M1 Learner + Debug Loops

### Done In Current Working Tree

- `LoopRuntimeService.restoreReplayPlan()` turns checkpoint/eval/agent artifacts into an explicit replay plan:
  - `loopType`
  - `resumeCursor`
  - `nextAction`
  - `evidenceRefs`
  - ordered replay steps
  - `replayable`
- `GET /api/loop/runs/{id}/replay` exposes the replay plan with the same student owner boundary as runtime snapshots.
- Learner replay is covered from resource-read checkpoint -> pending micro-check.
- Debug replay is covered from failed formal submission -> Bug Mine -> retry/recovery.
- `pythontopia_graph.eval.m1_golden_gate` adds an offline regression command with 12 golden cases:
  - 3 learner
  - 3 debug
  - 2 route
  - 2 safety
  - 2 leakage
- Canary-5 metadata skeleton is included in the M1 gate report with `real_traffic_enabled=false`.
- Report added at `docs/reports/2026-06-30-agentic-m1-golden-gate.md`.

### Still Needed Before M1 Complete

- None.

### Verification Log

| Time | Command | Result |
|---|---|---|
| 2026-06-30 16:08 | `python3 -m pytest -q tests/test_m1_golden_gate.py` | RED: missing `pythontopia_graph.eval.m1_golden_gate` |
| 2026-06-30 16:09 | `python3 -m pytest -q tests/test_m1_golden_gate.py` | passed: 3 tests after golden gate implementation |
| 2026-06-30 16:09 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopRunServiceTest#replayPlansRestoreLearnerAndDebugLoopsFromCheckpoints test` | RED: missing `restoreReplayPlan` |
| 2026-06-30 16:10 | same focused Maven service test | passed after replay plan implementation |
| 2026-06-30 16:10 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopControllerTest#replayEndpointReturnsExplicitResumePlan test` | RED: `/api/loop/runs/{id}/replay` returned 404 |
| 2026-06-30 16:11 | same focused Maven controller test | passed after replay endpoint implementation |
| 2026-06-30 16:12 | `python3 -m pytest -q tests/test_m1_golden_gate.py tests/test_eval_runner.py tests/test_agentic_ab.py tests/test_loop_contract.py` | initially failed: generic eval runner scanned M1-specific dataset |
| 2026-06-30 16:13 | same graph test set after moving M1 data to `eval/golden/` | passed: 43 tests |
| 2026-06-30 16:13 | `python3 -m pythontopia_graph.eval.m1_golden_gate` | passed: 12/12 cases, canary metadata-only |
| 2026-06-30 16:13 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopControllerTest,LoopRunServiceTest test` | passed: exit 0, Testcontainers/Flyway v183 covered |
| 2026-06-30 16:14 | `npm run typecheck` | passed: `vue-tsc --noEmit` exit 0 |
| 2026-06-30 16:16 | `PYT_E2E_REGISTER=1 npx playwright test e2e/agenticDeepLoop.spec.ts --retries=0` | passed: 29 tests / 1.4m, includes `/replay` assertion |
| 2026-06-30 16:19 | `python3 -m pytest -q tests/test_m1_golden_gate.py` | passed: 3 tests after formatting cleanup |
| 2026-06-30 16:19 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopRunServiceTest#replayPlansRestoreLearnerAndDebugLoopsFromCheckpoints,LoopControllerTest#replayEndpointReturnsExplicitResumePlan test` | passed: focused replay retest after cleanup |
| 2026-06-30 16:19 | `git diff --check` | passed |

### Commit Log

| Phase | Commit | Notes |
|---|---|---|
| M1 learner/debug replay + golden gate | `c8d85544` | ReplayPlan API, 12-case M1 golden gate, canary metadata-only, browser replay assertion |

## M2 Resource + World Loops

### Done In Current Working Tree

- Resource loop publish flow is explicit in graph responses:
  - design,
  - evidence assembly,
  - pedagogy critique,
  - grounding/safety,
  - publish decision,
  - evaluation hook.
- `pythontopia_graph.eval.m2_resource_world_gate` adds a 6-case offline gate for:
  - publish with bound evidence,
  - hold partial/no-evidence resources,
  - reject publish decisions without evidence,
  - accept evidence-bound world proposals,
  - reject unbound world proposals,
  - declare local ingestion boundary.
- Resource publish feedback/effectiveness is exposed at `GET /api/workshop/resources/{id}/effectiveness`:
  - feedback adoption/rejection counts,
  - resource check counts,
  - pass rate,
  - mastery delta sum,
  - evidence page ids,
  - `resource_feedback_or_check` evaluation hook.
- Quest/world loop proposal contract added:
  - `state_patch`,
  - `new_quests`,
  - `npc_dialogue`,
  - `reward_plan`,
  - evidence binding.
- Upload ingestion boundary declares current adapter as `local_text_pdf_docx`:
  - `.py/.txt/.md/.csv` via UTF-8 text,
  - `.pdf` via PDFBox text extraction,
  - `.docx` via POI XWPF text extraction,
  - external OCR and Marker disabled.
- Frontend API client exposes `getWorkshopResourceEffectiveness()`.
- Browser E2E now checks resource effectiveness after a passing micro-check.

### Still Needed Before M2 Complete

- None.

### Verification Log

| Time | Command | Result |
|---|---|---|
| 2026-06-30 16:28 | `python3 -m pytest -q tests/test_m2_resource_world_gate.py` | RED: missing `pythontopia_graph.schemas.world_loop` |
| 2026-06-30 16:28 | `mvn --settings .mvn/settings.build.xml -q -Dtest=ResourceWorkshopServiceTest#effectivenessExposesFeedbackChecksMasteryAndEvaluationHook,ResourceWorkshopControllerTest,UploadTextExtractorTest#boundaryDeclaresLocalTextPdfDocxAdapterWithoutExternalOcrOrMarker test` | RED: missing `ResourceEffectivenessDto` |
| 2026-06-30 16:30 | same graph M2 test | passed: 3 tests after publish flow + world proposal schema |
| 2026-06-30 16:30 | same Maven focus set | passed after effectiveness API + ingestion boundary |
| 2026-06-30 16:32 | `npm run test -- --run src/api/__tests__/workshop.spec.ts` | RED: missing `getWorkshopResourceEffectiveness` |
| 2026-06-30 16:33 | same frontend API test | passed: 1 test after API client implementation |
| 2026-06-30 16:34 | `python3 -m pytest -q tests/test_m2_resource_world_gate.py` | RED: missing `pythontopia_graph.eval.m2_resource_world_gate` |
| 2026-06-30 16:35 | `python3 -m pytest -q tests/test_m2_resource_world_gate.py` | passed: 4 tests after M2 gate implementation |
| 2026-06-30 16:35 | `python3 -m pythontopia_graph.eval.m2_resource_world_gate` | passed: 6/6 M2 gate cases |
| 2026-06-30 16:35 | `python3 -m pytest -q tests/test_m2_resource_world_gate.py tests/test_resource_graph.py tests/test_resource_schemas.py tests/test_loop_contract.py` | passed: 20 tests |
| 2026-06-30 16:35 | `mvn --settings .mvn/settings.build.xml -q -Dtest=ResourceWorkshopServiceTest,ResourceWorkshopControllerTest,UploadTextExtractorTest,QuestCompleteWithEvidenceTest test` | passed: exit 0 |
| 2026-06-30 16:35 | `npm run test -- --run src/api/__tests__/workshop.spec.ts` | passed: 1 test; existing Pyodide sourcemap warning only |
| 2026-06-30 16:35 | `npm run typecheck` | passed: `vue-tsc --noEmit` exit 0 |
| 2026-06-30 16:38 | `curl` health checks for `5173/18080/18091/18092` | passed: all `{"status":"ok"}` |
| 2026-06-30 16:40 | `PYT_E2E_REGISTER=1 npx playwright test e2e/agenticDeepLoop.spec.ts --retries=0` | passed: 30 tests / 2.4m, includes effectiveness assertion |

### Commit Log

| Phase | Commit | Notes |
|---|---|---|
| M2 resource/world gate | `b4d20016` | PublishFlow, WorldLoopProposal, resource effectiveness API, local ingestion boundary, browser effectiveness assertion |

## M3 System Evolution Loop

### Done In Current Working Tree

- File-backed M3 eval dataset with stable IDs:
  - `agentic-m3-system-evolution-v1`,
  - 8 trace cases across RESOURCE/LEARNER/DEBUG/WORLD/IMPROVE,
  - duplicate/blank stable IDs rejected before schema validation.
- `pythontopia_graph.eval.m3_system_evolution_gate` now runs the full system evolution chain:
  - trace/eval dataset load,
  - failure clustering by `failure_signature`,
  - one-variable policy candidate proposal,
  - offline replay gate,
  - 5% canary dry-run plan/execution,
  - rollback rules/status.
- Policy candidate is constrained to a single changed variable:
  - selected cluster: `evidence_missing`,
  - changed variable: `retrieval_top_k`,
  - target policy version: `agentic_loop_v2`.
- Backend policy registry now has explicit status transitions:
  - `DRAFT -> CANARY -> RETIRED`,
  - terminal `RETIRED/REJECTED` policies reject reactivation,
  - canary and rollback metadata are preserved in policy config.
- Local workflow scripts added:
  - `scripts/agentic-offline-eval.sh`,
  - `scripts/agentic-nightly-improve-loop.sh`,
  - `scripts/agentic-canary-rollout-dryrun.sh`,
  - shared runner `scripts/agentic-m3-system-evolution-gate.sh`.
- Experiment/monitoring report generated:
  - `docs/reports/2026-06-30-agentic-m3-system-evolution-gate.md`.

### Still Needed Before M3 Complete

- None.

### Verification Log

| Time | Command | Result |
|---|---|---|
| 2026-06-30 20:36 | `python3 -m pytest -q tests/test_m3_system_evolution_gate.py` | RED: `config_patch` shape invalid, duplicate ID check blocked by schema validation, CLI JSON missing `one_variable_only` |
| 2026-06-30 20:37 | same graph M3 test | passed: 3 tests after loader, config patch, computed field fixes |
| 2026-06-30 20:39 | `mvn --settings .mvn/settings.build.xml -q -DskipTests test-compile` | passed: backend compiles after policy transition implementation |
| 2026-06-30 20:39 | `python3 -m pythontopia_graph.eval.m3_system_evolution_gate` | passed: 8 cases, mean uplift `0.16`, regression rate `0.0`, canary `DRY_RUN_READY` |
| 2026-06-30 20:41 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopRunServiceTest test` | passed: policy transitions and rollback metadata covered with real Flyway/Testcontainers |
| 2026-06-30 20:42 | `bash -n scripts/agentic-m3-system-evolution-gate.sh scripts/agentic-offline-eval.sh scripts/agentic-nightly-improve-loop.sh scripts/agentic-canary-rollout-dryrun.sh` | passed |
| 2026-06-30 20:43 | `python3 -m pytest -q tests/test_m3_system_evolution_gate.py tests/test_eval_optimization.py tests/test_agentic_ab.py tests/test_loop_contract.py tests/test_m1_golden_gate.py tests/test_m2_resource_world_gate.py` | passed: 28 tests |
| 2026-06-30 20:43 | `scripts/agentic-offline-eval.sh && scripts/agentic-nightly-improve-loop.sh && scripts/agentic-canary-rollout-dryrun.sh` | passed: all three workflow dry-runs returned M3 report with `passed=true` |
| 2026-06-30 20:43 | `mvn --settings .mvn/settings.build.xml -q -Dtest=LoopControllerTest,LoopRunServiceTest,ResourceWorkshopServiceTest,ResourceWorkshopControllerTest,UploadTextExtractorTest,QuestCompleteWithEvidenceTest test` | passed: exit 0, Flyway v183 and M0-M3 backend paths covered |
| 2026-06-30 20:43 | `npm run typecheck` | passed: `vue-tsc --noEmit` exit 0 |
| 2026-06-30 20:43 | `npm run test -- --run src/api/__tests__/workshop.spec.ts` | passed: 1 test; existing Pyodide sourcemap warning only |
| 2026-06-30 20:44 | `curl` health checks for `5173/18080/18091/18092` | passed: all `{"status":"ok"}` after `./dev.sh` startup |
| 2026-06-30 20:46 | `PYT_E2E_REGISTER=1 npx playwright test e2e/agenticDeepLoop.spec.ts --retries=0` | passed: 30 tests / 1.9m |

### Commit Log

| Phase | Commit | Notes |
|---|---|---|
| M3 system evolution loop | `460ea4d7` | Stable eval dataset, failure clustering, one-variable policy candidate, offline replay gate, canary dry-run scripts, rollback status and policy lifecycle |

## Open Risks

- Existing worktree contains unrelated modified files, including licenses, screenshots, arena/boss files, and presentation artifacts. These must remain unstaged unless the user explicitly asks.
- Full backend `mvn test` has historical Testcontainers port-conflict flakiness; each phase must still run targeted Testcontainers tests plus broader checks when feasible.
- Real online LLM/provider evaluation is not part of the current local verification contract; M3 is offline + dry-run canary only, with `real_traffic_enabled=false`.
