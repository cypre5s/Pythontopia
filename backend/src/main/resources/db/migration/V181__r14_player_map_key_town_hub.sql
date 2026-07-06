-- Purpose: R-14 world contraction makes town_hub the single playable hub map.
-- Impact: player_state.map_key default and existing legacy town-beta/peripheral values.
-- Irreversible risk: low; old peripheral map keys are intentionally retired from main navigation.

ALTER TABLE player_state
  ALTER COLUMN map_key SET DEFAULT 'town_hub';

UPDATE player_state
SET map_key = 'town_hub'
WHERE map_key IS NULL
   OR map_key IN (
     'town-beta',
     'town_beta',
     'town',
     'town_main',
     'farm',
     'farm_north',
     'mine',
     'mine_south',
     'campus',
     'campus_east',
     'port',
     'port_west'
   );
