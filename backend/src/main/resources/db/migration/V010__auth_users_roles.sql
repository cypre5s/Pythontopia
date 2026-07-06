-- Purpose: create Pythontopia local auth users for students and teachers.
-- Tables affected: users.
-- Irreversible risk: none; this migration only creates tables and indexes.
-- Alethicode source: none; Pythontopia intentionally does not reuse Alethicode users schema.

CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(128) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  email VARCHAR(128) UNIQUE,
  role VARCHAR(16) NOT NULL CHECK (role IN ('STUDENT', 'TEACHER')),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_role_status ON users(role, status);
