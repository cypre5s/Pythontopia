# Teacher Review Queue Follow-up — 2026-10-15

## Scope

Continue the Web-first audit from local checkpoint `215d0028`. Fix a confirmed queue defect without adding an endpoint or schema: `/teacher/review` used `GET /api/teacher/problem-reviews?limit=100`, had no page navigation, and surfaced load failures only as a toast.

## Changes

- Reused `searchProblemReviews()` and the existing paged backend route with a page size of 100.
- Added previous/next page controls, refresh, a page-aware retry action, and a visible error state.
- Kept the latest moderation result when a slower queue response completes after it.
- Updated old teacher-page API mocks and added focused regression coverage.

## Verification

- RED/GREEN tests: pagination, initial-load retry, failed-page retry, and moderation-result race; existing teacher page cases use the same paged API contract. Targeted total: 14/14.
- `npm run test`: 275 files, 2596 tests passed.
- `npm run typecheck`: passed.
- ESLint for the changed Vue page and test files: passed.
- `npm run build`: passed. Existing Pyodide externalized Node builtin, `eval`, and large-chunk warnings remain. The build pre-hook only changed the generated course manifest's `generatedAt`; restored after confirming all course counts/content were unchanged.
- Real teacher session through Vite → Spring returned HTTP 200 for the paged queue and rendered 8 records at 320px. The screenshot exposed the fixed global companion covering queue cards; the review route now uses the existing `hideCompanion` switch. Browser recheck confirms the companion is hidden, no horizontal overflow, and zero page errors. Router/AppLayout tests: 23/23; typecheck passed. Temporary teacher session was revoked and the account deleted.
- Fresh DeepSeek Web validation: through Vite → Spring → Graph, a text Tutor request and a PNG `range(3)` diagram request both returned HTTP 200 and `RUN_FINISHED`; the text marker was returned, the diagram values 0/1/2 were recognized, and the response stream contained no image payload. The Graph log showed six DeepSeek `chat/completions` responses (all 200) in the two-minute window. Browser page errors: 0. Temporary student, Tutor sessions, and chat turns were deleted; the account count was verified as 0.
- One real `/api/tutor/ask` request through local Spring → Graph → DeepSeek returned HTTP 200, the requested text marker, and `RUN_FINISHED`; no image output was detected. The local application AI request cap was `false`. This probe called Spring directly, not Vite. The temporary student's `tutor_session`, `workshop_chat_turn`, and `users` rows were removed; follow-up counts were 0/0/0.
- A new student opening `/boss/7` was redirected by the existing chapter-star guard. This confirms the gate behavior only; it does not complete the final Boss fight. The temporary account and its session data were deleted.

## Still open

- Complete the final multi-stage Boss browser journey from an account with legitimate chapter unlock evidence.
- Full Maven remains environmentally blocked by Docker/Testcontainers port binding and PostgreSQL reset errors. Production Piston isolation, DeepSeek vendor billing/token reconciliation, Tutor/Alethicode event-by-event parity, and Twin delayed outcomes need external evidence or longer measurement.
- No performance test was run; it remains behind functional work.
