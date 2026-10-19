-- Purpose: keep gameplay settlement verdicts separate from reward-eligible verified passes.
-- Tables affected: expedition_verified_attempt.
-- Irreversible risk: dropping this column loses the verdicts needed to settle existing runs.
ALTER TABLE expedition_verified_attempt
  ADD COLUMN settlement_passed BOOLEAN;

UPDATE expedition_verified_attempt
SET settlement_passed = passed
WHERE settlement_passed IS NULL;

COMMENT ON COLUMN expedition_verified_attempt.settlement_passed IS
  'Whether the judged result may advance challenge settlement; trusted passes remain in passed and reward_eligible.';
