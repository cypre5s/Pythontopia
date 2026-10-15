package com.pythontopia.learner.reflection;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class ReflectionTaskConfig {

  /** Reflection is best-effort and must not inherit SSE's CallerRuns backpressure. */
  @Bean("learnerReflectionExecutor")
  ThreadPoolTaskExecutor learnerReflectionExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(32);
    executor.setThreadNamePrefix("learner-reflection-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    return executor;
  }
}
