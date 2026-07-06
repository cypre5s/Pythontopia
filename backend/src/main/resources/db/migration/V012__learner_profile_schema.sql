-- Purpose: create learner profile storage placeholders for phase 04 ProfileAgent.
-- Tables affected: learner_profile, profile_interview, profile_revision.
-- Irreversible risk: none; this migration only creates tables and indexes.
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/profile/
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V41__learner_course_progress.sql
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V63__add_learner_narrative_summary.sql

CREATE TABLE IF NOT EXISTS learner_profile (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  pack_id BIGINT NOT NULL DEFAULT 43 REFERENCES course_pack(id) ON DELETE RESTRICT CHECK (pack_id = 43),
  major VARCHAR(128),
  base_level JSONB NOT NULL DEFAULT '{}'::jsonb,
  learning_goal TEXT,
  cognitive_style VARCHAR(64),
  learning_preference JSONB NOT NULL DEFAULT '{}'::jsonb,
  error_patterns JSONB NOT NULL DEFAULT '{}'::jsonb,
  practice_capability JSONB NOT NULL DEFAULT '{}'::jsonb,
  emotion_persistence JSONB NOT NULL DEFAULT '{}'::jsonb,
  narrative_summary TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS profile_interview (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  session_id UUID NOT NULL,
  status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'COMPLETED', 'ABORTED')),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS profile_revision (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source VARCHAR(32) NOT NULL CHECK (source IN ('INTERVIEW', 'SUBMISSION', 'DEBUG', 'RESOURCE_FEEDBACK', 'QUEST', 'CLASSROOM')),
  diff JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_profile_interview_user ON profile_interview(user_id, status);
CREATE INDEX IF NOT EXISTS idx_profile_revision_user ON profile_revision(user_id, created_at DESC);
