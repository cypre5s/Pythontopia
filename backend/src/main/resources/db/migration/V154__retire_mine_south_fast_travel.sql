-- Purpose: R-14 单中央小镇收缩后，mine_south 风景地图已删除；后端快速旅行不能再承认 hub<->mine 路线。
-- Tables affected: fast_travel_route（删除所有 mine_south 相关路线）。
-- Irreversible risk: 若未来恢复矿洞风景地图，需要用新迁移重新 seed 路线。

DELETE FROM fast_travel_route
WHERE from_scene = 'mine_south'
   OR to_scene = 'mine_south';
