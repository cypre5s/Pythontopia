-- Purpose: persist generated learning resources from the resource workshop.
-- Tables affected: learning_resource.
-- Irreversible risk: dropping this table loses all generated resources and feedback history.

CREATE TABLE IF NOT EXISTS learning_resource (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  pack_id BIGINT NOT NULL REFERENCES course_pack(id) ON DELETE CASCADE,
  kc_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  chapter_id BIGINT,
  type VARCHAR(16) NOT NULL CHECK (
    type IN ('HANDOUT', 'MINDMAP', 'PRACTICE', 'CODE_LAB', 'READING', 'STORYBOARD')
  ),
  title VARCHAR(256) NOT NULL,
  content_format VARCHAR(16) NOT NULL CHECK (
    content_format IN ('MARKDOWN', 'MERMAID', 'STRUCT_JSON')
  ),
  content JSONB NOT NULL DEFAULT '{}'::jsonb,
  evidence_page_ids BIGINT[] NOT NULL DEFAULT ARRAY[]::BIGINT[],
  summary VARCHAR(512),
  safety_status VARCHAR(16) NOT NULL DEFAULT 'PASSED'
    CHECK (safety_status IN ('PASSED', 'FAILED', 'NO_EVIDENCE')),
  grounding_status VARCHAR(16) NOT NULL DEFAULT 'PASSED'
    CHECK (grounding_status IN ('PASSED', 'FAILED', 'NO_EVIDENCE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by_run_id BIGINT REFERENCES resource_run(id) ON DELETE SET NULL,
  feedback_status VARCHAR(16) NOT NULL DEFAULT 'NONE'
    CHECK (feedback_status IN ('NONE', 'LIKED', 'DISLIKED')),
  feedback_note VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_learning_resource_user_type
  ON learning_resource(user_id, type);
CREATE INDEX IF NOT EXISTS idx_learning_resource_user_created
  ON learning_resource(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_learning_resource_user_kc
  ON learning_resource USING GIN (kc_codes);
