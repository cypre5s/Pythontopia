package com.pythontopia.feynman;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.feynman.dto.FeynmanDtos.BugHuntResponse;
import com.pythontopia.feynman.dto.FeynmanDtos.SessionResponse;
import com.pythontopia.feynman.dto.FeynmanDtos.StartRequest;
import com.pythontopia.feynman.dto.FeynmanDtos.TeachRequest;
import com.pythontopia.feynman.dto.FeynmanDtos.TurnResponse;
import com.pythontopia.feynman.dto.FeynmanDtos.Verdict;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feynman")
public class FeynmanController {

  private final FeynmanSessionService sessionService;
  private final FeynmanJudgeService judgeService;
  private final TutorDailyRequestLimitService requestLimitService;
  private final StudentWriteThrottle studentWriteThrottle;

  public FeynmanController(
      FeynmanSessionService sessionService,
      FeynmanJudgeService judgeService,
      TutorDailyRequestLimitService requestLimitService,
      StudentWriteThrottle studentWriteThrottle) {
    this.sessionService = sessionService;
    this.judgeService = judgeService;
    this.requestLimitService = requestLimitService;
    this.studentWriteThrottle = studentWriteThrottle;
  }

  @PostMapping("/sessions")
  public SessionResponse start(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody StartRequest request) {
    requireStudent(user);
    if (!studentWriteThrottle.tryAcquire(user.userId(), 1)) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "STUDENT_WRITE_RATE_LIMITED",
          "每个账号每分钟最多写入 100 条学习记录");
    }
    return sessionService.start(user.userId(), request.kcCode());
  }

  @PostMapping("/sessions/{id}/teach")
  public TurnResponse teach(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable long id,
      @Valid @RequestBody TeachRequest request) {
    requireStudent(user);
    requestLimitService.reserve(user.userId());
    return sessionService.teach(user.userId(), id, request.explanation());
  }

  @PostMapping("/sessions/{id}/judge")
  public Verdict judge(@CurrentUser CurrentUserPrincipal user, @PathVariable long id) {
    requireStudent(user);
    if (!studentWriteThrottle.tryAcquireSandboxJudge(user.userId())) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "FEYNMAN_JUDGE_RATE_LIMITED",
          "代码判题每个账号每分钟最多 6 次，请稍后再试");
    }
    return judgeService.judge(user.userId(), id);
  }

  @PostMapping("/sessions/{id}/bug-hunt")
  public BugHuntResponse bugHunt(@CurrentUser CurrentUserPrincipal user, @PathVariable long id) {
    requireStudent(user);
    requestLimitService.reserve(user.userId());
    return sessionService.bugHunt(user.userId(), id);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用费曼学弟");
    }
  }
}
