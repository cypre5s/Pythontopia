package com.pythontopia.learner.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.event.LearningEventLogRepository;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 记忆检索服务单测：打分排序、limit clamp、DTO 分数分解、中文摘要、同章相关性解析。 */
class MemoryRetrievalServiceTest {

  private static final Instant NOW = Instant.now();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final LearningEventLogRepository eventRepo = mock(LearningEventLogRepository.class);
  private final CoursePackRepository coursePackRepository = mock(CoursePackRepository.class);
  private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
  private final MemoryEmbeddingRepository memoryEmbeddingRepository =
      mock(MemoryEmbeddingRepository.class);
  private final MemoryRetrievalService service =
      new MemoryRetrievalService(
          eventRepo, coursePackRepository, embeddingClient, memoryEmbeddingRepository);

  private static MemoryRow row(
      long id, String type, List<String> kcCodes, int importance, Instant createdAt) {
    return new MemoryRow(id, type, kcCodes, importance, createdAt, null);
  }

  private static CourseKc kc(String code, long chapterId) {
    return new CourseKc(1L, 43L, code, code, chapterId, 1, "basic", "");
  }

  @Test
  void retrievesTopKSortedByTotalDescending() {
    when(eventRepo.findRecentForMemory(eq(7L), anyInt()))
        .thenReturn(
            List.of(
                row(1, "resource_consumed", List.of("PY43-KC-001"), 1, NOW),
                row(
                    2,
                    "practice_failed",
                    List.of("PY43-KC-016"),
                    4,
                    NOW.minus(10, ChronoUnit.DAYS)),
                row(3, "comeback_solved", List.of("PY43-KC-016"), 5, NOW)));
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(List.of(kc("PY43-KC-016", 200L)));

    List<MemoryItemDto> items = service.retrieve(7L, "PY43-KC-016", 8);

    assertThat(items).hasSize(3);
    // comeback_solved（新+重要+命中）应排第一，resource_consumed（旧式无关低）垫底。
    assertThat(items.get(0).eventId()).isEqualTo(3);
    assertThat(items.get(0).totalScore())
        .isGreaterThanOrEqualTo(items.get(1).totalScore());
    assertThat(items.get(1).totalScore())
        .isGreaterThanOrEqualTo(items.get(2).totalScore());
  }

  @Test
  void dtoCarriesScoreBreakdownAndChineseSummary() {
    when(eventRepo.findRecentForMemory(eq(7L), anyInt()))
        .thenReturn(List.of(row(2, "practice_failed", List.of("PY43-KC-016"), 4, NOW)));
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(List.of(kc("PY43-KC-016", 200L)));

    MemoryItemDto dto = service.retrieve(7L, "PY43-KC-016", 8).get(0);

    assertThat(dto.eventId()).isEqualTo(2);
    assertThat(dto.eventType()).isEqualTo("practice_failed");
    assertThat(dto.importance()).isEqualTo(4);
    assertThat(dto.kcCodes()).containsExactly("PY43-KC-016");
    assertThat(dto.relevanceScore()).isEqualTo(1.0);
    assertThat(dto.recencyScore()).isGreaterThan(0.0);
    assertThat(dto.importanceScore()).isEqualTo(0.8);
    assertThat(dto.totalScore()).isGreaterThan(0.0);
    assertThat(dto.summaryZh()).contains("PY43-KC-016");
  }

  @Test
  void summaryReflectsFailureKindFromPayload() {
    com.fasterxml.jackson.databind.node.ObjectNode payload = MAPPER.createObjectNode();
    payload.put("kcCode", "PY43-KC-016");
    payload.put("failureKind", "WA");
    MemoryRow withPayload =
        new MemoryRow(9, "practice_failed", List.of("PY43-KC-016"), 4, NOW, payload);
    when(eventRepo.findRecentForMemory(eq(7L), anyInt())).thenReturn(List.of(withPayload));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());

    MemoryItemDto dto = service.retrieve(7L, null, 8).get(0);

    assertThat(dto.summaryZh()).contains("WA");
  }

  @Test
  void limitClampedToOneToThirty() {
    List<MemoryRow> many =
        java.util.stream.IntStream.rangeClosed(1, 40)
            .mapToObj(
                i ->
                    row(
                        i,
                        "resource_consumed",
                        List.of("PY43-KC-001"),
                        1,
                        NOW.minus(i, ChronoUnit.HOURS)))
            .toList();
    when(eventRepo.findRecentForMemory(eq(7L), anyInt())).thenReturn(many);
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());

    assertThat(service.retrieve(7L, null, 999)).hasSize(30); // 上限 30
    assertThat(service.retrieve(7L, null, 0)).hasSize(1); // 下限 1
    assertThat(service.retrieve(7L, null, -5)).hasSize(1);
  }

  @Test
  void sameChapterMemoryRanksAboveUnrelatedWhenFocused() {
    when(eventRepo.findRecentForMemory(eq(7L), anyInt()))
        .thenReturn(
            List.of(
                row(1, "practice_failed", List.of("PY43-KC-099"), 3, NOW), // 无关
                row(2, "practice_failed", List.of("PY43-KC-015"), 3, NOW))); // 同章
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenReturn(
            List.of(
                kc("PY43-KC-015", 200L),
                kc("PY43-KC-016", 200L),
                kc("PY43-KC-099", 999L)));

    List<MemoryItemDto> items = service.retrieve(7L, "PY43-KC-016", 8);

    // 同章 KC-015 relevance=0.5 高于无关 KC-099 relevance=0。
    assertThat(items.get(0).eventId()).isEqualTo(2);
    assertThat(items.get(0).relevanceScore()).isEqualTo(0.5);
    assertThat(items.get(1).relevanceScore()).isEqualTo(0.0);
  }

  @Test
  void emptyStreamReturnsEmptyList() {
    when(eventRepo.findRecentForMemory(eq(7L), anyInt())).thenReturn(List.of());
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());

    assertThat(service.retrieve(7L, "PY43-KC-016", 8)).isEmpty();
  }

  @Test
  void coursePackFailureDegradesToNoChapterRelevance() {
    when(eventRepo.findRecentForMemory(eq(7L), anyInt()))
        .thenReturn(List.of(row(1, "practice_failed", List.of("PY43-KC-015"), 3, NOW)));
    when(coursePackRepository.findKcs(eq(43L), eq(null)))
        .thenThrow(new RuntimeException("course pack unavailable"));

    // 课程包读失败不应让检索崩溃，只是失去同章相关性。
    List<MemoryItemDto> items = service.retrieve(7L, "PY43-KC-016", 8);
    assertThat(items).hasSize(1);
    assertThat(items.get(0).relevanceScore()).isEqualTo(0.0);
  }

  @Test
  void queryTextBlendsSemanticSimilarityIntoRelevance() {
    // KC 不命中但语义相近：借 queryText 余弦召回，relevance 提升。
    when(eventRepo.findRecentForMemory(eq(7L), anyInt()))
        .thenReturn(
            List.of(
                row(10, "practice_failed", List.of("PY43-KC-099"), 3, NOW),
                row(11, "practice_failed", List.of("PY43-KC-001"), 3, NOW)));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(embeddingClient.embed("列表别名")).thenReturn(Optional.of(new float[] {1f, 0f}));
    when(memoryEmbeddingRepository.searchByCosine(eq(7L), any(), anyInt()))
        .thenReturn(List.of(new MemoryEmbeddingRepository.ScoredEvent(10L, 0.9)));

    List<MemoryItemDto> items = service.retrieve(7L, "PY43-KC-016", "列表别名", 8);

    MemoryItemDto event10 =
        items.stream().filter(item -> item.eventId() == 10L).findFirst().orElseThrow();
    assertThat(event10.relevanceScore()).isEqualTo(0.9);
  }

  @Test
  void queryTextWithoutVectorFallsBackToKcMatch() {
    // embedding 离线（embed 返回空）→ 不触达向量库，退化为纯 KC 匹配（零回归）。
    when(eventRepo.findRecentForMemory(eq(7L), anyInt()))
        .thenReturn(List.of(row(10, "practice_failed", List.of("PY43-KC-099"), 3, NOW)));
    when(coursePackRepository.findKcs(eq(43L), eq(null))).thenReturn(List.of());
    when(embeddingClient.embed(any())).thenReturn(Optional.empty());

    List<MemoryItemDto> items = service.retrieve(7L, "PY43-KC-016", "任意查询", 8);

    assertThat(items.get(0).relevanceScore()).isEqualTo(0.0);
    verifyNoInteractions(memoryEmbeddingRepository);
  }
}
