package com.pythontopia.world.festival;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Read + lifecycle entry-point for the V118 festival catalog. Reads expose the schedule to
 * {@code CalendarController} and class dashboards; the {@link #onDayAdvanced(int)} /
 * {@link #onDayEnded(int)} hooks are designed to be called from
 * {@code WorldAdvanceService.sleep()} inside the same transaction that bumps
 * {@code world_session.day_index}, so the festival lifecycle is always consistent with the world
 * clock.
 *
 * <p>This R-12 W5 skeleton intentionally <strong>does not</strong> write the per-user
 * {@code FESTIVAL_START} / {@code FESTIVAL_END} rows into {@code world_event} yet — that is
 * owned by the follow-up R-Live-Festival sub-task once the per-user dispatcher
 * ({@code FestivalRewardDispatcher} in §8.3 of the design doc) is in place. See the TODO markers
 * below for the exact hook points.
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
   * Called by {@code WorldAdvanceService.sleep()} after {@code world_session.day_index} flips to
   * {@code newDay}. When the new day matches a seeded festival, marks it {@code 'active'} so the
   * HUD / classroom dashboards reflect the live state. Non-festival days are a no-op.
   */
  public void onDayAdvanced(int newDay) {
    Optional<WorldFestival> festival = repository.findByDay(newDay);
    if (festival.isEmpty()) {
      return;
    }
    repository.updateStatus(newDay, "active");
    log.info(
        "Festival entered: day_index={} name_key={}", newDay, festival.get().nameKey());
    // TODO(R-Live-Festival): emit FESTIVAL_START into world_event per active user once the
    // per-user dispatcher (FestivalRewardDispatcher, design doc §8.3) lands. Payload schema is
    // {name_key, theme, npcs[], rewards{}, started_at} — see §8.2 of
    // docs/gameplay/r12-deepen/05-festival-events.md.
  }

  /**
   * Called by {@code WorldAdvanceService.sleep()} the day after a festival, i.e. when {@code
   * world_session.day_index} has already moved past the festival day. Marks the festival
   * {@code 'completed'} so dashboards can render it as a closed timeline entry.
   */
  public void onDayEnded(int day) {
    if (repository.findByDay(day).isEmpty()) {
      return;
    }
    repository.updateStatus(day, "completed");
    log.info("Festival ended: day_index={}", day);
    // TODO(R-Live-Festival): emit FESTIVAL_END into world_event with payload
    // {name_key, ended_at, participate_count}.
  }
}
