-- Purpose: create lightweight classroom ownership and membership tables.
-- Tables affected: classrooms, classroom_members.
-- Irreversible risk: none; this migration only creates tables and indexes.
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V10__bootstrap_m9_classroom_core.sql
-- Alethicode source: /home/cypress/Alethicode/backend/src/main/resources/db/migration/V30__classroom_single_language_pack.sql

CREATE TABLE IF NOT EXISTS classrooms (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  teacher_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  pack_id BIGINT NOT NULL DEFAULT 43 REFERENCES course_pack(id) ON DELETE RESTRICT CHECK (pack_id = 43),
  join_code VARCHAR(6) NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_classrooms_teacher ON classrooms(teacher_id);

CREATE TABLE IF NOT EXISTS classroom_members (
  id BIGSERIAL PRIMARY KEY,
  classroom_id BIGINT NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
  student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED')),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (classroom_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_classroom_members_student ON classroom_members(student_id, status);
