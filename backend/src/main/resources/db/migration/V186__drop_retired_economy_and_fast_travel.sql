-- Purpose: 清掉 R-14「单中央小镇收缩」后残留的整套经济玩法 schema 与快速旅行路线表。
--   R-14 已删除商店 / 背包 / 货币 / 快速旅行玩法，但只做了一半清理：V126 正确 DROP 了
--   crop_tile_state、V135 正确 DROP 了 npc_gift_log/npc_relationship，而下面 6 张表被漏下：
--     * V103 item_catalog / inventory_item（道具目录 + 玩家背包）
--     * V105 player_currency / currency_transaction（金币宝石 + 流水账）
--     * V106 shop_listing（商店 SKU，外键指向 item_catalog）
--     * V119 fast_travel_route（快速旅行路线，V131 + V154 已把全部 seed 行删空）
--   V123 还在往 item_catalog / shop_listing 里 seed 15 件道具与 21 条 listing，属于纯粹的
--   死数据。全仓 grep 这 6 个表名在 backend/src/main/java 下零命中（save_slot.inventory_items
--   是同名 JSONB 列，属另一回事，保留不动）；随本迁移一并删除的还有死代码 world/travel 包。
--
-- Tables affected: shop_listing / inventory_item / item_catalog / currency_transaction /
--   player_currency / fast_travel_route（全部 DROP）。
--
-- Irreversible risk: 不可逆。这 6 张表在收缩后已无任何读写方，删表只丢失历史残留行；
--   若未来重新引入经济/快速旅行玩法，需用新迁移重新建表并重新 seed（V103/V105/V106/V119
--   的建表语句可直接参考）。删除顺序按外键依赖自底向上，不用 CASCADE，避免误伤未预期的
--   下游对象——若真有外键残留，迁移会显式失败而不是静默连坐。

-- 1. 商店 SKU（FK → item_catalog.item_key）
DROP TABLE IF EXISTS shop_listing;

-- 2. 玩家背包（FK → users.id、item_catalog.item_key）
DROP TABLE IF EXISTS inventory_item;

-- 3. 道具目录（被上面两张表引用，最后删）
DROP TABLE IF EXISTS item_catalog;

-- 4. 货币流水（append-only 账本，FK → users.id）
DROP TABLE IF EXISTS currency_transaction;

-- 5. 玩家钱包
DROP TABLE IF EXISTS player_currency;

-- 6. 快速旅行路线（V131 删外围图路线、V154 删 hub↔mine 路线后恒为空表）
DROP TABLE IF EXISTS fast_travel_route;
