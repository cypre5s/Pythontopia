-- Purpose: enrich course_problem difficulty for BKT mastery evaluation.
-- Normalize labels to basic/medium/hard; add pass_rate_estimate for calibration.
-- Tables affected: course_problem (ALTER + UPDATE only).
-- Irreversible risk: low.

UPDATE course_problem SET difficulty = 'basic' WHERE LOWER(difficulty) IN ('low', 'easy');
UPDATE course_problem SET difficulty = 'hard' WHERE LOWER(difficulty) IN ('high');
UPDATE course_problem SET difficulty = 'medium' WHERE difficulty IS NULL OR difficulty NOT IN ('basic', 'medium', 'hard');
ALTER TABLE course_problem ADD COLUMN IF NOT EXISTS pass_rate_estimate DOUBLE PRECISION;
