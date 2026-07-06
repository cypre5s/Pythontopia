package com.pythontopia.world.calendar;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.world.calendar.dto.CalendarTodayResponse;
import com.pythontopia.world.calendar.dto.WorldEventDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world/calendar")
public class CalendarController {

  private final CalendarService service;

  public CalendarController(CalendarService service) {
    this.service = service;
  }

  @GetMapping("/today")
  public CalendarTodayResponse today(@CurrentUser CurrentUserPrincipal user) {
    return service.today(user.userId());
  }

  @GetMapping("/events")
  public List<WorldEventDto> events(@CurrentUser CurrentUserPrincipal user) {
    return service.pendingEvents(user.userId());
  }

  @PostMapping("/events/{eventId}/process")
  public WorldEventDto markProcessed(
      @CurrentUser CurrentUserPrincipal user, @PathVariable Long eventId) {
    return service.markProcessed(user.userId(), eventId);
  }
}
