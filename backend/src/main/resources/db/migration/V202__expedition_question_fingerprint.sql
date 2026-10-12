-- Purpose: count one verified challenge per run even when identical content is issued with new problem IDs.
-- Tables affected: expedition_verified_attempt (deduplication key and unique run-scoped index).
-- Irreversible risk: dropping the column/index removes duplicate-question identity; existing score rows remain intact.
-- shortcut: old ledger rows stay NULL and use problem-ID dedupe; backfill only if pre-upgrade active runs need this guarantee.

ALTER TABLE expedition_verified_attempt
  ADD COLUMN question_fingerprint VARCHAR(64);

CREATE UNIQUE INDEX uq_expedition_verified_attempt_question_fingerprint
  ON expedition_verified_attempt (user_id, run_id, question_fingerprint)
  WHERE question_fingerprint IS NOT NULL;

COMMENT ON COLUMN expedition_verified_attempt.question_fingerprint
  IS 'SHA-256 of authoritative KC, visible prompt/samples, and judge cases; one score/evidence row per question per run';
