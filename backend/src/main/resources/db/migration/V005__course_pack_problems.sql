-- Purpose: create course problem seed table for server-side judging in later phases.
-- Tables affected: course_problem.
-- Irreversible risk: none; this migration only creates a table and indexes.
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V40__language_pack_course_enrichment.sql

CREATE TABLE IF NOT EXISTS course_problem (
  id BIGINT PRIMARY KEY,
  pack_id BIGINT NOT NULL REFERENCES course_pack(id) ON DELETE CASCADE,
  kc_code VARCHAR(64) NOT NULL DEFAULT '',
  title TEXT NOT NULL,
  statement_markdown TEXT NOT NULL DEFAULT '',
  input_description TEXT NOT NULL DEFAULT '',
  output_description TEXT NOT NULL DEFAULT '',
  difficulty VARCHAR(32) NOT NULL DEFAULT 'Low',
  samples JSONB NOT NULL DEFAULT '[]'::jsonb,
  expected_output_pattern TEXT NOT NULL DEFAULT '',
  hidden_test_cases JSONB NOT NULL DEFAULT '[]'::jsonb,
  page_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
  source_label TEXT,
  reference_solution_language TEXT,
  reference_solution_code TEXT,
  teaching_explanation TEXT NOT NULL DEFAULT '',
  common_mistakes JSONB NOT NULL DEFAULT '[]'::jsonb,
  create_time TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_course_problem_pack ON course_problem(pack_id);
CREATE INDEX IF NOT EXISTS idx_course_problem_kc ON course_problem(kc_code);
