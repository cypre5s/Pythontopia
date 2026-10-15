package com.pythontopia.learner.reflection;

import com.pythontopia.learner.event.LearningEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 反思触发器：监听进程内 {@link LearningEvent}，按用户累计新事件数；攒够阈值且过冷却窗口时，
 * 提交一次反思到线程池（不阻塞事件发布线程）。
 *
 * <p>成本红线：反思是唯一新增的周期性 LLM 调用，靠「N 事件阈值 + 冷却」限频；阈值/冷却可配
 * （{@code pythontopia.reflection.threshold/cooldownMinutes}）。
 *
 * <p>计数为进程内状态（{@link ConcurrentHashMap}），依赖项目「单 worker」约定；多 worker 部署需迁到
 * Redis/DB（此处刻意不做，符合 AGENTS.md 简单优先）。
 */
@Component
public class ReflectionTrigger {

  private static final long MIN_COUNTER_IDLE_MINUTES = 24 * 60L;

  private static final Logger log = LoggerFactory.getLogger(ReflectionTrigger.class);

  private final ReflectionService reflectionService;
  private final TaskExecutor executor;
  private final int threshold;
  private final long cooldownMinutes;
  private final long counterIdleMinutes;
  private final ConcurrentHashMap<Long, Counter> counters = new ConcurrentHashMap<>();

  public ReflectionTrigger(
      ReflectionService reflectionService,
      @Qualifier("learnerReflectionExecutor") TaskExecutor executor,
      @Value("${pythontopia.reflection.threshold:8}") int threshold,
      @Value("${pythontopia.reflection.cooldownMinutes:60}") long cooldownMinutes) {
    this.reflectionService = reflectionService;
    this.executor = executor;
    this.threshold = threshold;
    this.cooldownMinutes = cooldownMinutes;
    this.counterIdleMinutes = Math.max(MIN_COUNTER_IDLE_MINUTES, cooldownMinutes);
  }

  @EventListener
  public void onLearningEvent(LearningEvent event) {
    Long userId = event.userId();
    if (userId == null) {
      return;
    }
    if (shouldReflect(userId)) {
      try {
        executor.execute(() -> reflectionService.reflect(userId));
      } catch (TaskRejectedException exception) {
        log.warn("reflection skipped because background queue is full");
      }
    }
  }

  /** 计数 + 阈值 + 冷却判定；命中返回 true 并重置计数。 */
  private boolean shouldReflect(Long userId) {
    AtomicBoolean shouldReflect = new AtomicBoolean();
    Instant now = Instant.now();
    counters.compute(
        userId,
        (key, existing) -> {
          Counter counter = existing == null ? new Counter() : existing;
          counter.lastActivityAt = now;
          counter.sinceLast++;
          if (counter.sinceLast < threshold) {
            return counter;
          }
          // 达到阈值：消费这一批计数。冷却内则不触发，避免周期性 LLM 调用过密。
          counter.sinceLast = 0;
          if (counter.lastReflectedAt != null
              && Duration.between(counter.lastReflectedAt, now).toMinutes() < cooldownMinutes) {
            log.debug("reflection cooldown active");
            return counter;
          }
          counter.lastReflectedAt = now;
          shouldReflect.set(true);
          return counter;
        });
    return shouldReflect.get();
  }

  @Scheduled(fixedDelay = 1_800_000L, initialDelay = 1_800_000L)
  void cleanupIdleCounters() {
    Instant now = Instant.now();
    counters
        .keySet()
        .forEach(
            userId ->
                counters.computeIfPresent(
                    userId,
                    (key, current) ->
                        Duration.between(current.lastActivityAt, now).toMinutes()
                                >= counterIdleMinutes
                            ? null
                            : current));
  }

  /** 单用户的进程内计数：自上次反思以来的新事件数 + 最近活动和上次反思时间。 */
  private static final class Counter {
    private int sinceLast;
    private Instant lastActivityAt;
    private Instant lastReflectedAt;
  }
}
