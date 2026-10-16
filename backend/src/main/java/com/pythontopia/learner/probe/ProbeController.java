package com.pythontopia.learner.probe;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.probe.dto.ProbeRequest;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 世界内探针上报入口。学生做出的中性选择经服务端权威 {@link ProbeCatalog} 校验后发布
 * {@link LearningEvent.ProbeChoiceMade}，由 {@code PassiveProfileRevisionListener} 回流到画像维度。
 */
@RestController
@RequestMapping("/api/learner/probe")
public class ProbeController {

  private final ApplicationEventPublisher eventPublisher;
  private final StudentWriteThrottle studentWriteThrottle;

  public ProbeController(
      ApplicationEventPublisher eventPublisher, StudentWriteThrottle studentWriteThrottle) {
    this.eventPublisher = eventPublisher;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void submit(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody ProbeRequest request) {
    requireStudent(user);
    if (!ProbeCatalog.isValid(request.probeKey(), request.choiceId())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PROBE_INVALID",
          "Unknown probe choice: " + request.probeKey() + "/" + request.choiceId());
    }
    if (!studentWriteThrottle.tryAcquire(user.userId(), 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条学习事件");
    }
    eventPublisher.publishEvent(
        new LearningEvent.ProbeChoiceMade(user.userId(), request.probeKey(), request.choiceId()));
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可上报探针选择");
    }
  }
}
