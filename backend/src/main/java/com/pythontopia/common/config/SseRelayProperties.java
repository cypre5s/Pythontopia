package com.pythontopia.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 中继线程池（{@link com.pythontopia.common.sse.SseRelayExecutor}）的有界配置。键：{@code
 * pythontopia.sse.relay.core-pool-size} / {@code max-pool-size} / {@code queue-capacity}。缺省
 * core=4、max=16、queue=200；有界队列 + CallerRunsPolicy 防止突发 SSE 流量打满线程或 OOM。
 */
@ConfigurationProperties(prefix = "pythontopia.sse.relay")
public record SseRelayProperties(Integer corePoolSize, Integer maxPoolSize, Integer queueCapacity) {

  private static final int DEFAULT_CORE_POOL_SIZE = 4;
  private static final int DEFAULT_MAX_POOL_SIZE = 16;
  private static final int DEFAULT_QUEUE_CAPACITY = 200;

  public SseRelayProperties {
    if (corePoolSize == null || corePoolSize <= 0) {
      corePoolSize = DEFAULT_CORE_POOL_SIZE;
    }
    if (maxPoolSize == null || maxPoolSize <= 0) {
      maxPoolSize = DEFAULT_MAX_POOL_SIZE;
    }
    if (maxPoolSize < corePoolSize) {
      maxPoolSize = corePoolSize;
    }
    if (queueCapacity == null || queueCapacity <= 0) {
      queueCapacity = DEFAULT_QUEUE_CAPACITY;
    }
  }

  /** 提供给非 Spring 构造路径（如默认无参构造）的缺省配置。 */
  public static SseRelayProperties defaults() {
    return new SseRelayProperties(null, null, null);
  }
}
