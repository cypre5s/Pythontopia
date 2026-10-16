package com.pythontopia.learner.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.web.StudentWriteThrottle;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningEventControllerTest {

  @Mock LearningEventLogRepository repository;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final StudentWriteThrottle throttle = new StudentWriteThrottle();

  private ClientLearningEventGuard eventGuard() {
    return new ClientLearningEventGuard(objectMapper, throttle);
  }

  @Test
  void clientCannotForgeFormalPracticeOutcome() {
    LearningEventController controller =
        new LearningEventController(repository, eventGuard());
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);

    assertThatThrownBy(
            () ->
                controller.logEvent(
                    student,
                    new LearningEventLogDto("practice_succeeded", Map.of("kcCode", "PY43-KC-001"))))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("不允许客户端上报该事件");

    verifyNoInteractions(repository);
  }

  @Test
  void clientCannotForgeMediaConsumptionEvidence() {
    LearningEventController controller =
        new LearningEventController(repository, eventGuard());
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);

    assertThatThrownBy(
            () ->
                controller.logEvent(
                    student,
                    new LearningEventLogDto(
                        "media_consumed",
                        Map.of(
                            "mediaType",
                            "video",
                            "chapter",
                            43,
                            "kcCodes",
                            List.of("PY43-KC-001")))))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("不允许客户端上报该事件");

    verifyNoInteractions(repository);
  }

  @Test
  void invalidBatchIsRejectedBeforeAnyEventIsWritten() {
    LearningEventController controller =
        new LearningEventController(repository, eventGuard());
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    List<LearningEventLogDto> batch =
        List.of(
            new LearningEventLogDto("answer_feedback", Map.of("useful", true)),
            new LearningEventLogDto("practice_failed", Map.of("kcCode", "PY43-KC-001")));

    assertThatThrownBy(() -> controller.logBatch(student, batch))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class);

    verifyNoInteractions(repository);
  }

  @Test
  void oversizedBatchPayloadIsRejectedBeforeAnyEventIsWritten() {
    LearningEventController controller =
        new LearningEventController(repository, eventGuard());
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    List<LearningEventLogDto> batch =
        List.of(
            new LearningEventLogDto("answer_feedback", Map.of("useful", true)),
            new LearningEventLogDto("quiz_finished", Map.of("note", "x".repeat(4097))));

    assertThatThrownBy(() -> controller.logBatch(student, batch))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("payload");

    verifyNoInteractions(repository);
  }

  @Test
  void nullBatchIsRejectedAsBadRequest() {
    LearningEventController controller =
        new LearningEventController(repository, eventGuard());
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);

    assertThatThrownBy(() -> controller.logBatch(student, null))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("批次不能为空");

    verifyNoInteractions(repository);
  }

  @Test
  void nullEventInBatchIsRejectedBeforeAnyEventIsWritten() {
    LearningEventController controller = new LearningEventController(repository, eventGuard());
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);

    assertThatThrownBy(
            () -> controller.logBatch(student, java.util.Arrays.asList((LearningEventLogDto) null)))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class);

    verifyNoInteractions(repository);
  }

  @Test
  void rateLimitIsPerAccountAndRejectsBeforeWriting() {
    LearningEventController controller =
        new LearningEventController(repository, eventGuard());
    CurrentUserPrincipal student = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    List<LearningEventLogDto> batch =
        java.util.Collections.nCopies(
            100, new LearningEventLogDto("answer_feedback", Map.of("useful", true)));

    controller.logBatch(student, batch);

    assertThatThrownBy(
            () ->
                controller.logEvent(
                    student, new LearningEventLogDto("answer_feedback", Map.of("useful", true))))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("每分钟最多");
    assertThat(throttle.tryAcquire(8L, 1)).isTrue();
    verify(repository, times(100))
        .insert(
            7L,
            "answer_feedback",
            Map.of("useful", true),
            "frontend",
            EventImportance.of("answer_feedback"));
  }
}
