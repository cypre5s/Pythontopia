package com.pythontopia.learner.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.reflection.graph.InsightPayload;
import com.pythontopia.learner.reflection.graph.ReflectionGraphClient;
import com.pythontopia.learner.reflection.graph.ReflectionGraphResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 反思编排单测：graph 有洞察回写+落库、graph 故障规则兜底不抛、graph 空不回写、空记忆早退。 */
class ReflectionServiceTest {

  private final MemoryRetrievalService memoryRetrievalService =
      mock(MemoryRetrievalService.class);
  private final ReflectionGraphClient graphClient = mock(ReflectionGraphClient.class);
  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final LearnerInsightRepository insightRepository = mock(LearnerInsightRepository.class);

  private final ReflectionService service =
      new ReflectionService(
          memoryRetrievalService, graphClient, passiveProfileService, insightRepository);

  private static MemoryItemDto memory(long id, String type, String kc) {
    return new MemoryItemDto(
        id, type, type + "：" + kc, List.of(kc), "2026-06-08T00:00:00Z", 4, 0.9, 0.8, 1.0, 0.86);
  }

  @Test
  void graphInsightWritesBackProfileAndPersistsWithGraphSource() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(10, "practice_failed", "PY43-KC-016")));
    when(graphClient.reflect(eq(7L), anyList()))
        .thenReturn(
            new ReflectionGraphResponse(
                List.of(
                    new InsightPayload(
                        "列表别名可变性反复出错",
                        "error_pattern",
                        List.of("mutable_alias"),
                        List.of(10L),
                        0.8))));

    service.reflect(7L);

    ArgumentCaptor<DimensionDelta> deltaCaptor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("error_pattern"), deltaCaptor.capture());
    DimensionDelta delta = deltaCaptor.getValue();
    assertThat(delta.source()).isEqualTo("REFLECTION");
    assertThat(delta.tagsAdd()).contains("mutable_alias");

    verify(insightRepository)
        .insert(
            eq(7L),
            eq("列表别名可变性反复出错"),
            eq("error_pattern"),
            eq(List.of("mutable_alias")),
            eq(List.of(10L)),
            eq(0.8),
            eq("GRAPH"));
  }

  @Test
  void graphFailureFallsBackToRuleWithoutThrowing() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(
            List.of(
                memory(10, "practice_failed", "PY43-KC-016"),
                memory(11, "practice_failed", "PY43-KC-016"),
                memory(12, "practice_succeeded", "PY43-KC-001")));
    when(graphClient.reflect(eq(7L), anyList()))
        .thenThrow(new RuntimeException("graph down"));

    assertThatCode(() -> service.reflect(7L)).doesNotThrowAnyException();

    ArgumentCaptor<DimensionDelta> deltaCaptor = ArgumentCaptor.forClass(DimensionDelta.class);
    verify(passiveProfileService)
        .applyDimensionUpdate(eq(7L), eq("error_pattern"), deltaCaptor.capture());
    assertThat(deltaCaptor.getValue().source()).isEqualTo("REFLECTION");

    ArgumentCaptor<List<String>> tagsCaptor = captorOfList();
    ArgumentCaptor<List<Long>> evidenceCaptor = captorOfList();
    verify(insightRepository)
        .insert(
            eq(7L),
            any(),
            eq("error_pattern"),
            tagsCaptor.capture(),
            evidenceCaptor.capture(),
            anyDoubleArg(),
            eq("RULE"));
    // 兜底取最高频失败 KC（PY43-KC-016 出现 2 次）作为 error_pattern tag。
    assertThat(tagsCaptor.getValue()).containsExactly("PY43-KC-016");
    // evidence 必须引用真实失败事件 id（10、11），不含成功事件 12。
    assertThat(evidenceCaptor.getValue()).containsExactlyInAnyOrder(10L, 11L);
  }

  @Test
  void graphEmptyInsightsDoesNotWriteBackOrPersist() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(10, "practice_failed", "PY43-KC-016")));
    when(graphClient.reflect(eq(7L), anyList()))
        .thenReturn(new ReflectionGraphResponse(List.of()));

    service.reflect(7L);

    verifyNoInteractions(passiveProfileService);
    verify(insightRepository, never())
        .insert(anyLong(), any(), any(), anyList(), anyList(), anyDoubleArg(), any());
  }

  @Test
  void emptyMemoriesReturnsEarlyWithoutCallingGraph() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt())).thenReturn(List.of());

    service.reflect(7L);

    verifyNoInteractions(graphClient);
    verifyNoInteractions(passiveProfileService);
    verifyNoInteractions(insightRepository);
  }

  @Test
  void ruleFallbackProducesNothingWhenNoFailureMemories() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(12, "practice_succeeded", "PY43-KC-001")));
    when(graphClient.reflect(eq(7L), anyList()))
        .thenThrow(new RuntimeException("graph down"));

    assertThatCode(() -> service.reflect(7L)).doesNotThrowAnyException();

    verifyNoInteractions(passiveProfileService);
    verify(insightRepository, never())
        .insert(anyLong(), any(), any(), anyList(), anyList(), anyDoubleArg(), any());
  }

  @SuppressWarnings("unchecked")
  private static <T> ArgumentCaptor<List<T>> captorOfList() {
    return ArgumentCaptor.forClass(List.class);
  }

  private static double anyDoubleArg() {
    return org.mockito.ArgumentMatchers.anyDouble();
  }
}
