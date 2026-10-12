package com.pythontopia.learner.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.learner.event.LearningEventLogDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class LearningEventControllerTest {

  @Mock LearningEventLogRepository repository;
  @Mock ApplicationEventPublisher eventPublisher;

  @Test
  void clientCannotForgeFormalPracticeOutcome() {
    LearningEventController controller = new LearningEventController(repository, eventPublisher);
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);

    assertThatThrownBy(
            () -> controller.logEvent(
                student,
                new LearningEventLogDto("practice_succeeded", Map.of("kcCode", "PY43-KC-001"))))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("不允许客户端上报该事件");

    verifyNoInteractions(repository, eventPublisher);
  }

  @Test
  void invalidBatchIsRejectedBeforeAnyEventIsWritten() {
    LearningEventController controller = new LearningEventController(repository, eventPublisher);
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    List<LearningEventLogDto> batch = List.of(
        new LearningEventLogDto("answer_feedback", Map.of("useful", true)),
        new LearningEventLogDto("practice_failed", Map.of("kcCode", "PY43-KC-001")));

    assertThatThrownBy(() -> controller.logBatch(student, batch))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class);

    verifyNoInteractions(repository, eventPublisher);
  }
}
