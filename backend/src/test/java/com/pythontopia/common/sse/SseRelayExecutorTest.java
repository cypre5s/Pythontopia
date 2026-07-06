package com.pythontopia.common.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.pythontopia.common.config.SseRelayProperties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SseRelayExecutorTest {

  /**
   * 池满 + 队列满时，{@code CallerRunsPolicy} 必须在调用线程同步执行被拒任务：不抛未捕获异常、不
   * 无限新增线程，从而在并发压测下既不 OOM 也不打满线程。
   */
  @Test
  void boundedPoolFallsBackToCallerRunsWithoutThrowing() throws InterruptedException {
    SseRelayExecutor relay = new SseRelayExecutor(new SseRelayProperties(1, 1, 1));
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch firstTaskRunning = new CountDownLatch(1);
    try {
      // 任务1：占满唯一的工作线程。
      relay.submit(
          () -> {
            firstTaskRunning.countDown();
            await(release);
          });
      assertThat(firstTaskRunning.await(2, TimeUnit.SECONDS)).isTrue();

      // 任务2：填满容量为 1 的有界队列。
      relay.submit(() -> await(release));

      // 任务3：池满 + 队列满 → 触发 CallerRunsPolicy，在调用线程同步执行，不抛异常。
      String callerThread = Thread.currentThread().getName();
      AtomicReference<String> ranOnThread = new AtomicReference<>();
      assertThatCode(
              () -> relay.submit(() -> ranOnThread.set(Thread.currentThread().getName())))
          .doesNotThrowAnyException();
      assertThat(ranOnThread.get()).isEqualTo(callerThread);
    } finally {
      release.countDown();
      relay.shutdown();
    }
  }

  @Test
  void submitRunsOnNamedDaemonRelayThread() throws InterruptedException {
    SseRelayExecutor relay = new SseRelayExecutor(new SseRelayProperties(2, 4, 16));
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<String> threadName = new AtomicReference<>();
    AtomicBoolean daemon = new AtomicBoolean(false);
    try {
      relay.submit(
          () -> {
            threadName.set(Thread.currentThread().getName());
            daemon.set(Thread.currentThread().isDaemon());
            done.countDown();
          });
      assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(threadName.get()).startsWith("sse-relay-");
      assertThat(daemon.get()).isTrue();
    } finally {
      relay.shutdown();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
