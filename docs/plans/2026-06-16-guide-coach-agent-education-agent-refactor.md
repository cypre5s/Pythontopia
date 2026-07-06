# Guide Coach Agent Education Agent Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refactor AI 导学助手 from a mostly phase-button workflow into a bounded, manager-style education Agent that chooses pedagogical tools based on problem context, submission evidence, learner state, and feedback.

**Architecture:** Keep deterministic gates for safety, permissions, and AC/WA legality. Add `GuideCoachAgent` as the manager layer inside the graph service: it reads `TutorStudentState`, chooses the next pedagogical action, and calls existing specialist card generators as tools. Persist student state and action outcomes in Java so the agent can learn from click/submit feedback without becoming an open-ended ReAct loop.

**Tech Stack:** FastAPI, LangGraph StateGraph, Pydantic structured outputs, AG-UI semantic SSE, Spring Boot 3 / Java 21, PostgreSQL / Flyway, Vue 3, TypeScript strict, Pinia, Vitest, Maven, pytest.

---

## Product Decision

AI 导学助手要变成更 Agent 的教育 Agent：

- It is not just `if AC -> show review` and `if WA -> show diagnosis`.
- Deterministic `if-else` remains only for trigger gates and legal boundaries.
- The Agent decides what teaching move helps next: knowledge review, problem reading, Socratic hint, execution trace, visualization, skeleton, Parsons drill, AC review, or transfer practice.
- The Agent must be bounded, evidence-driven, and student-state-aware.
- The Agent must not reveal full submit-ready answers.
- The Agent must not use open-ended ReAct unless the user explicitly asks in a future decision.

## Current Anchors

Use and extend these existing foundations:

- Training page integration: `frontend/src/components/training/TrainingPanel.vue`
- Existing AI guide workflow panel: `frontend/src/components/training/TutorWorkflowPanel.vue`
- Tutor workflow API: `frontend/src/api/tutorWorkflow.ts`
- Tutor workflow graph: `services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py`
- Card generators: `services/pythontopia-graph/pythontopia_graph/agents/tutor_workflow_cards.py`
- Tutor workflow schemas: `services/pythontopia-graph/pythontopia_graph/schemas/tutor_workflow.py`
- Tutor workflow route: `services/pythontopia-graph/pythontopia_graph/routes/tutor_workflow.py`
- Java orchestration: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java`
- Java DTOs/repository: `backend/src/main/java/com/pythontopia/tutor/dto/*`, `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowRepository.java`
- Submission evidence: `backend/src/main/java/com/pythontopia/submission/service/SubmissionService.java`
- Learning events: `backend/src/main/java/com/pythontopia/learner/event/*`
- Mastery/confidence: `backend/src/main/java/com/pythontopia/learner/mastery/*`
- AG-UI SSE helpers: `services/pythontopia-graph/pythontopia_graph/sse_agui.py`, `frontend/src/composables/useSseStream.ts`, `frontend/src/composables/agui.ts`

## External Design References

- OpenAI Agents SDK defines agents as LLMs configured with instructions, tools, guardrails, handoffs, and structured outputs; use that as the definition of "more Agent".
- OpenAI Agents SDK manager pattern supports a central user-facing agent invoking specialist agents/tools while retaining conversation control.
- LangGraph persistence supports short-term thread state through checkpointers and long-term memory through stores; use the repo's Java persistence as the source of truth first, with graph state restored per run.
- LangGraph interrupts support human-in-the-loop pause/resume; use this conceptually for "主动说一句，等待学生点击继续", but implement the first phase through explicit UI actions and restored session state.
- AG-UI events support run, step, tool, state, and activity events; use this to stream agent decisions without exposing raw chain-of-thought.

## Target Agent Shape

```text
Submission or student request
  -> TriggerGate                 deterministic
  -> GuideCoachAgent             manager / pedagogical policy
  -> PedagogicalToolbox          existing card generators as tools
  -> GuardrailCritic             no full answer, no fake citation, no unsafe hints
  -> Structured Guide Card       rendered by TutorWorkflowPanel
  -> Student action / next submit
  -> TutorStudentState update
```

## Deterministic vs Agent Responsibilities

| Layer | Deterministic | Agent |
|---|---|---|
| Trigger | AC/WA/CE/RE/TLE detection | none |
| Permission | submission belongs to current user | none |
| Legality | AC only allows `AC_REVIEW`, non-AC only allows diagnosis | none |
| Tool whitelist | allowed actions by problem/submission state | rank and choose within whitelist |
| Pedagogy | max hint level, no answer leak | choose teaching strategy |
| Output | Pydantic schema validation | generate card content |
| Feedback | record click/submit events | infer next student state |

## New Concepts

### `TutorStudentState`

The durable learner/problem/session state that makes the guide feel agentic.

```python
class TutorStudentState(BaseModel):
    session_id: str
    problem_id: int
    user_id: int
    phase: Phase
    attempt_count: int = 0
    latest_verdict: str = ""
    consecutive_errors: int = 0
    hint_level: int = 0
    frustration_level: Literal["low", "moderate", "severe"] = "low"
    misconception_hypothesis: str = ""
    last_action: str = ""
    last_action_effective: bool | None = None
    last_card_key: str = ""
    next_best_action: str = ""
    mastery_snapshot: dict[str, Any] = Field(default_factory=dict)
    clicked_actions: list[str] = Field(default_factory=list)
    completed_checkpoints: list[str] = Field(default_factory=list)
```

### `GuideCoachDecision`

The manager Agent output. It chooses a next action and explains the decision in auditable fields, not hidden chain-of-thought.

```python
class GuideCoachDecision(BaseModel):
    next_event: WorkflowEvent
    tool_name: str
    hint_level: int = Field(ge=0, le=3)
    diagnosis_label: str = ""
    student_state_label: str = ""
    action_goal: str
    stop_condition: str
    allowed_followups: list[WorkflowAction] = Field(default_factory=list)
    confidence: float = Field(ge=0, le=1)
    reason_summary: str
```

### `PedagogicalToolbox`

Specialist tools map to existing card methods:

| Tool | Existing card method |
|---|---|
| `read_problem` | `TutorWorkflowCardAgent.problem_guide` |
| `ideate_solution` | `TutorWorkflowCardAgent.ideate` |
| `skeleton_code` | `TutorWorkflowCardAgent.skeleton` |
| `diagnose_submission` | `TutorWorkflowCardAgent.error_diagnosis` |
| `post_ac_review` | `TutorWorkflowCardAgent.post_ac` |
| `transfer_practice` | `TutorWorkflowCardAgent.transfer` |
| `knowledge_review` | `TutorWorkflowCardAgent.knowledge_review` |
| `execution_trace` | `TutorWorkflowCardAgent.execution_trace` |
| `visualize_flow` | `TutorWorkflowCardAgent.visualize` |
| `parsons_drill` | `TutorWorkflowCardAgent.parsons` |

## Non-Goals

- Do not let the Agent directly edit student code.
- Do not provide complete submit-ready solutions.
- Do not expose hidden tests.
- Do not convert transfer practice into official judge problems in this phase.
- Do not rewrite 百晓工坊.
- Do not remove the existing phase transition safety model; use it as guardrails.
- Do not add open-ended autonomous background planning.

## Database Design

Current workflow session/card tables already exist. Add a small durable state layer.

### Migration

Use the next available migration number at implementation time. As of this plan, current latest observed migration is `V164__chapter_assessment_kind.sql`; if still true, create:

- `backend/src/main/resources/db/migration/V165__tutor_workflow_agent_state.sql`

SQL:

```sql
CREATE TABLE tutor_workflow_agent_state (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES tutor_workflow_session(id) ON DELETE CASCADE,
  state_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  latest_decision_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT tutor_workflow_agent_state_session_unique UNIQUE (session_id)
);

CREATE TABLE tutor_workflow_action_event (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES tutor_workflow_session(id) ON DELETE CASCADE,
  seq INTEGER NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  action_key VARCHAR(64) NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tutor_workflow_action_event_session_seq
  ON tutor_workflow_action_event(session_id, seq DESC);
```

## Graph API Changes

Extend `WorkflowRunRequest`:

```python
coach_mode: bool = True
student_state: TutorStudentState | None = None
force_event: WorkflowEvent | None = None
```

Extend `WorkflowRunResult`:

```python
coach_decision: GuideCoachDecision | None = None
student_state: TutorStudentState | None = None
agentic: bool = True
```

Frontend DTO mirrors snake_case for graph result and camelCase for Java request only where existing conventions require.

### Task 1: Add Coach Schemas

**Files:**

- Modify: `services/pythontopia-graph/pythontopia_graph/schemas/tutor_workflow.py`
- Test: `services/pythontopia-graph/tests/test_tutor_workflow_coach_schema.py`

**Step 1: Write failing schema tests**

Create:

```python
from pythontopia_graph.schemas.tutor_workflow import GuideCoachDecision, TutorStudentState


def test_student_state_defaults_are_safe() -> None:
    state = TutorStudentState(session_id="wf-1", user_id=1, problem_id=42, phase="READING")
    assert state.hint_level == 0
    assert state.frustration_level == "low"
    assert state.clicked_actions == []


def test_coach_decision_requires_bounded_hint_level() -> None:
    decision = GuideCoachDecision(
        next_event="EXECUTION_TRACE",
        tool_name="execution_trace",
        hint_level=2,
        action_goal="定位变量更新偏差",
        stop_condition="学生说出第一处偏差",
        confidence=0.8,
        reason_summary="WA 且部分样例通过，先观察执行过程",
    )
    assert decision.next_event == "EXECUTION_TRACE"
```

**Step 2: Run tests to verify failure**

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_tutor_workflow_coach_schema.py
```

Expected: fail because classes do not exist.

**Step 3: Implement schemas**

Add `TutorStudentState` and `GuideCoachDecision` to `schemas/tutor_workflow.py`. Keep existing `WorkflowRunRequest` and `WorkflowRunResult` backward-compatible by using optional fields.

**Step 4: Run tests**

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_tutor_workflow_coach_schema.py tests/test_resource_schemas.py
```

Expected: pass.

**Step 5: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/schemas/tutor_workflow.py services/pythontopia-graph/tests/test_tutor_workflow_coach_schema.py
git commit -m "feat(guide): add coach decision schemas"
```

### Task 2: Implement GuideCoachAgent Decision Layer

**Files:**

- Create: `services/pythontopia-graph/pythontopia_graph/agents/guide_coach_agent.py`
- Test: `services/pythontopia-graph/tests/test_guide_coach_agent.py`
- Read-only: `services/pythontopia-graph/pythontopia_graph/agents/tutor_workflow_cards.py`

**Step 1: Write failing decision tests**

Create:

```python
@pytest.mark.asyncio
async def test_wa_prefers_execution_trace_before_more_code(fake_provider) -> None:
    agent = GuideCoachAgent(fake_provider)
    decision = await agent.decide(
        CoachContext(
            requested_event="ERROR_FEEDBACK",
            allowed_events=["ERROR_FEEDBACK", "EXECUTION_TRACE", "KNOWLEDGE_REVIEW", "IDEATING"],
            state=TutorStudentState(
                session_id="wf-1",
                user_id=1,
                problem_id=42,
                phase="CODING",
                latest_verdict="WA",
                consecutive_errors=1,
            ),
            problem=WorkflowProblemContext(problem_id=42, title="循环求和", kc_codes=["PY43-KC-016"]),
            diagnosis=WorkflowDiagnosisContext(submission_id=7, verdict="WA", passed_cases=2, total_cases=5),
            profile_brief="知识基础: 入门; 易错: off-by-one",
        )
    )
    assert decision.next_event in {"EXECUTION_TRACE", "ERROR_FEEDBACK"}
    assert decision.hint_level <= 2


@pytest.mark.asyncio
async def test_ac_after_retries_prefers_post_ac_then_transfer(fake_provider) -> None:
    ...
```

**Step 2: Run tests to verify failure**

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_guide_coach_agent.py
```

Expected: fail because agent does not exist.

**Step 3: Implement `GuideCoachAgent`**

Core API:

```python
class CoachContext(BaseModel):
    requested_event: WorkflowEvent
    allowed_events: list[WorkflowEvent]
    state: TutorStudentState
    problem: WorkflowProblemContext
    diagnosis: WorkflowDiagnosisContext | None = None
    profile_brief: str = ""
    recent_cards: list[dict[str, Any]] = Field(default_factory=list)


class GuideCoachAgent:
    def __init__(self, provider: LlmProvider) -> None:
        self._provider = provider

    async def decide(self, ctx: CoachContext) -> GuideCoachDecision:
        ...
```

Decision rules before LLM:

- If requested event is `AC_REVIEW`, choose `AC_REVIEW`.
- If requested event is `ERROR_FEEDBACK` and verdict is `CE`, choose `ERROR_FEEDBACK`.
- If verdict is `WA` and no `EXECUTION_TRACE` has been shown, prefer `EXECUTION_TRACE` or `ERROR_FEEDBACK`.
- If consecutive errors >= 2, lower cognitive load: `VISUALIZE`, `PARSONS`, or `KNOWLEDGE_REVIEW`.
- If hint_level >= 3, do not increase beyond 3.

LLM decision prompt:

- Must output `GuideCoachDecision`.
- Must choose only from `allowed_events`.
- Must not reveal full code answer.
- Must explain in `reason_summary` using concise audit text, not chain-of-thought.

Fallback:

```python
def _fallback_decision(ctx: CoachContext) -> GuideCoachDecision:
    if ctx.requested_event in ctx.allowed_events:
        return GuideCoachDecision(...)
    return GuideCoachDecision(next_event="READING", ...)
```

**Step 4: Run tests**

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_guide_coach_agent.py
```

Expected: pass.

**Step 5: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/agents/guide_coach_agent.py services/pythontopia-graph/tests/test_guide_coach_agent.py
git commit -m "feat(guide): add GuideCoachAgent decision layer"
```

### Task 3: Insert Coach Node Into Tutor Workflow Graph

**Files:**

- Modify: `services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/routes/tutor_workflow.py`
- Test: `services/pythontopia-graph/tests/test_tutor_workflow_coach_graph.py`

**Step 1: Write failing graph tests**

Create tests:

```python
@pytest.mark.asyncio
async def test_coach_decision_controls_generated_card(fake_provider, fake_evidence_fetcher) -> None:
    events = []
    async def emit(name, data):
        events.append((name, data))
    graph = TutorWorkflowGraph(fake_provider, fake_evidence_fetcher, emit=emit)
    state = await graph.run({
        "request": WorkflowRunRequest(..., event="ERROR_FEEDBACK", coach_mode=True),
        "current_phase": "CODING",
        "last_cards": [],
        "student_state": TutorStudentState(..., phase="CODING", latest_verdict="WA"),
    })
    assert state["coach_decision"].next_event in {"ERROR_FEEDBACK", "EXECUTION_TRACE"}
    assert state["available_actions"]
```

**Step 2: Run tests to verify failure**

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_tutor_workflow_coach_graph.py
```

Expected: fail because graph has no coach node.

**Step 3: Update graph topology**

Current:

```text
START -> validate_transition -> fetch_evidence -> generate_card -> validate_card -> decide_actions -> END
```

New:

```text
START
  -> validate_transition
  -> load_student_state
  -> coach_decide
  -> fetch_evidence
  -> generate_card_for_decision
  -> validate_card
  -> update_student_state
  -> decide_actions_from_coach
  -> END
```

Implementation details:

- `validate_transition` still validates requested event.
- `coach_decide` chooses `effective_event`.
- `generate_card` dispatches based on `effective_event`.
- If `coach_mode=False`, preserve legacy behavior.
- Emit AG-UI steps:
  - `coach.load_state`
  - `coach.decide`
  - `coach.update_state`

**Step 4: Route result wiring**

In `routes/tutor_workflow.py`, include:

```python
coach_decision=state.get("coach_decision")
student_state=state.get("student_state")
agentic=body.coach_mode
```

**Step 5: Run tests**

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_tutor_workflow_coach_graph.py tests/test_tutor_workflow.py tests/test_sse_agui.py
```

Expected: pass.

**Step 6: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py services/pythontopia-graph/pythontopia_graph/routes/tutor_workflow.py services/pythontopia-graph/tests/test_tutor_workflow_coach_graph.py
git commit -m "feat(guide): route tutor workflow through coach decisions"
```

### Task 4: Add Guardrail Critic for Pedagogical Output

**Files:**

- Create: `services/pythontopia-graph/pythontopia_graph/agents/guide_guardrail_critic.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py`
- Test: `services/pythontopia-graph/tests/test_guide_guardrail_critic.py`

**Step 1: Write failing tests**

```python
def test_rejects_submit_ready_solution_in_skeleton() -> None:
    card = {"skeleton": "n=int(input())\nprint(sum(range(n+1)))"}
    result = GuideGuardrailCritic().check("skeleton_code", card)
    assert not result.passed
    assert "submit_ready" in result.reasons


def test_allows_todo_skeleton() -> None:
    card = {"skeleton": "n=int(input())\n# TODO: 累加 1..n\nprint(ans)"}
    result = GuideGuardrailCritic().check("skeleton_code", card)
    assert result.passed
```

**Step 2: Run failing tests**

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_guide_guardrail_critic.py
```

Expected: fail because critic does not exist.

**Step 3: Implement critic**

Rules:

- Skeleton must contain TODO or blanks for core logic.
- Transfer problem must not include reference solution or test cases.
- Error diagnosis must not include complete corrected code.
- Courseware refs must come from evidence chunks, not model-generated page numbers.
- Hint level 0/1 cannot include full algorithm.

Return:

```python
class GuardrailResult(BaseModel):
    passed: bool
    reasons: list[str] = Field(default_factory=list)
```

**Step 4: Integrate into graph**

After card generation:

- Run critic.
- If failed and card generation can revise once, ask card agent to regenerate with guardrail reasons.
- Max one revision.
- If still failed, emit `RUN_ERROR` with `GUIDE_GUARDRAIL_FAILED`.

**Step 5: Run tests**

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_guide_guardrail_critic.py tests/test_tutor_workflow_coach_graph.py
```

Expected: pass.

**Step 6: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph/agents/guide_guardrail_critic.py services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py services/pythontopia-graph/tests/test_guide_guardrail_critic.py
git commit -m "feat(guide): add pedagogical guardrails"
```

### Task 5: Persist Student State and Coach Decisions in Java

**Files:**

- Create: `backend/src/main/resources/db/migration/V165__tutor_workflow_agent_state.sql` if V165 is still available
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowRepository.java`
- Create: `backend/src/main/java/com/pythontopia/tutor/dto/TutorWorkflowAgentStateDto.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowServiceTest.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowRepositoryTest.java` if repository tests exist or can be added with current test style.

**Step 1: Write failing repository/service tests**

Test state round trip:

```java
@Test
void persistsAgentStateFromGraphResult() {
  String resultJson = """
      {"result":{"phase":"ERROR_FEEDBACK","seq":2,
        "student_state":{"hint_level":2,"latest_verdict":"WA"},
        "coach_decision":{"next_event":"EXECUTION_TRACE","reason_summary":"部分样例通过，先看变量轨迹"}}}
      """;
  service.persistProjection(session, request, resultJson);
  TutorWorkflowAgentStateDto state = repository.findAgentState(session.id()).orElseThrow();
  assertThat(state.stateJson()).contains("\"hint_level\":2");
}
```

**Step 2: Run failing test**

Run:

```bash
cd backend
mvn -q -Dtest=TutorWorkflowServiceTest test
```

Expected: fail because state persistence does not exist.

**Step 3: Add migration**

Use SQL from Database Design. If another migration has taken V165, renumber before implementation.

**Step 4: Add repository methods**

```java
Optional<TutorWorkflowAgentStateDto> findAgentState(Long sessionId);
void upsertAgentState(Long sessionId, String stateJson, String decisionJson);
void insertActionEvent(Long sessionId, int seq, String eventType, String actionKey, String payloadJson);
```

**Step 5: Update service request body**

Before graph call:

- Load persisted state.
- Put `student_state` into graph body if present.
- Put `coach_mode=true`.

After result:

- Persist `student_state`.
- Persist `coach_decision`.
- Insert action event for generated card.

**Step 6: Run tests**

Run:

```bash
cd backend
mvn -q -Dtest=TutorWorkflowServiceTest,TutorWorkflowGraphClientTest test
```

Expected: pass.

**Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration backend/src/main/java/com/pythontopia/tutor backend/src/test/java/com/pythontopia/tutor
git commit -m "feat(guide): persist coach state and decisions"
```

### Task 6: Add AC/WA Nudge Cards in TrainingPanel

**Files:**

- Create: `frontend/src/components/training/GuideNudgeCard.vue`
- Modify: `frontend/src/components/training/TrainingPanel.vue`
- Modify: `frontend/tests/components/training/TrainingPanel.spec.ts` or create it if current suite lacks training coverage.
- Create: `frontend/tests/components/training/GuideNudgeCard.spec.ts`

**Step 1: Write failing nudge card tests**

```ts
it('renders AC nudge with review and transfer choices', () => {
  const wrapper = mount(GuideNudgeCard, { props: { verdict: 'AC' } });
  expect(wrapper.text()).toContain('过了');
  expect(wrapper.find('[data-testid="guide-nudge-ac-review"]').exists()).toBe(true);
});

it('renders WA nudge and waits for user click', () => {
  const wrapper = mount(GuideNudgeCard, { props: { verdict: 'WA' } });
  expect(wrapper.text()).toContain('没过');
  expect(wrapper.emitted('continue')).toBeUndefined();
});
```

**Step 2: Run failing tests**

Run:

```bash
cd frontend
npm run test -- GuideNudgeCard.spec.ts
```

Expected: fail because component does not exist.

**Step 3: Implement `GuideNudgeCard.vue`**

Props:

```ts
const props = defineProps<{
  verdict: 'AC' | 'WA' | 'CE' | 'RE' | 'TLE' | string;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  (e: 'continue', event: WorkflowEvent): void;
}>();
```

Behavior:

- AC: text "这题过了。要不要我帮你做 30 秒复盘，再给一道迁移题？"
- WA: text "这次没过。我可以先看代码和判题结果，给第一层错因分析。"
- CE: syntax-focused copy.
- RE/TLE: runtime/timeout-focused copy.
- Never auto-emits.

**Step 4: Wire TrainingPanel**

After `learning.training.submission` is available:

- Show `GuideNudgeCard` above or inside `TutorWorkflowPanel`.
- On continue:
  - `AC` -> call workflow event `AC_REVIEW`.
  - non-AC -> call workflow event `ERROR_FEEDBACK`.
- Pass current `submissionId`.

If `TutorWorkflowPanel` already exposes actions only internally, add a public method or prop `initialEventRequest` rather than duplicating SSE code in `TrainingPanel`.

**Step 5: Run tests**

Run:

```bash
cd frontend
npm run test -- GuideNudgeCard.spec.ts TrainingPanel.spec.ts
npm run typecheck
```

Expected: pass.

**Step 6: Commit**

```bash
git add frontend/src/components/training/GuideNudgeCard.vue frontend/src/components/training/TrainingPanel.vue frontend/tests/components/training
git commit -m "feat(guide): add click-to-continue AC WA nudges"
```

### Task 7: Let Available Actions Come From Coach Decisions

**Files:**

- Modify: `services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py`
- Modify: `frontend/src/components/training/TutorWorkflowPanel.vue`
- Modify: `frontend/src/api/tutorWorkflow.ts`
- Test: `services/pythontopia-graph/tests/test_tutor_workflow_coach_graph.py`
- Test: `frontend/tests/components/training/TutorWorkflowPanel.spec.ts`

**Step 1: Write failing graph test**

```python
def test_available_actions_are_ranked_by_coach_decision() -> None:
    state = ...
    actions = state["available_actions"]
    assert actions[0].event == state["coach_decision"].next_event
```

**Step 2: Write failing frontend test**

```ts
it('highlights the coach recommended next action', async () => {
  const result = fakeWorkflowResult({
    coach_decision: { next_event: 'EXECUTION_TRACE', reason_summary: '...' },
    available_actions: [{ key: 'execution_trace', label: '执行轨迹讲解', event: 'EXECUTION_TRACE' }],
  });
  ...
  expect(wrapper.get('[data-testid="coach-recommended-action"]').text()).toContain('执行轨迹');
});
```

**Step 3: Run failing tests**

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_tutor_workflow_coach_graph.py
cd ../../frontend
npm run test -- TutorWorkflowPanel.spec.ts
```

Expected: fail.

**Step 4: Implement action ranking**

Graph:

- Start from legal `PHASE_ACTIONS`.
- Merge coach allowed followups.
- Put `coach_decision.next_event` first when legal.
- Add `recommended=true` to first action.

Schema:

```python
class WorkflowAction(BaseModel):
    key: str
    label: str
    event: WorkflowEvent
    recommended: bool = False
    reason: str = ""
```

Frontend:

- Add optional DTO fields.
- Render recommended action first with "推荐下一步".
- Keep all other actions available.

**Step 5: Run tests**

Run:

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_tutor_workflow_coach_graph.py tests/test_resource_schemas.py
cd ../../frontend
npm run test -- TutorWorkflowPanel.spec.ts
npm run typecheck
```

Expected: pass.

**Step 6: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph frontend/src/api/tutorWorkflow.ts frontend/src/components/training/TutorWorkflowPanel.vue frontend/tests/components/training
git commit -m "feat(guide): rank workflow actions by coach decision"
```

### Task 8: Track Feedback From Clicks, Checkpoints, and Subsequent Submissions

**Files:**

- Modify: `backend/src/main/java/com/pythontopia/learner/event/LearningEvent.java`
- Modify: `backend/src/main/java/com/pythontopia/learner/event/MemoryStreamMirror.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowController.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java`
- Create DTO: `backend/src/main/java/com/pythontopia/tutor/dto/TutorWorkflowFeedbackRequest.java`
- Modify: `frontend/src/api/tutorWorkflow.ts`
- Modify: `frontend/src/components/training/TutorWorkflowPanel.vue`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowServiceTest.java`
- Test: `frontend/tests/components/training/TutorWorkflowPanel.spec.ts`

**Step 1: Write failing backend feedback test**

```java
@Test
void recordsGuideActionClickedEvent() {
  service.recordFeedback(userId, sessionKey, new TutorWorkflowFeedbackRequest("ACTION_CLICKED", "execution_trace", Map.of()));
  verify(eventPublisher).publishEvent(any(LearningEvent.GuideActionClicked.class));
}
```

**Step 2: Run failing backend test**

```bash
cd backend
mvn -q -Dtest=TutorWorkflowServiceTest test
```

Expected: fail.

**Step 3: Implement feedback endpoint**

Endpoint:

```java
@PostMapping("/workflow/sessions/{sessionKey}/feedback")
public ResponseEntity<Void> feedback(...) { ... }
```

Event types:

- `ACTION_CLICKED`
- `CHECKPOINT_COMPLETED`
- `CARD_HELPFUL`
- `CARD_NOT_HELPFUL`

Learning event records:

```java
record GuideActionClicked(Long userId, Long problemId, String kcCode, String actionKey) implements LearningEvent {}
record GuideCheckpointCompleted(Long userId, Long problemId, String kcCode, String cardKey) implements LearningEvent {}
```

**Step 4: Frontend emits feedback**

In `TutorWorkflowPanel.vue`:

- When user clicks an action, POST `ACTION_CLICKED`.
- When user marks "有帮助/没帮助", POST card feedback.
- Do not block the workflow if feedback POST fails.

**Step 5: Link subsequent submissions**

In Java service or repository:

- When a new submission for same problem arrives, future state build should compare with previous guide action.
- If prior non-AC followed by AC, set `last_action_effective=true`.
- If prior non-AC followed by non-AC, increase `hint_level` for next run.

**Step 6: Run tests**

```bash
cd backend
mvn -q -Dtest=TutorWorkflowServiceTest,SubmissionServiceTest test
cd ../frontend
npm run test -- TutorWorkflowPanel.spec.ts
npm run typecheck
```

Expected: pass.

**Step 7: Commit**

```bash
git add backend/src/main/java/com/pythontopia/learner/event backend/src/main/java/com/pythontopia/tutor frontend/src/api/tutorWorkflow.ts frontend/src/components/training/TutorWorkflowPanel.vue backend/src/test/java/com/pythontopia/tutor frontend/tests/components/training
git commit -m "feat(guide): feed student actions back into coach state"
```

### Task 9: Add Coach Golden Evals

**Files:**

- Create: `services/pythontopia-graph/tests/fixtures/guide_coach_golden.json`
- Create: `services/pythontopia-graph/tests/test_guide_coach_golden.py`

**Step 1: Create golden scenarios**

Include at least:

```json
[
  {
    "name": "wa_partial_pass_prefers_trace",
    "verdict": "WA",
    "passed_cases": 2,
    "total_cases": 5,
    "consecutive_errors": 1,
    "expected_events": ["EXECUTION_TRACE", "ERROR_FEEDBACK"],
    "forbidden_events": ["SKELETON"]
  },
  {
    "name": "ce_prefers_error_diagnosis",
    "verdict": "CE",
    "expected_events": ["ERROR_FEEDBACK"]
  },
  {
    "name": "repeated_wa_lowers_load",
    "verdict": "WA",
    "consecutive_errors": 3,
    "expected_events": ["VISUALIZE", "PARSONS", "KNOWLEDGE_REVIEW"]
  },
  {
    "name": "ac_after_retries_prefers_review",
    "verdict": "AC",
    "consecutive_errors": 2,
    "expected_events": ["AC_REVIEW"]
  }
]
```

**Step 2: Write eval runner**

Runner should:

- Load fixture.
- Build `CoachContext`.
- Use deterministic fake provider where possible.
- Assert `next_event` is expected.
- Assert forbidden events are not selected.
- Assert `hint_level <= 3`.
- Assert `reason_summary` is non-empty and does not contain chain-of-thought markers.

**Step 3: Run eval**

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_guide_coach_golden.py
```

Expected: pass.

**Step 4: Commit**

```bash
git add services/pythontopia-graph/tests/fixtures/guide_coach_golden.json services/pythontopia-graph/tests/test_guide_coach_golden.py
git commit -m "test(guide): add coach decision golden evals"
```

### Task 10: Surface Agentic Progress Without Raw Reasoning

**Files:**

- Modify: `services/pythontopia-graph/pythontopia_graph/sse_agui.py` only if helper additions are needed
- Modify: `services/pythontopia-graph/pythontopia_graph/graph/tutor_workflow_graph.py`
- Modify: `frontend/src/components/training/TutorWorkflowPanel.vue`
- Modify: `frontend/src/components/workshop/ToolCallTimeline.vue` only if reused for guide
- Test: `frontend/tests/components/training/TutorWorkflowPanel.spec.ts`
- Test: `services/pythontopia-graph/tests/test_sse_agui.py`

**Step 1: Write tests**

Graph should emit steps:

- `coach.load_state`
- `coach.decide`
- `coach.tool.<tool_name>`
- `coach.guardrail`
- `coach.update_state`

Frontend should render natural copy:

- "正在判断你卡在哪里"
- "正在选择下一步提示"
- "正在生成导学卡"
- "正在检查是否过度提示"

**Step 2: Run failing tests**

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_sse_agui.py tests/test_tutor_workflow_coach_graph.py
cd ../../frontend
npm run test -- TutorWorkflowPanel.spec.ts
```

Expected: fail until events/copy are wired.

**Step 3: Implement natural progress**

Do not expose raw chain-of-thought or prompt internals. Render only step summaries and `reason_summary`.

**Step 4: Run tests**

```bash
cd services/pythontopia-graph
python -m pytest -q tests/test_sse_agui.py tests/test_tutor_workflow_coach_graph.py
cd ../../frontend
npm run test -- TutorWorkflowPanel.spec.ts
npm run typecheck
```

Expected: pass.

**Step 5: Commit**

```bash
git add services/pythontopia-graph/pythontopia_graph frontend/src/components/training frontend/tests/components/training
git commit -m "feat(guide): show bounded coach progress"
```

### Task 11: Final End-to-End Verification

**Files:**

- No new files unless verification exposes bugs.

**Step 1: Graph tests**

```bash
cd services/pythontopia-graph
python -m pytest -q \
  tests/test_tutor_workflow_coach_schema.py \
  tests/test_guide_coach_agent.py \
  tests/test_tutor_workflow_coach_graph.py \
  tests/test_guide_guardrail_critic.py \
  tests/test_guide_coach_golden.py \
  tests/test_sse_agui.py
```

Expected: pass.

**Step 2: Backend tests**

```bash
cd backend
mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,TutorWorkflowSubmissionFeedbackListenerTest,SubmissionServiceTest test
mvn -s .mvn/settings.build.xml -Dtest=TutorGraphClientSseTest,TutorGraphClientFailureTest test
```

Expected: pass.

**Step 3: Frontend tests**

```bash
cd frontend
npm run test -- GuideNudgeCard.spec.ts TutorWorkflowPanel.spec.ts
npm run typecheck
```

Expected: pass.

**Step 4: Manual demo smoke**

Start stack:

```bash
cd /home/cypress/Pythontopia
./dev.sh
```

Smoke path:

1. Open a training problem.
2. Submit wrong code.
3. Confirm only a short WA nudge appears.
4. Click "开始分析".
5. Confirm GuideCoachAgent chooses a reasonable first action.
6. Submit improved code.
7. If AC, confirm short AC nudge appears.
8. Click "30 秒复盘".
9. Confirm AC review is structured and includes transfer direction.
10. Confirm no full answer or hidden tests appear.

**Step 5: Commit final fixes**

```bash
git status --short
git add <only-files-touched-for-this-plan>
git commit -m "chore(guide): verify coach agent refactor"
```

## Final Verification Record

Completed on 2026-06-16.

- Focused graph:
  `python3 -m pytest -q tests/test_tutor_workflow_coach_schema.py tests/test_guide_coach_agent.py tests/test_tutor_workflow_coach_graph.py tests/test_guide_guardrail_critic.py tests/test_guide_coach_golden.py tests/test_sse_agui.py`
  -> 38 passed.
- Focused backend workflow/submission:
  `mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,TutorWorkflowSubmissionFeedbackListenerTest,SubmissionServiceTest test`
  -> 17 tests, 0 failures, 0 errors.
- Focused backend graph client:
  `mvn -s .mvn/settings.build.xml -Dtest=TutorGraphClientSseTest,TutorGraphClientFailureTest test`
  -> 5 tests, 0 failures, 0 errors.
- Focused frontend:
  `npm run test -- tests/components/training/GuideNudgeCard.spec.ts tests/components/training/TutorWorkflowPanel.spec.ts`
  -> 2 files, 7 tests passed; `npm run typecheck` passed.
- Full graph:
  `python3 -m pytest -q` -> 652 passed, 1 warning.
- Full backend:
  `mvn -s .mvn/settings.build.xml test` -> 767 tests, 0 failures, 0 errors.
- Bilibili graph internal-auth focused gate:
  `mvn -s .mvn/settings.build.xml -Dtest=BilibiliVideoServiceTest,BilibiliVideoAcceptanceIntegrationTest test`
  -> 5 tests, 0 failures, 0 errors; live graph returned real Bilibili cards.
- Full frontend:
  `npm run test` -> 204 files / 1981 tests passed; `npm run typecheck` passed.
- Local stack health:
  `./dev.sh` started the stack; 5173 / 18080 / 18091 / 18092 health checks returned ok;
  `/api/course/videos` returned course video JSON; Postgres / Redis / graph / rag were healthy.

Manual browser AC/WA click-through, ECS public smoke, and Android / Windows / WeChat regressions were not executed in this verification pass.

## Acceptance Checklist

- [x] AC/WA/CE/RE/TLE trigger only a short nudge and wait for click.
- [x] Clicking continue invokes `GuideCoachAgent`, not a fixed event-only workflow.
- [x] Coach decision is structured and persisted.
- [x] `available_actions` are ranked by coach recommendation.
- [x] Student state tracks hint level, latest verdict, misconception hypothesis, and last action effectiveness.
- [x] Subsequent submissions update coach state.
- [x] Guardrails reject complete submit-ready code.
- [x] Transfer practice remains non-judge, no reference solution.
- [x] AG-UI progress is visible but does not reveal raw reasoning.
- [x] Focused graph, backend, and frontend tests pass.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Agent feels non-deterministic | Keep legal whitelist and golden evals |
| Agent leaks too much code | Guardrail critic plus skeleton validator |
| State drift between graph and Java | Java persisted state is source of truth; graph receives restored state every run |
| Too many prompts increase latency | Deterministic pre-rules choose obvious actions before LLM; keep one manager call per click |
| Existing workflow tests break | Keep `coach_mode=false` compatibility until tests are migrated |
| User gets overwhelmed | Continue one-card-per-click rule and short nudges |

## Handoff Notes

This plan should run before any deeper proactive companion work. It gives AI 导学助手 a real Agent core while preserving Pythontopia's educational boundaries: no hidden-test leakage, no full-answer dumping, and no open-ended autonomous loops.
