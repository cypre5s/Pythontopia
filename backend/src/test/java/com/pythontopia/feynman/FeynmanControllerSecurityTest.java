package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.feynman.dto.FeynmanDtos.TeachRequest;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

class FeynmanControllerSecurityTest {

  private final FeynmanSessionService sessionService = mock(FeynmanSessionService.class);
  private final FeynmanJudgeService judgeService = mock(FeynmanJudgeService.class);
  private final TutorDailyRequestLimitService requestLimitService =
      mock(TutorDailyRequestLimitService.class);
  private final FeynmanController controller =
      new FeynmanController(sessionService, judgeService, requestLimitService);
  private final CurrentUserPrincipal student =
      new CurrentUserPrincipal(7L, "audit-owner", Role.STUDENT);

  @Test
  void teachPropagatesAuthenticatedOwnerId() {
    controller.teach(student, 10L, new TeachRequest("explanation"));

    InOrder order = inOrder(requestLimitService, sessionService);
    order.verify(requestLimitService).reserve(7L);
    order.verify(sessionService).teach(7L, 10L, "explanation");
  }

  @Test
  void bugHuntReservesTheSharedDailyLimitBeforeCallingTheModel() {
    controller.bugHunt(student, 10L);

    InOrder order = inOrder(requestLimitService, sessionService);
    order.verify(requestLimitService).reserve(7L);
    order.verify(sessionService).bugHunt(7L, 10L);
  }

  @Test
  void exhaustedDailyLimitPreventsFeynmanModelCalls() {
    doThrow(new BusinessException(
            HttpStatus.TOO_MANY_REQUESTS,
            "TUTOR_DAILY_LIMIT_REACHED",
            "今日 AI 请求额度已达 30 次，请明天再试"))
        .when(requestLimitService)
        .reserve(7L);

    assertThatThrownBy(() -> controller.teach(student, 10L, new TeachRequest("explanation")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("30 次");

    verify(sessionService, never()).teach(7L, 10L, "explanation");
  }

  @Test
  void judgePropagatesAuthenticatedOwnerId() {
    controller.judge(student, 10L);

    verify(judgeService).judge(7L, 10L);
  }
}
