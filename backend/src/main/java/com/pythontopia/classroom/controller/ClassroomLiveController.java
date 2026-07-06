package com.pythontopia.classroom.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.service.ClassroomLiveService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/classroom/{classroomId}/live")
public class ClassroomLiveController {

  private final ClassroomLiveService liveService;

  public ClassroomLiveController(ClassroomLiveService liveService) {
    this.liveService = liveService;
  }

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter subscribe(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long classroomId) {
    if (user.role() != Role.TEACHER && user.role() != Role.ADMIN) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only teachers can subscribe to live feed");
    }
    return liveService.subscribe(classroomId, user.userId());
  }
}
