package com.pythontopia.world.festival;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 节日目录（V118）的读取 + 生命周期入口。读接口把日程暴露给 {@code CalendarController} 与班级看板；
 * {@link #onDayAdvanced(int)} / {@link #onDayEnded(int)} 须在 {@code WorldAdvanceService.sleep()} 内、
 * 与 {@code world_session.day_index} 自增同一事务调用，使节日生命周期始终与世界时钟一致。
 *
 * <p>状态写入是全日程级（无 user_id）；按用户的 {@code FESTIVAL_START}/{@code FESTIVAL_END} 事件目前
 * 不在此发出。
 */
@Service
public class WorldFestivalService {

  private static final Logger log = LoggerFactory.getLogger(WorldFestivalService.class);

  private final WorldFestivalRepository repository;

  public WorldFestivalService(WorldFestivalRepository repository) {
    this.repository = repository;
  }

  public Optional<WorldFestival> getCurrent(int currentDay) {
    return repository.findByDay(currentDay);
  }

  public List<WorldFestival> getUpcoming(int currentDay, int days) {
    if (days <= 0) {
      return List.of();
    }
    return repository.findUpcoming(currentDay, days);
  }

  /**
   * 由 {@code WorldAdvanceService.sleep()} 在 {@code world_session.day_index} 翻到 {@code newDay} 后调用。
   * 新一天命中已 seed 的节日时置为 {@code 'active'}，让 HUD/班级看板反映实时状态；非节日日 no-op。
   */
  public void onDayAdvanced(int newDay) {
    Optional<WorldFestival> festival = repository.findByDay(newDay);
    if (festival.isEmpty()) {
      return;
    }
    repository.updateStatus(newDay, "active");
    log.info(
        "Festival entered: day_index={} name_key={}", newDay, festival.get().nameKey());
    // 按用户维度的 FESTIVAL_START 事件目前不在此发出（待 per-user dispatcher 落地）。
  }

  /**
   * 由 {@code WorldAdvanceService.sleep()} 在节日次日（{@code world_session.day_index} 已越过节日当天）调用，
   * 置节日为 {@code 'completed'}，使看板按已结束的时间线条目渲染。
   */
  public void onDayEnded(int day) {
    if (repository.findByDay(day).isEmpty()) {
      return;
    }
    repository.updateStatus(day, "completed");
    log.info("Festival ended: day_index={}", day);
    // 按用户维度的 FESTIVAL_END 事件目前不在此发出（待 per-user dispatcher 落地）。
  }
}
