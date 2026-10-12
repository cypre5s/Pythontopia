-- Purpose: bind expedition challenge judgments to a user-owned run and derive leaderboard rewards
--          only from server-evaluated submissions; preserve existing client-era scores as unverified.
-- Tables affected: expedition_run, pregenerated_problem, expedition_score, expedition_verified_attempt.
-- Irreversible risk: existing expedition_score rows remain stored but are hidden from the verified
--          leaderboard; deleting verified-attempt rows removes the evidence used to reproduce scores.

ALTER TABLE expedition_run
  ADD COLUMN run_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX uq_expedition_run_user_run_id ON expedition_run (user_id, run_id);

ALTER TABLE pregenerated_problem
  ADD COLUMN expedition_run_id UUID;

ALTER TABLE expedition_score
  ADD COLUMN run_id UUID,
  ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_expedition_score_user_run
  ON expedition_score (user_id, run_id)
  WHERE run_id IS NOT NULL;

CREATE TABLE expedition_verified_attempt (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  run_id UUID NOT NULL,
  problem_id VARCHAR(256) NOT NULL,
  passed BOOLEAN NOT NULL,
  score INTEGER NOT NULL CHECK (score >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, run_id, problem_id)
);

CREATE INDEX idx_expedition_verified_attempt_run
  ON expedition_verified_attempt (user_id, run_id, created_at, id);
