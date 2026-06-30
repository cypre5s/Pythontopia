# Agentic 14-Week Completion Worklog

> Updated: 2026-06-30 16:05
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
| M0 Baseline Freeze | 2 weeks | startup/API/AG-UI/schema/run trace are consistent and covered by contract tests and golden browser paths | Contract/trace gate verified; commit pending |
| M1 Learner + Debug Loops | 4 weeks | learner/debug loops can restore, replay, use golden sets, and pass offline regression gates | Not complete |
| M2 Resource + World Loops | 4 weeks | resource review chain and quest/world updates are evidence-bound and feedback-aware | Not complete |
| M3 System Evolution Loop | 4 weeks | trace to eval to failure cluster to policy candidate to canary to rollback is automated | Not complete |

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

- Stage only M0-owned files.
- Commit M0 contract/trace gate.

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
| M0 contract/trace gate | pending commit | Executable contract checker and mandatory trace_id for new loop runs |

## M1 Learner + Debug Loops

### Remaining Requirements

- Make learner loop restoration/replay explicit through runtime checkpoint snapshots, not only resource-check state.
- Make debug loop restoration/replay explicit for failed submission -> Bug Mine -> retry/recovery.
- Build golden set v1 large enough to be useful for regression:
  - learner cases,
  - debug cases,
  - route/safety/leakage cases.
- Add offline regression command and report.
- Add canary-5 policy metadata skeleton without sending real traffic.

### Planned Tests

- Backend service tests for replay/restore ownership and invalid-state rejection.
- Graph tests for golden set evaluation and leakage rejection.
- Playwright path for failed formal submission -> debug loop -> checkpoint snapshot -> recovery evidence.

## M2 Resource + World Loops

### Remaining Requirements

- Make resource loop publish flow explicit:
  - design,
  - evidence assembly,
  - pedagogy critique,
  - grounding/safety,
  - publish decision,
  - evaluation hook.
- Persist or expose publish feedback and effectiveness signal.
- Add quest/world loop proposal contract:
  - `state_patch`,
  - `new_quests`,
  - `npc_dialogue`,
  - `reward_plan`,
  - evidence binding.
- Add OCR/Marker ingestion boundary or a clear local adapter with tests.

### Planned Tests

- Graph tests for resource publish/reject decisions.
- Backend tests for quest/world evidence requirements.
- Browser E2E for evidence-bound quest/world update after learning event.

## M3 System Evolution Loop

### Remaining Requirements

- Add eval dataset item persistence or file-backed dataset loader with stable IDs.
- Add failure cluster generation.
- Add policy candidate proposal constrained to one variable.
- Add offline replay gate.
- Add canary rollout plan/executor skeleton.
- Add rollback rules and status.
- Add CI workflow or local workflow scripts:
  - offline agent eval,
  - nightly improve loop,
  - canary rollout.
- Add experiment/monitoring view or report.

### Planned Tests

- Graph tests for trace export, score join, failure clustering, candidate proposal, replay gate.
- Backend tests for policy status transitions and rollback metadata.
- Script tests or dry-run workflow tests for eval/improve/canary commands.

## Open Risks

- Existing worktree contains unrelated modified files, including licenses, screenshots, arena/boss files, and presentation artifacts. These must remain unstaged unless the user explicitly asks.
- Full backend `mvn test` has historical Testcontainers port-conflict flakiness; each phase must still run targeted Testcontainers tests plus broader checks when feasible.
- Real online LLM/provider evaluation is not yet part of the current local verification contract.
