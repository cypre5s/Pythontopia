-- Purpose: make opening/resuming a profile interview atomic per user.
-- Tables affected: profile_interview.
-- Irreversible risk: older duplicate OPEN sessions are marked ABORTED; their rows remain available for audit.

WITH ranked_open_sessions AS (
  SELECT id,
         ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY started_at DESC, id DESC) AS row_number
  FROM profile_interview
  WHERE status = 'OPEN'
)
UPDATE profile_interview AS interview
SET status = 'ABORTED', completed_at = now()
FROM ranked_open_sessions AS ranked
WHERE interview.id = ranked.id
  AND ranked.row_number > 1;

CREATE UNIQUE INDEX uq_profile_interview_one_open_per_user
  ON profile_interview(user_id)
  WHERE status = 'OPEN';
