-- V189 Purpose: record the "learning" bucket (0.3 <= mastery < 0.8) of the classroom KC heatmap.
-- Tables affected: classroom_kc_snapshot.
-- Irreversible risk: none; additive column with a safe default.
--
-- Background: the heatmap groups every ACTIVE member into mastered (>= 0.8) / learning
-- ([0.3, 0.8)) / struggling (< 0.3), but the snapshot table only had passed_count and
-- failed_count. The middle bucket was silently dropped on write, so the cached snapshot
-- never satisfied passed_count + failed_count = student_count and could not be used to
-- reconstruct the heatmap.

ALTER TABLE classroom_kc_snapshot
  ADD COLUMN IF NOT EXISTS learning_count INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN classroom_kc_snapshot.passed_count IS '掌握度 >= 0.8 的成员数（mastered）';
COMMENT ON COLUMN classroom_kc_snapshot.learning_count IS '掌握度 [0.3, 0.8) 的成员数（learning）';
COMMENT ON COLUMN classroom_kc_snapshot.failed_count IS '掌握度 < 0.3 的成员数（struggling）';
