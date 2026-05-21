-- Purpose: add explicit indexes for phase 04 learner profile interview access paths.
-- Tables affected: learner_profile, profile_interview, profile_revision.
-- Irreversible risk: none; indexes can be dropped safely.

CREATE INDEX IF NOT EXISTS idx_learner_profile_user_id ON learner_profile(user_id);
CREATE INDEX IF NOT EXISTS idx_profile_interview_session_id ON profile_interview(session_id);
CREATE INDEX IF NOT EXISTS idx_profile_revision_user_created ON profile_revision(user_id, created_at DESC);
