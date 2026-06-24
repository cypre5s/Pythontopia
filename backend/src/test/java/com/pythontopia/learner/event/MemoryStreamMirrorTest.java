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

import com.pythontopia.learner.memory.MemoryEmbeddingWriter;
import com.pythontopia.npc.NpcMemoryService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
  void resourceCheckCompletedMirrorsPassAndFailWithEvidencePayload() {
    mirror.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            7L,
            99L,
            "PY43-KC-001",
            "MICRO_QUIZ",
            true,
            new java.math.BigDecimal("0.05"),
            null));

    ArgumentCaptor<Map<String, Object>> passPayload = ArgumentCaptor.forClass(Map.class);
    verify(repository)
        .insert(eq(7L), eq("resource_check_passed"), passPayload.capture(), eq("backend"), eq(3));
    assertThat(passPayload.getValue()).containsEntry("resourceId", 99L);
    assertThat(passPayload.getValue()).containsEntry("kcCode", "PY43-KC-001");
    assertThat(passPayload.getValue()).containsEntry("evidenceType", "MICRO_QUIZ");

    mirror.onResourceCheckCompleted(
        new LearningEvent.ResourceCheckCompleted(
            7L,
            99L,
            "PY43-KC-001",
            "MICRO_QUIZ",
            false,
            new java.math.BigDecimal("-0.02"),
            "PRACTICE"));

    ArgumentCaptor<Map<String, Object>> failPayload = ArgumentCaptor.forClass(Map.class);
    verify(repository)
        .insert(eq(7L), eq("resource_check_failed"), failPayload.capture(), eq("backend"), eq(4));
    assertThat(failPayload.getValue()).containsEntry("remediationType", "PRACTICE");
  }

  @Test
  void repositoryFailureIsSwallowedToProtectEventChain() {
    doThrow(new RuntimeException("db down"))
        .when(repository)
        .insert(anyLong(), anyString(), anyMap(), anyString(), anyInt());

    // 镜像写表抛异常时不得向上传播，否则会中断事件发布与 mastery/画像回写主链路。
    mirror.onPracticeFailed(new LearningEvent.PracticeFailed(7L, "K", 1L, "WA", "medium"));
    verifyNoInteractions(embeddingWriter);
    verifyNoInteractions(npcMemoryService);
  }
}
