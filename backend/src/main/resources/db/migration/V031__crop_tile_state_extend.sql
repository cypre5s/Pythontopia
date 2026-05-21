-- Purpose: extend crop_tile_state with KC lifecycle status and unlock flag.
-- Tables affected: crop_tile_state.
-- Irreversible risk: adding NOT NULL columns with defaults is safe; dropping
-- the new columns later would lose unlock state and pack id.

ALTER TABLE crop_tile_state
  ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'SEED',
  ADD COLUMN IF NOT EXISTS unlocked BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS unlocked_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS pack_id INT NOT NULL DEFAULT 43;

ALTER TABLE crop_tile_state
  DROP CONSTRAINT IF EXISTS crop_tile_state_status_check;

ALTER TABLE crop_tile_state
  ADD CONSTRAINT crop_tile_state_status_check CHECK (
    status IN ('SEED', 'SPROUT', 'MATURE', 'WITHERED', 'LOCKED')
  );

CREATE UNIQUE INDEX IF NOT EXISTS uq_crop_tile_user_pack_kc
  ON crop_tile_state(user_id, pack_id, kc_code);
