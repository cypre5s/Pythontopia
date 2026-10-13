-- Purpose: enforce an account-level daily Tutor request limit across sessions and app instances.
-- Tables affected: adds tutor_daily_request_usage.
-- Irreversible risk: dropping this table resets usage counters and allows another daily allowance.

CREATE TABLE tutor_daily_request_usage (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  usage_date DATE NOT NULL,
  request_count INTEGER NOT NULL CHECK (request_count > 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, usage_date)
);

CREATE INDEX idx_tutor_daily_request_usage_date
  ON tutor_daily_request_usage (usage_date);
