-- Purpose: R-14 方案A 收缩为单中央小镇 hub，移除 KC 作物/播种/浇水（农场）玩法。
-- Tables affected: 删除 crop_tile_state（V013 建表、V031 扩展列与约束）。
-- Irreversible: 是。crop_tile_state 行数据将永久删除；掌握度本身存于 kc_mastery_event，不受影响。
DROP TABLE IF EXISTS crop_tile_state;
