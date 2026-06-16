package com.pythontopia.media;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 媒体异步合成作业（Track B）的线程池装配。worker 线程数由 {@link MediaComposeProperties#poolSize()}
 * 控制；讯飞路数 Semaphore 由 {@code MediaJobService} 按配置自建（与线程池解耦，便于单测注入同步执行器）。
 */
@Configuration
public class MediaComposeConfig {

  /** 合成 worker 专用有界线程池：core=max=poolSize，超出排队，shutdown 时等待在合成的作业收尾。 */
  @Bean(name = "mediaComposeExecutor")
  ThreadPoolTaskExecutor mediaComposeExecutor(MediaComposeProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.poolSize());
    executor.setMaxPoolSize(properties.poolSize());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setThreadNamePrefix("media-compose-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(20);
    executor.initialize();
    return executor;
  }
}
