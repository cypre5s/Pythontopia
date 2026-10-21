package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.tutor.dto.TutorAskRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * 导师答疑同会话并发重复提交防护（TDD 红线测试）：同 (userId, conversationId) 的上一问还在
 * 流式作答时，新 ask 必须按统一错误约定快速拒绝（409 SESSION_BUSY）；流终结后释放；
 * 无 conversationId（独立单问）与不同会话互不阻塞。
 */
class TutorAskConcurrencyTest {

  private final TutorGraphClient graphClient = mock(TutorGraphClient.class);
  private final TutorRequestBudgetService requestBudgetService =
      mock(TutorRequestBudgetService.class);
  private final TutorService service =
      new TutorService(
          graphClient,
          mock(TutorSessionRepository.class),
          new ObjectMapper(),
          mock(PassiveProfileService.class),
          mock(ApplicationEventPublisher.class),
          mock(ClassroomDashboardService.class),
          mock(MemoryRetrievalService.class),
          mock(com.pythontopia.coursepack.service.CourseVideoTranscriptService.class),
          mock(TutorDailyRequestLimitService.class));

  private static final CurrentUserPrincipal STUDENT =
      new CurrentUserPrincipal(7L, "stu", Role.STUDENT);

  private static TutorAskRequest ask(String conversationId) {
    return new TutorAskRequest(
        "为什么循环不停", null, null, null, null, conversationId, null, null, null);
  }

  @Test
  void duplicateConcurrentAskOnSameConversationRejectedWithConflict() {
    service.ask(STUDENT, ask("conv-1"));

    assertThatThrownBy(() -> service.ask(STUDENT, ask("conv-1")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.code()).isEqualTo("SESSION_BUSY");
              assertThat(ex.getMessage()).matches(".*[\\u4e00-\\u9fa5].*");
              assertThat(ex.getMessage().length()).isLessThanOrEqualTo(30);
            });
    verify(requestBudgetService).reserve(7L);
  }

  @Test
  void conversationReleasedAfterStreamFinishedAllowsNextAsk() {
    service.ask(STUDENT, ask("conv-1"));

    ArgumentCaptor<Runnable> onFinished = ArgumentCaptor.forClass(Runnable.class);
    verify(graphClient)
        .askStream(eq(7L), any(), any(), any(), any(), any(), any(), any(), onFinished.capture());
    onFinished.getValue().run();

    assertThatCode(() -> service.ask(STUDENT, ask("conv-1"))).doesNotThrowAnyException();
  }

  @Test
  void differentConversationsDoNotBlockEachOther() {
    assertThatCode(
            () -> {
              service.ask(STUDENT, ask("conv-a"));
              service.ask(STUDENT, ask("conv-b"));
            })
        .doesNotThrowAnyException();
  }

  @Test
  void askWithoutConversationIdNeverBlocks() {
    assertThatCode(
            () -> {
              service.ask(STUDENT, ask(null));
              service.ask(STUDENT, ask(null));
              service.ask(STUDENT, ask("  "));
            })
        .doesNotThrowAnyException();
  }

  @Test
  void exhaustedDailyBudgetRejectsBeforeGraphAndReleasesConversationLock() {
    doThrow(
            new BusinessException(
                HttpStatus.TOO_MANY_REQUESTS,
                "TUTOR_DAILY_REQUEST_LIMIT_REACHED",
                "今日导师请求次数已达上限"))
        .when(requestBudgetService)
        .reserve(7L);

    assertThatThrownBy(() -> service.ask(STUDENT, ask("conv-budget")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    // The next attempt must see the budget response again rather than a stale SESSION_BUSY lock.
    assertThatThrownBy(() -> service.ask(STUDENT, ask("conv-budget")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

    verify(requestBudgetService, times(2)).reserve(7L);
    verifyNoInteractions(graphClient);
  }
}
