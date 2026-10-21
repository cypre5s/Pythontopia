-- Purpose: let administrators temporarily hide or restore seeded course resources.
-- Tables affected: adds course_resource_state; course seed tables and files stay unchanged.
-- Irreversible risk: dropping this table restores default availability and loses admin status overrides.
CREATE TABLE course_resource_state (
  pack_id BIGINT NOT NULL REFERENCES course_pack(id),
  resource_type VARCHAR(24) NOT NULL CHECK (
    resource_type IN ('DOCUMENT', 'CHAPTER', 'KNOWLEDGE_POINT', 'EXAMPLE', 'PROBLEM')
  ),
  resource_id BIGINT NOT NULL CHECK (resource_id > 0),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by BIGINT REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (pack_id, resource_type, resource_id)
);
