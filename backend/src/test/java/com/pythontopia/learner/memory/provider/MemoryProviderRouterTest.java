package com.pythontopia.learner.memory.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Router 单测（测试矩阵 ③「外部 500 → 回退 local」+ 选型 + ref 落库）：env 路由、fail-soft 回退、
 * remember 后落 {@code agent_memory_ref}（best-effort）。外部 provider 用 mock 模拟成功/抛错两态。
 */
class MemoryProviderRouterTest {

  private final MemoryRetrievalService retrieval = mock(MemoryRetrievalService.class);
  private final LocalMemoryProvider local = new LocalMemoryProvider(retrieval);
  private final Mem0MemoryProvider mem0 = mock(Mem0MemoryProvider.class);
  private final ZepMemoryProvider zep = mock(ZepMemoryProvider.class);
  private final AgentMemoryRefRepository refs = mock(AgentMemoryRefRepository.class);

  private MemoryProviderRouter router(String provider) {
    when(mem0.name()).thenReturn("mem0");
    when(zep.name()).thenReturn("zep");
    MemoryProviderProperties props =
        new MemoryProviderProperties(provider, "http://m", "k", "http://z", "k", 2000, 4000);
    return new MemoryProviderRouter(local, mem0, zep, props, refs);
  }

  private static RecalledMemory externalMemory() {
    return new RecalledMemory("外部事实", "PY43-KC-001", "fact", Instant.now(), 0.9, "mem0");
  }

  private void stubLocalRecall() {
    when(retrieval.retrieve(eq(7L), isNull(), anyString(), anyInt()))
        .thenReturn(
            List.of(
                new MemoryItemDto(
                    1L,
                    "practice_failed",
                    "本地事实",
                    List.of("PY43-KC-016"),
                    "2026-06-12T00:00:00Z",
                    4,
                    1.0,
                    0.8,
                    1.0,
                    0.9)));
  }

  @Test
  void activeProviderReflectsEnv() {
    assertThat(router("mem0").activeProvider()).isEqualTo("mem0");
    assertThat(router("zep").activeProvider()).isEqualTo("zep");
    assertThat(router("local").activeProvider()).isEqualTo("local");
    // 非法值归一 local。
    assertThat(router("bogus").activeProvider()).isEqualTo("local");
  }

  @Test
  void recallUsesActiveExternalProvider() {
    MemoryProviderRouter router = router("mem0");
    when(mem0.recall(eq(7L), eq("q"), anyInt())).thenReturn(List.of(externalMemory()));

    List<RecalledMemory> recalled = router.recall(7L, "q", 8);

    assertThat(recalled).hasSize(1);
    assertThat(recalled.get(0).source()).isEqualTo("mem0");
    verify(retrieval, never()).retrieve(anyLong(), isNull(), anyString(), anyInt());
  }

  @Test
  void recallFailSoftFallsBackToLocalOnExternalFailure() {
    MemoryProviderRouter router = router("mem0");
    when(mem0.recall(anyLong(), anyString(), anyInt()))
        .thenThrow(new MemoryProviderException("mem0 HTTP 500"));
    stubLocalRecall();

    List<RecalledMemory> recalled = router.recall(7L, "q", 8);

    assertThat(recalled).hasSize(1);
    assertThat(recalled.get(0).source()).isEqualTo("local");
    assertThat(recalled.get(0).text()).isEqualTo("本地事实");
  }

  @Test
  void rememberPersistsExternalRef() {
    MemoryProviderRouter router = router("mem0");
    when(mem0.remember(eq(7L), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new MemoryRef("mem0", "ext-1", "fact"));

    MemoryRef ref = router.remember(7L, MemoryEvent.of("fact", "通过了练习", "PY43-KC-007"));

    assertThat(ref.externalId()).isEqualTo("ext-1");
    verify(refs).record(7L, "mem0", "ext-1", "fact");
  }

  @Test
  void rememberFailSoftFallsBackToLocalAndPersistsLocalRef() {
    MemoryProviderRouter router = router("mem0");
    when(mem0.remember(anyLong(), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new MemoryProviderException("mem0 down"));

    MemoryRef ref = router.remember(7L, MemoryEvent.of("fact", "通过了练习", "PY43-KC-007"));

    assertThat(ref.provider()).isEqualTo("local");
    assertThat(ref.externalId()).isNotBlank();
    verify(refs).record(eq(7L), eq("local"), anyString(), eq("fact"));
  }
}
