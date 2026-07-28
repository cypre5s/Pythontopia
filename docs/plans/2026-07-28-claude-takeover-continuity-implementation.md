# Claude Code Takeover Continuity Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restore the backend baseline and complete the interrupted Feynman, AI Runtime, TutorBench, and learning-flow correctness work without adding a new product subsystem.

**Architecture:** Keep the existing Spring Boot, Vue, Graph, judge, evidence, and runtime boundaries. Apply small TDD slices on `main`; treat Claude worktree diffs as diagnostic evidence only. Make `main` authoritative and audit only worktree branches whose tip commit is dated 2026-07-21 or later.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, Flyway, Maven, PostgreSQL/Testcontainers, Vue 3, TypeScript strict, Pinia, Vitest, Playwright.

---

## Task 1: Make Flyway migration versions unique

**Files:**
- Create: `backend/src/test/java/com/pythontopia/migration/FlywayMigrationVersionUniquenessTest.java`
- Move: `backend/src/main/resources/db/migration/V186__users_token_version.sql`
- Move: `backend/src/main/resources/db/migration/V186__classroom_kc_snapshot_learning_count.sql`
- Move: `backend/src/main/resources/db/migration/V186__drop_retired_economy_and_fast_travel.sql`
- Modify: `backend/src/main/java/com/pythontopia/auth/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/pythontopia/auth/security/JwtService.java`
- Modify: migration-specific tests found by `rg "V186__" backend/src/test`
- Modify: `docs/project/CHANGELOG.md`
- Modify: `docs/plans/2026-07-28-claude-takeover-continuity-worklog.md`

### Step 1: Write the failing uniqueness test

Add a pure unit test that scans `src/main/resources/db/migration`, extracts the numeric part of every `V...__*.sql`, and asserts that no version has more than one filename:

```java
class FlywayMigrationVersionUniquenessTest {
  private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.+\\.sql$");

  @Test
  void versionedMigrationsHaveUniqueVersions() throws IOException {
    Map<String, List<String>> filesByVersion;
    try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
      filesByVersion =
          files.map(path -> path.getFileName().toString())
              .map(VERSION::matcher)
              .filter(Matcher::matches)
              .collect(Collectors.groupingBy(
                  matcher -> matcher.group(1),
                  TreeMap::new,
                  Collectors.mapping(matcher -> matcher.group(0), Collectors.toList())));
    }
    assertThat(filesByVersion)
        .allSatisfy((version, files) ->
            assertThat(files).as("Flyway version V%s", version).hasSize(1));
  }
}
```

### Step 2: Verify RED

Run:

```bash
cd backend
mvn -s /home/cypress/.m2/settings.xml \
  -Dtest=FlywayMigrationVersionUniquenessTest test
```

Expected: FAIL and list the four `V186` files.

### Step 3: Apply the minimal migration fix

Keep `V186__learner_profile_cognitive_style_jsonb.sql` because
`CognitiveStyleColumnMigrationTest` explicitly owns it. Rename:

```text
V186__users_token_version.sql
  → V188__users_token_version.sql
V186__classroom_kc_snapshot_learning_count.sql
  → V189__classroom_kc_snapshot_learning_count.sql
V186__drop_retired_economy_and_fast_travel.sql
  → V190__drop_retired_economy_and_fast_travel.sql
```

Update exact comments/references from V186 to the new version. Do not change SQL contents.

### Step 4: Verify GREEN and real Flyway

Run:

```bash
cd backend
mvn -s /home/cypress/.m2/settings.xml \
  -Dtest=FlywayMigrationVersionUniquenessTest,CognitiveStyleColumnMigrationTest,LoopRunServiceTest test
```

Expected: all tests PASS and `LoopRunServiceTest` migrates a fresh PostgreSQL database.

### Step 5: Update records and commit

Record RED/GREEN commands in the worklog and append a completed Phase 1 entry to Changelog.

Stage exact files and commit:

```bash
git commit -m "fix(backend): resolve merged Flyway migration versions"
```

---

## Task 2: Make Feynman teaching fail fast and replay real history

**Files:**
- Create: `backend/src/test/java/com/pythontopia/feynman/FeynmanSessionServiceTest.java`
- Modify: `backend/src/test/java/com/pythontopia/feynman/FeynmanGraphClientTest.java`
- Modify: `backend/src/main/java/com/pythontopia/feynman/FeynmanGraphClient.java`
- Modify: `backend/src/main/java/com/pythontopia/feynman/FeynmanSessionService.java`
- Modify: `backend/src/main/java/com/pythontopia/feynman/dto/FeynmanDtos.java`

### Step 1: Add the Graph history contract test

Extend `FeynmanGraphClientTest` so the request must contain:

```json
{
  "history": [
    {"role": "user", "content": "第一轮学生解释"},
    {"role": "assistant", "content": "第一轮学弟追问"}
  ]
}
```

Call:

```java
client.step(kcCode, misconceptions, currentTeaching, history);
```

### Step 2: Verify RED

Run:

```bash
cd backend
mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanGraphClientTest test
```

Expected: test compilation fails because the history-aware method and DTO do not exist.

### Step 3: Add session-service failure tests

Create focused Mockito tests:

1. Graph returns `Optional.empty()`:
   - `teach()` throws `BusinessException`;
   - status is `BAD_GATEWAY`;
   - code is `FEYNMAN_TUTEE_UNAVAILABLE`;
   - no TUTEE insert or session status update is issued.
2. Graph returns an empty misconception list:
   - session remains `OPEN`;
   - response includes `noticeMd`;
   - response does not claim the learner taught successfully.
3. Existing `feynman_turn` rows:
   - map `STUDENT → user`, `TUTEE → assistant`;
   - are sent in chronological order;
   - current teaching is not duplicated in history.

### Step 4: Verify RED

Run:

```bash
cd backend
mvn -s /home/cypress/.m2/settings.xml \
  -Dtest=FeynmanSessionServiceTest,FeynmanSessionServiceSecurityTest test
```

Expected: the new behavior assertions fail against fallbackStep and empty-stream `allMatch`.

### Step 5: Implement the smallest teaching-chain change

- Add `ConversationTurn` and Graph wire `Turn` records with explicit snake-case DTO fields.
- Query the latest 12 stored turns before inserting the current STUDENT turn.
- Keep at most 6000 history characters, dropping oldest history first.
- Replace `fallbackStep` with:

```java
.orElseThrow(() -> new BusinessException(
    HttpStatus.BAD_GATEWAY,
    "FEYNMAN_TUTEE_UNAVAILABLE",
    "AI 学弟暂时不可用，本次教学未判定成败，请稍后重试"));
```

- Delete `fallbackStep`.
- Require a non-empty misconception list in `allResolved`.
- Add nullable `noticeMd` to `TurnResponse`.

### Step 6: Verify GREEN

Run:

```bash
cd backend
mvn -s /home/cypress/.m2/settings.xml \
  -Dtest=FeynmanGraphClientTest,FeynmanSessionServiceTest,FeynmanSessionServiceSecurityTest,FeynmanControllerSecurityTest test
```

Expected: all tests PASS.

---

## Task 3: Make Feynman judging four-state and concurrency-idempotent

**Files:**
- Modify: `backend/src/test/java/com/pythontopia/feynman/FeynmanAntiCheatTest.java`
- Modify: `backend/src/test/java/com/pythontopia/feynman/FeynmanJudgeServiceTest.java`
- Modify: `backend/src/main/java/com/pythontopia/feynman/FeynmanAntiCheat.java`
- Modify: `backend/src/main/java/com/pythontopia/feynman/FeynmanJudgeService.java`
- Modify: `backend/src/main/java/com/pythontopia/feynman/dto/FeynmanDtos.java`

### Step 1: Add RED tests for code-specific anti-cheat

Add assertions:

```java
assertThat(FeynmanAntiCheat.looksLikeLeakedAnswer("def solve():\n  return 1")).isFalse();
assertThat(FeynmanAntiCheat.looksLikeLeakedAnswer("ANSWER_KEY = 'secret'")).isTrue();
assertThat(FeynmanAntiCheat.stripCodeFence("```python\nprint(1)\n```"))
    .isEqualTo("print(1)\n");
```

Run `FeynmanAntiCheatTest`; expect missing-method compilation failure.

### Step 2: Add RED tests for verdict semantics

Extend `FeynmanJudgeServiceTest` to cover:

- existing `JUDGED_PASS` returns `PASSED` without calling mastery or publishing events;
- AC with conditional status update returning 1 writes mastery/event once;
- AC with conditional status update returning 0 replays success without mastery/event;
- `SYSTEM_ERROR` returns `ERROR` and never writes `JUDGED_FAIL`;
- no test cases returns `ERROR`;
- concept mode returns `NA`;
- fenced `def` code reaches the sandbox;
- public-sample failure message says `公开样例` and does not expose expected output;
- hidden failure message contains no expected answer.

Inject a real small `JudgeWorkerPool` or a synchronous mock whose
`submitAndWait` executes the supplied callable. The service is called directly
from the controller/Tomcat thread, so this is not a nested worker-pool wait.

### Step 3: Verify RED

Run:

```bash
cd backend
mvn -s /home/cypress/.m2/settings.xml \
  -Dtest=FeynmanAntiCheatTest,FeynmanJudgeServiceTest test
```

Expected: compile/assertion failures for missing status, missing worker pool, and old boolean-only behavior.

### Step 4: Implement the smallest judge change

- Add `JudgeStatus { PASSED, FAILED, ERROR, NA }`.
- Keep `passed` as the boolean projection of `PASSED`, and add authoritative `status`.
- Submit `caseEvaluator.evaluate` through `JudgeWorkerPool`.
- On real AC, claim the one-time reward with:

```sql
UPDATE feynman_session
SET status = 'JUDGED_PASS'
WHERE id = ? AND status <> 'JUDGED_PASS'
```

- Only when the affected-row count is 1 may the transaction call
  `MasteryEvaluator.applyEvent` and publish `TaughtAiSucceeded`.
- Map infrastructure/no-case failures to `ERROR`, concept mode to `NA`.
- Split teaching-text anti-cheat from produced-code leakage checks.

### Step 5: Verify GREEN

Run the two tests again and then:

```bash
cd backend
mvn -s /home/cypress/.m2/settings.xml \
  -Dtest='com.pythontopia.feynman.*Test' test
```

Expected: all Feynman backend tests PASS.

### Step 6: Update records and commit Tasks 2–3

Add the exact RED/GREEN evidence to the worklog and append the completed
Feynman backend entry to Changelog.

Commit:

```bash
git commit -m "fix(feynman): make teaching and judging evidence-safe"
```

---

## Task 4: Align the Feynman frontend with backend truth

**Files:**
- Modify: `frontend/src/api/feynman.ts`
- Modify: `frontend/src/stores/feynman.ts`
- Modify: `frontend/src/components/feynman/FeynmanPanel.vue`
- Modify: `frontend/tests/components/FeynmanPanel.spec.ts`
- Create or modify: `frontend/tests/api/feynman.spec.ts`
- Create or modify: `frontend/tests/stores/feynman.spec.ts`

### Step 1: Write RED API/store/component tests

Cover:

- teach request timeout is greater than backend connect+read upper bound;
- judge request has a 60-second timeout;
- `ERROR` and `NA` remain distinct in Pinia and are not coerced to `FAILED`;
- a Graph failure preserves the submitted explanation and shows a retry button;
- a `noticeMd` response renders separately from the LLM reply;
- concept mode disables true judging with a neutral explanation;
- an already `PASSED` session disables repeated judging;
- `ERROR` uses warning styling and `NA` uses neutral styling.

### Step 2: Verify RED

Run:

```bash
cd frontend
npx vitest run \
  tests/components/FeynmanPanel.spec.ts \
  tests/api/feynman.spec.ts \
  tests/stores/feynman.spec.ts
```

Expected: failures because status, timeout, notice, retry, and disable behavior do not exist.

### Step 3: Implement minimal UI/DTO changes

- Make verdict `status` required and add `NA`.
- Use 35 seconds for Graph-backed teach/bug-hunt and 60 seconds for judge.
- Use backend status as Pinia truth.
- Preserve only the most recent failed teach text for explicit retry.
- Render `noticeMd` as a compact system notice.
- Do not add new pages or cards.

### Step 4: Verify GREEN

Run:

```bash
cd frontend
npx vitest run \
  tests/components/FeynmanPanel.spec.ts \
  tests/api/feynman.spec.ts \
  tests/stores/feynman.spec.ts
npm run typecheck
```

Expected: tests and typecheck PASS.

### Step 5: Commit

Update worklog/Changelog and commit:

```bash
git commit -m "fix(feynman): align UI with four-state judge contract"
```

---

## Task 5: Give AI Runtime an explicit SELF or PLATFORM subject

**Files:**
- Create: `backend/src/main/java/com/pythontopia/ai/runtime/AiRuntimePlatformSnapshotRepository.java`
- Create: `backend/src/test/java/com/pythontopia/ai/runtime/AiRuntimePlatformSnapshotRepositoryTest.java`
- Modify: `backend/src/main/java/com/pythontopia/ai/runtime/AiRuntimeOperationsDto.java`
- Modify: `backend/src/main/java/com/pythontopia/ai/runtime/AiRuntimeSummaryService.java`
- Modify: `backend/src/main/java/com/pythontopia/ai/runtime/AiRuntimeSummaryController.java`
- Modify: `backend/src/test/java/com/pythontopia/ai/runtime/AiRuntimeSummaryServiceTest.java`
- Modify: `backend/src/test/java/com/pythontopia/ai/runtime/AiRuntimeSummaryControllerTest.java`
- Modify: `frontend/src/api/aiRuntime.ts`
- Modify: `frontend/src/components/workshop/RuntimeOperationsPanel.vue`
- Modify: `frontend/tests/api/aiRuntime.spec.ts`
- Modify: `frontend/tests/components/workshop/RuntimeOperationsPanel.spec.ts`

### Step 1: Write RED backend tests

Service/controller tests must prove:

- STUDENT → existing personal summary and subject `SELF`;
- TEACHER/ADMIN → `operationsForPlatform()` and subject `PLATFORM`;
- teacher ID is never passed to `summaryForUser`;
- platform snapshot with zero evidence produces `INSUFFICIENT_EVIDENCE`;
- platform evidence/resource/touched-KC counts drive the existing five operation statuses without exposing user IDs.

Repository integration test migrates an empty PostgreSQL database and calls
`snapshot()`. Expected: all aggregate counts are zero, proving the SQL references
real tables/columns and uses one aggregate query.

### Step 2: Verify RED

Run:

```bash
cd backend
mvn -s /home/cypress/.m2/settings.xml \
  -Dtest=AiRuntimeSummaryServiceTest,AiRuntimeSummaryControllerTest,AiRuntimePlatformSnapshotRepositoryTest test
```

Expected: missing subject/platform repository behavior.

### Step 3: Implement minimal platform aggregation

Create one repository query over existing facts:

- active STUDENT count from `users`;
- learners with evidence from the union of
  `learning_event_log`, `learning_resource`, `resource_learning_check`, and `kc_mastery_event`;
- failed practice count from `learning_event_log`;
- resource run count from `resource_run`;
- touched KC count from existing KC evidence tables.

Return only aggregate counts. Add:

```java
record OperationsSubject(
    String scope,
    String label,
    int learnerCount,
    int evidenceLearnerCount) {}
```

Controller behavior:

```java
if (user.role() == Role.STUDENT) {
  return service.operationsForUser(user.userId());
}
return service.operationsForPlatform();
```

Do not add request parameters or a new endpoint.

### Step 4: Add RED frontend subject tests

Require `AiRuntimeOperations.subject` and assert the panel renders:

```text
本人学习证据
```

or:

```text
全平台学习证据
```

### Step 5: Implement and verify frontend

Add the explicit subject DTO and one compact subject line in
`RuntimeOperationsPanel`; do not add a new card.

Run:

```bash
cd frontend
npx vitest run \
  tests/api/aiRuntime.spec.ts \
  tests/components/workshop/RuntimeOperationsPanel.spec.ts
npm run typecheck
```

Expected: PASS.

---

## Task 6: Correct TutorBench remediation semantics

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/learner/effect/LearningEffectTraceRepository.java`
- Modify: `backend/src/main/java/com/pythontopia/tutorbench/TutorBenchEvaluator.java`
- Modify: `backend/src/test/java/com/pythontopia/learner/effect/LearningEffectTraceServiceTest.java`
- Modify: `backend/src/test/java/com/pythontopia/tutorbench/TutorBenchEvaluatorTest.java`

### Step 1: Write RED tests

Add:

- one failed-and-remediated check plus 99 passes → remediation rate `1.0`;
- zero failed checks → anti-cheat/remediation case passes with message containing `不适用`;
- all-zero new learner is not accused of gaming;
- failed checks with zero remediation fail specifically as “未触发补救”;
- zero failures with excess mastery delta still fail as gaming risk.

### Step 2: Verify RED

Run:

```bash
cd backend
mvn -s /home/cypress/.m2/settings.xml \
  -Dtest=LearningEffectTraceServiceTest,TutorBenchEvaluatorTest test
```

Expected: old denominator and old zero-failure logic fail.

### Step 3: Implement minimal correction

- Compute remediation rate with failed checks as denominator.
- Branch `failedCheckCount == 0` before requiring remediation.
- Keep the mastery-delta anti-gaming check active in both branches.

### Step 4: Verify GREEN and commit Tasks 5–6

Run targeted backend/frontend tests again. Update worklog/Changelog and commit:

```bash
git commit -m "fix(runtime): aggregate teacher operations and correct TutorBench"
```

---

## Task 7: Full regression and real browser continuity

**Files:**
- Create or modify: `frontend/e2e/feynmanContinuity.spec.ts`
- Modify: `docs/project/CHANGELOG.md`
- Modify: `docs/plans/2026-07-28-claude-takeover-continuity-worklog.md`

### Step 1: Backend full regression

Run:

```bash
cd backend
mvn -s /home/cypress/.m2/settings.xml clean test
```

Expected: `BUILD SUCCESS`, zero failures/errors. If Testcontainers host-port
contention occurs, preserve the full output and rerun each exact failing class;
do not report targeted reruns as a clean full run.

### Step 2: Python services regression

Run:

```bash
cd services/pythontopia-graph
.venv/bin/python -m pytest -q

cd ../pythontopia-rag
.venv/bin/python -m pytest -q
```

Expected: all non-skipped tests PASS.

### Step 3: Frontend full gates

Run:

```bash
cd frontend
npm run typecheck
npm run lint
npm run test
```

Expected: no type/lint errors and all tests PASS.

### Step 4: Real stack health

Run root `./dev.sh`, then verify:

```bash
curl -fsS http://127.0.0.1:5173/healthz
curl -fsS http://127.0.0.1:18080/healthz
curl -fsS http://127.0.0.1:18091/healthz
curl -fsS http://127.0.0.1:18092/healthz
```

Expected: every service reports `{"status":"ok"}`.

### Step 5: Real browser tests

Use existing authentication helpers from `publicAcceptance.spec.ts`. The
dedicated Feynman test must:

1. register/login a real student;
2. open `/feynman?kc=PY43-KC-010`;
3. start a real session;
4. verify Graph failure is shown as retryable and not as TAUGHT when the service is intentionally unavailable, or complete a real Graph turn when available;
5. exercise concept mode and prove true judging is disabled with a neutral message;
6. verify no console/page fatal errors.

Then run:

```bash
cd frontend
PYT_E2E_REGISTER=1 npx playwright test \
  e2e/feynmanContinuity.spec.ts \
  e2e/publicAcceptance.spec.ts \
  --project=chromium --workers=1 --retries=0 \
  --grep "费曼|主线地图|主城画布加载完成"
```

Expected: all selected tests PASS.

### Step 6: Static checks and final documentation

Run:

```bash
git diff --check
git status --short
```

Update worklog with every actual count and mark only proven phases complete.
Append final Changelog entry with implemented behavior and honest unverified risk.

Commit exact documentation and E2E files:

```bash
git commit -m "test: verify Feynman and learning-loop continuity"
```

---

## Task 8: Merge recent-week worktrees into main

**Files:** Git metadata only; do not edit product files during the audit.

### Step 1: Enumerate scoped worktrees/branches

List all worktrees and branch tips:

```bash
git worktree list --porcelain
git for-each-ref refs/heads \
  --format='%(committerdate:iso8601)|%(refname:short)|%(objectname)'
```

Keep only branch tips committed from 2026-07-21 through 2026-07-28.

### Step 2: Classify every scoped branch

For each recent branch:

```bash
git merge-base --is-ancestor <branch> main
git log --oneline --left-right main...<branch>
git -C <worktree-path> status --short
```

Classify:

- already merged: branch tip is a `main` ancestor;
- task-relevant dirty WIP: behavior has been reimplemented and verified on main;
- unmerged commits: inspect diff and merge/cherry-pick only when in scope and independently verified;
- unrelated recent branch: report, do not silently contaminate main.

### Step 3: Merge relevant commits and verify

Merge/cherry-pick in chronological dependency order. Resolve conflicts using
current tested `main` behavior as authority. After every merge, rerun the
affected targeted tests.

### Step 4: Final proof

For every recent in-scope branch, prove its required behavior/commit is present
on `main`. Record exclusions and reasons in the worklog. Do not delete worktrees.
