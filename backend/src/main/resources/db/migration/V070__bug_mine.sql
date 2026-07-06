-- Purpose: persist Bug Mine debug sessions and structured debug cards.
-- Tables affected: bug_mine_session, bug_mine_card.
-- Irreversible risk: dropping these tables loses debug audit and prevents Stage 13 review hall
--                    from showing 修复历史。

CREATE TABLE IF NOT EXISTS bug_mine_session (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  submission_id BIGINT NOT NULL UNIQUE REFERENCES practice_submission(id) ON DELETE CASCADE,
  problem_id BIGINT NOT NULL REFERENCES course_problem(id) ON DELETE CASCADE,
  quest_id BIGINT REFERENCES quest(id) ON DELETE SET NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
    CHECK (status IN ('OPEN', 'RESOLVED', 'ABANDONED')),
  retry_count INT NOT NULL DEFAULT 0,
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_bug_mine_session_user_status
  ON bug_mine_session(user_id, status);

CREATE TABLE IF NOT EXISTS bug_mine_card (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES bug_mine_session(id) ON DELETE CASCADE,
  card_type VARCHAR(32) NOT NULL CHECK (
    card_type IN (
      'COUNTER_EXAMPLE',
      'VARIABLE_TRACE',
      'WRONG_ASSUMPTION',
      'HINT_LAYER_1',
      'HINT_LAYER_2',
      'HINT_LAYER_3'
    )
  ),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  revealed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (session_id, card_type)
);

CREATE INDEX IF NOT EXISTS idx_bug_mine_card_session
  ON bug_mine_card(session_id);
