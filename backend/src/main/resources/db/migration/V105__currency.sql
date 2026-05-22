-- Purpose: track in-game coins/gems and append-only currency transactions for shop, quest rewards
--          and bug-mine penalties.
-- Tables affected: player_currency, currency_transaction.
-- Irreversible risk: dropping currency_transaction loses the ledger required for review-hall audits.

CREATE TABLE IF NOT EXISTS player_currency (
  user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  coins INTEGER NOT NULL DEFAULT 50
    CONSTRAINT player_currency_coins_nonneg CHECK (coins >= 0),
  gems INTEGER NOT NULL DEFAULT 0
    CONSTRAINT player_currency_gems_nonneg CHECK (gems >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS currency_transaction (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  delta_coins INTEGER NOT NULL DEFAULT 0,
  delta_gems INTEGER NOT NULL DEFAULT 0,
  reason VARCHAR(64) NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_currency_tx_user_time
  ON currency_transaction(user_id, created_at DESC);

COMMENT ON TABLE player_currency IS '玩家当前货币余额，按 user_id 1:1';
COMMENT ON TABLE currency_transaction IS '货币变动流水（append-only）';
