# Mainline Missing OJ Entry Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let a learner request and solve an AI programming exercise for a KC with no mainline programming problem.

**Architecture:** Reuse `useChallengeJudge`, the existing Boss problem and attempt APIs, `ChallengeWorkspace`, and the shared reviewed problem bank. Pass the current stage KC codes into `StageExamplePractice`; show an on-demand request for each KC missing from the stage's programming examples. Display only a same-KC problem accepted by the server review flow; submit through the existing server judge and refresh the mastery snapshot. Do not mark the stage OJ requirement complete or weaken expedition prerequisite checks. No API, table, model, or service changes.

**Tech Stack:** Vue 3, TypeScript, Vue Router, Vitest, Playwright.

---

### Task 1: Add failing regressions for missing programming problems

**Files:**
- Modify: `frontend/tests/components/mainline-stage/StageExamplePractice.spec.ts`
- Test: `frontend/tests/components/mainline-stage/StageExamplePractice.spec.ts`

Add a case with KC-010 and KC-011 and no programming examples. Assert each gets an on-demand AI practice action, while a KC already covered by an example does not get one. Mock the existing challenge judge for the component test.

Run the focused component and page specs; confirm the new assertions fail because the AI practice action does not exist.

### Task 2: Wire the existing route from the stage page

**Files:**
- Modify: `frontend/src/components/mainline-stage/StageExamplePractice.vue`
- Modify: `frontend/src/pages/StageLearnPage.vue`
- Test: `frontend/tests/pages/StageLearnPage.spec.ts`

Pass the stage KC codes into the practice card. Compute KCs not represented by `examples[].kcCode`; for each one, request a same-KC reviewed problem through `useChallengeJudge` and render it in `ChallengeWorkspace`. Submit with the existing server judge and refresh the mastery snapshot, without counting this extra exercise as a stage example. Preserve current training and completion links for existing examples. Add a page-level assertion that stage 5 offers the KC-010 action.

### Task 3: Verify behavior and record the result

Run the focused component/page tests, full frontend Vitest, `npm run typecheck`, ESLint on the changed Vue/spec files, and `git diff --check`. With the running local stack, verify stage 5 returns and displays a reviewed KC-010 AI programming exercise. Do not submit an automated answer as student evidence.

Update `docs/project/CHANGELOG.md` and `docs/operations/execution_status.md` with the implementation and current verification evidence.
