-- Purpose: Align new player coordinates with the town_hub map spawn at tile (25, 20).
-- Affected table: player_state (tile_x and tile_y defaults).
-- Irreversible risk: none; only future rows use these defaults.

ALTER TABLE player_state
  ALTER COLUMN tile_x SET DEFAULT 25,
  ALTER COLUMN tile_y SET DEFAULT 20;
