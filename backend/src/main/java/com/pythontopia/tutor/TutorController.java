package com.pythontopia.tutor;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.tutor.dto.StyleEngagedRequest;
import com.pythontopia.tutor.dto.TutorAskRequest;
import com.pythontopia.tutor.dto.TutorSessionDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 导师问答对外入口。{@code POST /ask} 以 SSE 流式返回导师解答——<b>学生与教师/管理员均可用</b>（教师在自己
 * 班级页带 {@code classroomId} 即触发 copilot，由 {@code TutorService} 注入本班只读学情；学生传了也被忽略）。
 * {@code POST /style-engaged} 上报学生采纳的讲法（因材施教闭环，返回 204，<b>仅学生</b>）；{@code GET /sessions}
 * 拉取当前用户的历史会话。
 */
@RestController
@RequestMapping("/api/tutor")
public class TutorController {

  private final TutorService service;

  public TutorController(TutorService service) {
    this.service = service;
  }

  @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter ask(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody TutorAskRequest request) {
    // 学生与教师/管理员均可答疑；教师 copilot 的班级数据注入仅对「归属教师 + classroomId」生效，
    // 学生即便传 classroomId 也被 TutorService 忽略（绝不触达班级聚合），见 TutorService#resolveClassroomBrief。
    return service.ask(user, request);
  }

  @PostMapping("/style-engaged")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void styleEngaged(
      @CurrentUser CurrentUserPrincipal user,
      @Valid @RequestBody StyleEngagedRequest request) {
    requireStudent(user);
    service.recordStyleEngaged(user.userId(), request.style(), request.kcCode());
  }

  @GetMapping("/sessions")
  public List<TutorSessionDto> listSessions(
      @CurrentUser CurrentUserPrincipal user,
      @RequestParam(defaultValue = "20") int limit) {
    return service.listSessions(user.userId(), limit);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用导师问答");
    }
  }
}
