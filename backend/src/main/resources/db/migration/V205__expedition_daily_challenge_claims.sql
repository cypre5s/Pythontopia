-- Purpose: enforce one server-authorized expedition daily challenge per student and Shanghai date.
-- Tables affected: expedition_daily_claim (new).
-- Irreversible risk: deleting claims allows a student to start another daily run for that date.

CREATE TABLE expedition_daily_claim (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  challenge_date DATE NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, challenge_date)
);

INSERT INTO expedition_daily_claim (user_id, challenge_date)
SELECT user_id, (created_at AT TIME ZONE 'Asia/Shanghai')::date
FROM expedition_run
WHERE is_daily = TRUE
  AND (created_at AT TIME ZONE 'Asia/Shanghai')::date = (now() AT TIME ZONE 'Asia/Shanghai')::date
ON CONFLICT (user_id, challenge_date) DO NOTHING;
