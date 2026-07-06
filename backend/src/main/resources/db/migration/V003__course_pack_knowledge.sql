-- Purpose: create KC table for Python language foundations.
-- Tables affected: course_kc.
-- Irreversible risk: none; this migration only creates a table and indexes.
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V25__language_pack_knowledge.sql
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V40__language_pack_course_enrichment.sql

CREATE TABLE IF NOT EXISTS course_kc (
  id BIGINT PRIMARY KEY,
  pack_id BIGINT NOT NULL REFERENCES course_pack(id) ON DELETE CASCADE,
  kc_code VARCHAR(64) NOT NULL,
  kc_name VARCHAR(256) NOT NULL,
  name_normalized VARCHAR(256) NOT NULL,
  name_en VARCHAR(256) NOT NULL DEFAULT '',
  chapter_id BIGINT REFERENCES course_chapter(id) ON DELETE SET NULL,
  chapter_order INTEGER NOT NULL,
  difficulty VARCHAR(32) NOT NULL DEFAULT 'basic',
  prerequisite_codes TEXT[] NOT NULL DEFAULT ARRAY[]::text[],
  summary_markdown TEXT NOT NULL DEFAULT '',
  page_refs BIGINT[] NOT NULL DEFAULT ARRAY[]::bigint[],
  create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (pack_id, kc_code),
  UNIQUE (pack_id, name_normalized)
);

CREATE INDEX IF NOT EXISTS idx_course_kc_pack ON course_kc(pack_id);
CREATE INDEX IF NOT EXISTS idx_course_kc_chapter ON course_kc(chapter_id);
CREATE INDEX IF NOT EXISTS idx_course_kc_code ON course_kc(kc_code);
