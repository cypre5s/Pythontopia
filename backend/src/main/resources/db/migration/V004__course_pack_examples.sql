-- Purpose: create course examples extracted from packId=43 courseware.
-- Tables affected: course_example.
-- Irreversible risk: none; this migration only creates a table and indexes.
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V26__language_pack_examples.sql

CREATE TABLE IF NOT EXISTS course_example (
  id BIGINT PRIMARY KEY,
  pack_id BIGINT NOT NULL REFERENCES course_pack(id) ON DELETE CASCADE,
  document_id BIGINT REFERENCES course_document(id) ON DELETE SET NULL,
  kc_code VARCHAR(64) NOT NULL DEFAULT '',
  title TEXT NOT NULL,
  code TEXT NOT NULL DEFAULT '',
  explanation_markdown TEXT NOT NULL DEFAULT '',
  input_description TEXT NOT NULL DEFAULT '',
  output_description TEXT NOT NULL DEFAULT '',
  unit_type VARCHAR(32) NOT NULL DEFAULT 'demo',
  oj_convertible BOOLEAN NOT NULL DEFAULT false,
  oj_block_reason TEXT NOT NULL DEFAULT '',
  page_refs BIGINT[] NOT NULL DEFAULT ARRAY[]::bigint[],
  create_time TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_course_example_pack ON course_example(pack_id);
CREATE INDEX IF NOT EXISTS idx_course_example_kc ON course_example(kc_code);
