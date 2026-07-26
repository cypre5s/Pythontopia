package com.pythontopia.classroom.controller;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.classroom.security.ClassroomGuard;
import com.pythontopia.classroom.service.ClassroomLiveService;
import com.pythontopia.common.web.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 课堂直播 SSE 订阅入口。<b>红线</b>：classroomId 是顺序 BIGSERIAL，只校验角色不校验归属等于
 * 「任一教师可枚举订阅任意班级」。这里与 dashboard / quest / remediation 三条教师入口口径一致，
 * 统一走 {@link ClassroomGuard#requireOwner}（角色 + 归属双校验）。
 */
@RestController
@RequestMapping("/api/classroom/{classroomId}/live")
public class ClassroomLiveController {

  private final ClassroomLiveService liveService;
  private final ClassroomGuard classroomGuard;

  public ClassroomLiveController(ClassroomLiveService liveService, ClassroomGuard classroomGuard) {
    this.liveService = liveService;
    this.classroomGuard = classroomGuard;
  }

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter subscribe(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable Long classroomId) {
    classroomGuard.requireOwner(user, classroomId);
    return liveService.subscribe(classroomId, user.userId());
  }
}
