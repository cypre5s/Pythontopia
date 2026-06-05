package com.pythontopia.world;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.world.calendar.CalendarMath;
import com.pythontopia.world.calendar.WorldEvent;
import com.pythontopia.world.calendar.WorldEventRepository;
import com.pythontopia.world.calendar.dto.WorldEventDto;
import com.pythontopia.world.dto.WorldAdvanceResponse;
import com.pythontopia.world.dto.WorldSnapshotDto;
import com.pythontopia.world.repository.GameBootstrapRepository;
import com.pythontopia.world.repository.WorldRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现「睡觉 → 推进一天」循环。每次 tick：
 *
 * <ol>
 *   <li>{@code day_index} +1，轮转 {@code weekday}；当 28 天的季度翻页时推进 {@code season/month_index}；
 *   <li>随机选择新天气（测试中按种子可复现）；
 *   <li>调用 {@link MasteryEvaluator#decayAll} 对每个 KC 掌握度应用遗忘曲线；
 *   <li>调用 {@link PassiveProfileService} 将 {@code emotion_persistence.score} +0.01 以奖励每日游玩；
 *   <li>为新一天写入可选的 {@link WorldEvent}（天气变化、课堂提示）供 UI 日历展示。
 * </ol>
 */
@Service
public class WorldAdvanceService {

  private static final Logger log = LoggerFactory.getLogger(WorldAdvanceService.class);

  private static final List<String> WEATHERS = List.of("SUNNY", "CLOUDY", "RAIN", "WIND");
  private static final double DAILY_PERSISTENCE_DELTA = 0.01;

  private final WorldRepository worldRepository;
  private final GameBootstrapRepository bootstrapRepository;
  private final WorldEventRepository eventRepository;
  private final MasteryEvaluator masteryEvaluator;
  private final PassiveProfileService passiveProfileService;
  private final Random random;

  @Autowired
  public WorldAdvanceService(
      WorldRepository worldRepository,
      GameBootstrapRepository bootstrapRepository,
      WorldEventRepository eventRepository,
      MasteryEvaluator masteryEvaluator,
      PassiveProfileService passiveProfileService) {
    this(
        worldRepository,
        bootstrapRepository,
        eventRepository,
        masteryEvaluator,
        passiveProfileService,
        new Random());
  }

  WorldAdvanceService(
      WorldRepository worldRepository,
      GameBootstrapRepository bootstrapRepository,
      WorldEventRepository eventRepository,
      MasteryEvaluator masteryEvaluator,
      PassiveProfileService passiveProfileService,
      Random random) {
    this.worldRepository = worldRepository;
    this.bootstrapRepository = bootstrapRepository;
    this.eventRepository = eventRepository;
    this.masteryEvaluator = masteryEvaluator;
    this.passiveProfileService = passiveProfileService;
    this.random = random;
  }

  @Transactional
  public WorldAdvanceResponse sleep(Long userId) {
    GameBootstrapRepository.BootstrapRow row =
        bootstrapRepository
            .findRow(userId)
            .filter(r -> r.world() != null)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "WORLD_SESSION_MISSING",
                        "World session has not been initialised yet"));
    WorldSnapshotDto previous = row.world();
    String newWeekday = CalendarMath.nextWeekday(previous.weekday());
    CalendarMath.SeasonRoll seasonRoll =
        CalendarMath.advanceSeason(previous.dayIndex(), previous.monthIndex(), previous.season());
    String newWeather = WEATHERS.get(random.nextInt(WEATHERS.size()));

    worldRepository.advanceDay(
        userId, newWeather, newWeekday, seasonRoll.season(), seasonRoll.monthIndex(), "morning");

    try {
      masteryEvaluator.decayAll(userId);
    } catch (Exception exception) {
      log.warn("WorldAdvanceService.decayAll failed for user {}: {}", userId, exception.getMessage());
    }

    try {
      passiveProfileService.applyDimensionUpdate(
          userId,
          "emotion_persistence",
          new DimensionDelta(
              DAILY_PERSISTENCE_DELTA, 1, null, Map.of("source_kind", "DAILY_SLEEP"), "QUEST"));
    } catch (Exception exception) {
      log.warn("WorldAdvanceService persistence bump failed for user {}: {}", userId, exception.getMessage());
    }

    List<WorldEventDto> newEvents = new ArrayList<>();
    if (!newWeather.equals(previous.weather())) {
      WorldEvent weatherEvent =
          eventRepository.insert(
              userId,
              LocalDate.now(),
              "WEATHER_CHANGE",
              Map.of("from", previous.weather(), "to", newWeather));
      newEvents.add(WorldEventDto.from(weatherEvent));
    }
    if (!seasonRoll.season().equals(previous.season())) {
      WorldEvent seasonEvent =
          eventRepository.insert(
              userId,
              LocalDate.now(),
              "SEASON_CHANGE",
              Map.of("from", previous.season(), "to", seasonRoll.season()));
      newEvents.add(WorldEventDto.from(seasonEvent));
    }

    WorldSnapshotDto refreshed =
        bootstrapRepository.findRow(userId).map(GameBootstrapRepository.BootstrapRow::world).orElse(null);
    return new WorldAdvanceResponse(refreshed, newEvents);
  }
}
