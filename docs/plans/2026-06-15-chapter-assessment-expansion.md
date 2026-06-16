# Chapter Assessment Expansion Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a 33-question chapter quiz and expand the final chapter test to 99 questions, with whole-paper submission, 120-minute final duration, evidence grounding, and cross-paper deduplication.

**Architecture:** Reuse `chapter_test` with an assessment-kind discriminator. Generate quizzes in one Graph request and finals as three merged quiz-sized Graph requests. Keep grading server-side and normalize weighted raw scores to 100.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL/Flyway, Python/FastAPI/Pydantic, Vue 3, TypeScript, Vitest, pytest.

---

### Task 1: Persist and isolate assessment kinds

**Files:**
- Create: `backend/src/main/resources/db/migration/V164__chapter_assessment_kind.sql`
- Create: `backend/src/main/java/com/pythontopia/mainline/domain/ChapterAssessmentKind.java`
- Modify: `backend/src/main/java/com/pythontopia/mainline/domain/ChapterTestRecord.java`
- Modify: `backend/src/main/java/com/pythontopia/mainline/test/ChapterTestRepository.java`
- Test: `backend/src/test/java/com/pythontopia/mainline/test/ChapterTestServiceTest.java`

1. Write failing tests proving QUIZ and FINAL attempts, latest records, and best scores are isolated.
2. Run the focused backend tests and confirm the new calls do not compile or fail expectations.
3. Add the migration, enum, record field, and repository overloads.
4. Keep legacy repository methods delegating to FINAL so overview, teacher dashboard, and star calculation remain compatible.
5. Run focused tests.

### Task 2: Add quiz orchestration and final exclusions

**Files:**
- Create: `backend/src/main/java/com/pythontopia/mainline/test/ChapterQuizController.java`
- Modify: `backend/src/main/java/com/pythontopia/mainline/test/ChapterTestService.java`
- Modify: `backend/src/main/java/com/pythontopia/mainline/test/ChapterTestPregenService.java`
- Modify: `backend/src/main/java/com/pythontopia/mainline/test/ChapterTestGraphClient.java`
- Test: `backend/src/test/java/com/pythontopia/mainline/test/ChapterTestServiceTest.java`
- Test: `backend/src/test/java/com/pythontopia/mainline/test/ChapterTestPregenServiceTest.java`

1. Write failing tests for QUIZ `10/10/10/3` with 3600 seconds and FINAL `30/30/30/9` with 7200 seconds.
2. Write a failing test proving FINAL generation receives normalized signatures from the latest QUIZ paper.
3. Add generic internal prepare/start/submit/latest methods parameterized by assessment kind.
4. Add strict generated-paper validation before `READY`.
5. Remove the old wrong-sized placeholder fallback from the new generation path; failures remain FAILED.
6. Add quiz REST endpoints and run focused tests.

### Task 3: Normalize whole-paper scores

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/mainline/test/ChapterTestJudge.java`
- Test: `backend/src/test/java/com/pythontopia/mainline/test/ChapterTestJudgeTest.java`

1. Write failing tests proving a fully correct arbitrary-weight paper scores 100 and a half-credit paper scores 50.
2. Normalize the aggregate score while preserving per-item raw awarded/max weights.
3. Run judge and service tests.

### Task 4: Generate 99 questions in bounded Graph batches

**Files:**
- Modify: `services/pythontopia-graph/pythontopia_graph/schemas/chapter_test.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/agents/paper_designer.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/agents/paper_pipeline.py`
- Test: `services/pythontopia-graph/tests/test_chapter_test_pipeline.py`

1. Write a failing test requesting `30/30/30/9` and providing three valid `10/10/10/3` payloads.
2. Assert 99 merged items, exact type counts, unique prefixed qids, and three provider calls.
3. Write a failing exclusion test where a generated stem matches `excluded_questions`.
4. Add `excluded_questions` to the request schema and prompt.
5. Split large counts into bounded chunks, merge papers, prefix qids, and reject normalized signature conflicts.
6. Run chapter-test Graph tests.

### Task 5: Expose quiz mode in the web client

**Files:**
- Modify: `frontend/src/api/chapterTest.ts`
- Modify: `frontend/src/components/chaptertest/useChapterTestExam.ts`
- Modify: `frontend/src/pages/ChapterTestPage.vue`
- Modify: `frontend/src/app/router.ts`
- Modify: `frontend/src/pages/StageLearnPage.vue`
- Test: `frontend/tests/api/chapterTest.spec.ts`
- Test: `frontend/tests/pages/ChapterTestPage.spec.ts`
- Test: `frontend/tests/pages/StageLearnPage.spec.ts`

1. Write failing API tests for `/quiz/*` and existing `/test/*`.
2. Write failing page tests for quiz title/count/time copy and final title/count/time copy.
3. Write a failing stage-page test for the final-stage quiz entry gated by video and PPT completion.
4. Parameterize API/composable/page mode, register the route, and add the entry button.
5. Run focused Vitest suites and typecheck.

### Task 6: Verify locally and deploy

**Files:**
- Modify only if needed by verified failures.

1. Run backend focused tests, then `mvn test`.
2. Run Graph chapter-test tests, then the Graph test suite.
3. Run frontend focused tests, `npm run typecheck`, and `npm run test`.
4. Build the production release archive with the repository release script.
5. Verify the archive checksum, upload it to `ecs-pythontopia`, and run `install_release.sh`.
6. Run public acceptance with `NO_PROXY=47.120.29.62`.
7. Use an authenticated public smoke flow to verify QUIZ has `10/10/10/3`, FINAL has `30/30/30/9`, final duration is 7200 seconds, and no normalized stem/title signature overlaps.
