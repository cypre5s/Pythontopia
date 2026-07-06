# 2026-07-01 Launch Deep-Chain Acceptance

## Scope

This run verifies the local full-stack launch path for Pythontopia with real browser clicks,
real JWT sessions, real `/api` calls, graph/rag services, formal judge submissions, and teacher/student flows.

It adds one new Playwright suite:

- `frontend/e2e/deliveryClickRobustness.spec.ts`

The new suite targets repeated and varied click behavior that was not fully covered by the existing deep-chain tests:

- town HUD hide/reveal, pause menu open/close, and menu navigation to workshop,
- mainline map drawer open/close/reopen and stage deep-link return,
- guide desktop pager and mobile nav toggle overflow check,
- workshop history/study/calls drawers and resource modal close paths,
- direct refresh for training, board, review hall, profile, bug mine, problems, endless, and expedition,
- teacher home and classroom dashboard tab switching.

## Results

| Gate | Command | Result |
|---|---|---|
| Full-stack health | `curl :5173/:18080/:18091/:18092 /healthz` | PASS, all returned `{"status":"ok"}` |
| Frontend typecheck | `npm run typecheck` | PASS |
| Whitespace check | `git diff --check` | PASS |
| Functional Playwright deep-chain | `PYT_E2E_REGISTER=1 PYT_E2E_TEACHER_INVITE_CODE=pytopia-teacher npx playwright test e2e/agenticDeepLoop.spec.ts e2e/agenticDeepMatrix.spec.ts e2e/publicAcceptance.spec.ts e2e/studentLearningLoop.spec.ts e2e/teacherStudentFullFlow.spec.ts e2e/deliveryClickRobustness.spec.ts --project=chromium --workers=1` | PASS, 222 passed, 21.6m |
| Guide screenshot / scene coverage | `PYT_E2E_REGISTER=1 PYT_E2E_TEACHER_INVITE_CODE=pytopia-teacher npx playwright test e2e/guideScreenshots.spec.ts --project=chromium --workers=1 --retries=0` | PASS, 24 passed, 5.6m |
| Current Playwright inventory | `npx playwright test --project=chromium --list` | 246 tests in 7 files |

## Coverage Summary

| Area | Evidence |
|---|---|
| Auth/session | unauth redirect, register/login, guestOnly guard, demo account login |
| Town hub | Phaser canvas, HUD, pause menu, probe clearing, no fatal console |
| Mainline | map chapter selection, drawer close/reopen, stage deep-link, stage page return |
| AI guide | multi-turn SSE, fuzzy-goal clarification, injected network failure and retry |
| Guide/manual | desktop anchors, image lightbox, pager, mobile nav, mobile overflow check |
| Workshop | real streaming response, guide-to-workshop deep-link, calls/study/history drawers, resource modal close by button and Escape |
| RAG/library | `input函数`, `for循环`, `列表` all returned page-number evidence |
| Training/judge | AC, WA, RE; RE error surface checked for leakage |
| Learning loop | micro-check failure remediation, repeated failure resource swap, failed submission replan, passed submission loop pass |
| Bug Mine | failed submission opens debug session, layered cards, retry submit |
| Review/profile | review hall and learner profile page render |
| Teacher/classroom | teacher class creation, student join, task publish, AC evidence completion, teacher dashboard sync |
| Deep refresh | `/guide`, `/workshop`, `/mainline-map`, `/training`, `/board`, `/review-hall`, `/profile`, `/bug-mine`, `/problems`, `/endless`, `/expedition` |
| Error/security surface | login/API failures checked for stack/internal-host/token leakage |
| Screenshot scene coverage | town, pause, guide, mainline, stage, library, workshop, training, bug mine, review, profile, teacher, NPC, RAG, expedition, endless |

## Notes

- The first run of `deliveryClickRobustness.spec.ts` exposed two test-environment issues:
  - new-student profile probe blocked HUD clicks; the suite now clears blocking probes like the existing public acceptance suite,
  - mainline chapter selection uses a visually hidden accessibility mirror; the suite now reopens it with the established `dispatchEvent('click')` pattern and asserts the drawer is visible before entering a stage.
- `guideScreenshots.spec.ts` intentionally writes to `frontend/public/guide/screenshots/`. Screenshot assets were already dirty in this worktree and are not part of the launch-test code change.
- Real online LLM/provider canary traffic was not enabled here; this run verifies the local full-stack graph/rag-backed paths and dry-run/local service behavior.

## Remaining Risk

- The AI guide SSE paths are real and passed, but they are the slowest area in the suite: the longest test took 4.6m in this run.
- The worktree contains unrelated pre-existing dirty files. Only the new E2E suite, this report, and the changelog entry should be staged for this acceptance work.
