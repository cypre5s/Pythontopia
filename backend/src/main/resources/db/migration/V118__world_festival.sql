-- Purpose: bootstrap the static festival catalog consumed by WorldFestivalService. Each row maps
--          one in-game day_index to a localisable name_key plus a mutable lifecycle status that
--          R-12 W5 flips between 'pending' → 'active' → 'completed' as the world calendar ticks.
--          See docs/gameplay/r12-deepen/05-festival-events.md §1, §8.1 for the 11 milestone-tier
--          festivals seeded here.
-- Tables affected: world_festival (new).
-- Irreversible risk: dropping world_festival deletes the canonical festival schedule; downstream
--                    services (WorldAdvanceService.sleep → FESTIVAL_START, CalendarController
--                    activeFestival, teacher classroom dashboards) will silently lose the trigger
--                    source. Status mutations are reversible (UPDATE back to 'pending') and the
--                    11 seed rows can be replayed safely via ON CONFLICT DO NOTHING below.

CREATE TABLE IF NOT EXISTS world_festival (
  id BIGSERIAL PRIMARY KEY,
  day_index INTEGER NOT NULL UNIQUE
    CONSTRAINT world_festival_day_index_range CHECK (day_index BETWEEN 1 AND 112),
  name_key VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending'
    CONSTRAINT world_festival_status_check
      CHECK (status IN ('pending', 'active', 'completed')),
  override_payload JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- day_index UNIQUE already creates an implicit btree index used by findByDay / findUpcoming;
-- no extra index is needed at R-12 W5 scale (11 rows total).

COMMENT ON TABLE world_festival IS
  '11 个里程碑级节日静态时间表（R-12 Deepen 第 05 篇）。day_index 与 world_session.day_index 对齐，status 表示节日生命周期（pending=未触发 / active=进行中 / completed=已结束），override_payload 预留教师与运营的运行时微调入口（reward 覆写、HUD 文案等）。';
COMMENT ON COLUMN world_festival.day_index IS '游戏内日历的整数日（1..112），与 16 周 4 季节框架一一对齐';
COMMENT ON COLUMN world_festival.name_key IS '节日唯一英文键，前端按 i18n 映射成中英文展示文案';
COMMENT ON COLUMN world_festival.status IS '节日生命周期；写权限只属于 WorldFestivalService.onDayAdvanced / onDayEnded';
COMMENT ON COLUMN world_festival.override_payload IS '运行时覆盖参数（reward 配额 / banner 文案 / bgm key 等），为空表示沿用默认';

INSERT INTO world_festival (day_index, name_key) VALUES
  (1,   'spring_opening'),
  (14,  'spring_quiz'),
  (28,  'spring_finale_race'),
  (35,  'midterm_festival'),
  (56,  'port_fair'),
  (60,  'bug_carnival'),
  (70,  'final_warmup'),
  (84,  'finals_festival'),
  (90,  'winter_showcase'),
  (100, 'silent_night'),
  (112, 'new_year_eve')
ON CONFLICT (day_index) DO NOTHING;
