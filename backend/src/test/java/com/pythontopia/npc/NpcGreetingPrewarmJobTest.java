package com.pythontopia.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.learner.event.LearningEventLogRepository;
import com.pythontopia.npc.dto.NpcGreetingResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class NpcGreetingPrewarmJobTest {

  private final LearningEventLogRepository eventRepository =
      mock(LearningEventLogRepository.class);
  private final NpcGreetingService greetingService = mock(NpcGreetingService.class);

  private final ApplicationContextRunner defaultConfigRunner =
      new ApplicationContextRunner().withUserConfiguration(DefaultConfig.class);

  private NpcGreetingPrewarmJob job(boolean enabled) {
    return new NpcGreetingPrewarmJob(eventRepository, greetingService, enabled, 7, 200);
  }

  @Test
  void prewarmsEachActiveUserAcrossAllCoreNpcs() {
    when(eventRepository.findActiveUserIds(any(), anyInt())).thenReturn(List.of(1L, 2L));
    when(greetingService.prewarm(anyLong(), anyString())).thenReturn(NpcGreetingResponse.none());

    job(true).prewarmDaily();

    // 2 个活跃学生 × 核心 NPC 全量预热。
    verify(greetingService, times(2 * NpcGreetingPrewarmJob.CORE_NPCS.size()))
        .prewarm(anyLong(), anyString());
  }

  @Test
  void disabledSkipsAllWork() {
    job(false).prewarmDaily();
    verifyNoInteractions(eventRepository, greetingService);
  }

  @Test
  void prewarmingIsDisabledByDefault() {
    defaultConfigRunner.run(
        context -> {
          LearningEventLogRepository eventRepo = context.getBean(LearningEventLogRepository.class);
          when(eventRepo.findActiveUserIds(any(), anyInt())).thenReturn(List.of());

          context.getBean(NpcGreetingPrewarmJob.class).prewarmDaily();

          verifyNoInteractions(eventRepo, context.getBean(NpcGreetingService.class));
        });
  }

  @Test
  void singleFailureDoesNotAbortRemaining() {
    when(eventRepository.findActiveUserIds(any(), anyInt())).thenReturn(List.of(1L));
    // 首个 NPC 预热抛异常，其余仍应继续。
    when(greetingService.prewarm(eq(1L), anyString()))
        .thenThrow(new RuntimeException("boom"))
        .thenReturn(NpcGreetingResponse.none());

    job(true).prewarmDaily(); // 不抛

    verify(greetingService, times(NpcGreetingPrewarmJob.CORE_NPCS.size()))
        .prewarm(eq(1L), anyString());
  }

  @Test
  void listActiveUsersFailureIsSwallowed() {
    when(eventRepository.findActiveUserIds(any(), anyInt()))
        .thenThrow(new RuntimeException("db down"));

    job(true).prewarmDaily(); // 不抛

    verify(greetingService, times(0)).prewarm(anyLong(), anyString());
  }

  @Test
  void listActiveUsersFailureDoesNotLogExceptionMessage() {
    String sentinel = "private-npc-prewarm-list-sentinel";
    when(eventRepository.findActiveUserIds(any(), anyInt()))
        .thenThrow(new RuntimeException(sentinel));
    Logger logger = (Logger) LoggerFactory.getLogger(NpcGreetingPrewarmJob.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      job(true).prewarmDaily();
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=RuntimeException"))
        .noneMatch(message -> message.contains(sentinel));
  }

  @Test
  void perUserPrewarmFailureDoesNotLogUserOrExceptionMessage() {
    String sentinel = "private-npc-prewarm-user-sentinel";
    when(eventRepository.findActiveUserIds(any(), anyInt())).thenReturn(List.of(987654L));
    when(greetingService.prewarm(anyLong(), anyString())).thenThrow(new RuntimeException(sentinel));
    Logger logger = (Logger) LoggerFactory.getLogger(NpcGreetingPrewarmJob.class);
    Level previousLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      job(true).prewarmDaily();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=RuntimeException"))
        .noneMatch(message -> message.contains(sentinel) || message.contains("987654"));
  }

  @Configuration(proxyBeanMethods = false)
  @Import(NpcGreetingPrewarmJob.class)
  static class DefaultConfig {

    @Bean
    LearningEventLogRepository eventRepository() {
      return mock(LearningEventLogRepository.class);
    }

    @Bean
    NpcGreetingService greetingService() {
      return mock(NpcGreetingService.class);
    }
  }
}
