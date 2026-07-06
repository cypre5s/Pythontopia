-- R-12 W5 能量与疲劳曲线骨架：把"占位 HUD 能量条"升级为后端权威字段。
-- Tables affected: world_session（V013 建立，V107 扩展日历列）。
-- Irreversible risk: 删除新列会丢失玩家当日剩余能量、能量上限和过劳累计分钟，
--                    导致 EnergyService.applyDelta 在下次会话恢复时把所有玩家
--                    回放成"满能量、零过劳"，过劳保护链路被绕过一轮。
-- 设计依据: docs/gameplay/r12-deepen/03-energy-fatigue-curve.md §2 / §6 / §8.1。

ALTER TABLE world_session
  ADD COLUMN IF NOT EXISTS energy INTEGER NOT NULL DEFAULT 100,
  ADD COLUMN IF NOT EXISTS energy_cap INTEGER NOT NULL DEFAULT 100,
  ADD COLUMN IF NOT EXISTS overwork_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE world_session
  DROP CONSTRAINT IF EXISTS world_session_energy_range;
ALTER TABLE world_session
  ADD CONSTRAINT world_session_energy_range
    CHECK (energy BETWEEN 0 AND 100);

ALTER TABLE world_session
  DROP CONSTRAINT IF EXISTS world_session_energy_cap_range;
ALTER TABLE world_session
  ADD CONSTRAINT world_session_energy_cap_range
    CHECK (energy_cap BETWEEN 60 AND 110);

ALTER TABLE world_session
  DROP CONSTRAINT IF EXISTS world_session_overwork_minutes_non_negative;
ALTER TABLE world_session
  ADD CONSTRAINT world_session_overwork_minutes_non_negative
    CHECK (overwork_minutes >= 0);

COMMENT ON COLUMN world_session.energy IS
  'R-12 当前剩余能量 (0-100)，由 EnergyService.applyDelta 唯一写入，HUD 实时镜像；不允许业务层直接 update。';
COMMENT ON COLUMN world_session.energy_cap IS
  'R-12 当日能量上限 (60-110)：默认 100；通宵 → 90；过劳 240 分钟锁 80；节日 spring_opening +10 → 110。sleep 推日时重置回 100。';
COMMENT ON COLUMN world_session.overwork_minutes IS
  'R-12 单日连续会话累计真实分钟，用于 §6 过劳保护 (warn=120 / capDown=240 / forceQuit=360)。sleep 推日时清零。';
