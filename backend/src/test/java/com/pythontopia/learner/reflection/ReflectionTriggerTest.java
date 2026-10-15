package com.pythontopia.learner.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.learner.event.LearningEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 反思触发单测：攒够阈值才触发一次、冷却窗口内不重复触发、按用户独立计数。 */
class ReflectionTriggerTest {

  private static final int THRESHOLD = 8;
  private static final long COOLDOWN_MINUTES = 60;

  private final ReflectionService reflectionService = mock(ReflectionService.class);
  private final SseRelayExecutor executor = mock(SseRelayExecutor.class);
  private ReflectionTrigger trigger;

  @BeforeEach
  void setUp() {
    // 让 executor 同步执行，便于断言 reflect 调用次数。
    doAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return null;
            })
        .when(executor)
        .submit(any(Runnable.class));
    trigger = new ReflectionTrigger(reflectionService, executor, THRESHOLD, COOLDOWN_MINUTES);
  }

  private void fire(long userId) {
    trigger.onLearningEvent(new LearningEvent.PracticeFailed(userId, "PY43-KC-016", 5L, "WA", "medium"));
  }

  @Test
  void doesNotReflectBeforeThreshold() {
    for (int i = 0; i < THRESHOLD - 1; i++) {
      fire(7L);
    }
    verify(reflectionService, never()).reflect(eq(7L));
  }

  @Test
  void reflectsOnceWhenThresholdReached() {
    for (int i = 0; i < THRESHOLD; i++) {
      fire(7L);
    }
    verify(reflectionService, times(1)).reflect(eq(7L));
  }

  @Test
  void doesNotReflectAgainDuringCooldown() {
    for (int i = 0; i < THRESHOLD; i++) {
      fire(7L);
    }
    // 冷却窗口内再攒一批（含达到阈值）也不应再次触发。
    for (int i = 0; i < THRESHOLD; i++) {
      fire(7L);
    }
    verify(reflectionService, times(1)).reflect(eq(7L));
  }

  @Test
  void cooldownLogDoesNotIncludeUserId() {
    long userId = 987654321L;
    Logger logger = (Logger) LoggerFactory.getLogger(ReflectionTrigger.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);
    try {
      for (int i = 0; i < THRESHOLD * 2; i++) {
        fire(userId);
      }
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("reflection cooldown active")
                    .doesNotContain(Long.toString(userId)));
  }

  @Test
  void countsPerUserIndependently() {
    for (int i = 0; i < THRESHOLD; i++) {
      fire(7L);
    }
    for (int i = 0; i < THRESHOLD - 1; i++) {
      fire(9L);
    }
    verify(reflectionService, times(1)).reflect(eq(7L));
    verify(reflectionService, never()).reflect(eq(9L));
  }

  @Test
  void ignoresEventsWithoutUserId() {
    // userId 为空的事件不计数、不触发（防 NPE）。
    trigger.onLearningEvent(new LearningEvent.PracticeFailed(null, "K", 1L, "WA", "medium"));
    verify(reflectionService, never()).reflect(any());
  }
}
