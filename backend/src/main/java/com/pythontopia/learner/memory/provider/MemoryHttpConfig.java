package com.pythontopia.learner.memory.provider;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 外部记忆 provider（Mem0 / Zep）专用 {@link RestTemplate}，沿用既有 {@code RestTemplateBuilder} 基础设施
 * （与 {@code graphRestTemplate} / {@code ragRestTemplate} 物理隔离），按 {@link MemoryProviderProperties}
 * 配置 connect/read 超时，确保外部不可达时阈值内 fail-fast，再由 {@link MemoryProviderRouter} fail-soft 回退 local。
 *
 * <p>独立 bean 名 {@code memoryRestTemplate}，不标 {@code @Primary}，不影响既有按名注入的客户端。
 */
@Configuration
public class MemoryHttpConfig {

  @Bean
  RestTemplate memoryRestTemplate(
      RestTemplateBuilder builder, MemoryProviderProperties properties) {
    return builder
        .setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()))
        .build();
  }
}
