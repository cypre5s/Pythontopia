# AI Guide Autonomous Planning Chain Deep Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Upgrade AI 导学助手 from a single-step bounded coach into a deep autonomous planning chain that can decompose a tutoring goal into multiple steps, adapt long-term learning paths from evidence, and update transfer-learning profile signals while keeping student confirmation on consequential changes.

**Architecture:** Use the approved Option A boundary: the agent may autonomously build and revise a per-problem episode plan and propose long-term path changes, but it may not silently replace the active learning path or force a transfer exercise. The graph service owns structured agent decisions and educational tool orchestration; Spring Boot owns persistence, permissions, evidence validation, mastery/profile updates, and path proposal confirmation; Vue only renders plans, cards, proposals, and confirmation actions. Every generated card or transfer exercise remains schema-validated, guardrailed, and auditable.

**Tech Stack:** FastAPI, LangGraph StateGraph, Pydantic structured outputs, Spring Boot 3 / Java 21, PostgreSQL / Flyway, existing judge sandbox services, existing path planner graph, Vue 3, TypeScript strict, Pinia, Vitest, Maven, pytest.

---

## Product Decision

The chosen boundary is **Option A: deep but bounded autonomy**.

- The AI guide can maintain a multi-step tutoring episode for the current problem.
- The AI guide can revise that episode after card feedback and later submissions.
- The AI guide can propose a draft learning path when evidence shows the active path is stale.
- The AI guide can generate and validate transfer practice, then ask the student to accept it.
- The AI guide cannot silently activate a new learning path.
- The AI guide cannot silently start a transfer attempt.
- The AI guide cannot reveal full answers, hidden tests, raw chain-of-thought, or fabricated course references.
- The implementation remains no-ReAct by default: bounded planning, bounded reflection, explicit stop conditions, and student-triggered progression.

## Current Anchors

Use the shipped manager-agent implementation as the base:

- Current plan/review record: `docs/reports/2026-06-16-guide-coach-plan-review.md`
- First-wave plan: `docs/plans/2026-06-16-guide-coach-agent-education-agent-refactor.md`
- Training integration: `frontend/src/components/training/TrainingPanel.vue`
- Guide nudge: `frontend/src/components/training/GuideNudgeCard.vue`
- Workflow panel: `frontend/src/components/training/TutorWorkflowPanel.vue`
- Tutor workflow API: `frontend/src/api/tutorWorkflow.ts`
- Tutor workflow backend: `backend/src/main/java/com/pythontopia/tutor/*`
- Tutor workflow DB: `backend/src/main/resources/db/migration/V161__tutor_workflow.sql`, `backend/src/main/resources/db/migration/V165__tutor_workflow_agent_state.sql`
- Tutor workflow graph: `services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py`
- Current manager agent: `services/pythontopia-graph/pythontopia_graph/agents/guide_coach_agent.py`
- Current cards: `services/pythontopia-graph/pythontopia_graph/agents/tutor_workflow_cards.py`
- Current guardrail: `services/pythontopia-graph/pythontopia_graph/agents/guide_guardrail_critic.py`
- Current schemas: `services/pythontopia-graph/pythontopia_graph/schemas/tutor_workflow.py`
- Path planner graph route: `services/pythontopia-graph/pythontopia_graph/routes/path.py`
- Path planner agent: `services/pythontopia-graph/pythontopia_graph/agents/path_planner_agent.py`
- Java path service: `backend/src/main/java/com/pythontopia/learner/path/service/LearningPathService.java`
- Learning events: `backend/src/main/java/com/pythontopia/learner/event/LearningEvent.java`
- Mastery evaluator: `backend/src/main/java/com/pythontopia/learner/mastery/MasteryEvaluator.java`
- Passive profile updates: `backend/src/main/java/com/pythontopia/learner/profile/PassiveProfileRevisionListener.java`
- Existing judge sandbox: `backend/src/main/java/com/pythontopia/submission/judge/*`
- Existing boss generated problem validators: `services/pythontopia-graph/pythontopia_graph/agents/boss/*`

## Target User Experience

### Current Behavior

The student submits code. The UI gives a short nudge. After clicking continue, the manager agent chooses one card: execution trace, error diagnosis, knowledge review, AC review, transfer practice, and so on.

### Target Behavior

The student submits code. The UI gives the same short nudge. After clicking continue, the guide says, in a compact visible plan:

```text
我打算分三步陪你处理：
1. 先看变量怎么跑偏
2. 再补一下 range 边界
3. 最后让你自己改一次并重新提交
```

Only the first step card is generated immediately. The student controls every continuation. Later submissions update the episode. If the student ACs, the guide can create a validated transfer practice. If repeated evidence shows this KC keeps blocking progress, the guide proposes a learning path draft and asks the student whether to accept it.

## Target Architecture

```text
TrainingPanel / TutorWorkflowPanel
  -> TutorWorkflowController
  -> TutorWorkflowService
    -> restore TutorStudentState
    -> restore TutorEpisodePlan
    -> enrich with problem, diagnosis, profile, mastery, recent episode events
    -> graph /tutor/workflow/runs
      -> validate_transition
      -> coach_decide
      -> episode_plan_or_revise
      -> fetch_evidence
      -> generate_current_step_card
      -> guardrail
      -> decide_actions
    -> persist card, coach state, episode plan, action event
  -> render current card + episode timeline + recommended next step
  -> student click / feedback / submit
  -> action event + LearningEvent
  -> episode advancement
  -> optional transfer practice
  -> optional path adaptation proposal
```

## Deep Concepts

### `TutorEpisodePlan`

A durable short-horizon plan for one problem/session.

```python
class TutorEpisodeStep(BaseModel):
    step_id: str
    event: WorkflowEvent
    tool_name: str
    title: str
    goal: str
    student_visible_reason: str
    stop_condition: str
    status: Literal["PENDING", "ACTIVE", "DONE", "SKIPPED", "REVISED"] = "PENDING"
    depends_on: list[str] = Field(default_factory=list)
    expected_evidence: Literal[
        "CARD_VIEWED",
        "CHECKPOINT_COMPLETED",
        "ACTION_CLICKED",
        "SUBMISSION_OBSERVED",
        "AC_SUBMISSION",
        "TRANSFER_ATTEMPT",
    ] = "CARD_VIEWED"

class TutorEpisodePlan(BaseModel):
    plan_id: str
    session_id: str
    problem_id: int
    user_id: int
    objective: str
    trigger_event: WorkflowEvent
    trigger_verdict: str = ""
    revision: int = 0
    current_step_id: str
    steps: list[TutorEpisodeStep]
    stop_policy: Literal["WAIT_FOR_CLICK", "WAIT_FOR_SUBMISSION", "DONE"] = "WAIT_FOR_CLICK"
    revision_reason: str = ""
    created_by: Literal["coach", "fallback"] = "coach"
    evidence_summary: str = ""
```

### `PathAdaptationProposal`

A proposal generated from accumulated evidence. It is never auto-promoted.

```java
public record PathAdaptationProposalDto(
    Long id,
    Long draftPathId,
    String status,
    String reasonSummary,
    List<String> evidenceEventIds,
    List<String> affectedKcCodes,
    String sourceSessionKey
) {}
```

### `TransferPracticeDraft`

A validated transfer exercise derived from AC review and hidden from normal course listings until accepted as a guide transfer attempt.

```python
class TransferPracticeDraft(BaseModel):
    source_problem_id: int
    kc_code: str
    transfer_axis: Literal["constraint", "input_shape", "edge_case", "near_kc", "real_context"]
    title: str
    statement_markdown: str
    input_description: str
    output_description: str
    samples: list[TestCase]
    hidden_test_cases: list[TestCase]
    reference_solution: str
    hints: list[str] = Field(default_factory=list)
    validation: dict[str, Any] = Field(default_factory=dict)
```

The student sees only title, statement, input/output description, samples, and hints. Hidden tests and reference solution never leave backend/graph internal boundaries.

## Acceptance Criteria

- AC/WA/CE/RE/TLE nudge remains one short sentence plus explicit click.
- Every workflow run returns at most one newly generated card.
- The UI can show a 2-4 step episode plan without exposing raw reasoning.
- Episode steps advance only through explicit student actions, checkpoint completion, or later submissions.
- Repeated WA/RE/TLE can revise the episode plan and raise hint level, but cannot dump full code.
- AC review can generate a transfer practice draft with hidden tests and reference solution validated before persistence.
- Transfer practice can be accepted and attempted from the guide UI.
- Transfer attempts publish learning events and update profile/mastery signals.
- Long-term path adaptation creates a DRAFT learning path and a proposal, never silently replacing ACTIVE.
- Students can accept or dismiss the path proposal.
- All generated resources have structured outputs, guardrail results, and audit events.
- Full local graph/backend/frontend gates pass before claiming completion.

## Non-Goals

- Do not enable open-ended ReAct.
- Do not auto-replace active learning paths.
- Do not add teacher override dashboards in this plan.
- Do not merge 百晓工坊 and AI 导学助手.
- Do not expose hidden tests, reference solutions, raw prompt, raw chain-of-thought, or internal score matrices.
- Do not make generated transfer problems appear in the normal course problem list unless the student accepts a transfer attempt surface that explicitly labels it as AI guide transfer practice.

---

## Task 0: Baseline And Safety Snapshot

**Files:**
- Read: `docs/reports/2026-06-16-guide-coach-plan-review.md`
- Read: `docs/plans/2026-06-16-guide-coach-agent-education-agent-refactor.md`
- Read: `frontend/src/components/training/TutorWorkflowPanel.vue`
- Read: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java`
- Read: `services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py`
- Read: `services/pythontopia-graph/pythontopia_graph/agents/guide_coach_agent.py`

**Step 1: Capture current git state**

Run:

```bash
git status --short
git log --oneline -8
```

Expected: identify unrelated dirty files before touching anything. Do not revert user changes.

**Step 2: Run focused current guide tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_tutor_workflow_coach_schema.py tests/test_guide_coach_agent.py tests/test_tutor_workflow_coach_graph.py tests/test_guide_guardrail_critic.py tests/test_guide_coach_golden.py tests/test_sse_agui.py
```

Expected: existing guide tests pass.

**Step 3: Run backend focused tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,TutorWorkflowSubmissionFeedbackListenerTest,SubmissionServiceTest test
```

Expected: existing guide/backend tests pass.

**Step 4: Run frontend focused tests**

Run:

```bash
cd frontend
npm run test -- tests/components/training/GuideNudgeCard.spec.ts tests/components/training/TutorWorkflowPanel.spec.ts tests/components/TrainingPanel.spec.ts
npm run typecheck
```

Expected: existing guide UI tests and typecheck pass.

**Step 5: Commit**

Do not commit in Task 0 unless the baseline run required fixing an unrelated broken test. If a fix was required, commit it separately:

```bash
git add <fixed-files>
git commit -m "test(guide): restore baseline before planning chain work"
```

---

## Task 1: Graph Episode Plan Schema

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/schemas/tutor_workflow.py`
- Test: `services/pythontopia-graph/tests/test_tutor_episode_plan_schema.py`

**Step 1: Write failing schema tests**

Create tests that assert:

- `TutorEpisodePlan` accepts 2-4 steps.
- Exactly one step can be `ACTIVE`.
- `current_step_id` must exist in `steps`.
- Step events must be valid `WorkflowEvent`.
- `reason_summary` and `student_visible_reason` must not contain raw reasoning markers such as `chain-of-thought`, `hidden reasoning`, `system prompt`.
- Plans longer than 4 steps are rejected.

Example test shape:

```python
def test_episode_plan_requires_current_step_in_steps():
    with pytest.raises(ValidationError):
        TutorEpisodePlan(
            plan_id="ep-1",
            session_id="s-1",
            problem_id=476,
            user_id=1,
            objective="Fix wrong answer",
            trigger_event="ERROR_FEEDBACK",
            current_step_id="missing",
            steps=[episode_step("s1", "EXECUTION_TRACE", "ACTIVE")],
        )
```

**Step 2: Run test to verify it fails**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_tutor_episode_plan_schema.py
```

Expected: FAIL because schema does not exist.

**Step 3: Add schemas**

In `tutor_workflow.py`, add:

- `TutorEpisodeStepStatus`
- `TutorEpisodeExpectedEvidence`
- `TutorEpisodeStep`
- `TutorEpisodePlan`
- `TutorEpisodePlanPatch`

Keep them near existing `TutorStudentState` and `GuideCoachDecision` to keep tutor workflow contracts in one schema file.

**Step 4: Extend run request/result**

Modify existing `WorkflowRunRequest` and `WorkflowRunResult`:

```python
episode_plan: TutorEpisodePlan | None = None
episode_patch: TutorEpisodePlanPatch | None = None
```

Keep fields optional for backward compatibility.

**Step 5: Run schema tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_tutor_episode_plan_schema.py
```

Expected: PASS.

**Step 6: Run existing tutor workflow schema tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_tutor_workflow_coach_schema.py tests/test_guide_coach_agent.py
```

Expected: PASS.

**Step 7: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/schemas/tutor_workflow.py services/pythontopia-graph/tests/test_tutor_episode_plan_schema.py
git commit -m "feat(guide): add tutor episode plan schema"
```

---

## Task 2: Guide Episode Planner Agent

**Files:**
- Create: `services/pythontopia-graph/pythontopia_graph/agents/guide_episode_planner.py`
- Test: `services/pythontopia-graph/tests/test_guide_episode_planner.py`
- Test fixture: `services/pythontopia-graph/tests/fixtures/guide_episode_planner_golden.json`

**Step 1: Write failing golden tests**

Create golden scenarios:

- `WA_first_attempt`: plan is `EXECUTION_TRACE -> KNOWLEDGE_REVIEW -> SKELETON or CODING`.
- `CE`: plan is `ERROR_FEEDBACK -> SKELETON or CODING`.
- `WA_repeated_3`: plan starts with low-load action: `VISUALIZE`, `PARSONS`, or `KNOWLEDGE_REVIEW`.
- `AC_after_retries`: plan is `AC_REVIEW -> TRANSFER`.
- `student_clicked_knowledge_review`: plan starts with `KNOWLEDGE_REVIEW` if legal.

Assert:

- 2-4 steps.
- Step events are within `allowed_events`.
- First step equals `GuideCoachDecision.next_event`.
- No step has full-answer labels like `COMPLETE_SOLUTION`.
- Each step has `goal`, `stop_condition`, and `student_visible_reason`.

**Step 2: Run test to verify it fails**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_guide_episode_planner.py
```

Expected: FAIL because `GuideEpisodePlanner` does not exist.

**Step 3: Implement deterministic-first planner**

Create `GuideEpisodePlanner` with:

```python
class GuideEpisodePlanner:
    def plan(self, ctx: CoachContext, decision: GuideCoachDecision) -> TutorEpisodePlan:
        ...

    def revise(
        self,
        ctx: CoachContext,
        previous: TutorEpisodePlan,
        decision: GuideCoachDecision,
        evidence: EpisodeRevisionEvidence,
    ) -> TutorEpisodePlan:
        ...
```

Rules:

- Always make the first active step match `decision.next_event`.
- AC plans include `AC_REVIEW` then `TRANSFER` if transfer is allowed.
- WA first plans prefer execution trace before error diagnosis when `EXECUTION_TRACE` is allowed.
- Repeated errors lower cognitive load.
- Student requested actions remain first when legal and not unsafe.
- Revision bumps `revision += 1` and marks obsolete pending steps as `REVISED`.
- Fallback returns a 2-step plan: current decision step + checkpoint/submit step.

**Step 4: Add optional LLM structured planning**

Use LLM only when deterministic rules do not cover the case. The LLM must output JSON and may only select from allowed events. Invalid LLM output falls back to deterministic plan.

Prompt rules:

- No chain-of-thought.
- Use short student-visible reasons.
- Do not include full code.
- Do not invent course pages.
- Max 4 steps.

**Step 5: Run planner tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_guide_episode_planner.py
```

Expected: PASS.

**Step 6: Run coach tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_guide_coach_agent.py tests/test_guide_coach_golden.py tests/test_tutor_episode_plan_schema.py
```

Expected: PASS.

**Step 7: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/agents/guide_episode_planner.py services/pythontopia-graph/tests/test_guide_episode_planner.py services/pythontopia-graph/tests/fixtures/guide_episode_planner_golden.json
git commit -m "feat(guide): plan bounded tutoring episodes"
```

---

## Task 3: Graph Episode Node Integration

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/routes/tutor_workflow.py`
- Test: `services/pythontopia-graph/tests/test_tutor_workflow_episode_graph.py`

**Step 1: Write failing graph tests**

Test cases:

- `ERROR_FEEDBACK` request with WA returns `episode_plan` and generates only the first card.
- A restored `episode_plan` with next pending step can be advanced by clicking the recommended action.
- A later failed submission revises the episode plan when `consecutive_errors >= 3`.
- `coach_mode=false` still behaves like old path and does not require episode plan.
- SSE traces include `coach.plan_episode` or `coach.revise_episode` but do not expose raw prompt.

**Step 2: Run test to verify it fails**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_tutor_workflow_episode_graph.py
```

Expected: FAIL because graph has no episode node.

**Step 3: Add graph node**

In `TutorWorkflowGraph._build`, insert after `coach_decide`:

```text
coach_decide -> episode_plan -> fetch_evidence
```

Add `_node_episode_plan`:

- If `coach_mode=false`, pass through.
- If request contains no `episode_plan`, create one.
- If request contains an episode plan and current evidence implies revision, revise it.
- Set `effective_event` to the active step event.
- Set `episode_plan` in graph state.
- Emit `coach.plan_episode` or `coach.revise_episode`.

**Step 4: Keep one-card rule**

Ensure `_node_generate_card` still generates only `effective_event`, never every episode step.

**Step 5: Result projection**

Modify route result so `WorkflowRunResult` includes:

- `episode_plan`
- `episode_patch` if using patch semantics
- actual `event` as the active step event
- `agentic=true`

**Step 6: Run graph episode tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_tutor_workflow_episode_graph.py
```

Expected: PASS.

**Step 7: Run all guide graph tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_tutor_workflow_coach_graph.py tests/test_tutor_workflow_episode_graph.py tests/test_guide_guardrail_critic.py tests/test_sse_agui.py
```

Expected: PASS.

**Step 8: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py services/pythontopia-graph/pythontopia_graph/routes/tutor_workflow.py services/pythontopia-graph/tests/test_tutor_workflow_episode_graph.py
git commit -m "feat(guide): integrate episode planning into tutor graph"
```

---

## Task 4: Episode Guardrail And Plan Safety

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/agents/guide_guardrail_critic.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py`
- Test: `services/pythontopia-graph/tests/test_guide_episode_guardrail.py`

**Step 1: Write failing guardrail tests**

Assert that a plan fails when:

- Any step asks for full code.
- Any step references hidden tests.
- Any step exposes raw reasoning.
- `TRANSFER` appears before `AC_REVIEW`.
- Plan has more than 4 steps.
- Plan has no stop condition.

**Step 2: Run test to verify it fails**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_guide_episode_guardrail.py
```

Expected: FAIL.

**Step 3: Extend guardrail**

Add:

```python
def check_episode_plan(self, plan: TutorEpisodePlan) -> GuardrailResult:
    ...
```

Keep checks deterministic and high-confidence. Do not use an LLM for plan safety in this task.

**Step 4: Integrate guardrail**

Call `check_episode_plan` in `_node_episode_plan`. If failed:

- First failure: fallback to deterministic two-step plan.
- Second failure: return `GUIDE_EPISODE_GUARDRAIL_FAILED`.

**Step 5: Run tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_guide_episode_guardrail.py tests/test_tutor_workflow_episode_graph.py
```

Expected: PASS.

**Step 6: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/agents/guide_guardrail_critic.py services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py services/pythontopia-graph/tests/test_guide_episode_guardrail.py
git commit -m "feat(guide): guardrail episode plans"
```

---

## Task 5: Java DTOs For Episode Plan

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/tutor/graph/TutorGraphClient.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/dto/TutorWorkflowRunResult.java`
- Create: `backend/src/main/java/com/pythontopia/tutor/dto/TutorEpisodePlanDto.java`
- Create: `backend/src/main/java/com/pythontopia/tutor/dto/TutorEpisodeStepDto.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorGraphClientSseTest.java`

**Step 1: Write failing deserialization test**

Extend `TutorGraphClientSseTest` with an SSE result containing `episode_plan`. Assert Java DTO maps:

- `planId`
- `currentStepId`
- `revision`
- `steps[0].event`
- `steps[0].status`

**Step 2: Run test to verify it fails**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorGraphClientSseTest test
```

Expected: FAIL due missing DTO fields.

**Step 3: Add DTOs**

Use Java records:

```java
public record TutorEpisodePlanDto(
    @JsonProperty("plan_id") String planId,
    @JsonProperty("session_id") String sessionId,
    @JsonProperty("problem_id") Long problemId,
    @JsonProperty("user_id") Long userId,
    String objective,
    @JsonProperty("trigger_event") String triggerEvent,
    @JsonProperty("trigger_verdict") String triggerVerdict,
    int revision,
    @JsonProperty("current_step_id") String currentStepId,
    List<TutorEpisodeStepDto> steps,
    @JsonProperty("stop_policy") String stopPolicy,
    @JsonProperty("revision_reason") String revisionReason,
    @JsonProperty("evidence_summary") String evidenceSummary
) {}
```

**Step 4: Add result field**

Add `episodePlan` to `TutorWorkflowRunResult`.

**Step 5: Run test**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorGraphClientSseTest test
```

Expected: PASS.

**Step 6: Commit**

```bash
git add backend/src/main/java/com/pythontopia/tutor/dto/TutorEpisodePlanDto.java backend/src/main/java/com/pythontopia/tutor/dto/TutorEpisodeStepDto.java backend/src/main/java/com/pythontopia/tutor/dto/TutorWorkflowRunResult.java backend/src/main/java/com/pythontopia/tutor/graph/TutorGraphClient.java backend/src/test/java/com/pythontopia/tutor/TutorGraphClientSseTest.java
git commit -m "feat(guide): map episode plans from graph"
```

---

## Task 6: Episode Plan Persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V166__tutor_workflow_episode_plan.sql`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowRepository.java`
- Create: `backend/src/main/java/com/pythontopia/tutor/dto/TutorWorkflowEpisodePlanRecord.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowRepositoryTest.java`

**Step 1: Write failing repository tests**

Add tests for:

- Upsert episode plan by `session_id`.
- Find latest episode plan for session.
- Insert episode step event.
- Cascade delete via `tutor_workflow_session`.

**Step 2: Create migration**

Use next migration number after current `V165`.

```sql
-- Purpose: persist bounded AI guide episode plans and step evidence.
-- Tables affected: tutor_workflow_episode_plan, tutor_workflow_episode_step_event.
-- Irreversible risk: deleting these tables loses guide planning audit history, but not submissions or mastery.

CREATE TABLE tutor_workflow_episode_plan (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES tutor_workflow_session(id) ON DELETE CASCADE,
  plan_key VARCHAR(80) NOT NULL,
  revision INTEGER NOT NULL DEFAULT 0,
  current_step_id VARCHAR(80) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  plan_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_tutor_workflow_episode_session UNIQUE(session_id),
  CONSTRAINT ck_tutor_workflow_episode_status CHECK (status IN ('ACTIVE','DONE','ABANDONED','SUPERSEDED'))
);

CREATE TABLE tutor_workflow_episode_step_event (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES tutor_workflow_session(id) ON DELETE CASCADE,
  plan_id BIGINT REFERENCES tutor_workflow_episode_plan(id) ON DELETE SET NULL,
  step_id VARCHAR(80) NOT NULL,
  event_type VARCHAR(48) NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tutor_workflow_episode_session
  ON tutor_workflow_episode_plan(session_id, updated_at DESC);

CREATE INDEX idx_tutor_workflow_episode_step_event_session
  ON tutor_workflow_episode_step_event(session_id, created_at DESC);
```

**Step 3: Implement repository**

Add methods:

- `Optional<TutorWorkflowEpisodePlanRecord> findEpisodePlan(Long sessionId)`
- `void upsertEpisodePlan(Long sessionId, String planKey, int revision, String currentStepId, String status, String planJson)`
- `void insertEpisodeStepEvent(Long sessionId, Long planId, String stepId, String eventType, String payloadJson)`

**Step 4: Run repository tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowRepositoryTest test
```

Expected: PASS and Flyway validates migration.

**Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V166__tutor_workflow_episode_plan.sql backend/src/main/java/com/pythontopia/tutor/TutorWorkflowRepository.java backend/src/main/java/com/pythontopia/tutor/dto/TutorWorkflowEpisodePlanRecord.java backend/src/test/java/com/pythontopia/tutor/TutorWorkflowRepositoryTest.java
git commit -m "feat(guide): persist tutor episode plans"
```

---

## Task 7: Restore And Persist Episode Plans In Service

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowServiceTest.java`

**Step 1: Write failing service tests**

Add tests:

- `run` injects restored `episode_plan` into graph body.
- Graph result `episode_plan` is upserted.
- Card generation records both `CARD_GENERATED` and `EPISODE_STEP_STARTED`.
- `CHECKPOINT_COMPLETED` feedback records `EPISODE_STEP_COMPLETED` when the card belongs to current step.

**Step 2: Run test to verify it fails**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest test
```

Expected: FAIL.

**Step 3: Inject restored plan**

In `TutorWorkflowService.run`, after restoring agent state:

```java
repository.findEpisodePlan(session.id())
    .ifPresent(plan -> body.put("episode_plan", readJson(plan.planJson())));
```

**Step 4: Persist graph result**

In `persistProjection`, if result has episode plan:

- Upsert episode plan.
- Insert `EPISODE_STEP_STARTED` for current active step.
- Include `episode_plan_revision` and `current_step_id` in `CARD_GENERATED` payload.

**Step 5: Feedback integration**

In `recordFeedback`, when event type is `CHECKPOINT_COMPLETED`:

- Insert `EPISODE_STEP_COMPLETED`.
- Keep publishing `LearningEvent.GuideCheckpointCompleted`.

**Step 6: Run service tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest test
```

Expected: PASS.

**Step 7: Commit**

```bash
git add backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java backend/src/test/java/com/pythontopia/tutor/TutorWorkflowServiceTest.java
git commit -m "feat(guide): restore and persist episode plans"
```

---

## Task 8: Submission-Driven Episode Advancement And Revision

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowSubmissionFeedbackListener.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowSubmissionFeedbackListenerTest.java`

**Step 1: Write failing listener tests**

Add tests:

- Failure submission after active step increments attempts and records `EPISODE_STEP_EVIDENCE`.
- AC submission marks episode status `DONE` when stop policy expects `AC_SUBMISSION`.
- Repeated failures mark current step `REVISED_PENDING` or record revision-needed payload.
- Listener never calls graph directly.

**Step 2: Run test to verify it fails**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowSubmissionFeedbackListenerTest test
```

Expected: FAIL.

**Step 3: Add episode evidence update**

Extend listener to:

- Find current episode plan.
- Insert `EPISODE_STEP_EVIDENCE` event with verdict, accepted, attempt count.
- If accepted, mark plan `DONE` when current objective is complete.
- If failed and consecutive errors crosses threshold, mark plan status `ACTIVE` but include `needs_revision=true` in plan JSON or a step event.

Do not call graph in the listener. Revision happens on next student click/run.

**Step 4: Run tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowSubmissionFeedbackListenerTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/src/main/java/com/pythontopia/tutor/TutorWorkflowSubmissionFeedbackListener.java backend/src/test/java/com/pythontopia/tutor/TutorWorkflowSubmissionFeedbackListenerTest.java
git commit -m "feat(guide): advance episodes from submissions"
```

---

## Task 9: Frontend Episode Timeline Contract

**Files:**
- Modify: `frontend/src/api/tutorWorkflow.ts`
- Modify: `frontend/src/components/training/TutorWorkflowPanel.vue`
- Test: `frontend/tests/components/training/TutorWorkflowPanel.spec.ts`

**Step 1: Write failing UI tests**

Tests:

- Renders episode objective.
- Renders 2-4 step timeline.
- Highlights active step.
- Shows revised step label if status is `REVISED`.
- Does not hide non-recommended actions.
- Does not display internal `tool_name` or raw node names.

**Step 2: Run test to verify it fails**

Run:

```bash
cd frontend
npm run test -- tests/components/training/TutorWorkflowPanel.spec.ts
```

Expected: FAIL.

**Step 3: Add TS DTOs**

In `tutorWorkflow.ts`, add:

```ts
export interface TutorEpisodeStepDto {
  stepId: string;
  event: WorkflowEvent;
  title: string;
  goal: string;
  studentVisibleReason: string;
  stopCondition: string;
  status: 'PENDING' | 'ACTIVE' | 'DONE' | 'SKIPPED' | 'REVISED';
}

export interface TutorEpisodePlanDto {
  planId: string;
  objective: string;
  revision: number;
  currentStepId: string;
  steps: TutorEpisodeStepDto[];
  stopPolicy: string;
  revisionReason?: string;
  evidenceSummary?: string;
}
```

Add optional `episodePlan?: TutorEpisodePlanDto | null` to `WorkflowRunResultDto`.

**Step 4: Render timeline**

In `TutorWorkflowPanel.vue`:

- Add `episodePlan = ref<TutorEpisodePlanDto | null>(null)`.
- Update it after SSE result.
- Render compact section above cards.
- Use Chinese labels:
  - `ACTIVE`: 当前
  - `DONE`: 已完成
  - `PENDING`: 待继续
  - `REVISED`: 已调整
  - `SKIPPED`: 已跳过

**Step 5: Run UI tests**

Run:

```bash
cd frontend
npm run test -- tests/components/training/TutorWorkflowPanel.spec.ts
npm run typecheck
```

Expected: PASS.

**Step 6: Commit**

```bash
git add frontend/src/api/tutorWorkflow.ts frontend/src/components/training/TutorWorkflowPanel.vue frontend/tests/components/training/TutorWorkflowPanel.spec.ts
git commit -m "feat(guide): show tutor episode timeline"
```

---

## Task 10: Transfer Practice Internal Schema And Validator

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/schemas/tutor_workflow.py`
- Create: `services/pythontopia-graph/pythontopia_graph/agents/transfer_practice_validator.py`
- Test: `services/pythontopia-graph/tests/test_transfer_practice_validator.py`

**Step 1: Write failing validator tests**

Tests:

- Reject draft without reference solution.
- Reject draft with fewer than 3 test cases.
- Reject draft with no sample test case.
- Reject draft whose reference solution fails self-test.
- Reject draft with hidden-test tokens in public statement.
- Reject draft with KC code outside source problem KC/near-KC whitelist.
- Accept a valid simple transfer problem.

**Step 2: Run test to verify it fails**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_transfer_practice_validator.py
```

Expected: FAIL.

**Step 3: Add internal schema**

Add `TransferPracticeDraft`, `TransferTestCase`, `TransferValidationResult`.

Do not reuse public `TransferProblemCard` for internal validated practice; it intentionally hides judge details.

**Step 4: Implement validator**

Reuse existing boss validation concepts:

- Structural checks similar to `agents/boss/struct_validator.py`.
- Self-test logic similar to `agents/boss/self_tester.py`.
- KC grounding logic similar to `agents/boss/grounding.py`.

Keep imports local and focused; do not couple the guide to full boss mode.

**Step 5: Run validator tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_transfer_practice_validator.py
```

Expected: PASS.

**Step 6: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/schemas/tutor_workflow.py services/pythontopia-graph/pythontopia_graph/agents/transfer_practice_validator.py services/pythontopia-graph/tests/test_transfer_practice_validator.py
git commit -m "feat(guide): validate transfer practice drafts"
```

---

## Task 11: Transfer Practice Generation In Graph

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/agents/tutor_workflow_cards.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/routes/tutor_workflow.py`
- Test: `services/pythontopia-graph/tests/test_tutor_transfer_practice_graph.py`

**Step 1: Write failing graph tests**

Tests:

- After `AC_REVIEW`, `TRANSFER` can return both public transfer card and internal `transfer_practice_draft`.
- Draft is validated before result.
- Invalid draft is retried once.
- Second invalid draft fails with `TRANSFER_VALIDATION_FAILED`.
- Public result excludes hidden tests and reference solution.

**Step 2: Run test to verify it fails**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_tutor_transfer_practice_graph.py
```

Expected: FAIL.

**Step 3: Extend card generator**

Add a new method:

```python
async def transfer_practice_draft(
    self,
    problem: WorkflowProblemContext,
    next_practice_direction: str,
    profile_brief: str,
) -> TransferPracticeDraft:
    ...
```

Keep existing `transfer()` card method for public card rendering. The graph should call both for `TRANSFER`.

**Step 4: Extend graph state/result**

When event is `TRANSFER`:

- Generate public `transfer_problem` card.
- Generate internal `transfer_practice_draft`.
- Validate draft.
- Attach draft to result under internal-only field, for Java backend.

**Step 5: Run graph transfer tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_tutor_transfer_practice_graph.py
```

Expected: PASS.

**Step 6: Run guide graph tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_tutor_workflow_episode_graph.py tests/test_tutor_transfer_practice_graph.py tests/test_guide_guardrail_critic.py
```

Expected: PASS.

**Step 7: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/agents/tutor_workflow_cards.py services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py services/pythontopia-graph/pythontopia_graph/routes/tutor_workflow.py services/pythontopia-graph/tests/test_tutor_transfer_practice_graph.py
git commit -m "feat(guide): generate validated transfer practice"
```

---

## Task 12: Transfer Practice Persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V167__tutor_transfer_practice.sql`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowRepository.java`
- Create: `backend/src/main/java/com/pythontopia/tutor/dto/TutorTransferPracticeRecord.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowRepositoryTest.java`

**Step 1: Write failing repository tests**

Tests:

- Insert draft with public JSON and private judge JSON.
- Find draft by id and current user/session.
- Accept draft changes status to `ACCEPTED`.
- Attempt status is isolated from normal course problems.

**Step 2: Create migration**

```sql
-- Purpose: persist validated AI guide transfer practices without exposing hidden judge data.
-- Tables affected: tutor_transfer_practice, tutor_transfer_attempt.
-- Irreversible risk: deleting these tables loses generated transfer practice audit and attempts, but not course seed data.

CREATE TABLE tutor_transfer_practice (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES tutor_workflow_session(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  source_problem_id BIGINT NOT NULL REFERENCES course_problem(id) ON DELETE CASCADE,
  kc_code VARCHAR(32) NOT NULL,
  transfer_axis VARCHAR(32) NOT NULL,
  title VARCHAR(200) NOT NULL,
  public_problem_json JSONB NOT NULL,
  judge_payload_json JSONB NOT NULL,
  validation_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  accepted_at TIMESTAMPTZ,
  CONSTRAINT ck_tutor_transfer_practice_status CHECK (status IN ('DRAFT','ACCEPTED','DISMISSED','EXPIRED'))
);

CREATE TABLE tutor_transfer_attempt (
  id BIGSERIAL PRIMARY KEY,
  transfer_practice_id BIGINT NOT NULL REFERENCES tutor_transfer_practice(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  code TEXT NOT NULL,
  verdict VARCHAR(24) NOT NULL,
  passed_cases INTEGER NOT NULL DEFAULT 0,
  total_cases INTEGER NOT NULL DEFAULT 0,
  failure_reason TEXT,
  trace_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tutor_transfer_practice_user_status
  ON tutor_transfer_practice(user_id, status, created_at DESC);

CREATE INDEX idx_tutor_transfer_attempt_practice
  ON tutor_transfer_attempt(transfer_practice_id, created_at DESC);
```

**Step 3: Implement repository**

Add:

- `insertTransferPractice(...)`
- `findTransferPracticeForUser(userId, id)`
- `acceptTransferPractice(userId, id)`
- `dismissTransferPractice(userId, id)`
- `insertTransferAttempt(...)`

**Step 4: Run tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowRepositoryTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V167__tutor_transfer_practice.sql backend/src/main/java/com/pythontopia/tutor/TutorWorkflowRepository.java backend/src/main/java/com/pythontopia/tutor/dto/TutorTransferPracticeRecord.java backend/src/test/java/com/pythontopia/tutor/TutorWorkflowRepositoryTest.java
git commit -m "feat(guide): persist validated transfer practice"
```

---

## Task 13: Transfer Practice Service And Endpoints

**Files:**
- Create: `backend/src/main/java/com/pythontopia/tutor/TutorTransferPracticeService.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowController.java`
- Create: `backend/src/main/java/com/pythontopia/tutor/dto/TutorTransferPracticeDto.java`
- Create: `backend/src/main/java/com/pythontopia/tutor/dto/TutorTransferAttemptRequest.java`
- Create: `backend/src/main/java/com/pythontopia/tutor/dto/TutorTransferAttemptResult.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorTransferPracticeServiceTest.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorTransferPracticeControllerTest.java`

**Step 1: Write failing service tests**

Tests:

- Accepting draft checks ownership.
- Dismissing draft checks ownership.
- Attempt on non-accepted draft returns business error.
- Attempt runs all hidden cases through `JudgeCaseEvaluator` and `PythonSandbox`.
- AC attempt publishes transfer success event.
- WA attempt publishes transfer failure event.
- Response never includes hidden tests or reference solution.

**Step 2: Add DTOs**

Request:

```java
public record TutorTransferAttemptRequest(@NotBlank String code) {}
```

Result:

```java
public record TutorTransferAttemptResult(
    Long attemptId,
    String verdict,
    int passedCases,
    int totalCases,
    String failureReason
) {}
```

**Step 3: Implement service**

Use existing sandbox components:

- `JudgeCaseEvaluator`
- `JudgeTestCase`
- `PythonSandbox`
- `StaticCodeChecker` if available through judge service path

Publish learning events in later Task 14. For this task, record attempt and return result.

**Step 4: Add controller routes**

Under existing tutor workflow controller:

- `POST /api/tutor/workflow/transfer-practices/{id}/accept`
- `POST /api/tutor/workflow/transfer-practices/{id}/dismiss`
- `POST /api/tutor/workflow/transfer-practices/{id}/attempt`

Student only.

**Step 5: Run tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorTransferPracticeServiceTest,TutorTransferPracticeControllerTest test
```

Expected: PASS.

**Step 6: Commit**

```bash
git add backend/src/main/java/com/pythontopia/tutor/TutorTransferPracticeService.java backend/src/main/java/com/pythontopia/tutor/TutorWorkflowController.java backend/src/main/java/com/pythontopia/tutor/dto/TutorTransferPracticeDto.java backend/src/main/java/com/pythontopia/tutor/dto/TutorTransferAttemptRequest.java backend/src/main/java/com/pythontopia/tutor/dto/TutorTransferAttemptResult.java backend/src/test/java/com/pythontopia/tutor/TutorTransferPracticeServiceTest.java backend/src/test/java/com/pythontopia/tutor/TutorTransferPracticeControllerTest.java
git commit -m "feat(guide): support transfer practice attempts"
```

---

## Task 14: Transfer Learning Events And Profile Updates

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/learner/event/LearningEvent.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/event/MemoryStreamMirror.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/profile/PassiveProfileRevisionListener.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/event/LearningEventListener.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorTransferPracticeService.java`
- Test: `backend/src/test/java/com/pythontopia/learner/profile/PassiveProfileRevisionListenerTest.java`
- Test: `backend/src/test/java/com/pythontopia/learner/event/LearningEventListenerTest.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorTransferPracticeServiceTest.java`

**Step 1: Write failing event/profile tests**

Tests:

- Transfer AC updates `practical_ability`.
- Transfer WA updates `error_pattern` with `transfer_failed`.
- Near miss can update `knowledge_base` or confidence only if cases passed above threshold.
- Events mirror to `learning_event_log`.
- Mastery updates use existing `MasterySource.PRACTICE_PASS/PRACTICE_FAIL` with reason prefix `transfer:`.

**Step 2: Extend LearningEvent**

Add:

```java
record TransferPracticeStarted(Long userId, String kcCode, Long transferPracticeId, Long sourceProblemId)
    implements LearningEvent {}

record TransferPracticeSucceeded(Long userId, String kcCode, Long transferPracticeId, Long sourceProblemId)
    implements LearningEvent {}

record TransferPracticeFailed(
    Long userId,
    String kcCode,
    Long transferPracticeId,
    Long sourceProblemId,
    String failureKind,
    int passedCases,
    int totalCases)
    implements LearningEvent {}
```

Update sealed permits list.

**Step 3: Publish from transfer service**

- Accept -> `TransferPracticeStarted`
- AC attempt -> `TransferPracticeSucceeded`
- non-AC -> `TransferPracticeFailed`

**Step 4: Profile listener**

Mapping:

- `TransferPracticeSucceeded`: `practical_ability +0.04`, tag `transfer_success`
- `TransferPracticeFailed`: `error_pattern +0.02`, tag `transfer_failed`
- If `passedCases > 0 && passedCases < totalCases`, add tag `near_miss`

Use existing revision source strings (`PRACTICE_PASS`, `PRACTICE_FAIL`) to avoid migration churn unless tests show constraints require changes.

**Step 5: Mastery listener**

Use existing `MasteryEvaluator.applyEventWithDifficulty` with:

- success: `PRACTICE_PASS`
- fail: `PRACTICE_FAIL`
- reason: `transfer:<transferPracticeId>:source:<sourceProblemId>`

**Step 6: Memory mirror**

Mirror events to `learning_event_log` with event types:

- `transfer_practice_started`
- `transfer_practice_succeeded`
- `transfer_practice_failed`

**Step 7: Run tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=PassiveProfileRevisionListenerTest,LearningEventListenerTest,TutorTransferPracticeServiceTest test
```

Expected: PASS.

**Step 8: Commit**

```bash
git add backend/src/main/java/com/pythontopia/learner/event/LearningEvent.java backend/src/main/java/com/pythontopia/learner/event/MemoryStreamMirror.java backend/src/main/java/com/pythontopia/learner/profile/PassiveProfileRevisionListener.java backend/src/main/java/com/pythontopia/learner/event/LearningEventListener.java backend/src/main/java/com/pythontopia/tutor/TutorTransferPracticeService.java backend/src/test/java/com/pythontopia/learner/profile/PassiveProfileRevisionListenerTest.java backend/src/test/java/com/pythontopia/learner/event/LearningEventListenerTest.java backend/src/test/java/com/pythontopia/tutor/TutorTransferPracticeServiceTest.java
git commit -m "feat(guide): update profile from transfer practice"
```

---

## Task 15: Frontend Transfer Practice Attempt UI

**Files:**
- Modify: `frontend/src/api/tutorWorkflow.ts`
- Create: `frontend/src/components/training/TutorTransferPracticeCard.vue`
- Modify: `frontend/src/components/training/TutorWorkflowPanel.vue`
- Test: `frontend/tests/components/training/TutorTransferPracticeCard.spec.ts`
- Test: `frontend/tests/components/training/TutorWorkflowPanel.spec.ts`

**Step 1: Write failing UI tests**

Tests:

- Transfer card shows accept button when draft exists.
- Accepting shows code editor area.
- Attempt posts code and shows verdict.
- Hidden tests/reference solution are never rendered.
- Dismiss button hides draft and records status.

**Step 2: Add API methods**

In `tutorWorkflow.ts`:

- `acceptTransferPractice(id: number)`
- `dismissTransferPractice(id: number)`
- `attemptTransferPractice(id: number, code: string)`

Use `suppressNetworkToast` only for non-critical feedback; attempt errors should surface.

**Step 3: Create component**

`TutorTransferPracticeCard.vue` should:

- Render public problem.
- Render samples.
- Render student code textarea.
- Emit attempt result.
- Never accept props containing hidden tests or reference solution.

**Step 4: Integrate panel**

When a `TRANSFER` card includes `transferPracticeId`, render the new component under the transfer card.

**Step 5: Run tests**

Run:

```bash
cd frontend
npm run test -- tests/components/training/TutorTransferPracticeCard.spec.ts tests/components/training/TutorWorkflowPanel.spec.ts
npm run typecheck
```

Expected: PASS.

**Step 6: Commit**

```bash
git add frontend/src/api/tutorWorkflow.ts frontend/src/components/training/TutorTransferPracticeCard.vue frontend/src/components/training/TutorWorkflowPanel.vue frontend/tests/components/training/TutorTransferPracticeCard.spec.ts frontend/tests/components/training/TutorWorkflowPanel.spec.ts
git commit -m "feat(guide): let students attempt transfer practice"
```

---

## Task 16: Path Adaptation Proposal Persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V168__guide_path_adaptation_proposal.sql`
- Create: `backend/src/main/java/com/pythontopia/tutor/dto/GuidePathAdaptationProposalDto.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowRepository.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowRepositoryTest.java`

**Step 1: Write failing repository tests**

Tests:

- Insert path adaptation proposal.
- Find active proposal by session.
- Accept proposal marks accepted.
- Dismiss proposal marks dismissed.
- Proposal references `learning_path` draft.

**Step 2: Create migration**

```sql
-- Purpose: persist AI guide proposals to adapt long-term learning path without auto-promoting them.
-- Tables affected: guide_path_adaptation_proposal.
-- Irreversible risk: deleting this table loses proposal audit only; active paths remain intact.

CREATE TABLE guide_path_adaptation_proposal (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  session_id BIGINT REFERENCES tutor_workflow_session(id) ON DELETE SET NULL,
  draft_path_id BIGINT REFERENCES learning_path(id) ON DELETE SET NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  reason_summary TEXT NOT NULL,
  affected_kc_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
  evidence_event_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  decided_at TIMESTAMPTZ,
  CONSTRAINT ck_guide_path_adaptation_status CHECK (status IN ('PENDING','ACCEPTED','DISMISSED','EXPIRED'))
);

CREATE INDEX idx_guide_path_adaptation_user_status
  ON guide_path_adaptation_proposal(user_id, status, created_at DESC);
```

**Step 3: Add repository methods**

- `insertPathAdaptationProposal(...)`
- `findPendingPathAdaptationProposal(userId, sessionId)`
- `acceptPathAdaptationProposal(userId, proposalId)`
- `dismissPathAdaptationProposal(userId, proposalId)`

**Step 4: Run tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowRepositoryTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V168__guide_path_adaptation_proposal.sql backend/src/main/java/com/pythontopia/tutor/dto/GuidePathAdaptationProposalDto.java backend/src/main/java/com/pythontopia/tutor/TutorWorkflowRepository.java backend/src/test/java/com/pythontopia/tutor/TutorWorkflowRepositoryTest.java
git commit -m "feat(guide): persist path adaptation proposals"
```

---

## Task 17: Path Adaptation Service

**Files:**
- Create: `backend/src/main/java/com/pythontopia/tutor/GuidePathAdaptationService.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/path/service/LearningPathService.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/path/controller/LearningPathController.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/GuidePathAdaptationServiceTest.java`
- Test: `backend/src/test/java/com/pythontopia/learner/path/LearningPathServiceTest.java`

**Step 1: Write failing adaptation tests**

Tests:

- Repeated failures in same KC create a draft path proposal.
- One isolated WA does not create a proposal.
- Transfer failure plus low mastery creates proposal.
- Existing pending proposal prevents duplicates.
- Accepting proposal promotes draft through existing `LearningPathService.promoteDraft`.
- Dismissing proposal leaves active path unchanged.

**Step 2: Expose draft planning API internally**

`LearningPathService.planDraft(userId)` already exists. Keep it as internal service call. Do not call `plan(userId)` because that archives active paths.

If `promoteDraft` is not already public enough for tutor service, expose a package-safe method or controller route with ownership checks.

**Step 3: Implement adaptation policy**

Rules:

- Create proposal when one of these is true:
  - same KC has `consecutive_errors >= 3` in guide state;
  - transfer practice failed twice;
  - mastery score for KC below 0.35 after two guided interventions;
  - student marked two cards as not helpful and then failed again.
- Do not create proposal when:
  - no active guide session;
  - no profile;
  - no active path and no draft can be generated;
  - there is already a pending proposal.

Reason summary must be student-visible:

```text
你最近在 PY43-KC-031 连续卡住，我建议把接下来两天的路径改成先补循环边界，再回到原题练习。
```

**Step 4: Add controller endpoints**

Either under tutor workflow controller:

- `GET /api/tutor/workflow/sessions/{sessionKey}/path-adaptation`
- `POST /api/tutor/workflow/path-adaptations/{id}/accept`
- `POST /api/tutor/workflow/path-adaptations/{id}/dismiss`

Or under learner path controller if cleaner:

- `POST /api/learner/path/proposals/{id}/accept`
- `POST /api/learner/path/proposals/{id}/dismiss`

Prefer tutor workflow controller for proposal retrieval and learner path controller for final promotion if existing route style supports it.

**Step 5: Run tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=GuidePathAdaptationServiceTest,LearningPathServiceTest test
```

Expected: PASS.

**Step 6: Commit**

```bash
git add backend/src/main/java/com/pythontopia/tutor/GuidePathAdaptationService.java backend/src/main/java/com/pythontopia/learner/path/service/LearningPathService.java backend/src/main/java/com/pythontopia/learner/path/controller/LearningPathController.java backend/src/test/java/com/pythontopia/tutor/GuidePathAdaptationServiceTest.java backend/src/test/java/com/pythontopia/learner/path/LearningPathServiceTest.java
git commit -m "feat(guide): propose learning path adaptations"
```

---

## Task 18: Trigger Path Adaptation From Guide Evidence

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowSubmissionFeedbackListener.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowSubmissionFeedbackListenerTest.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowServiceTest.java`

**Step 1: Write failing trigger tests**

Tests:

- Third failed submission triggers adaptation service.
- `CARD_NOT_HELPFUL` + later failure triggers adaptation check.
- AC does not trigger path proposal unless transfer failure evidence exists.
- Trigger failures are logged but do not block submission or guide card generation.

**Step 2: Inject adaptation service**

Use optional fail-soft invocation:

```java
try {
  guidePathAdaptationService.maybePropose(userId, session.id());
} catch (Exception ex) {
  log.warn("guide path adaptation skipped", ex);
}
```

**Step 3: Run tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowSubmissionFeedbackListenerTest,TutorWorkflowServiceTest test
```

Expected: PASS.

**Step 4: Commit**

```bash
git add backend/src/main/java/com/pythontopia/tutor/TutorWorkflowSubmissionFeedbackListener.java backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java backend/src/test/java/com/pythontopia/tutor/TutorWorkflowSubmissionFeedbackListenerTest.java backend/src/test/java/com/pythontopia/tutor/TutorWorkflowServiceTest.java
git commit -m "feat(guide): trigger path adaptation from evidence"
```

---

## Task 19: Frontend Path Adaptation Proposal UI

**Files:**
- Modify: `frontend/src/api/tutorWorkflow.ts`
- Create: `frontend/src/components/training/GuidePathAdaptationCard.vue`
- Modify: `frontend/src/components/training/TutorWorkflowPanel.vue`
- Test: `frontend/tests/components/training/GuidePathAdaptationCard.spec.ts`
- Test: `frontend/tests/components/training/TutorWorkflowPanel.spec.ts`

**Step 1: Write failing UI tests**

Tests:

- Proposal card renders reason summary and affected KCs.
- Accept calls API and shows success.
- Dismiss calls API and hides card.
- Proposal does not auto-accept on render.
- UI copy clearly says this is a draft path change.

**Step 2: Add API methods**

Add:

- `getPathAdaptationProposal(sessionKey: string)`
- `acceptPathAdaptationProposal(id: number)`
- `dismissPathAdaptationProposal(id: number)`

**Step 3: Add component**

`GuidePathAdaptationCard.vue` copy:

```text
我建议调整一下接下来的学习路径
原因：...
影响知识点：...
按钮：查看并接受草稿 / 先不调整
```

**Step 4: Integrate panel**

After run result or after submission-observed traces, fetch pending proposal for session. Keep it compact under episode timeline.

**Step 5: Run tests**

Run:

```bash
cd frontend
npm run test -- tests/components/training/GuidePathAdaptationCard.spec.ts tests/components/training/TutorWorkflowPanel.spec.ts
npm run typecheck
```

Expected: PASS.

**Step 6: Commit**

```bash
git add frontend/src/api/tutorWorkflow.ts frontend/src/components/training/GuidePathAdaptationCard.vue frontend/src/components/training/TutorWorkflowPanel.vue frontend/tests/components/training/GuidePathAdaptationCard.spec.ts frontend/tests/components/training/TutorWorkflowPanel.spec.ts
git commit -m "feat(guide): show path adaptation proposals"
```

---

## Task 20: Extend Path Planner With Guide And Transfer Signals

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/schemas/path.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/agents/path_planner_agent.py`
- Modify: `backend/src/main/java/com/pythontopia/learner/path/graph/PathPlannerGraphRequest.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/path/service/LearningPathService.java`
- Test: `services/pythontopia-graph/tests/test_path_planner_agent.py`
- Test: `backend/src/test/java/com/pythontopia/learner/path/LearningPathServiceTest.java`

**Step 1: Write failing planner tests**

Tests:

- KC with recent guide repeated error is promoted.
- KC with transfer failure is promoted above normal low mastery tie.
- KC with transfer success can unlock near-KC challenge node.
- Generated plan reason mentions guide evidence without exposing raw card content.

**Step 2: Extend graph schema**

Add to `PathPlannerRequest`:

```python
guide_signals: list[PlannerGuideSignal] = Field(default_factory=list)
transfer_signals: list[PlannerTransferSignal] = Field(default_factory=list)
```

**Step 3: Extend Java request**

Add records:

```java
record GuideSignal(String kcCode, String signalType, int weight, String evidenceSummary) {}
record TransferSignal(String kcCode, String result, Long sourceProblemId, int passedCases, int totalCases) {}
```

**Step 4: Build signals in `LearningPathService`**

Read from:

- `tutor_workflow_action_event`
- `tutor_transfer_attempt`
- `guide_path_adaptation_proposal`

Keep repository queries bounded by limit and recent window.

**Step 5: Planner scoring**

Modify `_select_focus_kcs`:

- error pattern remains highest base signal.
- guide repeated error adds a boost.
- transfer failure adds a boost.
- transfer success can add a near-KC node but must respect prerequisites.

**Step 6: Run tests**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q tests/test_path_planner_agent.py

cd ../../backend
mvn -s .mvn/settings.build.xml -Dtest=LearningPathServiceTest test
```

Expected: PASS.

**Step 7: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/schemas/path.py services/pythontopia-graph/pythontopia_graph/agents/path_planner_agent.py services/pythontopia-graph/tests/test_path_planner_agent.py backend/src/main/java/com/pythontopia/learner/path/graph/PathPlannerGraphRequest.java backend/src/main/java/com/pythontopia/learner/path/service/LearningPathService.java backend/src/test/java/com/pythontopia/learner/path/LearningPathServiceTest.java
git commit -m "feat(path): adapt plans from guide transfer signals"
```

---

## Task 21: Decision Trace And Audit Visibility

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/learner/decision/DecisionTraceService.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/GuidePathAdaptationService.java`
- Test: `backend/src/test/java/com/pythontopia/learner/decision/DecisionTraceServiceTest.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowServiceTest.java`

**Step 1: Write failing audit tests**

Tests:

- Episode plan decision creates a decision trace category `guide`.
- Path proposal creates a decision trace category `path`.
- Transfer validation creates a decision trace category `transfer`.
- Trace payload omits hidden tests and reference solution.

**Step 2: Add decision trace writes**

Trace conclusions:

- Guide episode: "导学助手计划先做执行轨迹，再补知识点。"
- Transfer practice: "迁移题已通过结构、KC、参考解自测。"
- Path adaptation: "建议生成路径草稿，等待学生确认。"

**Step 3: Run tests**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=DecisionTraceServiceTest,TutorWorkflowServiceTest,GuidePathAdaptationServiceTest test
```

Expected: PASS.

**Step 4: Commit**

```bash
git add backend/src/main/java/com/pythontopia/learner/decision/DecisionTraceService.java backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java backend/src/main/java/com/pythontopia/tutor/GuidePathAdaptationService.java backend/src/test/java/com/pythontopia/learner/decision/DecisionTraceServiceTest.java backend/src/test/java/com/pythontopia/tutor/TutorWorkflowServiceTest.java
git commit -m "feat(guide): audit autonomous planning decisions"
```

---

## Task 22: End-To-End Browser Flow

**Files:**
- Create: `frontend/e2e/guide-autonomous-planning.spec.ts`
- Modify: `frontend/playwright.config.ts` only if needed

**Step 1: Write E2E test**

Cover:

1. Open a training problem.
2. Submit a wrong answer.
3. See short guide nudge.
4. Click continue.
5. See episode timeline.
6. See one generated card.
7. Mark checkpoint complete.
8. Submit another answer.
9. Observe updated episode state.
10. Submit accepted answer.
11. Click AC review.
12. Generate transfer practice.
13. Accept and attempt transfer practice.

If full graph LLM is too slow for CI, use test fixtures or mock graph endpoint at the browser layer. Keep one real integration smoke outside CI.

**Step 2: Run E2E**

Run:

```bash
cd frontend
npm run test:e2e -- e2e/guide-autonomous-planning.spec.ts
```

Expected: PASS in local dev stack or documented mock mode.

**Step 3: Commit**

```bash
git add frontend/e2e/guide-autonomous-planning.spec.ts frontend/playwright.config.ts
git commit -m "test(guide): cover autonomous planning browser flow"
```

---

## Task 23: Documentation And Changelog

**Files:**
- Modify: `docs/reports/2026-06-16-guide-coach-plan-review.md`
- Modify: `CHANGELOG.md`
- Create: `docs/reports/2026-06-17-ai-guide-autonomous-planning-chain-acceptance.md`

**Step 1: Update implementation report**

Append a new section:

```markdown
## 第二阶段深化：自主规划链

- Episode Plan
- Transfer Practice
- Path Adaptation
- Transfer Profile Signals
- Verification
- Remaining Risks
```

**Step 2: Update changelog**

Add entries under current unreleased/date section:

- AI 导学助手 now maintains bounded multi-step tutoring episodes.
- Transfer practice is validated before student attempt.
- Guide evidence can propose draft learning path adjustments.
- Transfer attempts update profile and mastery signals.

**Step 3: Write acceptance report**

Include:

- What changed.
- What was verified.
- What was not verified.
- Remaining risks.
- Whether demo chain is affected.

**Step 4: Commit**

```bash
git add docs/reports/2026-06-16-guide-coach-plan-review.md CHANGELOG.md docs/reports/2026-06-17-ai-guide-autonomous-planning-chain-acceptance.md
git commit -m "docs(guide): document autonomous planning chain"
```

---

## Task 24: Final Verification Gate

**Files:**
- No code files unless failures require fixes.
- Update: `docs/reports/2026-06-17-ai-guide-autonomous-planning-chain-acceptance.md`

**Step 1: Graph focused**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q \
  tests/test_tutor_episode_plan_schema.py \
  tests/test_guide_episode_planner.py \
  tests/test_tutor_workflow_episode_graph.py \
  tests/test_guide_episode_guardrail.py \
  tests/test_transfer_practice_validator.py \
  tests/test_tutor_transfer_practice_graph.py \
  tests/test_path_planner_agent.py
```

Expected: all pass.

**Step 2: Graph full**

Run:

```bash
cd services/pythontopia-graph
python3 -m pytest -q
```

Expected: full graph suite pass.

**Step 3: Backend focused**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,TutorWorkflowSubmissionFeedbackListenerTest,TutorWorkflowRepositoryTest,TutorTransferPracticeServiceTest,TutorTransferPracticeControllerTest,GuidePathAdaptationServiceTest,LearningPathServiceTest,PassiveProfileRevisionListenerTest,LearningEventListenerTest test
```

Expected: all pass.

**Step 4: Backend full**

Run:

```bash
cd backend
mvn -s .mvn/settings.build.xml test
```

Expected: full backend suite pass.

**Step 5: Frontend focused**

Run:

```bash
cd frontend
npm run test -- \
  tests/components/training/GuideNudgeCard.spec.ts \
  tests/components/training/TutorWorkflowPanel.spec.ts \
  tests/components/training/TutorTransferPracticeCard.spec.ts \
  tests/components/training/GuidePathAdaptationCard.spec.ts \
  tests/components/TrainingPanel.spec.ts
npm run typecheck
```

Expected: all pass.

**Step 6: Frontend full**

Run:

```bash
cd frontend
npm run test
npm run typecheck
```

Expected: full frontend suite pass.

**Step 7: Local stack smoke**

Run:

```bash
cd /home/cypress/Pythontopia
./dev.sh
curl -fsS http://127.0.0.1:5173/healthz
curl -fsS http://127.0.0.1:18080/actuator/health
curl -fsS http://127.0.0.1:18091/healthz
curl -fsS http://127.0.0.1:18092/healthz
```

Expected: frontend, backend, graph, rag healthy.

**Step 8: Manual browser acceptance**

Manually verify:

- WA nudge -> continue -> episode timeline -> one card.
- repeated WA -> episode revision.
- AC nudge -> AC review -> transfer practice proposal.
- transfer attempt AC/WA updates UI.
- path adaptation proposal requires explicit accept.

**Step 9: Update acceptance report**

Record exact command outputs and uncovered scope.

**Step 10: Commit any verification doc updates**

```bash
git add docs/reports/2026-06-17-ai-guide-autonomous-planning-chain-acceptance.md
git commit -m "test(guide): record autonomous planning acceptance"
```

---

## Rollout Notes

- This plan intentionally uses multiple commits. Do not squash until after final verification.
- If transfer practice generation becomes too unstable, keep episode planning and path adaptation enabled, but feature-flag transfer practice attempts behind `pythontopia.guide.transfer-practice.enabled=false` until validation is stable.
- If path proposal generation is noisy, raise thresholds rather than removing the feature.
- Any generated transfer exercise must be labeled as AI guide transfer practice, not as original course-pack content.
- Public UI must never display hidden tests, reference solutions, or raw internal reasons.

## Suggested Feature Flags

Add only if needed during rollout:

```yaml
pythontopia:
  guide:
    episode-planning:
      enabled: true
    transfer-practice:
      enabled: true
    path-adaptation:
      enabled: true
```

Use feature flags to stage deep functionality without leaving half-wired UI.

## Implementation Order Summary

1. Graph episode schema.
2. Graph episode planner.
3. Graph episode integration.
4. Episode guardrail.
5. Java DTO mapping.
6. Episode persistence.
7. Episode service restore/persist.
8. Submission-driven episode evidence.
9. Frontend episode timeline.
10. Transfer validator.
11. Transfer generation.
12. Transfer persistence.
13. Transfer endpoints and attempts.
14. Transfer learning/profile events.
15. Transfer UI.
16. Path proposal persistence.
17. Path adaptation service.
18. Path adaptation triggers.
19. Path proposal UI.
20. Path planner signal enrichment.
21. Decision trace.
22. Browser E2E.
23. Docs/changelog.
24. Final verification.

This is intentionally not an MVP. It is a full deepening track that turns the AI 导学助手 into a bounded education-planning agent while preserving Pythontopia's core constraints: evidence first, student confirmation, no full-answer leaks, no silent path replacement, and no open-ended ReAct loop.
