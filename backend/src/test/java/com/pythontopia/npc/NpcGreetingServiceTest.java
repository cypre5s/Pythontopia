package com.pythontopia.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.npc.dto.NpcDialogResponse;
import com.pythontopia.npc.dto.NpcGreetingResponse;
import com.pythontopia.npc.graph.NpcDialogGraphClient;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

class NpcGreetingServiceTest {

  private final MemoryRetrievalService memoryRetrievalService = mock(MemoryRetrievalService.class);
  private final PassiveProfileService passiveProfileService = mock(PassiveProfileService.class);
  private final NpcPersonaService personaService = mock(NpcPersonaService.class);
  private final NpcDialogGraphClient graphClient = mock(NpcDialogGraphClient.class);
  private final TutorDailyRequestLimitService requestLimit = mock(TutorDailyRequestLimitService.class);

  // 可推进的测试时钟：用于验证 30 分钟缓存命中与过期重算。
  private final Instant[] nowHolder = {Instant.parse("2026-06-10T08:00:00Z")};
  private final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return nowHolder[0];
        }
      };

  private final NpcGreetingService service =
      new NpcGreetingService(
          memoryRetrievalService, passiveProfileService, personaService, graphClient, clock, requestLimit);

  private static MemoryItemDto memory(long eventId, String summary, String kc) {
    return new MemoryItemDto(
        eventId, "practice_failed", summary, List.of(kc), "2026-06-09T00:00:00Z", 4, 1.0, 0.8, 0.9, 0.9);
  }

  private void advance(Duration d) {
    nowHolder[0] = nowHolder[0].plus(d);
  }

  @Test
  void personalizedGreetingFromMemoryThenServesFromCache() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "练习失败：PY43-KC-016（WA）", "PY43-KC-016")));
    when(personaService.load("foreman_bug"))
        .thenReturn(new NpcPersona("foreman_bug", "矿洞工头", "core"));
    when(passiveProfileService.getDimensions(7L)).thenReturn(List.of());
    when(graphClient.talk(eq("foreman_bug"), anyString(), any(), anyString()))
        .thenReturn(
            Optional.of(
                new NpcDialogResponse("上次 KC-016 卡了 3 次，今天去矿洞复盘？", List.of(9L), true)));

    NpcGreetingResponse first = service.greeting(7L, "foreman_bug");
    assertThat(first.available()).isTrue();
    assertThat(first.degraded()).isFalse();
    assertThat(first.cached()).isFalse();
    assertThat(first.greeting()).contains("KC-016");
    assertThat(first.grounded()).isTrue();
    assertThat(first.citedEventIds()).containsExactly(9L);

    // 30 分钟内第二次：复用缓存（cached=true），不再调用 graph。
    advance(Duration.ofMinutes(29));
    NpcGreetingResponse second = service.greeting(7L, "foreman_bug");
    assertThat(second.cached()).isTrue();
    assertThat(second.greeting()).isEqualTo(first.greeting());
    verify(graphClient, times(1)).talk(eq("foreman_bug"), anyString(), any(), anyString());
    verify(requestLimit).reserve(7L);
  }

  @Test
  void cacheExpiresAfterTtlAndRecomputes() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "练习失败：PY43-KC-016", "PY43-KC-016")));
    when(personaService.load(anyString())).thenReturn(new NpcPersona("foreman_bug", "工头", "core"));
    when(passiveProfileService.getDimensions(anyLong())).thenReturn(List.of());
    when(graphClient.talk(anyString(), anyString(), any(), anyString()))
        .thenReturn(Optional.of(new NpcDialogResponse("嗨", List.of(9L), true)));

    service.greeting(7L, "foreman_bug");
    advance(NpcGreetingService.CACHE_TTL.plusMinutes(1)); // 过 TTL（当日级）
    NpcGreetingResponse third = service.greeting(7L, "foreman_bug");

    assertThat(third.cached()).isFalse();
    verify(graphClient, times(2)).talk(anyString(), anyString(), any(), anyString());
  }

  @Test
  void removesExpiredEntriesDuringScheduledCacheCleanup() throws ReflectiveOperationException {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "练习失败：PY43-KC-016", "PY43-KC-016")));
    when(personaService.load(anyString())).thenReturn(new NpcPersona("foreman_bug", "工头", "core"));
    when(passiveProfileService.getDimensions(anyLong())).thenReturn(List.of());
    when(graphClient.talk(anyString(), anyString(), any(), anyString()))
        .thenReturn(Optional.of(new NpcDialogResponse("嗨", List.of(9L), true)));
    service.greeting(7L, "foreman_bug");
    advance(NpcGreetingService.CACHE_TTL);

    service.cleanupExpiredCache();

    Field cacheField = NpcGreetingService.class.getDeclaredField("cache");
    cacheField.setAccessible(true);
    assertThat((Map<?, ?>) cacheField.get(service)).isEmpty();
  }

  @Test
  void prewarmForcesRecomputeAndCachesForLaterCacheHit() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "练习失败：PY43-KC-016", "PY43-KC-016")));
    when(personaService.load(anyString())).thenReturn(new NpcPersona("foreman_bug", "工头", "core"));
    when(passiveProfileService.getDimensions(anyLong())).thenReturn(List.of());
    when(graphClient.talk(anyString(), anyString(), any(), anyString()))
        .thenReturn(Optional.of(new NpcDialogResponse("预热寒暄", List.of(9L), true)));

    // 预热（每日预生成）：强制重算并写缓存。
    NpcGreetingResponse warmed = service.prewarm(7L, "foreman_bug");
    assertThat(warmed.available()).isTrue();
    assertThat(warmed.cached()).isFalse();

    // 玩家随后进对话：命中预热缓存，不再调 graph。
    NpcGreetingResponse hit = service.greeting(7L, "foreman_bug");
    assertThat(hit.cached()).isTrue();
    assertThat(hit.greeting()).isEqualTo(warmed.greeting());
    verify(graphClient, times(1)).talk(anyString(), anyString(), any(), anyString());
    verify(requestLimit).reserve(7L);
  }

  @Test
  void exhaustedDailyBudgetPreventsPrewarmModelCall() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "练习失败：PY43-KC-016", "PY43-KC-016")));
    org.mockito.Mockito.doThrow(
            new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TUTOR_DAILY_LIMIT_REACHED", "daily limit"))
        .when(requestLimit)
        .reserve(7L);

    assertThatThrownBy(() -> service.prewarm(7L, "foreman_bug"))
        .isInstanceOf(BusinessException.class);
    verify(graphClient, never()).talk(anyString(), anyString(), any(), anyString());
  }

  @Test
  void exhaustedDailyBudgetPreventsGreetingModelCall() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "练习失败：PY43-KC-016", "PY43-KC-016")));
    org.mockito.Mockito.doThrow(
            new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TUTOR_DAILY_LIMIT_REACHED", "daily limit"))
        .when(requestLimit)
        .reserve(7L);

    assertThatThrownBy(() -> service.greeting(7L, "foreman_bug"))
        .isInstanceOf(BusinessException.class);
    verify(graphClient, never()).talk(anyString(), anyString(), any(), anyString());
  }

  @Test
  void memoryRetrievalFailureFailsFastToDegradedAndIsNotCached() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenThrow(new RuntimeException("memory db down"));

    NpcGreetingResponse first = service.greeting(7L, "foreman_bug");
    assertThat(first.available()).isFalse();
    assertThat(first.degraded()).isTrue();
    assertThat(first.greeting()).isEmpty();

    // 故障不缓存：第二次仍尝试检索（可恢复），graph 始终未被调用。
    service.greeting(7L, "foreman_bug");
    verify(memoryRetrievalService, times(2)).retrieve(eq(7L), isNull(), anyInt());
    verify(graphClient, never()).talk(anyString(), anyString(), any(), anyString());
  }

  @Test
  void memoryRetrievalFailureDoesNotLogStudentMemoryOrUpstreamError() {
    String sentinel = "private-npc-memory-log-sentinel";
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenThrow(new RuntimeException(sentinel));
    Logger logger = (Logger) LoggerFactory.getLogger(NpcGreetingService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      service.greeting(7L, "foreman_bug");
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=RuntimeException"))
        .noneMatch(message -> message.contains(sentinel) || message.contains("user=7"));
  }

  @Test
  void profileFailureDoesNotLogLearnerProfileOrUpstreamError() {
    String sentinel = "private-npc-profile-log-sentinel";
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "练习失败：PY43-KC-016", "PY43-KC-016")));
    when(personaService.load("foreman_bug"))
        .thenReturn(new NpcPersona("foreman_bug", "矿洞工头", "core"));
    when(passiveProfileService.getDimensions(7L)).thenThrow(new RuntimeException(sentinel));
    when(graphClient.talk(eq("foreman_bug"), anyString(), any(), anyString()))
        .thenReturn(Optional.of(new NpcDialogResponse("嗨", List.of(9L), true)));
    Logger logger = (Logger) LoggerFactory.getLogger(NpcGreetingService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      service.greeting(7L, "foreman_bug");
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=RuntimeException"))
        .noneMatch(message -> message.contains(sentinel) || message.contains("user=7"));
  }

  @Test
  void graphUnavailableFailsFastToDegradedAndIsNotCached() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "练习失败：PY43-KC-016", "PY43-KC-016")));
    when(personaService.load(anyString())).thenReturn(new NpcPersona("foreman_bug", "工头", "core"));
    when(passiveProfileService.getDimensions(anyLong())).thenReturn(List.of());
    when(graphClient.talk(anyString(), anyString(), any(), anyString())).thenReturn(Optional.empty());

    NpcGreetingResponse first = service.greeting(7L, "foreman_bug");
    assertThat(first.available()).isFalse();
    assertThat(first.degraded()).isTrue();

    // 下游故障不缓存：第二次重试调用 graph。
    service.greeting(7L, "foreman_bug");
    verify(graphClient, times(2)).talk(anyString(), anyString(), any(), anyString());
  }

  @Test
  void emptyMemoryReturnsNoneAndDoesNotCallGraphAndCaches() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt())).thenReturn(List.of());

    NpcGreetingResponse first = service.greeting(7L, "foreman_bug");
    assertThat(first.available()).isFalse();
    assertThat(first.degraded()).isFalse(); // 非故障：不展示「记忆服务暂不可用」
    assertThat(first.greeting()).isEmpty();

    // 确定态「无记忆」被缓存：第二次直接命中缓存，不再检索、不调 graph。
    NpcGreetingResponse second = service.greeting(7L, "foreman_bug");
    assertThat(second.cached()).isTrue();
    verify(memoryRetrievalService, times(1)).retrieve(eq(7L), isNull(), anyInt());
    verify(graphClient, never()).talk(anyString(), anyString(), any(), anyString());
    verify(requestLimit, never()).reserve(7L);
  }

  @Test
  void prewarmWithoutMemoriesDoesNotReserveBudget() {
    when(memoryRetrievalService.retrieve(eq(7L), isNull(), anyInt())).thenReturn(List.of());

    service.prewarm(7L, "foreman_bug");

    verify(requestLimit, never()).reserve(7L);
    verify(graphClient, never()).talk(anyString(), anyString(), any(), anyString());
  }

  @Test
  void cacheKeyedPerNpcAndStudent() {
    when(memoryRetrievalService.retrieve(anyLong(), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "s", "PY43-KC-016")));
    when(personaService.load(anyString())).thenReturn(new NpcPersona("k", "n", "core"));
    when(passiveProfileService.getDimensions(anyLong())).thenReturn(List.of());
    when(graphClient.talk(anyString(), anyString(), any(), anyString()))
        .thenReturn(Optional.of(new NpcDialogResponse("嗨", List.of(9L), true)));

    service.greeting(7L, "foreman_bug");
    service.greeting(7L, "librarian_doc"); // 不同 NPC：独立缓存键
    service.greeting(8L, "foreman_bug"); // 不同学生：独立缓存键

    verify(graphClient, times(3)).talk(anyString(), anyString(), any(), anyString());
  }

  @Test
  void blankGraphReplyTreatedAsDegraded() {
    when(memoryRetrievalService.retrieve(anyLong(), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "s", "PY43-KC-016")));
    when(personaService.load(anyString())).thenReturn(new NpcPersona("k", "n", "core"));
    when(passiveProfileService.getDimensions(anyLong())).thenReturn(List.of());
    when(graphClient.talk(anyString(), anyString(), any(), anyString()))
        .thenReturn(Optional.of(new NpcDialogResponse("   ", List.of(), false)));

    NpcGreetingResponse resp = service.greeting(7L, "foreman_bug");
    assertThat(resp.available()).isFalse();
    assertThat(resp.degraded()).isTrue();
  }

  @Test
  void personaLoadFailureStillProducesGreeting() {
    when(memoryRetrievalService.retrieve(anyLong(), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "s", "PY43-KC-016")));
    when(personaService.load(anyString())).thenThrow(new IllegalArgumentException("unknown npc"));
    when(passiveProfileService.getDimensions(anyLong())).thenReturn(List.of());
    when(graphClient.talk(anyString(), anyString(), any(), anyString()))
        .thenReturn(Optional.of(new NpcDialogResponse("你好", List.of(9L), true)));

    NpcGreetingResponse resp = service.greeting(7L, "ghost");
    assertThat(resp.available()).isTrue();
    assertThat(resp.greeting()).isEqualTo("你好");
  }

  @Test
  void profileBriefFailureDoesNotBlockGreeting() {
    when(memoryRetrievalService.retrieve(anyLong(), isNull(), anyInt()))
        .thenReturn(List.of(memory(9L, "s", "PY43-KC-016")));
    when(personaService.load(anyString())).thenReturn(new NpcPersona("k", "n", "core"));
    when(passiveProfileService.getDimensions(anyLong())).thenThrow(new RuntimeException("profile down"));
    when(graphClient.talk(anyString(), anyString(), any(), anyString()))
        .thenReturn(Optional.of(new NpcDialogResponse("你好", List.of(9L), true)));

    NpcGreetingResponse resp = service.greeting(7L, "foreman_bug");
    assertThat(resp.available()).isTrue();
  }

  @Test
  void buildProfileBriefIncludesStyleAndErrorTags() {
    List<ProfileDimensionDto> dims =
        List.of(
            new ProfileDimensionDto(
                "cognitive_style", "认知风格", 0.7, 0.6, 5, true, List.of("visual")),
            new ProfileDimensionDto(
                "error_pattern", "易错模式", 0.4, 0.5, 4, false, List.of("indent", "off_by_one", "type")));
    String brief = NpcGreetingService.buildProfileBrief(dims);
    assertThat(brief).contains("认知风格: visual");
    assertThat(brief).contains("易错: indent,off_by_one,type");
  }

  @Test
  void buildProfileBriefOmitsLowConfidenceStyle() {
    List<ProfileDimensionDto> dims =
        List.of(
            new ProfileDimensionDto(
                "cognitive_style", "认知风格", 0.7, 0.1, 1, true, List.of("visual")));
    assertThat(NpcGreetingService.buildProfileBrief(dims)).doesNotContain("认知风格");
  }

  @Test
  void buildGreetingPromptCarriesProfileWhenPresent() {
    assertThat(NpcGreetingService.buildGreetingPrompt("认知风格: visual"))
        .contains("认知风格: visual")
        .contains("1-2 句");
    assertThat(NpcGreetingService.buildGreetingPrompt("")).doesNotContain("画像：");
  }
}
