# Pythontopia Agentic Runtime Progress

> Updated: 2026-07-01
> Current objective: execute the pasted Agentic Runtime Phase 0-9 plan from the current `Pythontopia` checkout, with real implementation, tests, changelog updates, progress docs, and local commits per phase.

## Current Phase

| Field | Value |
|---|---|
| Phase | Phase 9 - Full-chain testing and Beta handoff |
| Status | Complete - local full-chain verification passed |
| Goal | Prove local full-chain Agentic Learning Loop and prepare Beta handoff docs |
| Scope | Backend/graph/rag/frontend/db/API/E2E verification, startup health, docs, changelog, final commit |

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

### Phase 6 Game World Loop Work

- Audited existing world foundations:
  - `WorldLoopProposal`,
  - `WorldStatePatch`,
  - `WorldQuestProposal`,
  - `NpcDialoguePatch`,
  - `RewardPlan`,
  - M2 resource/world evidence gate,
  - R-14/R-15 `town_hub` and no-economy constraints.
- Reused the existing `WorldLoopProposal` evidence contract instead of creating a competing world-update schema.
- Added `pythontopia_graph.runtime.world_state_loop` with a deterministic, evidence-bound six-stage world loop:
  - `State`: normalizes the learner snapshot into single-hub `town_hub` state,
  - `Quest`: updates active/completed quest graph from the learning action,
  - `Action`: classifies formal submission, page read, resource consumption, debug recovery, or profile action,
  - `Reward`: emits only learning-safe rewards such as badge, mentor trust, mastery delta, title, or quest branch unlock,
  - `World Update`: emits evidence-bound `town_hub` state patches,
  - `New Quest`: emits the next evidence-required quest.
- Added `POST /runtime/world-state-runs` in the graph service so API consumers can display the current world loop state without frontend fake data.
- Enforced R-14 world constraints:
  - output state is normalized to `town_hub`,
  - all state patches use `town_hub.*` keys,
  - inventory/currency/shop/coin/gold style reward requests are rejected,
  - no farm/crop/economy path is reintroduced.
- Added rollback visibility through `RollbackPatch`, preserving previous world-state values for each proposal patch.
- Added fail-safe behavior:
  - missing evidence returns `REJECTED`,
  - forbidden economy rewards return `REJECTED`,
  - rejected runs emit no `WorldLoopProposal`,
  - page-read actions create formal-submission follow-up quests without mastery reward.
- Returned a frozen-contract `LoopResult` with `WORLD`, `policy_version`, `trace_id`, evidence refs, proposal artifact, scores, rollback patch, and next actions.

### Phase 7 System Evolution Loop Work

- Audited existing M3 system-evolution foundations:
  - `agentic_m3_system_evolution.jsonl`,
  - `m3_system_evolution_gate.py`,
  - failure clustering,
  - one-variable policy candidate generation,
  - offline replay gate,
  - canary dry-run plan,
  - rollback status,
  - local scripts for offline eval, nightly improve loop, and canary dry-run,
  - backend policy registry status transitions.
- Reused the existing M3 eval/canary/rollback functions instead of duplicating the scoring and policy-candidate logic.
- Added `pythontopia_graph.runtime.system_evolution_loop` with a deterministic, governance-bound six-stage system loop:
  - `Trace`: summarizes eval traces, stable ids, loop types, policy versions, and trace ids,
  - `Eval`: combines offline replay gate with required regression/adversarial/long-running/audit suite results,
  - `Cluster`: reuses M3 failure clustering across failed traces,
  - `Improve`: reuses one-variable `PolicyCandidate`,
  - `Deploy`: builds blocked/canary dry-run/rollback-required deployment state,
  - `Monitor`: maps schema validity, regression rate, cost ratio, live error rate, and sample size into continue/block/rollback decisions.
- Added `POST /runtime/system-evolution-runs` in the graph service so API consumers can display policy-evolution state without frontend fake data.
- Added stricter Phase 7 gates beyond M3:
  - required suite categories are `regression`, `adversarial`, `long_running`, and `audit`,
  - failing or missing suite categories block deploy,
  - offline replay regression blocks deploy,
  - monitor regressions return rollback next actions.
- Returned a frozen-contract `LoopResult` with `IMPROVE`, `policy_version`, `trace_id`, eval trace evidence refs, candidate/deployment artifacts, scores, monitor decision, and next actions.

### Phase 8 Frontend And Teacher Operations Work

- Audited existing runtime/operations surfaces:
  - frontend Vite proxy only exposes `/api` through Spring Boot,
  - `AiRuntimeSummaryPanel` already consumes `/api/ai/runtime/summary`,
  - `WorkshopPage` already has a compact "调用过程" drawer,
  - `TeacherReviewPage` already provides a teacher review surface,
  - backend `AiRuntimeSummaryService` already combines protocol, evidence graph, memory, Twin, and browse safety gates.
- Added backend `AiRuntimeOperationsDto` and `GET /api/ai/runtime/operations`:
  - student/teacher/admin readable,
  - derives loop operation state from real protocol/evidence/Twin summary,
  - exposes five loop families: `LEARNER`, `DEBUG`, `RESOURCE`, `WORLD`, `IMPROVE`,
  - exposes governance items for resource review, world patch review, and policy canary review.
- Kept `/api/ai/runtime/summary` student-only; teacher operations use the new operations endpoint instead of weakening the original summary permission.
- Added frontend `fetchAiRuntimeOperations()` with explicit DTO types.
- Added `RuntimeOperationsPanel.vue`, a compact API-backed operations panel that renders loop state and governance items without fake PASS status.
- Wired `RuntimeOperationsPanel` into:
  - `WorkshopPage` call-process drawer for student-facing loop visibility,
  - `TeacherReviewPage` for teacher governance visibility beside the existing AI problem review queue.
- Added fail-safe display behavior:
  - insufficient evidence remains `证据不足`,
  - warn states remain `需关注`,
  - the panel surfaces next actions and evidence refs instead of explanatory filler.

### Phase 9 Full-Chain Testing And Beta Handoff Work

- Extended `frontend/e2e/agenticDeepLoop.spec.ts` with final runtime operations acceptance:
  - `GET /api/ai/runtime/operations` must expose `LEARNER / DEBUG / RESOURCE / WORLD / IMPROVE`,
  - governance must expose `resource_review / world_patch_review / policy_canary_review`,
  - loop and governance rows must carry real labels, next actions, review routes, and evidence refs.
- Added browser-level coverage that opens the workshop "调用过程" drawer and verifies the real `RuntimeOperationsPanel` renders "闭环运维状态" and governance content.
- Started the local stack through root `./dev.sh` and confirmed:
  - backend `:18080` healthy,
  - graph `:18091` healthy,
  - rag `:18092` healthy,
  - Vite frontend `:5173` healthy.
- Ran the full local verification matrix for the Agentic Runtime slice:
  - graph runtime/gates,
  - RAG service tests,
  - backend runtime/summary/control-plane tests,
  - frontend typecheck,
  - Phase 8 API/component/page tests,
  - Playwright browser deep loop.
- Added `docs/reports/2026-07-01-agentic-runtime-phase9-handoff.md` with verification matrix, browser coverage, boundaries, and demo notes.
- Added `frontend/e2e/agenticDeepMatrix.spec.ts` with 153 additional deep-chain checks.
- Ran the requested 100+ E2E acceptance: original 32-test deep loop plus 153-test matrix, total 185 passed.
- Added `docs/reports/2026-07-01-agentic-runtime-100-e2e-results.md` with a tabular result summary.

## In Progress

- None.

## Next

1. Keep future production work scoped to external-provider eval, real canary traffic, or scheduled telemetry ingestion; those are outside this local Phase 9 handoff.

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
| Reuse `WorldLoopProposal` for Phase 6 | M2 already froze evidence-bound world proposal semantics; Phase 6 adds runtime production around that contract |
| Keep Phase 6 rewards non-economic | R-14/R-15 removed inventory, currency, and shop systems from the learning mainline |
| Emit rollback patches with previous values | World updates need review/rollback visibility before Phase 8 exposes teacher operations |
| Reuse M3 evolution functions for Phase 7 | M3 already owns clustering, candidate generation, offline replay, canary dry-run, and rollback metadata |
| Require four eval suite categories before deploy | Phase 7 raises the gate from one M3 dataset to regression/adversarial/long-running/audit coverage |
| Treat monitor violations as rollback actions | A canary candidate must not proceed when schema validity, regression rate, cost ratio, or live error rate crosses rules |
| Put Phase 8 frontend access behind Spring `/api` | Vite only proxies `/api`; direct graph `/runtime` calls would bypass backend auth and client contracts |
| Add operations endpoint instead of loosening student summary | Teachers need governance visibility, but the existing summary endpoint was intentionally student scoped |
| Reuse one operations panel across student and teacher surfaces | Keeps UI/API semantics consistent and avoids a second fake teacher-only runtime model |
| Make Phase 9 browser acceptance assert runtime operations UI directly | Phase 8 must be proven in the same full-chain flow, not only in component tests |
| Treat local provider-free verification as the Beta handoff boundary | Online LLM/provider quality eval and production canary traffic need separate credentials, traffic policy, and operations windows |

## Changed Files

Phase 8 intended files:

- `backend/src/main/java/com/pythontopia/ai/runtime/AiRuntimeOperationsDto.java`
- `backend/src/main/java/com/pythontopia/ai/runtime/AiRuntimeSummaryController.java`
- `backend/src/main/java/com/pythontopia/ai/runtime/AiRuntimeSummaryService.java`
- `backend/src/test/java/com/pythontopia/ai/runtime/AiRuntimeSummaryControllerTest.java`
- `backend/src/test/java/com/pythontopia/ai/runtime/AiRuntimeSummaryServiceTest.java`
- `frontend/src/api/aiRuntime.ts`
- `frontend/src/components/workshop/RuntimeOperationsPanel.vue`
- `frontend/src/pages/WorkshopPage.vue`
- `frontend/src/pages/teacher/TeacherReviewPage.vue`
- `frontend/tests/api/aiRuntime.spec.ts`
- `frontend/tests/components/workshop/RuntimeOperationsPanel.spec.ts`
- `frontend/tests/pages/WorkshopPage.spec.ts`
- `frontend/tests/pages/TeacherReviewPage.spec.ts`
- `docs/agentic-runtime-progress.md`
- `docs/agentic-runtime-plan.md`
- `docs/project/CHANGELOG.md`

Phase 9 intended files:

- `frontend/e2e/agenticDeepLoop.spec.ts`
- `frontend/e2e/agenticDeepMatrix.spec.ts`
- `docs/reports/2026-07-01-agentic-runtime-phase9-handoff.md`
- `docs/reports/2026-07-01-agentic-runtime-100-e2e-results.md`
- `docs/agentic-runtime-progress.md`
- `docs/project/CHANGELOG.md`

## Tests

| Time | Command | Result |
|---|---|---|
| 2026-06-30 | `git diff --check` | passed |
| 2026-06-30 | `rg -n "Repository State Confirmation|Phase 0|Current Phase|Verification Matrix|Commit Rules|Agentic Runtime Phase 0" docs/agentic-runtime-plan.md docs/agentic-runtime-progress.md docs/project/CHANGELOG.md` | passed: required headings and changelog entry found |
| 2026-06-30 | `git status --short -- docs/agentic-runtime-plan.md docs/agentic-runtime-progress.md docs/project/CHANGELOG.md` | passed: only Phase 0 docs/changelog listed for this scope |
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
| 2026-06-30 | `python3 -m pytest -q tests/test_world_state_loop.py` | RED: failed at collection because `pythontopia_graph.runtime.world_state_loop` did not exist |
| 2026-06-30 | `python3 -m pytest -q tests/test_world_state_loop.py` | passed: 6 tests covering full world loop, missing evidence, forbidden economy reward, rollback patch, page-read follow-up, and API-visible route |
| 2026-06-30 | `python3 -m pytest -q tests/test_world_state_loop.py tests/test_m2_resource_world_gate.py tests/test_loop_runtime.py tests/test_loop_contract.py tests/test_resource_lifecycle_loop.py tests/test_code_debug_loop.py tests/test_student_learning_loop.py tests/test_m1_golden_gate.py tests/test_m3_system_evolution_gate.py` | passed: 55 tests |
| 2026-06-30 | `git diff --check` | passed |
| 2026-06-30 | `python3 -m pytest -q tests/test_system_evolution_loop.py` | RED: failed at collection because `pythontopia_graph.runtime.system_evolution_loop` did not exist |
| 2026-06-30 | `python3 -m pytest -q tests/test_system_evolution_loop.py` | passed: 5 tests covering dry-run success, monitor rollback, adversarial suite block, offline regression block, and API-visible route |
| 2026-06-30 | `python3 -m pytest -q tests/test_system_evolution_loop.py tests/test_m3_system_evolution_gate.py tests/test_loop_runtime.py tests/test_loop_contract.py tests/test_world_state_loop.py tests/test_resource_lifecycle_loop.py tests/test_code_debug_loop.py tests/test_student_learning_loop.py tests/test_m1_golden_gate.py tests/test_m2_resource_world_gate.py` | passed: 60 tests |
| 2026-06-30 | `git diff --check` | passed |
| 2026-06-30 | `mvn --settings .mvn/settings.build.xml -q -Dtest=AiRuntimeSummaryServiceTest,AiRuntimeSummaryControllerTest test` | RED: failed because `AiRuntimeOperationsDto`, `operationsForUser`, and controller `operations` did not exist |
| 2026-06-30 | `npm run test -- --run tests/api/aiRuntime.spec.ts tests/components/workshop/RuntimeOperationsPanel.spec.ts tests/pages/WorkshopPage.spec.ts tests/pages/TeacherReviewPage.spec.ts` | RED: failed because `fetchAiRuntimeOperations`, `RuntimeOperationsPanel.vue`, and page wiring did not exist |
| 2026-06-30 | `mvn --settings .mvn/settings.build.xml -q -Dtest=AiRuntimeSummaryServiceTest,AiRuntimeSummaryControllerTest test` | passed |
| 2026-06-30 | `npm run test -- --run tests/api/aiRuntime.spec.ts tests/components/workshop/RuntimeOperationsPanel.spec.ts tests/pages/WorkshopPage.spec.ts tests/pages/TeacherReviewPage.spec.ts` | passed: 28 tests; existing Pyodide sourcemap warning remains; jsdom printed a non-failing ECONNREFUSED log from existing async learning-event noise |
| 2026-06-30 | `npm run typecheck` | passed |
| 2026-06-30 | `mvn --settings .mvn/settings.build.xml -q -Dtest=AiRuntimeSummaryServiceTest,AiRuntimeSummaryControllerTest,AgentRuntimeControllerTest,LoopControllerTest test` | passed |
| 2026-06-30 | `git diff --check` | passed |
| 2026-07-01 | `python3 -m pytest -q tests/test_system_evolution_loop.py tests/test_m3_system_evolution_gate.py tests/test_loop_runtime.py tests/test_loop_contract.py tests/test_world_state_loop.py tests/test_resource_lifecycle_loop.py tests/test_code_debug_loop.py tests/test_student_learning_loop.py tests/test_m1_golden_gate.py tests/test_m2_resource_world_gate.py` | passed: 60 tests |
| 2026-07-01 | `python3 -m pytest -q` in `services/pythontopia-rag` | passed: 129 tests |
| 2026-07-01 | `mvn --settings .mvn/settings.build.xml -q -Dtest=AiRuntimeSummaryServiceTest,AiRuntimeSummaryControllerTest,AgentRuntimeControllerTest,LoopControllerTest test` | passed: exit 0 |
| 2026-07-01 | `npm run typecheck` | passed: exit 0 |
| 2026-07-01 | `npm run test -- --run tests/api/aiRuntime.spec.ts tests/components/workshop/RuntimeOperationsPanel.spec.ts tests/pages/WorkshopPage.spec.ts tests/pages/TeacherReviewPage.spec.ts` | passed: 28 tests; existing Pyodide sourcemap warning and non-failing jsdom ECONNREFUSED log remain |
| 2026-07-01 | `./dev.sh` | passed: Postgres, backend, graph, rag, and Vite reached ready state |
| 2026-07-01 | `curl --noproxy '*' -fsS http://127.0.0.1:18080/healthz` | passed: `{"status":"ok"}` |
| 2026-07-01 | `curl --noproxy '*' -fsS http://127.0.0.1:18091/healthz` | passed: `{"status":"ok"}` |
| 2026-07-01 | `curl --noproxy '*' -fsS http://127.0.0.1:18092/healthz` | passed: `{"status":"ok"}` |
| 2026-07-01 | `curl --noproxy '*' -fsS http://127.0.0.1:5173/healthz` | passed: `{"status":"ok"}` |
| 2026-07-01 | `PYT_E2E_REGISTER=1 npx playwright test e2e/agenticDeepLoop.spec.ts --project=chromium --workers=1` | passed: 32 tests / 1.5m after runtime operations API and call-drawer UI assertions |
| 2026-07-01 | `npx playwright test e2e/agenticDeepMatrix.spec.ts --project=chromium --list` | passed: listed 153 tests |
| 2026-07-01 | `PYT_E2E_REGISTER=1 npx playwright test e2e/agenticDeepLoop.spec.ts e2e/agenticDeepMatrix.spec.ts --project=chromium --workers=1` | passed: 185 tests / 3.4m |
| 2026-07-01 | `git diff --check` | passed |

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
- Phase 6 emits graph-side proposal and rollback state; backend persistence/application of world patches remains a later integration concern unless Phase 8/9 wires it earlier.
- Phase 6 normalizes world state to `town_hub`; any legacy peripheral-map client state should be treated as input drift and not revived as a mainline scene.
- Phase 7 is graph-side runtime and API-visible status; actual production traffic routing remains dry-run/human-approved per existing canary policy.
- Phase 7 monitor decisions consume snapshots rather than reading live telemetry directly; wiring scheduled telemetry ingestion remains later deployment/ops work.
- Phase 8 operations status is derived from current runtime/evidence summaries; it is a visibility/governance surface, not an automatic promotion or rollback executor.
- Phase 9 local verification does not claim external LLM/provider-backed quality evaluation, production canary traffic, or scheduled telemetry ingestion.

## Changelog Links

- Phase 8 changelog entry in `docs/project/CHANGELOG.md`.
- Phase 9 changelog entry in `docs/project/CHANGELOG.md`.

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
| Phase 5 docs | `c8ece464` | Recorded Phase 5 commit hash |
| Phase 6 | `89795575` | Game world loop implementation and tests |
| Phase 6 docs | `e6cac5c9` | Recorded Phase 6 commit hash |
| Phase 7 | `396fe6cb` | System evolution loop implementation and tests |
| Phase 7 docs | `03d65f69` | Recorded Phase 7 commit hash |
| Phase 8 | `1fe47064` | Frontend and teacher operations |
| Phase 8 docs | `8500421a` | Recorded Phase 8 commit hash |
| Phase 9 | `c590bf40` | Full-chain testing and Beta handoff |
| Phase 9 100+ E2E | `cc1496ea` | 153-test deep matrix plus 185-test combined browser run |
