package com.pythontopia.feynman;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.feynman.dto.FeynmanDtos.TeachRequest;
import org.junit.jupiter.api.Test;

class FeynmanControllerSecurityTest {

  private final FeynmanSessionService sessionService = mock(FeynmanSessionService.class);
  private final FeynmanJudgeService judgeService = mock(FeynmanJudgeService.class);
  private final FeynmanController controller = new FeynmanController(sessionService, judgeService);
  private final CurrentUserPrincipal student =
      new CurrentUserPrincipal(7L, "audit-owner", Role.STUDENT);

  @Test
  void teachPropagatesAuthenticatedOwnerId() {
    controller.teach(student, 10L, new TeachRequest("explanation"));

    verify(sessionService).teach(7L, 10L, "explanation");
  }

  @Test
  void judgePropagatesAuthenticatedOwnerId() {
    controller.judge(student, 10L);

    verify(judgeService).judge(7L, 10L);
  }
}
