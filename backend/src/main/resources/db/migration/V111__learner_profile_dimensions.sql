-- Purpose: Phase M4 passive-profile pipeline - introduce a single unified 8-dimension JSONB
--          aggregate column on learner_profile so that PassiveProfileService (which converts
--          PracticeSucceeded / PracticeFailed / DebugResolved / ResourceConsumed events into
--          fine-grained dimension deltas) has one source of truth at
--          learner_profile.dimensions, while the legacy column-per-dimension columns
--          (major / base_level / learning_goal / cognitive_style / learning_preference /
--          error_patterns / practice_capability / emotion_persistence / narrative_summary)
--          remain untouched for the INTERVIEW upsert flow defined in V012.
-- Tables affected: learner_profile (new column `dimensions` + GIN index + bootstrap UPDATE).
-- Irreversible risk: dropping the column would discard passive-update score history that has
--                    not been mirrored back into the legacy columns. The seed UPDATE is
--                    idempotent: it only fires when `dimensions = '{}'::jsonb`, so re-running
--                    on rows whose passive deltas already populated `dimensions` is a no-op.

ALTER TABLE learner_profile
  ADD COLUMN IF NOT EXISTS dimensions JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN learner_profile.dimensions IS
  'M4 passive-profile 8-dimension aggregate (JSONB): keys = professional_background, '
  'knowledge_base, learning_goal, cognitive_style, learning_preference, error_pattern, '
  'practical_ability, emotion_persistence. Each sub-object exposes score (0..1), '
  'evidence_count, tags[], last_updated and free-form metadata written by PassiveProfileService.';

UPDATE learner_profile SET dimensions = jsonb_build_object(
  'professional_background', jsonb_build_object(
    'score',         0.5,
    'major',         major,
    'last_updated',  to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  ),
  'knowledge_base', jsonb_build_object(
    'score',                0.5,
    'base_level_snapshot',  COALESCE(base_level, '{}'::jsonb),
    'last_updated',         to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  ),
  'learning_goal', jsonb_build_object(
    'score',         0.5,
    'raw',           learning_goal,
    'last_updated',  to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  ),
  'cognitive_style', jsonb_build_object(
    'score',         0.5,
    'raw',           cognitive_style,
    'last_updated',  to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  ),
  'learning_preference', jsonb_build_object(
    'score',                0.5,
    'preference_snapshot',  COALESCE(learning_preference, '{}'::jsonb),
    'last_updated',         to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  ),
  'error_pattern', jsonb_build_object(
    'score',              0.5,
    'patterns_snapshot',  COALESCE(error_patterns, '{}'::jsonb),
    'last_updated',       to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  ),
  'practical_ability', jsonb_build_object(
    'score',                0.5,
    'capability_snapshot',  COALESCE(practice_capability, '{}'::jsonb),
    'last_updated',         to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  ),
  'emotion_persistence', jsonb_build_object(
    'score',                 0.5,
    'persistence_snapshot',  COALESCE(emotion_persistence, '{}'::jsonb),
    'last_updated',          to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  )
)
WHERE dimensions = '{}'::jsonb;

CREATE INDEX IF NOT EXISTS ix_learner_profile_dimensions_gin
  ON learner_profile USING GIN (dimensions);
