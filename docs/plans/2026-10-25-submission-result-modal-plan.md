# Submission Result Modal Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Show every formal judge verdict in a dismissible training result modal; show truthful same-problem/same-language runtime and memory percentiles after at least ten usable AC submissions; and make the AI Tutor advance from real submissions into AC review/transfer or error feedback.

**Architecture:** Extend the existing Piston result contract with measured peak memory and persist it through the existing `memory_kb` field. Add an owner-checked submission performance endpoint that computes independent per-submission percentiles server-side. Reuse one accessible Vue modal in TrainingPanel (including tutor/variant submissions) and BugMinePanel retries; keep a compact reopen control after dismissal. Persist the latest tutor submission ID/verdict/phase in the existing session state, auto-run the matching trusted feedback event once, and bind the 476→488 transfer relationship in course data because 476 has no same-KC variant.

**Tech Stack:** Java 21, Spring Boot, JDBC, Piston API v2, Vue 3, TypeScript strict, Pinia, Vitest, Playwright.

---

### Task 1: Carry measured runtime and peak memory through the judge

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/submission/judge/SandboxRunResult.java`
- Modify: `backend/src/main/java/com/pythontopia/submission/judge/PistonPythonSandbox.java`
- Modify: `backend/src/main/java/com/pythontopia/submission/judge/JudgeCaseEvaluator.java`
- Modify: `backend/src/main/java/com/pythontopia/submission/judge/JudgeService.java`
- Modify: `backend/src/main/java/com/pythontopia/submission/repository/PracticeSubmissionRepository.java`
- Test: `backend/src/test/java/com/pythontopia/submission/judge/PistonPythonSandboxTest.java`
- Test: `backend/src/test/java/com/pythontopia/submission/judge/JudgeCaseEvaluatorTest.java`
- Test: `backend/src/test/java/com/pythontopia/submission/judge/JudgeServiceTest.java`

**Steps:**

1. Add Piston response fixtures asserting `run.memory` bytes convert to KB; assert an absent/invalid memory value remains null.
2. Extend sandbox/evaluation results to carry nullable memory KB, summing per-case wall time as today and taking the maximum per-case memory.
3. Persist runtime and peak memory together for final judge status; preserve null for static-check/system errors or absent sandbox measurements.
4. Run the three targeted backend test classes and fix any other `SandboxRunResult` constructor call sites reported by compilation.

### Task 2: Add private, submission-based AC performance rankings

**Files:**
- Create: `backend/src/main/java/com/pythontopia/submission/dto/SubmissionPerformanceDto.java`
- Create: `backend/src/main/java/com/pythontopia/submission/service/SubmissionPerformanceService.java`
- Modify: `backend/src/main/java/com/pythontopia/submission/repository/PracticeSubmissionRepository.java`
- Modify: `backend/src/main/java/com/pythontopia/submission/controller/SubmissionController.java`
- Test: `backend/src/test/java/com/pythontopia/submission/SubmissionPerformanceServiceTest.java`
- Test: `backend/src/test/java/com/pythontopia/submission/SubmissionControllerTest.java`

**Steps:**

1. Add tests for owner-only lookup, AC-only comparisons, submission-count samples, strict-greater “beats” percentages, independent metric counts, and the ten-submission visibility threshold.
2. Add a repository aggregate query scoped to the submission’s problem and language, counting AC submissions with available metrics; do not return individual records or identities.
3. Add `GET /api/submissions/{id}/performance`; resolve the submission under the current student ID and return percentages only when each metric has at least ten available AC submissions.
4. Verify that a foreign submission returns the same not-found behavior as the existing detail endpoint and that a non-AC submission cannot retrieve ranking data.

### Task 3: Build the dismissible result details modal

**Files:**
- Modify: `frontend/src/api/dto/Submission.ts`
- Modify: `frontend/src/api/submissions.ts`
- Create: `frontend/src/components/training/SubmissionResultModal.vue`
- Test: `frontend/src/components/training/__tests__/SubmissionResultModal.spec.ts`

**Steps:**

1. Add the performance DTO and typed API client; test AC metric cards, below-threshold rank omission, and ranking API failure fallback.
2. Build a responsive, accessible modal using existing Pythontopia colors and components: verdict hero, passed-case count, submission time/language, runtime and memory cards, and per-metric Beats bars.
3. Support close button, Escape, and backdrop click; keep hidden-case inputs/expected outputs out of the rendering path.
4. Show safe failure reason and relevant existing action for non-AC results; never show unrelated users or their source code.

### Task 4: Connect all training result surfaces and update project records

**Files:**
- Modify: `frontend/src/components/training/TrainingPanel.vue`
- Modify: `frontend/src/components/bugmine/BugMinePanel.vue`
- Modify: `docs/clients/api-contract.md`
- Modify: `docs/project/CHANGELOG.md`
- Create: `docs/project/WORK_LOG.md`
- Test: `frontend/src/components/training/__tests__/SubmissionResultModal.spec.ts`

**Steps:**

1. Open the modal when the training store receives a new formal judge result, including tutor-transfer submissions; do not auto-open a previously loaded result.
2. In variant and BugMine retry flows, fetch the owned submission detail after judging and show the same modal; preserve the existing navigation/resolve behavior after dismissal.
3. Replace the large persistent inline result with a compact summary that reopens the latest detail.
4. Document the new authenticated API, sample semantics, threshold, and user-visible interaction; add a dated work-log entry and changelog item.

### Task 5: Advance Tutor from authoritative submission feedback

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowSubmissionFeedbackListener.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowRepository.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/TutorWorkflowService.java`
- Modify: `backend/src/main/java/com/pythontopia/tutor/dto/TutorWorkflowSessionDto.java` or active snapshot mapping
- Modify: `backend/src/main/resources/db/migration/V235__tutor_transfer_problem_relation.sql`
- Modify: `backend/src/main/java/com/pythontopia/submission/repository/PublicProblemRepository.java`
- Modify: `services/pythontopia-graph/pythontopia_graph/schemas/tutor_workflow.py`
- Modify: `frontend/src/api/tutorWorkflow.ts`
- Modify: `frontend/src/components/training/TutorWorkflowPanel.vue`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowSubmissionFeedbackListenerTest.java`
- Test: `backend/src/test/java/com/pythontopia/tutor/TutorWorkflowServiceTest.java`
- Test: `services/pythontopia-graph/tests/test_tutor_workflow.py`
- Test: `frontend/src/components/training/__tests__/TutorWorkflowPanel.spec.ts`

**Steps:**

1. Preserve submission ID, problem ID, and verdict in tutor checkpoint/snapshot; update phase to AC_REVIEW for base AC or ERROR_FEEDBACK for learner failures. SYSTEM_ERROR remains outside this feedback path.
2. Link generated AC_REVIEW / ERROR_FEEDBACK / TRANSFER cards to the source submission ID and have TutorWorkflowPanel use server snapshot state to recover a not-yet-generated event after submit or reload.
3. After a source AC, run AC_REVIEW and then TRANSFER only when the persisted post-AC direction and trusted transfer problem are available; for WA/RE/TLE/CE/MLE run ERROR_FEEDBACK once. Do not reveal hidden tests.
4. Search same-KC transfer variants first. When none exist for problem 476, use an explicit reversible 476→488 course-problem relation. 488 adds circumference alongside area and two output lines; retain its existing `Low` metadata and describe the exercise truthfully as a more involved application.
5. Add focused tests for event feedback, phase restoration, duplicate suppression, trusted transfer binding, and migration data. Include transfer submissions in result modal handling while keeping the base session's problem binding intact.

### Task 6: Verify, push, and publish the rebuilt release

**Commands / files:**
- Backend: `cd backend && mvn test`
- Frontend: `cd frontend && npm run typecheck && npm run lint && npm run test && npm run build`
- Browser: run the training submission flow in Chromium and inspect console, page errors, and failed requests.
- Release: `.github/workflows/publish-ecs-release.yml`

**Steps:**

1. Run backend, graph, and frontend checks required by `AGENTS.md`; fix failures without staging unrelated worktree changes.
2. Use the running local stack for real AC and non-AC submissions; verify Piston metrics, modal close/reopen, tutor AC→transfer and failure→diagnosis, refresh recovery, and browser console/network evidence.
3. Commit only feature files and this feature’s documentation; leave pre-existing dirty files unstaged.
4. Push `main`, tag the new `web-vYYYYMMDD.N` release, and monitor the existing workflow until it builds, verifies, and uploads the release package.
5. Confirm the GitHub Release asset exists and record the package URL, size, checksum, commit, and CI result in the work log.
