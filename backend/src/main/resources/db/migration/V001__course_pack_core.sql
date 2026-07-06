-- Purpose: create core course pack tables for Pythontopia packId=43.
-- Tables affected: course_pack, course_document, course_chapter.
-- Irreversible risk: none; this migration only creates tables and indexes.
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V22__bootstrap_language_pack_core.sql
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V23__language_pack_documents.sql
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V25__language_pack_knowledge.sql

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS course_pack (
  id BIGINT PRIMARY KEY,
  slug VARCHAR(128) NOT NULL,
  version INTEGER NOT NULL,
  name VARCHAR(256) NOT NULL,
  primary_language VARCHAR(64) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL,
  document_count INTEGER NOT NULL DEFAULT 0,
  page_count INTEGER NOT NULL DEFAULT 0,
  chapter_count INTEGER NOT NULL DEFAULT 0,
  kc_count INTEGER NOT NULL DEFAULT 0,
  example_count INTEGER NOT NULL DEFAULT 0,
  problem_count INTEGER NOT NULL DEFAULT 0,
  course_objective TEXT,
  target_audience TEXT,
  total_hours INTEGER,
  create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (slug, version)
);

CREATE TABLE IF NOT EXISTS course_document (
  id BIGINT PRIMARY KEY,
  pack_id BIGINT NOT NULL REFERENCES course_pack(id) ON DELETE CASCADE,
  original_filename VARCHAR(512) NOT NULL,
  original_path TEXT NOT NULL,
  canonical_path TEXT,
  preview_pdf_path TEXT,
  file_hash VARCHAR(128) NOT NULL,
  file_size_bytes BIGINT NOT NULL DEFAULT 0,
  page_count INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  update_time TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_course_document_pack ON course_document(pack_id);

CREATE TABLE IF NOT EXISTS course_chapter (
  id BIGINT PRIMARY KEY,
  pack_id BIGINT NOT NULL REFERENCES course_pack(id) ON DELETE CASCADE,
  order_index INTEGER NOT NULL,
  title VARCHAR(512) NOT NULL,
  summary_markdown TEXT,
  learning_objectives TEXT,
  create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (pack_id, order_index)
);

CREATE INDEX IF NOT EXISTS idx_course_chapter_pack ON course_chapter(pack_id);
