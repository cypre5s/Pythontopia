-- Purpose: model the player inventory + a shared item_catalog seed used by shops, gifts and quests.
-- Tables affected: item_catalog, inventory_item.
-- Irreversible risk: dropping inventory_item wipes every player's bag; item_catalog drop cascades
--                    to all listings (V106) and references.

CREATE TABLE IF NOT EXISTS item_catalog (
  item_key VARCHAR(64) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  category VARCHAR(32) NOT NULL
    CONSTRAINT item_catalog_category_check
      CHECK (category IN ('seed', 'tool', 'gift', 'consumable', 'book', 'quest')),
  base_price INTEGER NOT NULL DEFAULT 0
    CONSTRAINT item_catalog_base_price_nonneg CHECK (base_price >= 0),
  gift_tier_map JSONB NOT NULL DEFAULT '{}'::jsonb,
  description TEXT NOT NULL DEFAULT '',
  create_time TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS inventory_item (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  item_key VARCHAR(64) NOT NULL REFERENCES item_catalog(item_key) ON DELETE RESTRICT,
  qty INTEGER NOT NULL DEFAULT 0
    CONSTRAINT inventory_item_qty_nonneg CHECK (qty >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_inventory_user_item UNIQUE (user_id, item_key)
);

CREATE INDEX IF NOT EXISTS idx_inventory_item_user ON inventory_item(user_id);

INSERT INTO item_catalog (item_key, display_name, category, base_price, description) VALUES
  ('python_book',     'Python 经典书', 'book',       100, '可作为礼物送给学者型 NPC'),
  ('coffee',          '咖啡',          'consumable',  30, '提神，可作为礼物'),
  ('trophy',          '奖杯',          'gift',       200, '高级礼物'),
  ('seed_chapter_1',  '基础种子',      'seed',        10, '第 1 章 KC 种子'),
  ('hoe',             '锄头',          'tool',        50, '翻地工具'),
  ('debug_lamp',      '调试灯',        'tool',        80, 'Bug 矿洞照明')
ON CONFLICT (item_key) DO NOTHING;
