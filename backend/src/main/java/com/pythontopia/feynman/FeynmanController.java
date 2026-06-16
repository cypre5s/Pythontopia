package com.pythontopia.feynman;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.web.CurrentUser;
import com.pythontopia.feynman.dto.FeynmanDtos.SessionResponse;
import com.pythontopia.feynman.dto.FeynmanDtos.StartRequest;
import com.pythontopia.feynman.dto.FeynmanDtos.TeachRequest;
import com.pythontopia.feynman.dto.FeynmanDtos.TurnResponse;
import com.pythontopia.feynman.dto.FeynmanDtos.Verdict;
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

  public FeynmanController(
      FeynmanSessionService sessionService, FeynmanJudgeService judgeService) {
    this.sessionService = sessionService;
    this.judgeService = judgeService;
  }

  @PostMapping("/sessions")
  public SessionResponse start(
      @CurrentUser CurrentUserPrincipal user, @Valid @RequestBody StartRequest request) {
    requireStudent(user);
    return sessionService.start(user.userId(), request.kcCode());
  }

  @PostMapping("/sessions/{id}/teach")
  public TurnResponse teach(
      @CurrentUser CurrentUserPrincipal user,
      @PathVariable long id,
      @Valid @RequestBody TeachRequest request) {
    requireStudent(user);
    return sessionService.teach(user.userId(), id, request.explanation());
  }

  @PostMapping("/sessions/{id}/judge")
  public Verdict judge(@CurrentUser CurrentUserPrincipal user, @PathVariable long id) {
    requireStudent(user);
    return judgeService.judge(user.userId(), id);
  }

  private void requireStudent(CurrentUserPrincipal user) {
    if (user.role() != Role.STUDENT) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅学生可使用费曼学弟");
    }
  }
}
