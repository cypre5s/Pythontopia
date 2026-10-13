-- Purpose: cap aggregate AI entry requests across self-registered accounts.
-- Tables affected: adds ai_provider_daily_request_usage.
-- Irreversible risk: dropping this table resets the shared daily cap and permits another allowance.

CREATE TABLE ai_provider_daily_request_usage (
  usage_date DATE PRIMARY KEY,
  request_count INTEGER NOT NULL CHECK (request_count > 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
