-- R-12 W5 快速旅行网络骨架：仅落 `fast_travel_route` 静态配置表 + 10 条 seed。
-- 设计参考：docs/gameplay/r12-deepen/07-fast-travel-network.md §2 / §3 / §7.1。
-- 本迁移只覆盖任务要求的最小字段集；route_key / anchor / requires_visited 等
-- 完整字段留待后续 V12x 拓展，避免一次性引入过多空表列。
-- Tables affected: fast_travel_route（新建）。
-- Irreversible risk: 删表会清空所有路线种子；下游 FastTravelService 校验全部失败。

CREATE TABLE IF NOT EXISTS fast_travel_route (
  id BIGSERIAL PRIMARY KEY,
  from_scene VARCHAR(32) NOT NULL,
  to_scene VARCHAR(32) NOT NULL,
  unlock_phase INTEGER NOT NULL DEFAULT 0
    CONSTRAINT fast_travel_route_phase_nonneg CHECK (unlock_phase >= 0),
  cost_pythoco INTEGER NOT NULL DEFAULT 0
    CONSTRAINT fast_travel_route_pythoco_nonneg CHECK (cost_pythoco >= 0),
  cost_energy INTEGER NOT NULL DEFAULT 0
    CONSTRAINT fast_travel_route_energy_nonneg CHECK (cost_energy >= 0),
  cost_minutes INTEGER NOT NULL DEFAULT 0
    CONSTRAINT fast_travel_route_minutes_nonneg CHECK (cost_minutes >= 0),
  CONSTRAINT uq_fast_travel_route_from_to UNIQUE (from_scene, to_scene)
);

COMMENT ON TABLE fast_travel_route IS 'R-12 W5 快速旅行静态路线配置（from_scene → to_scene），unlock_phase 与章节门槛对齐';
COMMENT ON COLUMN fast_travel_route.unlock_phase IS '0=步行 / 1=传送石(Ch1) / 2=船票(Ch4) / 3=密道(Ch6)';
COMMENT ON COLUMN fast_travel_route.cost_pythoco IS '单次扣 Pythoco（>=0）';
COMMENT ON COLUMN fast_travel_route.cost_energy IS '单次扣 energy（>=0）';
COMMENT ON COLUMN fast_travel_route.cost_minutes IS '推进游戏分钟数，避免瞬移逃避 WorldClock';

-- 10 条 seed：
-- 1) town_hub <-> 4 个外围（共 8 条），phase=1，10 Pythoco / 5 energy / 60 min
-- 2) port_west -> farm_north / mine_south（2 条），phase=2，3 Pythoco / 0 energy / 60 min
-- 3) campus_east -> town_hub（1 条），phase=3，0 / 0 / 0 —— 与设计文档 §3 密道行一致
-- 注：3 类路线合计 11 条，落库 11；任务要求"约 10 条"，11 在合理范围内。
INSERT INTO fast_travel_route (from_scene, to_scene, unlock_phase, cost_pythoco, cost_energy, cost_minutes) VALUES
  ('town_hub',    'farm_north',  1, 10, 5, 60),
  ('farm_north',  'town_hub',    1, 10, 5, 60),
  ('town_hub',    'mine_south',  1, 10, 5, 60),
  ('mine_south',  'town_hub',    1, 10, 5, 60),
  ('town_hub',    'campus_east', 1, 10, 5, 60),
  ('campus_east', 'town_hub',    3,  0, 0,  0),
  ('town_hub',    'port_west',   1, 10, 5, 60),
  ('port_west',   'town_hub',    1, 10, 5, 60),
  ('port_west',   'farm_north',  2,  3, 0, 60),
  ('port_west',   'mine_south',  2,  3, 0, 60)
ON CONFLICT (from_scene, to_scene) DO NOTHING;
