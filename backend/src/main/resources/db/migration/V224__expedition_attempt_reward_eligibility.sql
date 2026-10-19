-- Purpose: keep revoked or unapproved AI judge results available for challenge settlement without awarding score.
-- Tables affected: expedition_verified_attempt.
-- Irreversible risk: dropping this column later loses eligibility for attempts recorded after this migration.
ALTER TABLE expedition_verified_attempt
  ADD COLUMN reward_eligible BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN expedition_verified_attempt.reward_eligible IS
  'Whether this verified judge result may contribute to expedition score and learning rewards.';
