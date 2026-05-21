-- Purpose: build the classroom_quest table that stage 12 will write to and stage 08 reads from.
-- Tables affected: classroom_quest.
-- Irreversible risk: dropping the table loses every teacher-assigned quest.

CREATE TABLE IF NOT EXISTS classroom_quest (
  id BIGSERIAL PRIMARY KEY,
  classroom_id BIGINT NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
  title VARCHAR(256) NOT NULL,
  description VARCHAR(1024),
  kc_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  node_type VARCHAR(24) NOT NULL CHECK (
    node_type IN ('READ_RESOURCE', 'SOLVE_PROBLEM', 'FIX_BUG', 'PLANT_KC', 'PROFILE_UPDATE')
  ),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  due_date DATE NOT NULL,
  created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_classroom_quest_classroom_status
  ON classroom_quest(classroom_id, status, due_date);
CREATE INDEX IF NOT EXISTS idx_classroom_quest_kc
  ON classroom_quest USING GIN (kc_codes);
