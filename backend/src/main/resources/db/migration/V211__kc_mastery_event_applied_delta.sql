-- Purpose: record the actual clamped change to mastery separately from the requested event delta.
-- Tables affected: kc_mastery_event.
-- Irreversible risk: dropping this nullable column makes future weekly mastery changes unreportable again.

ALTER TABLE kc_mastery_event
  ADD COLUMN applied_delta NUMERIC(5, 3);
