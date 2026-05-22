-- Purpose: add supplementary indexes on course_example to accelerate workshop / quest lookups.
-- Tables affected: course_example (indexes only).
-- Irreversible risk: none; pure index creation.
-- Note on schema reality: course_example.kc_code is a single VARCHAR (not an array) and there is
--                        no difficulty column in V004; the original task hint assumed an array
--                        plus difficulty, so this migration adds the actually useful indexes
--                        without inventing schema. idx_course_example_pack and
--                        idx_course_example_kc already exist (created in V004); we add the
--                        composite + secondary lookups only.

CREATE INDEX IF NOT EXISTS idx_course_example_pack_kc
  ON course_example(pack_id, kc_code);

CREATE INDEX IF NOT EXISTS idx_course_example_unit_type
  ON course_example(unit_type);

CREATE INDEX IF NOT EXISTS idx_course_example_document
  ON course_example(document_id);

CREATE INDEX IF NOT EXISTS idx_course_example_oj_convertible
  ON course_example(oj_convertible)
  WHERE oj_convertible = true;
