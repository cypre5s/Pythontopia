# Admin, Profile, KC Labels, and Video Retrieval Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix admin landing and reported 403s, remove user-facing KC identifiers, make learner profile input and evidence views useful, fix the mainline drawer overlap, then add timestamped video transcript retrieval that improves courseware evidence recall.

**Architecture:** Keep role routing in the Vue router and use the existing 65-name KC map. Keep learner self-report separate from evidence-derived profile scores and reuse the existing interview API. Index video transcript segments in a separate RAG index; use timestamped transcript matches as bounded query expansion for the existing page retriever, so AI callers still receive authentic course-page evidence IDs.

**Tech Stack:** Vue 3, TypeScript, Vue Router, Vitest, Spring Boot profile and tutor APIs, FastAPI RAG, existing BM25/hash embedder, Docker Compose release scripts, Playwright.

---

## Decisions and constraints

- Root/login defaults resolve to `/admin` for ADMIN and `/world` for other roles. Explicit admin deep links are retained; admin redirects to student-only routes land at `/admin`.
- KC IDs remain valid internal/API values and route parameters. Every rendered label, badge, narrative, and selector uses the existing KC name map; unknown IDs are suppressed instead of echoed.
- The profile radar only plots evidence-backed scores. Self-reported goal/background/preference values remain visibly labeled as self-report and continue to feed existing personalization signals.
- Video transcripts are machine-generated and unreviewed. Video matches may select a bounded same-chapter course-page retrieval pass, but are never returned as page citations or used to satisfy evidence gates.
- Preserve existing deployment data and database volumes. Mount the already-packaged transcript JSON read-only into the RAG container.

## Implementation tasks

1. Add a shared role-aware landing resolver and router/login tests. Cover root, login with a student-route `redirect`, and authenticated `/login` access for ADMIN.
2. Replace all visible raw KC renderings across learner, teacher, and admin Vue pages. Change unknown-code fallback and add seed-map completeness and rendering tests.
3. Improve `ProfilePage` and `ProfileRadar`: expose the existing interview directly, format profile enums as human labels, display the purpose/source of each field, refresh profile dimensions after an interview, and fix chart clipping/empty-evidence explanation. Test update, error, and evidence gating behavior.
4. Reserve vertical and horizontal space for the mainline drawer close button; assert no overlap with the star badge at desktop and narrow viewport widths.
5. Add a typed timestamped video transcript source/index and query expansion to `pythontopia-rag`. Keep page and video indexes separate, bound video-derived supplemental page hits, expose retrieval trace/status, and test parsing, provenance, filters, ranking, and the existing golden retrieval set.
6. Mount production and development transcript data read-only into RAG; validate one-time clean deployment configuration and preserve existing shared data/volumes.
7. Update changelog and this work log, run frontend/backend/RAG/Graph checks and real-browser acceptance, then build and deploy ECS and push the verified changes to GitHub.

## Acceptance evidence

- Router unit tests prove ADMIN never defaults to `/world`; browser network log proves the admin session no longer requests the student-only endpoints in the attachment.
- Frontend source scan finds no rendered `KC-###`/`PY43-KC-###` labels; all 65 seeded knowledge points map to names.
- Profile browser flow can open, submit, and finish the real interview; the displayed profile updates; radar distinguishes missing evidence from zero/neutral scores and labels self-report separately.
- Mainline drawer screenshot and DOM geometry show the close button does not cover the star badge at desktop and mobile widths.
- RAG evaluation compares baseline page retrieval with video-assisted retrieval on a fixed query set, reports Recall@5 and citation page IDs, and proves transcript matches never become courseware page IDs.
- Deployed browser smoke confirms admin landing and student UI flows, no relevant `console`/`pageerror`/failed API requests, and the current release health checks pass.

## Work log

- 2026-10-21: Read the supplied browser error attachment. It reports student-only API 403s after admin navigation and slow Web Vitals. Verified the current root and authenticated guest redirects both default to `/world`.
- 2026-10-21: Confirmed the KC map has exactly 65/65 seeded names and found direct identifier rendering in bug mine, teacher/admin review, stage, code theater, weekly companion, classroom, and NPC UI.
- 2026-10-21: Confirmed the profile API returns eight dimensions with evidence confidence; current interview is reachable from a collapsed section on the learning-twin page, and the radar's horizontal labels can be clipped by its 320px viewBox.
- 2026-10-21: Confirmed video transcripts exist for 34 videos and are marked `machine_generated_unreviewed`; current service only adds a ±30 second playhead window to tutor context. Production release already packages transcript JSON but RAG does not mount or index it.
