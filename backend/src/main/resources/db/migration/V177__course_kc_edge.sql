-- Purpose: create an auditable typed KC knowledge-edge table for course graph retrieval.
-- Tables affected: course_kc_edge.
-- Irreversible risk: none; this migration only creates a new table and indexes.

CREATE TABLE IF NOT EXISTS course_kc_edge (
  id BIGSERIAL PRIMARY KEY,
  pack_id BIGINT NOT NULL REFERENCES course_pack(id) ON DELETE CASCADE,
  subject_type VARCHAR(32) NOT NULL,
  subject_id VARCHAR(128) NOT NULL,
  relation VARCHAR(64) NOT NULL,
  object_type VARCHAR(32) NOT NULL,
  object_id VARCHAR(128) NOT NULL,
  source VARCHAR(64) NOT NULL,
  confidence NUMERIC(5,4) NOT NULL DEFAULT 1.0,
  evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (pack_id, subject_type, subject_id, relation, object_type, object_id),
  CHECK (confidence > 0 AND confidence <= 1),
  CHECK (relation IN (
    'requires',
    'co_occurs_with',
    'explained_by_page',
    'exemplified_by_example',
    'assessed_by_problem',
    'same_chapter',
    'has_common_mistake'
  ))
);

CREATE INDEX IF NOT EXISTS idx_course_kc_edge_subject
  ON course_kc_edge(pack_id, subject_type, subject_id, relation);

CREATE INDEX IF NOT EXISTS idx_course_kc_edge_object
  ON course_kc_edge(pack_id, object_type, object_id, relation);

CREATE INDEX IF NOT EXISTS idx_course_kc_edge_relation
  ON course_kc_edge(pack_id, relation);
