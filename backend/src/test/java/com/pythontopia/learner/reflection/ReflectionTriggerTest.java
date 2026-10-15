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
import com.pythontopia.learner.event.LearningEvent;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 反思触发单测：攒够阈值才触发一次、冷却窗口内不重复触发、按用户独立计数。 */
class ReflectionTriggerTest {

  private static final int THRESHOLD = 8;
  private static final long COOLDOWN_MINUTES = 60;

  private final ReflectionService reflectionService = mock(ReflectionService.class);
  private final TaskExecutor executor = new SyncTaskExecutor();
  private ReflectionTrigger trigger;

  @BeforeEach
  void setUp() {
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

  @Test
  void discardsIdleUserProgressDuringScheduledCounterCleanup() throws ReflectiveOperationException {
    for (int i = 0; i < THRESHOLD - 1; i++) {
      fire(7L);
    }
    ageCounter(7L);
    trigger.cleanupIdleCounters();
    fire(7L);

    verify(reflectionService, never()).reflect(eq(7L));
  }

  @Test
  void doesNotRunReflectionOnLearningEventThreadWhenDedicatedExecutorIsSaturated()
      throws InterruptedException {
    ThreadPoolTaskExecutor saturatedExecutor = new ThreadPoolTaskExecutor();
    saturatedExecutor.setCorePoolSize(1);
    saturatedExecutor.setMaxPoolSize(1);
    saturatedExecutor.setQueueCapacity(1);
    saturatedExecutor.initialize();
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch workerStarted = new CountDownLatch(1);
    CountDownLatch eventReturned = new CountDownLatch(1);
    AtomicBoolean reflectionRan = new AtomicBoolean();
    try {
      saturatedExecutor.execute(
          () -> {
            workerStarted.countDown();
            await(release);
          });
      assertThat(workerStarted.await(2, TimeUnit.SECONDS)).isTrue();
      saturatedExecutor.execute(() -> await(release));
      doAnswer(
              invocation -> {
                reflectionRan.set(true);
                await(release);
                return null;
              })
          .when(reflectionService)
          .reflect(7L);

      ReflectionTrigger saturatedTrigger =
          new ReflectionTrigger(reflectionService, saturatedExecutor, 1, COOLDOWN_MINUTES);
      Thread publisher =
          new Thread(
              () -> {
                saturatedTrigger.onLearningEvent(
                    new LearningEvent.PracticeFailed(7L, "PY43-KC-016", 5L, "WA", "medium"));
                eventReturned.countDown();
              });
      publisher.start();

      assertThat(eventReturned.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(reflectionRan.get()).isFalse();
      publisher.join(2_000);
      assertThat(publisher.isAlive()).isFalse();
    } finally {
      release.countDown();
      saturatedExecutor.shutdown();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void ageCounter(long userId) throws ReflectiveOperationException {
    Field countersField = ReflectionTrigger.class.getDeclaredField("counters");
    countersField.setAccessible(true);
    Object counter = ((Map<?, ?>) countersField.get(trigger)).get(userId);
    Field lastActivityField = counter.getClass().getDeclaredField("lastActivityAt");
    lastActivityField.setAccessible(true);
    lastActivityField.set(counter, Instant.EPOCH);
  }
}
