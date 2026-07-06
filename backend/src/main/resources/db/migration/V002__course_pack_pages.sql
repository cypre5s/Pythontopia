-- Purpose: create page-level courseware table for RAG and evidence citation.
-- Tables affected: course_page.
-- Irreversible risk: none; this migration only creates a table and indexes.
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V24__language_pack_pages.sql

CREATE TABLE IF NOT EXISTS course_page (
  id BIGINT PRIMARY KEY,
  pack_id BIGINT NOT NULL REFERENCES course_pack(id) ON DELETE CASCADE,
  document_id BIGINT NOT NULL REFERENCES course_document(id) ON DELETE CASCADE,
  chapter_id BIGINT REFERENCES course_chapter(id) ON DELETE SET NULL,
  page_no INTEGER NOT NULL,
  chunk_index INTEGER NOT NULL DEFAULT 0,
  title VARCHAR(512) NOT NULL DEFAULT '',
  content_markdown TEXT NOT NULL DEFAULT '',
  text_hash VARCHAR(128) NOT NULL DEFAULT '',
  preview_asset_path TEXT NOT NULL DEFAULT '',
  excerpt TEXT NOT NULL DEFAULT '',
  create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (document_id, page_no, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_course_page_pack ON course_page(pack_id);
CREATE INDEX IF NOT EXISTS idx_course_page_document ON course_page(document_id);
CREATE INDEX IF NOT EXISTS idx_course_page_chapter ON course_page(chapter_id);
