package com.pythontopia.common.sse;

import com.pythontopia.common.config.SseRelayProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SSE 中继任务线程池：改为有界 {@link ThreadPoolExecutor}（core/max/queue 可配，默认 4/16/200），
 * 满载时用 {@code CallerRunsPolicy} 回压调用线程，避免无上界线程池在突发流量下打满线程或 OOM。
 * 线程命名且为 daemon，保留 {@link PreDestroy} 优雅关闭。
 */
@Component
public class SseRelayExecutor {

  private static final long KEEP_ALIVE_SECONDS = 60L;

  private final ThreadPoolExecutor executor;

  @Autowired
  public SseRelayExecutor(SseRelayProperties properties) {
    this.executor =
        new ThreadPoolExecutor(
            properties.corePoolSize(),
            properties.maxPoolSize(),
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(properties.queueCapacity()),
            namedDaemonThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy());
  }

  /** 缺省配置构造（供测试等非 Spring 路径使用）。 */
  public SseRelayExecutor() {
    this(SseRelayProperties.defaults());
  }

  public void submit(Runnable task) {
    executor.submit(task);
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private static ThreadFactory namedDaemonThreadFactory() {
    AtomicInteger counter = new AtomicInteger(1);
    return runnable -> {
      Thread thread = new Thread(runnable, "sse-relay-" + counter.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    };
  }
}
