-- Purpose: emit one authoritative media_consumed event when a student's mainline video or courseware progress crosses its completion threshold.
-- Tables affected: mainline_stage_progress (two nullable event-deduplication timestamps).
-- Irreversible risk: none; dropping either marker can allow one duplicate event on a later progress report, while student progress remains intact.

ALTER TABLE mainline_stage_progress
  ADD COLUMN video_consumed_at TIMESTAMPTZ,
  ADD COLUMN courseware_consumed_at TIMESTAMPTZ;
