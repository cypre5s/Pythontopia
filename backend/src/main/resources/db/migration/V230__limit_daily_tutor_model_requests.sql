-- Purpose: enforce a cross-session daily Tutor request ceiling per account.
-- Tables affected: tutor_model_daily_usage (new, one current-day counter per account).
-- Irreversible risk: dropping the table removes current daily usage reservations; older-day counts
--   are intentionally not retained because the policy only requires a current-day limit.

CREATE TABLE tutor_model_daily_usage (
  user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  usage_date DATE NOT NULL,
  request_count INT NOT NULL CHECK (request_count BETWEEN 1 AND 30),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE tutor_model_daily_usage
  IS 'Current Asia/Shanghai calendar-day count of top-level Tutor model request reservations per account; not token or billing usage';
