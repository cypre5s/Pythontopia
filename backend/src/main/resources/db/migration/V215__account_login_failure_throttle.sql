-- Purpose: track account-wide failed login attempts and apply a short progressive cooldown.
-- Tables affected: users.
-- Irreversible risk: dropping these columns removes login-abuse history and cooldown state.

ALTER TABLE users
  ADD COLUMN login_failed_count INTEGER NOT NULL DEFAULT 0 CHECK (login_failed_count >= 0),
  ADD COLUMN login_failure_window_started_at TIMESTAMPTZ,
  ADD COLUMN login_blocked_until TIMESTAMPTZ;
