-- Purpose: allow learning_path.status to take the new DRAFT value used by the review hall.
-- Tables affected: learning_path.
-- Irreversible risk: weakening the constraint is reversible; existing rows are unaffected.

ALTER TABLE learning_path DROP CONSTRAINT IF EXISTS learning_path_status_check;
ALTER TABLE learning_path ADD CONSTRAINT learning_path_status_check
  CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DRAFT'));
