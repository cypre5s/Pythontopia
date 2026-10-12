package com.pythontopia.submission.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.common.error.BusinessException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class JudgeWorkerPoolTest {

  @Test
  void rejectsWhenWorkersAndQueueAreSaturated() throws Exception {
    JudgeWorkerPool pool = new JudgeWorkerPool(1, 1, 1);
    var requestThreads = Executors.newFixedThreadPool(2);
    CountDownLatch workerStarted = new CountDownLatch(1);
    CountDownLatch releaseWorker = new CountDownLatch(1);
    try {
      var first =
          requestThreads.submit(
              () ->
                  pool.submitAndWait(
                      () -> {
                        workerStarted.countDown();
                        releaseWorker.await();
                        return 1;
                      }));
      assertThat(workerStarted.await(2, TimeUnit.SECONDS)).isTrue();

      var queued = requestThreads.submit(() -> pool.submitAndWait(() -> 2));
      var executor =
          (java.util.concurrent.ThreadPoolExecutor)
              ReflectionTestUtils.getField(pool, "executor");
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (executor.getQueue().size() != 1 && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertThat(executor.getQueue()).hasSize(1);

      assertThatThrownBy(() -> pool.submitAndWait(() -> 3))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              exception ->
                  assertThat(((BusinessException) exception).status())
                      .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

      releaseWorker.countDown();
      assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(1);
      assertThat(queued.get(2, TimeUnit.SECONDS)).isEqualTo(2);
    } finally {
      releaseWorker.countDown();
      requestThreads.shutdownNow();
      pool.shutdown();
    }
  }
}
