-- Purpose: bind verified expedition attempts to their authoritative map node and boss phase,
--          and allow only one successful score contribution per node/phase.
-- Tables affected: expedition_verified_attempt.
-- Irreversible risk: active runs settle using at most one passing attempt per bound node/phase;
--          legacy attempts that cannot be joined to a server-owned node remain available as
--          learning evidence but do not contribute score after settlement.

ALTER TABLE expedition_verified_attempt
  ADD COLUMN node_id VARCHAR(32),
  ADD COLUMN boss_phase INT CHECK (boss_phase BETWEEN 1 AND 3),
  ADD COLUMN score_awarded BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE expedition_verified_attempt AS attempt
SET node_id = problem.expedition_node_id,
    boss_phase = NULLIF(
      substring(problem.problem_id FROM '^issue-boss-phase-([1-3])-'),
      ''
    )::INT
FROM pregenerated_problem AS problem
WHERE problem.user_id = attempt.user_id
  AND problem.expedition_run_id = attempt.run_id
  AND problem.problem_id = attempt.problem_id
  AND problem.expedition_node_id IS NOT NULL;

WITH first_pass_per_node_phase AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY user_id, run_id, node_id, COALESCE(boss_phase, 0)
           ORDER BY created_at, id
         ) AS pass_order
  FROM expedition_verified_attempt
  WHERE passed = TRUE AND node_id IS NOT NULL
)
UPDATE expedition_verified_attempt AS attempt
SET score_awarded = TRUE
FROM first_pass_per_node_phase AS first_pass
WHERE first_pass.id = attempt.id
  AND first_pass.pass_order = 1;

CREATE UNIQUE INDEX uq_expedition_verified_attempt_score_slot
  ON expedition_verified_attempt (
    user_id,
    run_id,
    node_id,
    COALESCE(boss_phase, 0)
  )
  WHERE score_awarded = TRUE AND node_id IS NOT NULL;

CREATE INDEX idx_expedition_verified_attempt_node
  ON expedition_verified_attempt (user_id, run_id, node_id, boss_phase, created_at, id);

COMMENT ON COLUMN expedition_verified_attempt.node_id
  IS 'Authoritative expedition map node that issued the judged problem';
COMMENT ON COLUMN expedition_verified_attempt.boss_phase
  IS 'Server-owned boss phase; NULL for non-boss nodes';
COMMENT ON COLUMN expedition_verified_attempt.score_awarded
  IS 'True only for the first passing attempt that can contribute score/depth for its node/phase';
