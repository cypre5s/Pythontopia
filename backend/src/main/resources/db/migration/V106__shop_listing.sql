-- Purpose: define shop SKUs (price, stock, weekday availability) for each town merchant.
-- Tables affected: shop_listing.
-- Irreversible risk: dropping shop_listing requires re-seeding the merchant inventory.

CREATE TABLE IF NOT EXISTS shop_listing (
  id BIGSERIAL PRIMARY KEY,
  shop_key VARCHAR(64) NOT NULL,
  item_key VARCHAR(64) NOT NULL REFERENCES item_catalog(item_key) ON DELETE CASCADE,
  price_coins INTEGER NOT NULL
    CONSTRAINT shop_listing_price_coins_positive CHECK (price_coins > 0),
  price_gems INTEGER NOT NULL DEFAULT 0
    CONSTRAINT shop_listing_price_gems_nonneg CHECK (price_gems >= 0),
  stock INTEGER,
  available_days INTEGER[] DEFAULT NULL,
  CONSTRAINT uq_shop_listing UNIQUE (shop_key, item_key)
);

CREATE INDEX IF NOT EXISTS idx_shop_listing_shop ON shop_listing(shop_key);

INSERT INTO shop_listing (shop_key, item_key, price_coins, stock) VALUES
  ('merchant_port', 'python_book',     100,    5),
  ('merchant_port', 'coffee',           30, NULL),
  ('merchant_port', 'hoe',              50,    1),
  ('merchant_port', 'debug_lamp',       80,    3),
  ('merchant_port', 'seed_chapter_1',   10, NULL)
ON CONFLICT (shop_key, item_key) DO NOTHING;

COMMENT ON COLUMN shop_listing.stock IS 'NULL = 无限库存';
COMMENT ON COLUMN shop_listing.available_days IS '0=Sun..6=Sat，NULL = 全周可购';
