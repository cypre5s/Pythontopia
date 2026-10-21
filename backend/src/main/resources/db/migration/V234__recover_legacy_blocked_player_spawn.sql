-- Purpose: Move players left at the old default (12, 10), which is inside the library collision area.
-- Affected table: player_state (existing town_hub coordinates).
-- Irreversible risk: the old tile is blocked in town_hub and cannot represent a playable position.

UPDATE player_state
SET tile_x = 25,
    tile_y = 20,
    updated_at = now()
WHERE map_key = 'town_hub'
  AND tile_x = 12
  AND tile_y = 10;
