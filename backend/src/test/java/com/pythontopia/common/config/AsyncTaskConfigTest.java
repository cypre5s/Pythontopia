package com.pythontopia.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncTaskConfigTest {

  @Test
  void defaultAsyncExecutorHasBoundedWorkersAndQueue() {
    ThreadPoolTaskExecutor executor = new AsyncTaskConfig().taskExecutor();
    executor.initialize();
    try {
      assertThat(executor.getCorePoolSize()).isEqualTo(2);
      assertThat(executor.getMaxPoolSize()).isEqualTo(2);
      assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(16);
    } finally {
      executor.shutdown();
    }
  }
}
