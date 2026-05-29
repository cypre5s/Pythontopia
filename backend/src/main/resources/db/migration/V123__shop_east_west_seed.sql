-- Purpose: Seed shop listings for shop_east (工具铺) and shop_west (杂货铺).
-- Tables affected: item_catalog, shop_listing.
-- Irreversible risk: none (additive INSERT with ON CONFLICT DO NOTHING).

-- Add more items to the catalog for the two town shops
INSERT INTO item_catalog (item_key, display_name, category, base_price, description) VALUES
  ('watering_can',   '浇水壶',       'tool',        40,  '给知识种子浇水，加速成长'),
  ('axe',            '斧头',         'tool',        60,  '清除枯萎的知识树桩'),
  ('pickaxe',        '镐',           'tool',        70,  'Bug 矿洞开采工具'),
  ('fertilizer',     '肥料',         'consumable',  20,  '提升 KC 掌握度增长速率'),
  ('bread',          '面包',         'consumable',  15,  '恢复 20 点能量'),
  ('flower',         '鲜花',         'gift',        25,  'NPC 友好度 +3'),
  ('bug_jar',        'Bug 瓶',       'tool',        35,  '捕捉矿洞 Bug 的容器'),
  ('notebook',       '笔记本',       'book',        45,  '记录学习笔记，复盘时可翻阅'),
  ('energy_potion',  '精力药水',     'consumable',  50,  '恢复 50 点能量'),
  ('seed_chapter_2', '进阶种子',     'seed',        15,  '第 2 章 KC 种子'),
  ('seed_chapter_3', '计算种子',     'seed',        20,  '第 3 章 KC 种子'),
  ('seed_chapter_4', '流程种子',     'seed',        25,  '第 4 章 KC 种子'),
  ('seed_chapter_5', '结构种子',     'seed',        30,  '第 5 章 KC 种子'),
  ('seed_chapter_6', '存取种子',     'seed',        35,  '第 6 章 KC 种子'),
  ('seed_chapter_7', '抽象种子',     'seed',        40,  '第 7 章 KC 种子')
ON CONFLICT (item_key) DO NOTHING;

-- shop_east: 工具铺 — 主卖工具和种子
INSERT INTO shop_listing (shop_key, item_key, price_coins, stock) VALUES
  ('shop_east', 'hoe',              50,  NULL),
  ('shop_east', 'watering_can',     40,  NULL),
  ('shop_east', 'axe',              60,  NULL),
  ('shop_east', 'pickaxe',          70,  NULL),
  ('shop_east', 'bug_jar',          35,  NULL),
  ('shop_east', 'debug_lamp',       80,  3),
  ('shop_east', 'seed_chapter_1',   10,  NULL),
  ('shop_east', 'seed_chapter_2',   15,  NULL),
  ('shop_east', 'seed_chapter_3',   20,  NULL),
  ('shop_east', 'seed_chapter_4',   25,  NULL),
  ('shop_east', 'seed_chapter_5',   30,  NULL),
  ('shop_east', 'seed_chapter_6',   35,  NULL),
  ('shop_east', 'seed_chapter_7',   40,  NULL)
ON CONFLICT (shop_key, item_key) DO NOTHING;

-- shop_west: 杂货铺 — 主卖消耗品、书籍和礼物
INSERT INTO shop_listing (shop_key, item_key, price_coins, stock) VALUES
  ('shop_west', 'bread',            15,  NULL),
  ('shop_west', 'coffee',           30,  NULL),
  ('shop_west', 'energy_potion',    50,  NULL),
  ('shop_west', 'fertilizer',       20,  NULL),
  ('shop_west', 'flower',           25,  NULL),
  ('shop_west', 'python_book',     100,  5),
  ('shop_west', 'notebook',         45,  NULL),
  ('shop_west', 'trophy',          200,  1)
ON CONFLICT (shop_key, item_key) DO NOTHING;
