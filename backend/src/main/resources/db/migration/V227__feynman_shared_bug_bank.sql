-- Purpose: share mechanically validated, AI-generated generic Feynman bug samples by course KC.
-- Tables affected: creates feynman_bug_bank; it stores no learner IDs or real-mistake evidence.
-- Irreversible risk: dropping this table deletes generated shared samples; existing learner data is unaffected.

CREATE TABLE feynman_bug_bank (
  id BIGSERIAL PRIMARY KEY,
  kc_code VARCHAR(40) NOT NULL,
  topic VARCHAR(160) NOT NULL,
  difficulty VARCHAR(16) NOT NULL CHECK (difficulty IN ('easy', 'medium', 'hard')),
  misconception_tag VARCHAR(80) NOT NULL,
  buggy_code TEXT NOT NULL,
  fixed_code TEXT NOT NULL,
  bug_line_indices JSONB NOT NULL CHECK (jsonb_typeof(bug_line_indices) = 'array'),
  hint_md VARCHAR(500) NOT NULL,
  sample_hash CHAR(64) NOT NULL UNIQUE,
  use_count BIGINT NOT NULL DEFAULT 0 CHECK (use_count >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_feynman_bug_bank_kc_reuse
  ON feynman_bug_bank (kc_code, difficulty, use_count, id);
