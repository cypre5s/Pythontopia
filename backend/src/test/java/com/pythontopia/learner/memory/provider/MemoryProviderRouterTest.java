package com.pythontopia.learner.memory.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

/** 验证远程 provider 配置被忽略，学习记忆始终走 local。 */
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
  void remoteMemoryProviderConfigurationCannotEnableExternalLearningDataRequests() {
    assertThat(router("mem0").activeProvider()).isEqualTo("local");
    assertThat(router("zep").activeProvider()).isEqualTo("local");
    assertThat(router("local").activeProvider()).isEqualTo("local");
    // 非法值归一 local。
    assertThat(router("bogus").activeProvider()).isEqualTo("local");
  }

  @Test
  void configuredRemoteProviderStillReadsAndWritesOnlyLocalMemory() {
    MemoryProviderRouter router = router("mem0");
    stubLocalRecall();

    List<RecalledMemory> recalled = router.recall(7L, "q", 8);
    MemoryRef ref = router.remember(7L, MemoryEvent.of("fact", "通过了练习", "PY43-KC-007"));

    assertThat(recalled).singleElement().extracting(RecalledMemory::source).isEqualTo("local");
    assertThat(ref.provider()).isEqualTo("local");
    verify(refs).record(eq(7L), eq("local"), anyString(), eq("fact"));
    verify(mem0).name();
    verify(zep).name();
    verifyNoMoreInteractions(mem0, zep);
  }

  @Test
  void referenceAuditFailureDoesNotLogStudentIdOrDatabaseDetails() {
    doThrow(new DataAccessResourceFailureException("private-memory-ref-db-sentinel"))
        .when(refs)
        .record(eq(7L), eq("local"), anyString(), eq("fact"));
    Logger logger = (Logger) LoggerFactory.getLogger(MemoryProviderRouter.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      MemoryRef ref =
          router("local").remember(7L, MemoryEvent.of("fact", "已通过练习", "PY43-KC-007"));

      assertThat(ref.provider()).isEqualTo("local");
      assertThat(appender.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getFormattedMessage())
                    .doesNotContain("7")
                    .doesNotContain("private-memory-ref-db-sentinel");
                assertThat(event.getThrowableProxy()).isNull();
              });
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
