package com.pythontopia.learner.memory.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Local provider 单测（测试矩阵 ①「local 召回」）：复用既有 {@link MemoryRetrievalService}（memory_embedding
 * 召回）并映射为 {@link RecalledMemory}；focusKc 恒传 null（brief 召回不绑章节），query 空白降级为 null。
 */
class LocalMemoryProviderTest {

  private final MemoryRetrievalService retrieval = mock(MemoryRetrievalService.class);
  private final LocalMemoryProvider provider = new LocalMemoryProvider(retrieval);

  private static MemoryItemDto item(
      long id, String type, String summary, String kc, String createdAt, double total) {
    return new MemoryItemDto(
        id, type, summary, kc == null ? List.of() : List.of(kc), createdAt, 4, 1.0, 0.8, 1.0, total);
  }

  @Test
  void recallDelegatesToRetrievalAndMaps() {
    when(retrieval.retrieve(eq(7L), isNull(), eq("列表"), eq(5)))
        .thenReturn(
            List.of(
                item(
                    1L,
                    "practice_failed",
                    "练习失败：PY43-KC-016（WA）",
                    "PY43-KC-016",
                    "2026-06-12T00:00:00Z",
                    0.9)));

    List<RecalledMemory> recalled = provider.recall(7L, "列表", 5);

    assertThat(recalled).hasSize(1);
    RecalledMemory memory = recalled.get(0);
    assertThat(memory.text()).isEqualTo("练习失败：PY43-KC-016（WA）");
    assertThat(memory.kcCode()).isEqualTo("PY43-KC-016");
    assertThat(memory.kind()).isEqualTo("practice_failed");
    assertThat(memory.occurredAt()).isEqualTo(Instant.parse("2026-06-12T00:00:00Z"));
    assertThat(memory.score()).isEqualTo(0.9);
    assertThat(memory.source()).isEqualTo("local");
    assertThat(memory.isStruggle()).isTrue();
  }

  @Test
  void blankQueryDelegatesAsNull() {
    when(retrieval.retrieve(eq(7L), isNull(), isNull(), eq(8))).thenReturn(List.of());

    List<RecalledMemory> recalled = provider.recall(7L, "   ", 8);

    assertThat(recalled).isEmpty();
  }

  @Test
  void rememberReturnsLocalRefWithoutTouchingEventLog() {
    MemoryRef ref = provider.remember(7L, MemoryEvent.of("fact", "今天学了 for 循环", "PY43-KC-007"));

    assertThat(ref.provider()).isEqualTo("local");
    assertThat(ref.externalId()).isNotBlank();
    assertThat(ref.kind()).isEqualTo("fact");
  }

  @Test
  void rememberRejectsBlankText() {
    assertThatThrownBy(() -> provider.remember(7L, new MemoryEvent("fact", "  ", null, Instant.now())))
        .isInstanceOf(MemoryProviderException.class);
  }
}
