-- R-14 世界收缩：快速旅行仅保留 town_hub <-> mine_south 双向路线。
-- 移除外围图 farm_north / campus_east / port_west 相关 seed（V119 遗留）。
-- Tables affected: fast_travel_route（DELETE 8 行外围路线，保留 2 行 hub↔mine）。
-- Irreversible risk: 已依赖外围图传送的客户端需 fallback 到 town_hub（前端已做别名归一）。

DELETE FROM fast_travel_route
WHERE from_scene IN ('farm_north', 'campus_east', 'port_west')
   OR to_scene IN ('farm_north', 'campus_east', 'port_west');
