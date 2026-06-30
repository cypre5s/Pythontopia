# Pythontopia Agentic Runtime Progress

> Updated: 2026-06-30
> Current objective: execute the pasted Agentic Runtime Phase 0-9 plan from the current `Pythontopia` checkout, with real implementation, tests, changelog updates, progress docs, and local commits per phase.

## Current Phase

| Field | Value |
|---|---|
| Phase | Phase 1 - Unified event model and Agent Run control plane |
| Status | Complete pending local commit |
| Goal | Expose unified event and agent-run control-plane APIs, persist loop state and policy decisions, keep existing public APIs compatible |
| Scope | Backend API, Flyway migration, runtime repository/service extensions, focused tests, docs/changelog |

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

## In Progress

- Preparing narrow staging for Phase 1 files only.

## Next

1. Finish Phase 1 verification.
2. Commit Phase 1.
3. Start Phase 2 by auditing existing graph loop skeletons against the new Phase 2 acceptance requirements.

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

## Changed Files

Phase 1 intended files:

- `backend/src/main/resources/db/migration/V184__agent_runtime_control_plane.sql`
- `backend/src/main/java/com/pythontopia/agent/runtime/AgentRuntimeController.java`
- `backend/src/main/java/com/pythontopia/agent/runtime/AgentRuntimeEventRepository.java`
- `backend/src/main/java/com/pythontopia/agent/runtime/AgentRuntimeEventService.java`
- `backend/src/main/java/com/pythontopia/loop/LoopRuntimeRepository.java`
- `backend/src/main/java/com/pythontopia/loop/LoopRuntimeService.java`
- `backend/src/test/java/com/pythontopia/agent/runtime/AgentRuntimeControllerTest.java`
- `backend/src/test/java/com/pythontopia/loop/LoopRunServiceTest.java`
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

## Risks

- Existing unrelated dirty files must not be staged.
- Later phases need careful gap analysis to avoid duplicating or regressing existing M0-M3 runtime work.
- Full online model/provider eval remains outside local verification unless a later phase explicitly wires provider-backed gates.
- Phase 1 is backend control plane only; frontend runtime visibility and teacher operations remain Phase 8 scope.
- `GET /api/agent-runs/{id}/stream` currently emits deterministic lifecycle replay events from persisted run metadata; richer live streaming remains a later runtime orchestration concern.

## Changelog Links

- Phase 1 changelog entry in `CHANGELOG.md`.

## Commits

| Phase | Commit | Notes |
|---|---|---|
| Phase 0 | `e0cdbebc` | Baseline audit docs and execution tracking |
| Phase 1 | Pending | Unified event model and Agent Run control plane |
