package com.pythontopia.world.calendar;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.world.calendar.dto.CalendarTodayResponse;
import com.pythontopia.world.calendar.dto.WorldEventDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world/calendar")
public class CalendarController {

  private final CalendarService service;
  private final StudentWriteThrottle studentWriteThrottle;

  public CalendarController(CalendarService service, StudentWriteThrottle studentWriteThrottle) {
    this.service = service;
    this.studentWriteThrottle = studentWriteThrottle;
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
    if (!studentWriteThrottle.tryAcquire(user.userId(), 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条记录");
    }
    return service.markProcessed(user.userId(), eventId);
  }
}
