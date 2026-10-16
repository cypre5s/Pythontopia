package com.pythontopia.learner.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.learner.memory.MemoryEmbeddingWriter;
import com.pythontopia.npc.NpcMemoryService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;

/** 进程内 LearningEvent → learning_event_log 镜像写表的单测（含 importance 规则与异常隔离）。 */
class MemoryStreamMirrorTest {

  private final LearningEventLogRepository repository = mock(LearningEventLogRepository.class);
  private final MemoryEmbeddingWriter embeddingWriter = mock(MemoryEmbeddingWriter.class);
  private final NpcMemoryService npcMemoryService = mock(NpcMemoryService.class);
  private final MemoryStreamMirror mirror =
      new MemoryStreamMirror(repository, embeddingWriter, npcMemoryService);

  @Test
  @SuppressWarnings("unchecked")
  void practiceFailedMirroredWithImportanceAndPayload() {
    mirror.onPracticeFailed(new LearningEvent.PracticeFailed(7L, "PY43-KC-016", 5L, "WA", "medium"));

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(repository)
        .insert(eq(7L), eq("practice_failed"), payloadCaptor.capture(), eq("backend"), eq(4));
    Map<String, Object> payload = payloadCaptor.getValue();
    assertThat(payload).containsEntry("kcCode", "PY43-KC-016");
    assertThat(payload).containsEntry("problemId", 5L);
    assertThat(payload).containsEntry("failureKind", "WA");
    verify(embeddingWriter).embedAndStore(eq(0L), eq("practice_failed"), eq(payloadCaptor.getValue()));
    verify(npcMemoryService).remember(eq(7L), eq("coach_q"), eq(0L), eq(0.5));
  }

  @Test
  void comebackSolvedMirroredWithImportanceFive() {
    mirror.onComebackSolved(new LearningEvent.ComebackSolved(7L, 5L, "PY43-KC-016", 3));

    verify(repository).insert(eq(7L), eq("comeback_solved"), anyMap(), eq("backend"), eq(5));
  }

  @Test
  void resourceConsumedMirroredWithImportanceOne() {
    mirror.onResourceConsumed(new LearningEvent.ResourceConsumed(7L, "PY43-KC-001", "VIDEO"));

    verify(repository).insert(eq(7L), eq("resource_consumed"), anyMap(), eq("backend"), eq(1));
  }

  @Test
  @SuppressWarnings("unchecked")
  void chapterAssessmentOutcomeMirrorsItsVerifiedQuestionEvidence() {
    mirror.onChapterAssessmentScored(
        new LearningEvent.ChapterAssessmentScored(
            7L, 100L, 1, "FINAL", "PY43-KC-003", 1, 2));

    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(repository)
        .insert(
            eq(7L),
            eq("chapter_assessment_failed"),
            payloadCaptor.capture(),
            eq("backend"),
            eq(4));
    assertThat(payloadCaptor.getValue())
        .containsEntry("assessmentId", 100L)
        .containsEntry("chapter", 1)
        .containsEntry("assessmentKind", "FINAL")
        .containsEntry("kcCode", "PY43-KC-003")
        .containsEntry("correctQuestions", 1)
        .containsEntry("questionCount", 2);
  }

  @Test
  @SuppressWarnings("unchecked")
  void resourceCheckCompletedMirrorsPassAndFailWithEvidencePayload() {
    mirror.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            7L,
            13L,
            99L,
            "PY43-KC-001",
            "MICRO_QUIZ",
            true,
            new java.math.BigDecimal("0.05"),
            null));

    ArgumentCaptor<Map<String, Object>> passPayload = ArgumentCaptor.forClass(Map.class);
    verify(repository)
        .insert(eq(7L), eq("resource_check_passed"), passPayload.capture(), eq("backend"), eq(3));
    assertThat(passPayload.getValue()).containsEntry("checkId", 13L);
    assertThat(passPayload.getValue()).containsEntry("resourceId", 99L);
    assertThat(passPayload.getValue()).containsEntry("kcCode", "PY43-KC-001");
    assertThat(passPayload.getValue()).containsEntry("evidenceType", "MICRO_QUIZ");

    mirror.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            7L,
            14L,
            99L,
            "PY43-KC-001",
            "MICRO_QUIZ",
            false,
            new java.math.BigDecimal("-0.02"),
            "PRACTICE"));

    ArgumentCaptor<Map<String, Object>> failPayload = ArgumentCaptor.forClass(Map.class);
    verify(repository)
        .insert(eq(7L), eq("resource_check_failed"), failPayload.capture(), eq("backend"), eq(4));
    assertThat(failPayload.getValue()).containsEntry("checkId", 14L);
    assertThat(failPayload.getValue()).containsEntry("remediationType", "PRACTICE");
  }

  @Test
  void repositoryFailureIsSwallowedToProtectEventChain() {
    String sentinel = "private-learning-event-sentinel";
    doThrow(new RuntimeException(sentinel))
        .when(repository)
        .insert(anyLong(), anyString(), anyMap(), anyString(), anyInt());

    // 镜像写表抛异常时不得向上传播，否则会中断事件发布与 mastery/画像回写主链路。
    Logger logger = (Logger) LoggerFactory.getLogger(MemoryStreamMirror.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      mirror.onPracticeFailed(new LearningEvent.PracticeFailed(7L, "K", 1L, "WA", "medium"));
    } finally {
      logger.detachAppender(appender);
    }

    verifyNoInteractions(embeddingWriter);
    verifyNoInteractions(npcMemoryService);
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains(sentinel));
  }

  @Test
  void embeddingQueueRejectionKeepsNpcMemoryAndDoesNotLogUserId() {
    doThrow(new TaskRejectedException("queue full"))
        .when(embeddingWriter)
        .embedAndStore(anyLong(), anyString(), anyMap());
    Logger logger = (Logger) LoggerFactory.getLogger(MemoryStreamMirror.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      mirror.onPracticeFailed(
          new LearningEvent.PracticeFailed(701L, "PY43-KC-016", 5L, "WA", "medium"));
    } finally {
      logger.detachAppender(appender);
    }

    verify(npcMemoryService).remember(eq(701L), eq("coach_q"), eq(0L), eq(0.5));
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains("701"));
  }
}
