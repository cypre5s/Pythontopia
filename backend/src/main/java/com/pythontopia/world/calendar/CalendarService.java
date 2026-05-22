package com.pythontopia.world.calendar;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.calendar.dto.CalendarTodayResponse;
import com.pythontopia.world.calendar.dto.WorldEventDto;
import com.pythontopia.world.repository.GameBootstrapRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Read-only helpers for the in-game calendar UI. */
@Service
public class CalendarService {

  private final GameBootstrapRepository bootstrapRepository;
  private final WorldEventRepository eventRepository;

  public CalendarService(
      GameBootstrapRepository bootstrapRepository, WorldEventRepository eventRepository) {
    this.bootstrapRepository = bootstrapRepository;
    this.eventRepository = eventRepository;
  }

  public CalendarTodayResponse today(Long userId) {
    var row = bootstrapRepository.findRow(userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.CONFLICT,
                    "WORLD_SESSION_MISSING",
                    "World session has not been initialised yet"));
    if (row.world() == null) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "WORLD_SESSION_MISSING",
          "World session has not been initialised yet");
    }
    var world = row.world();
    return new CalendarTodayResponse(
        world.dayIndex(),
        world.monthIndex(),
        world.weekday(),
        world.season(),
        world.timeOfDay(),
        world.weather(),
        world.inGameMinutes());
  }

  public List<WorldEventDto> pendingEvents(Long userId) {
    return eventRepository.findUnprocessed(userId).stream().map(WorldEventDto::from).toList();
  }

  public WorldEventDto markProcessed(Long userId, Long eventId) {
    return WorldEventDto.from(eventRepository.markProcessed(userId, eventId));
  }
}
