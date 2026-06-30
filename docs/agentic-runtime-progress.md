# Pythontopia Agentic Runtime Progress

> Updated: 2026-06-30
> Current objective: execute the pasted Agentic Runtime Phase 0-9 plan from the current `Pythontopia` checkout, with real implementation, tests, changelog updates, progress docs, and local commits per phase.

## Current Phase

| Field | Value |
|---|---|
| Phase | Phase 2 - Loop Runtime skeleton and bounded state machines |
| Status | Complete pending local commit |
| Goal | Provide a shared graph-side runtime skeleton for five bounded loop families with structured LoopResult output |
| Scope | Graph runtime module, five loop templates, deterministic execution/fallback, focused pytest coverage, docs/changelog |

## Completed

### Pre-existing Agentic Baseline From Earlier Work

- M0 baseline freeze:
  - loop contract checker,
  - runtime persistence tables,
  - trace gate,
  - stronger workshop evidence gates.
- M1 learner/debug replay:
  - `ReplayPlan`,
  - `/api/loop/runs/{id}/replay`,
  - M1 golden gate.
- M2 resource/world:
  - resource publish flow,
  - world loop proposal evidence contract,
  - resource effectiveness API,
  - ingestion boundary.
- M3 system evolution:
  - stable M3 eval dataset,
  - failure clusters,
  - one-variable policy candidate,
  - offline replay gate,
  - canary dry-run scripts,
  - rollback status and policy lifecycle.

These are retained as the current implementation baseline. They do not by themselves close the new Phase 1-9 scope.

### Phase 0 Audit Work

- Read the two pasted execution-plan files.
- Read/inspected:
  - `README.md`,
  - `AGENTS.md`,
  - `docs/clients/api-contract.md`,
  - backend package layout,
  - frontend package layout,
  - graph/rag service package layouts,
  - Flyway migration tail through `V183`,
  - test directories,
  - `.github/workflows/ci.yml`,
  - build manifests: `backend/pom.xml`, `frontend/package.json`, graph/rag `pyproject.toml`.
- Confirmed current dirty worktree contains many unrelated modified files; Phase 0 must stage only docs/changelog files owned by this work.

### Phase 1 Control Plane Work

- Added resource-style control-plane endpoints under `/api`:
  - `POST /api/events`,
  - `POST /api/agent-runs`,
  - `GET /api/agent-runs/{id}`,
  - `GET /api/agent-runs/{id}/stream`,
  - `POST /api/agent-runs/{id}/loop-states`,
  - `POST /api/agent-runs/{id}/policy-decisions`.
- Reused existing `learning_event_log` as the authoritative event table rather than creating a duplicate `learning_event` table.
- Kept existing `/api/loop/**` runtime audit endpoints compatible.
- Added Flyway `V184__agent_runtime_control_plane.sql` for:
  - `loop_state`,
  - `policy_decision`.
- Extended `LoopRuntimeService` and `LoopRuntimeRepository` with owner-scoped loop state and policy-decision persistence.
- Added TDD coverage:
  - MockMvc API coverage in `AgentRuntimeControllerTest`,
  - Testcontainers/Flyway persistence and validation coverage in `LoopRunServiceTest`.

### Phase 2 Loop Runtime Skeleton Work

- Added `pythontopia_graph.runtime.loop_runtime` as a shared deterministic graph-side runtime layer.
- Added five bounded loop templates:
  - `student_learning_loop` → `LEARNER`,
  - `code_debug_loop` → `DEBUG`,
  - `resource_generation_loop` → `RESOURCE`,
  - `game_world_loop` → `WORLD`,
  - `system_evolution_loop` → `IMPROVE`.
- Each template now has explicit states, max attempts, fallback action, trace propagation, and policy-version propagation.
- Runtime execution now returns a structured `LoopExecutionResult` containing a validated `LoopResult` from the frozen loop contract.
- Fail paths are bounded:
  - missing evidence returns `REJECTED` with a fallback action,
  - max-step truncation returns `PARTIAL` with a resume action,
  - invalid max-step config fails fast.

## In Progress

- Preparing narrow staging for Phase 2 files only.

## Next

1. Commit Phase 2.
2. Start Phase 3 by auditing existing learner-loop code against Observe → Diagnose → Teach → Practice → Reflect → Update.

## Decisions

| Decision | Reason |
|---|---|
| Treat prior M0-M3 work as baseline, not as full new-plan completion | The new pasted plan has stricter Phase 0-9 requirements, including frontend/teacher ops and full Beta handoff |
| Preserve public client contract from `docs/clients/api-contract.md` | Four client families consume the same Spring Boot API |
| Add control plane internally rather than replacing existing student/teacher APIs | Existing Tutor/Workshop/Orchestrator/Mainline flows are already user-facing and tested |
| Keep `town_hub` and no economy systems as hard constraints | R-14/R-15 explicitly removed multi-map farm/economy mainline concepts |
| Stage narrowly | Current worktree contains unrelated user/generated changes |
| Reuse `learning_event_log` for `/api/events` instead of adding a duplicate table | The repository already has a tested append-only learning-event memory stream with importance scoring |
| Expose `/api/agent-runs` as a compatibility control-plane alias over `LoopRunService` | Existing `loop_run` already carries user/session/trace/policy-relevant run audit fields and is used by `/api/loop/runs` |
| Add graph-side `LoopRuntime` as a small deterministic skeleton, not a replacement for orchestrator agents | Phase 2 asks for common state-machine semantics; Phase 3-7 will deepen each loop without breaking existing orchestrator behavior |
| Use frozen `LoopResult` contract as the runtime output | Keeps Phase 2 output consumable by AG-UI, eval gates, and backend control plane |

## Changed Files

Phase 2 intended files:

- `services/pythontopia-graph/pythontopia_graph/runtime/__init__.py`
- `services/pythontopia-graph/pythontopia_graph/runtime/loop_runtime.py`
- `services/pythontopia-graph/tests/test_loop_runtime.py`
- `docs/agentic-runtime-progress.md`
- `docs/agentic-runtime-plan.md`
- `CHANGELOG.md`

## Tests

| Time | Command | Result |
|---|---|---|
| 2026-06-30 | `git diff --check` | passed |
| 2026-06-30 | `rg -n "Repository State Confirmation|Phase 0|Current Phase|Verification Matrix|Commit Rules|Agentic Runtime Phase 0" docs/agentic-runtime-plan.md docs/agentic-runtime-progress.md CHANGELOG.md` | passed: required headings and changelog entry found |
| 2026-06-30 | `git status --short -- docs/agentic-runtime-plan.md docs/agentic-runtime-progress.md CHANGELOG.md` | passed: only Phase 0 docs/changelog listed for this scope |
| 2026-06-30 | `mvn --settings .mvn/settings.build.xml -q -Dtest=AgentRuntimeControllerTest,LoopRunServiceTest#phase1ControlPlanePersistsEventLoopStateAndPolicyDecision,LoopRunServiceTest#phase1ControlPlaneRejectsInvalidEventsStatesAndDecisions test` | RED: failed at test compile because `AgentRuntimeController`, `AgentRuntimeEventService`, and `AgentRuntimeEventRepository` did not exist |
| 2026-06-30 | same focused Maven command after implementation | passed: Flyway applied through `v184`, MockMvc endpoints and true-db persistence checks passed |
| 2026-06-30 | `mvn --settings .mvn/settings.build.xml -q -Dtest=AgentRuntimeControllerTest,LoopControllerTest,LoopRunServiceTest test` | passed: new control plane, legacy `/api/loop/**` controller, and full loop runtime true-db suite |
| 2026-06-30 | `mvn --settings .mvn/settings.build.xml -q -DskipTests test-compile` | passed |
| 2026-06-30 | `git diff --check` | passed |
| 2026-06-30 | `python3 -m pytest -q tests/test_loop_runtime.py` | RED: failed at collection because `pythontopia_graph.runtime` did not exist |
| 2026-06-30 | `python3 -m pytest -q tests/test_loop_runtime.py` | passed: 13 tests |
| 2026-06-30 | `python3 -m pytest -q tests/test_loop_runtime.py tests/test_agentic_loop.py tests/test_loop_contract.py` | passed: 61 tests |
| 2026-06-30 | `python3 -m pytest -q tests/test_loop_runtime.py tests/test_agentic_loop.py tests/test_loop_contract.py tests/test_agentic_ab.py tests/test_m1_golden_gate.py tests/test_m2_resource_world_gate.py tests/test_m3_system_evolution_gate.py` | passed: 78 tests |

## Risks

- Existing unrelated dirty files must not be staged.
- Later phases need careful gap analysis to avoid duplicating or regressing existing M0-M3 runtime work.
- Full online model/provider eval remains outside local verification unless a later phase explicitly wires provider-backed gates.
- Phase 1 is backend control plane only; frontend runtime visibility and teacher operations remain Phase 8 scope.
- `GET /api/agent-runs/{id}/stream` currently emits deterministic lifecycle replay events from persisted run metadata; richer live streaming remains a later runtime orchestration concern.
- Phase 2 runtime is a shared skeleton. It proves five loop families have bounded state flow and structured output, but the full learner/debug/resource/world/evolution domain logic remains Phase 3-7 scope.

## Changelog Links

- Phase 2 changelog entry in `CHANGELOG.md`.

## Commits

| Phase | Commit | Notes |
|---|---|---|
| Phase 0 | `e0cdbebc` | Baseline audit docs and execution tracking |
| Phase 1 | `9221e7eb` | Unified event model and Agent Run control plane |
| Phase 1 docs | `d403d3ea` | Recorded Phase 1 commit hash |
| Phase 2 | `e1f5f3a0` | Loop Runtime skeleton and bounded state machines |
