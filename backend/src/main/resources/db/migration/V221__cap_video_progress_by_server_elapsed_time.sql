-- Purpose: cap claimed video watch progress by time elapsed between server-observed progress requests.
-- Tables affected: mainline_stage_progress (nullable per-user/per-stage video request timestamp).
-- Irreversible risk: none; existing watch totals are preserved and the nullable timestamp can be dropped.
-- Existing rows are intentionally not backfilled because their true last video report time is unknown.

ALTER TABLE mainline_stage_progress
  ADD COLUMN video_last_reported_at TIMESTAMPTZ;
