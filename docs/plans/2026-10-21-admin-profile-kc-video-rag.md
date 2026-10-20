# Admin, Profile, KC Labels, and Video Retrieval Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix admin landing and reported 403s, remove user-facing KC identifiers, make learner profile input and evidence views useful, fix the mainline drawer overlap, let Feynman teaching start without wrong-answer history, then add timestamped video transcript retrieval that improves courseware evidence recall.

**Architecture:** Keep role routing in the Vue router and use the existing 65-name KC map. Keep learner self-report separate from evidence-derived profile scores and reuse the existing interview API. In Feynman sessions with no historical mistakes, allow an independent teaching conversation with an empty misconception list; do not synthesize mistake evidence or unlock mistake-based judging. Index video transcript segments in a separate RAG index; use timestamped transcript matches as bounded query expansion for the existing page retriever, so AI callers still receive authentic course-page evidence IDs.

**Tech Stack:** Vue 3, TypeScript, Vue Router, Vitest, Spring Boot profile and tutor APIs, FastAPI RAG, existing BM25/hash embedder, Docker Compose release scripts, Playwright.

---

## Decisions and constraints

- Root/login defaults resolve to `/admin` for ADMIN and `/world` for other roles. Explicit admin deep links are retained; admin redirects to student-only routes land at `/admin`.
- KC IDs remain valid internal/API values and route parameters. Every rendered label, badge, narrative, and selector uses the existing KC name map; unknown IDs are suppressed instead of echoed.
- The profile radar only plots evidence-backed scores. Self-reported goal/background/preference values remain visibly labeled as self-report and continue to feed existing personalization signals.
- Video transcripts are machine-generated and unreviewed. Video matches may select a bounded same-chapter course-page retrieval pass, but are never returned as page citations or used to satisfy evidence gates.
- Feynman teaching works with or without historical practice failures. Empty history uses a clearly labeled independent teaching mode; misconception evidence stays empty and no mistake-based judgment is claimed.
- Preserve existing deployment data and database volumes. Mount the already-packaged transcript JSON read-only into the RAG container.

## Implementation tasks

1. Add a shared role-aware landing resolver and router/login tests. Cover root, login with a student-route `redirect`, and authenticated `/login` access for ADMIN.
2. Replace all visible raw KC renderings across learner, teacher, and admin Vue pages. Change unknown-code fallback and add seed-map completeness and rendering tests.
3. Improve `ProfilePage` and `ProfileRadar`: expose the existing interview directly, format profile enums as human labels, display the purpose/source of each field, refresh profile dimensions after an interview, and fix chart clipping/empty-evidence explanation. Test update, error, and evidence gating behavior.
4. Reserve vertical and horizontal space for the mainline drawer close button; assert no overlap with the star badge at desktop and narrow viewport widths.
5. Allow Feynman tutoring with an empty mistake history. The Graph agent should answer the student's explanation without inventing misconceptions, the backend should keep the session open with no mistake notice, and the UI should label independent teaching mode. Test real-error and no-error paths.
6. Add a typed timestamped video transcript source/index and query expansion to `pythontopia-rag`. Keep page and video indexes separate, bound video-derived supplemental page hits, expose retrieval trace/status, and test parsing, provenance, filters, ranking, and the existing golden retrieval set.
7. Mount production and development transcript data read-only into RAG; validate one-time clean deployment configuration and preserve existing shared data/volumes.
8. Update changelog and this work log, run frontend/backend/RAG/Graph checks and real-browser acceptance, then build and deploy ECS and push the verified changes to GitHub.

## Acceptance evidence

- Router unit tests prove ADMIN never defaults to `/world`; browser network log proves the admin session no longer requests the student-only endpoints in the attachment.
- Frontend source scan finds no rendered `KC-###`/`PY43-KC-###` labels; all 65 seeded knowledge points map to names.
- Profile browser flow can open, submit, and finish the real interview; the displayed profile updates; radar distinguishes missing evidence from zero/neutral scores and labels self-report separately.
- Mainline drawer screenshot and DOM geometry show the close button does not cover the star badge at desktop and mobile widths.
- A student with no failed submissions can choose a course knowledge point, explain it to the AI tutee, receive a response, and see that no error record or mistake-based verdict was fabricated.
- RAG evaluation compares baseline page retrieval with video-assisted retrieval on a fixed query set, reports Recall@5 and citation page IDs, and proves transcript matches never become courseware page IDs.
- Deployed browser smoke confirms admin landing and student UI flows, no relevant `console`/`pageerror`/failed API requests, and the current release health checks pass.

## Work log

- 2026-10-21: Read the supplied browser error attachment. It reports student-only API 403s after admin navigation and slow Web Vitals. Verified the current root and authenticated guest redirects both default to `/world`.
- 2026-10-21: Confirmed the KC map has exactly 65/65 seeded names and found direct identifier rendering in bug mine, teacher/admin review, stage, code theater, weekly companion, classroom, and NPC UI.
- 2026-10-21: Confirmed the profile API returns eight dimensions with evidence confidence; current interview is reachable from a collapsed section on the learning-twin page, and the radar's horizontal labels can be clipped by its 320px viewBox.
- 2026-10-21: Confirmed video transcripts exist for 34 videos and are marked `machine_generated_unreviewed`; current service only adds a ±30 second playhead window to tutor context. Production release already packages transcript JSON but RAG does not mount or index it.
- 2026-10-21: The Feynman page already allowed selecting a KC without wrong-answer history and the bug-hunt path already had generic samples, but the tutor rejected an empty misconception set and displayed a misleading "do more problems" notice. The no-history teaching path must preserve an empty evidence list.
- 2026-10-21: Implemented Feynman independent teaching across Vue, Graph, and Java. Empty-history replies keep misconceptions/evidence empty and produce no judgeable code; Java returns `NA` and skips mastery judging. Frontend Feynman tests 16/16, Graph Feynman tests 30/30 with Ruff clean, backend Feynman tests 19/19, and frontend typecheck passed. ECS Chromium used a newly registered student with no practice history, selected the correctly mapped “for 循环与遍历” KC, and completed a real DeepSeek response; misconceptions remained empty, no code/fix or judge request was produced, and browser/API errors were zero.
- 2026-10-21: Refined no-history Feynman replies to carry the selected course KC's Chinese topic and minimal learner preference into Graph. The current student explanation/question is sent as untrusted data; no-history responses keep misconception evidence empty and discard generated code. No-error bug hunts reuse matching shared samples; only mechanically validated AI-generated generic course-KC samples are persisted, without learner IDs or real-mistake data. Starting another round clears prior chat/judge state immediately. Full local results: frontend 280 files / 2,686 tests, Graph 1,968 passed / 3 skipped with Ruff clean, backend 1,782 tests with 0 failures/errors/skips; frontend typecheck and lint passed. Production/browser acceptance for this refinement is pending deployment.
- 2026-10-21: Local verification completed for the current branch: frontend full Vitest, typecheck, lint; Graph full suite 1,966 passed; RAG full suite 171 passed plus Ruff; backend full Maven 1,777 passed with no failures/errors/skips. Local Chromium with the real Spring, PostgreSQL, Graph/RAG services confirmed admin `/admin/operations` with no 403/API failures, student `/world`, profile interview visibility, and no close-button/star overlap at desktop or mobile widths; console errors, page errors, and failed requests were zero. Screenshots are in `/tmp/pythontopia-{admin-landing,mainline-desktop,mainline-mobile,profile-interview}.png`.
- 2026-10-21: Local RAG status reports 848 indexed video segments and 591 real courseware chunks. Queries for list append, `input`, and `for` returned courseware-only citations with video timestamp traces. The fixed fixture golden set remains 59/59 Recall@5 before and after video assistance. ECS release `20261021-014723` is healthy; a production query returned courseware page IDs `10996/10997/11042/11102` plus video timestamps, with no transcript citations. Production RAG reports `hash-bow-1024`, `degraded=true`, 591 chunks and 848 video segments; semantic vector quality was not validated.
- 2026-10-21: Release archive SHA-256 `7210587d50ac200bf606942eefa6fdaf94c73e06594c2229f3b43568701c36b6` matched at ECS. Installer preserved `deploy_pytopia_pg`, wrote a 444 KB pg_dump, kept previous release `20261020-191037`, installed `20261021-014723`, and removed the uploaded archive after success. All seven services and HTTPS health checks passed. The branch was pushed to GitHub as `codex/admin-profile-kc-video-rag`.
