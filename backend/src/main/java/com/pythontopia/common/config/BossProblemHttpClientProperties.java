package com.pythontopia.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Boss / endless 现场出题需要真实 LLM + 审查，读超时应长于普通 graph 调用；连接失败仍沿用 graph
 * 客户端的快速失败阈值。
 */
@ConfigurationProperties(prefix = "pythontopia.http.boss-problem")
public record BossProblemHttpClientProperties(Integer readTimeoutMs) {

  private static final int DEFAULT_READ_TIMEOUT_MS = 180000;

  public BossProblemHttpClientProperties {
    if (readTimeoutMs == null || readTimeoutMs <= 0) {
      readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
    }
  }
}
