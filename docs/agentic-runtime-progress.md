# Pythontopia Agentic Runtime Progress

> Updated: 2026-06-30
> Current objective: execute the pasted Agentic Runtime Phase 0-9 plan from the current `Pythontopia` checkout, with real implementation, tests, changelog updates, progress docs, and local commits per phase.

## Current Phase

| Field | Value |
|---|---|
| Phase | Phase 6 - Game world loop |
| Status | In progress - audit |
| Goal | Implement State -> Quest -> Action -> Reward -> World Update -> New Quest as a learning-bound world loop |
| Scope | Existing world proposal schema, quests/mainline/town hub state, non-economic reward constraints, graph runtime world loop, API/state exposure, focused tests |

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

### Phase 3 Student Learning Loop Work

- Audited existing learner-adjacent graph code:
  - `PathPlannerAgent`,
  - `ReflectionAgent`,
  - `PracticeAgent`,
  - `LoopRuntime`,
  - loop contract schemas,
  - FastAPI route registration.
- Added `pythontopia_graph.runtime.student_learning_loop` with a deterministic, evidence-bound six-stage learner loop:
  - `Observe`: aggregates triggering and recent events into `ObservationSnapshot`,
  - `Diagnose`: identifies KC gaps, repeated failures, low mastery, missing evidence, Pyodide-only preview, or formal-pass success,
  - `Teach`: emits a structured minimal intervention, remediation, evidence-review, or consolidation plan,
  - `Practice`: emits targeted, remediation, transfer, evidence micro-check, or formal-submission assignments,
  - `Reflect`: emits an evidence-event-bound reflection prompt,
  - `Update`: emits profile dimension patches, KC mastery deltas, and review schedule items.
- Added `POST /runtime/student-learning-runs` in the graph service so API consumers can display the current learner loop state without frontend fake data.
- Preserved the formal judge boundary:
  - Pyodide preview pass returns `PARTIAL` and asks for formal judge submission,
  - only `submission.passed` can produce a positive mastery delta.
- Added fail-safe behavior:
  - missing evidence returns `REJECTED`,
  - low-confidence diagnosis uses `insufficient_evidence`,
  - continuous failures route to remediation rather than harder practice,
  - no complete-answer text is produced.
- Returned a frozen-contract `LoopResult` with `LEARNER`, `policy_version`, `trace_id`, evidence refs, scores, steps, and next actions.

### Phase 4 Code Debug Loop Work

- Audited existing debug/session foundations:
  - graph `DebugAgent` and `/agents/debug/analyze`,
  - backend `practice_submission`, `judge_result`, and `debug_case`,
  - backend `bug_mine_session` and `bug_mine_card`,
  - Bug Mine retry and variant-submit flows,
  - M1 replay coverage for failed submission -> Bug Mine -> retry/recovery.
- Reused existing backend debug-session persistence instead of adding duplicate submission/session tables:
  - `V060__practice_submission.sql` already persists formal submissions, judge results, and debug cases,
  - `V070__bug_mine.sql` already persists Bug Mine sessions and structured debug cards,
  - later Bug Mine migrations already cover depth, retry, and variant mastery.
- Added `pythontopia_graph.runtime.code_debug_loop` with a deterministic, evidence-bound six-stage debug loop:
  - `Fail`: captures failed formal submission, verdict, KC, evidence refs, and attempt count,
  - `Hypothesize`: produces three ranked hypotheses from runtime behavior, code structure, and KC-prerequisite angles,
  - `Hint`: emits a minimal/evidence-only hint without complete-answer leakage,
  - `Resubmit`: waits for or consumes a formal resubmission snapshot,
  - `Compare`: compares verdict, code-change summary, and judge behavior,
  - `Transfer`: emits either transfer practice or prerequisite remediation.
- Added `DebugMemoryWriteback` output to recommend grounded error-pattern writeback with evidence submission ids.
- Added `POST /runtime/code-debug-runs` in the graph service so API consumers can display the current debug loop state without frontend fake data.
- Added fail-safe behavior:
  - missing evidence returns `REJECTED`,
  - missing resubmission returns `PARTIAL` and asks for formal judge resubmission,
  - three failed attempts return prerequisite remediation fallback,
  - resolved resubmission emits a transfer task.
- Returned a frozen-contract `LoopResult` with `DEBUG`, `policy_version`, `trace_id`, evidence refs, scores, steps, and next actions.

### Phase 5 Resource Generation Loop Work

- Audited existing resource foundations:
  - `ResourceGraph`,
  - resource agents and `ResourceProduceResponse`,
  - `ResourcePublishFlow` and `build_publish_flow`,
  - M2 resource/world gate,
  - backend workshop publish/effectiveness APIs.
- Reused existing ResourceGraph as the generation/critique engine instead of duplicating the LLM generation path:
  - evidence fetch,
  - resource agent design,
  - grounding critic,
  - hallucination detector,
  - semantic grounding,
  - safety critic,
  - revision retries/oscillation,
  - publish decision,
  - evaluation hook.
- Added `pythontopia_graph.runtime.resource_lifecycle_loop` with a deterministic, artifact-lifecycle runtime:
  - `Goal`: normalizes user/KC/resource goal,
  - `Generate`: consumes the real `ResourceProduceResponse` artifact,
  - `Critique`: summarizes pedagogy, grounding, safety, and game-fit gates,
  - `Revise`: emits revision requirements and parent artifact linkage,
  - `Publish`: emits manifest and publish state,
  - `Evaluate`: maps effectiveness snapshots to lifecycle signals.
- Added `POST /runtime/resource-lifecycle-runs` in the graph service so API consumers can display artifact lifecycle state without frontend fake data.
- Added lifecycle decisions:
  - grounded safe resources publish,
  - no-evidence resources hold for review,
  - critic failures reject publishing,
  - negative effectiveness requests revision after publish,
  - revision parents are preserved in `ArtifactManifest.revision_of`.
- Returned a frozen-contract `LoopResult` with `RESOURCE`, `policy_version`, `trace_id`, evidence refs, artifact manifest, scores, and next actions.

## In Progress

- Phase 6 audit of existing world loop proposal schema, quest/mainline state, town_hub constraints, and non-economic reward rules.

## Next

1. Write Phase 6 RED tests for world state reduction, quest graph generation, non-economic rewards, evidence-bound patches, rollback, and no inventory/currency/shop leakage.
2. Implement the structured graph-side world state loop and any API-visible status route needed for Phase 6.

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
| Implement Phase 3 in graph runtime first and expose an internal route | The pasted Phase 3 requires structured learner-loop state plus an API-visible status; this avoids fake frontend data while leaving Phase 8 to build full UI |
| Treat Pyodide preview as non-mastery evidence | Project rules require formal service-side submissions for official practice evidence and mastery updates |
| Keep Phase 3 deterministic | This phase proves state, evidence, and update semantics locally without relying on online model/provider availability |
| Reuse Bug Mine as the backend debug session store for Phase 4 | Existing migrations and services already persist failed submissions, debug cards, retry counts, depth, and variant mastery; duplicating them would split the debug source of truth |
| Implement Phase 4 graph runtime as a status/contract layer around existing DebugAgent/Bug Mine | Phase 4 needs recoverable state and transfer/memory outputs beyond the single-call DebugAgent response |
| Keep debug hints minimal | The project forbids direct complete-answer leakage, so Phase 4 stores hints and transfer prompts rather than runnable fixes |
| Reuse ResourceGraph as the Phase 5 generation engine | It already performs evidence fetch, generation, critique, revision retry, publish decision, and evaluation-hook binding; Phase 5 adds lifecycle state instead of re-running the same graph |
| Keep artifact lifecycle separate from backend persistence | The runtime emits manifest/revision/evaluation state for API visibility; workshop persistence and effectiveness APIs remain the authoritative backend integration points |

## Changed Files

Phase 5 intended files:

- `services/pythontopia-graph/pythontopia_graph/runtime/resource_lifecycle_loop.py`
- `services/pythontopia-graph/pythontopia_graph/routes/resource_lifecycle_runtime.py`
- `services/pythontopia-graph/pythontopia_graph/main.py`
- `services/pythontopia-graph/tests/test_resource_lifecycle_loop.py`
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
| 2026-06-30 | `python3 -m pytest -q tests/test_student_learning_loop.py` | RED: failed at collection because `pythontopia_graph.runtime.student_learning_loop` did not exist |
| 2026-06-30 | `python3 -m pytest -q tests/test_student_learning_loop.py` | passed: 6 tests covering full loop, missing evidence, three failures remediation, Pyodide preview gate, formal pass update, and API-visible route |
| 2026-06-30 | `python3 -m pytest -q tests/test_student_learning_loop.py tests/test_loop_runtime.py tests/test_agentic_loop.py tests/test_loop_contract.py tests/test_path_planner_agent.py tests/test_reflection_agent.py tests/test_m1_golden_gate.py tests/test_m2_resource_world_gate.py tests/test_m3_system_evolution_gate.py` | passed: 97 tests |
| 2026-06-30 | `python3 -m pytest -q tests/test_code_debug_loop.py` | RED: failed at collection because `pythontopia_graph.runtime.code_debug_loop` did not exist |
| 2026-06-30 | `python3 -m pytest -q tests/test_code_debug_loop.py` | passed: 7 tests covering resolved WA full loop, awaiting resubmission, RE, TLE, missing evidence, three-attempt fallback, and API-visible route |
| 2026-06-30 | `python3 -m pytest -q tests/test_code_debug_loop.py tests/test_debug_agent.py tests/test_student_learning_loop.py tests/test_loop_runtime.py tests/test_agentic_loop.py tests/test_loop_contract.py tests/test_m1_golden_gate.py tests/test_m2_resource_world_gate.py tests/test_m3_system_evolution_gate.py` | passed: 89 tests |
| 2026-06-30 | `python3 -m pytest -q tests/test_resource_lifecycle_loop.py` | RED: failed at collection because `pythontopia_graph.runtime.resource_lifecycle_loop` did not exist |
| 2026-06-30 | `python3 -m pytest -q tests/test_resource_lifecycle_loop.py` | passed: 6 tests covering publish/evaluate lifecycle, no-evidence hold, critic rejection, revision parent, negative effectiveness revision, and API-visible route |
| 2026-06-30 | `python3 -m pytest -q tests/test_resource_lifecycle_loop.py tests/test_resource_graph.py tests/test_m2_resource_world_gate.py tests/test_resource_agents.py tests/test_resource_schemas.py tests/test_loop_runtime.py tests/test_loop_contract.py tests/test_code_debug_loop.py tests/test_student_learning_loop.py tests/test_m1_golden_gate.py tests/test_m3_system_evolution_gate.py` | passed: 70 tests |

## Risks

- Existing unrelated dirty files must not be staged.
- Later phases need careful gap analysis to avoid duplicating or regressing existing M0-M3 runtime work.
- Full online model/provider eval remains outside local verification unless a later phase explicitly wires provider-backed gates.
- Phase 1 is backend control plane only; frontend runtime visibility and teacher operations remain Phase 8 scope.
- `GET /api/agent-runs/{id}/stream` currently emits deterministic lifecycle replay events from persisted run metadata; richer live streaming remains a later runtime orchestration concern.
- Phase 2 runtime is a shared skeleton. It proves five loop families have bounded state flow and structured output, but the full learner/debug/resource/world/evolution domain logic remains Phase 3-7 scope.
- Phase 3 is graph-side runtime and API-visible status only; full student/teacher frontend panels remain Phase 8.
- Phase 3 update output is a structured writeback plan, not direct backend mutation. Backend persistence orchestration remains a later integration concern unless Phase 4-9 wires it sooner.
- Phase 3 uses deterministic rules and provided course evidence; online model-backed personalization remains outside this local verification.
- Phase 4 graph runtime emits a debug memory writeback plan; backend persistence already exists for Bug Mine cards, but automatic profile/error-memory application remains a later orchestration concern.
- Phase 4 does not run a real judge; it consumes formal judge snapshots from existing submission/Bug Mine infrastructure and tests the loop contract around those snapshots.
- Phase 5 consumes `ResourceProduceResponse` rather than invoking live LLM/provider calls; existing `ResourceGraph` tests continue to verify generation and publish-flow behavior.
- Phase 5 evaluation is snapshot-driven and deterministic; scheduled 24h/7d evaluation jobs remain a later orchestration/deployment concern.

## Changelog Links

- Phase 5 changelog entry in `CHANGELOG.md`.

## Commits

| Phase | Commit | Notes |
|---|---|---|
| Phase 0 | `e0cdbebc` | Baseline audit docs and execution tracking |
| Phase 0 docs | `f6a4d88a` | Recorded Phase 0 commit hash |
| Phase 1 | `9221e7eb` | Unified event model and Agent Run control plane |
| Phase 1 docs | `d403d3ea` | Recorded Phase 1 commit hash |
| Phase 2 | `e1f5f3a0` | Loop Runtime skeleton and bounded state machines |
| Phase 2 docs | `cb021486` | Recorded Phase 2 commit hash |
| Phase 3 | `168edefa` | Student learning loop implementation and tests |
| Phase 3 docs | `3d6c11ea` | Recorded Phase 3 commit hash |
| Phase 4 | `f14175da` | Code debug loop implementation and tests |
| Phase 4 docs | `cd40df60` | Recorded Phase 4 commit hash |
| Phase 5 | `f930554c` | Resource lifecycle loop implementation and tests |
