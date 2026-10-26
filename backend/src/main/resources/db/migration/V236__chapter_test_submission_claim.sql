-- Purpose: serialize chapter-test judging without holding a database transaction during sandbox work.
-- Tables affected: chapter_test (submission claim token and lease timestamp).
-- Irreversible risk: dropping these columns loses in-flight claim state; stale in-progress submissions may
--                    be judged more than once until their result is persisted.

ALTER TABLE chapter_test
  ADD COLUMN submission_claim_token UUID,
  ADD COLUMN submission_claimed_at TIMESTAMPTZ,
  ADD CONSTRAINT chk_chapter_test_submission_claim
    CHECK ((submission_claim_token IS NULL) = (submission_claimed_at IS NULL));
