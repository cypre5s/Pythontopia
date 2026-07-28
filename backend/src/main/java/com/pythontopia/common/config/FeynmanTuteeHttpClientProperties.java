package com.pythontopia.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 费曼 AI 学弟教学回合的专用读取超时。该端点需要一次真实 LLM 理解、误区更新与可判题代码生成，
 * 因此独立于普通 Graph 30 秒窗口；连接失败仍沿用普通 Graph 的快速失败阈值。
 */
@ConfigurationProperties(prefix = "pythontopia.http.feynman-tutee")
public record FeynmanTuteeHttpClientProperties(Integer readTimeoutMs) {

  private static final int DEFAULT_READ_TIMEOUT_MS = 90000;

  public FeynmanTuteeHttpClientProperties {
    if (readTimeoutMs == null || readTimeoutMs <= 0) {
      readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
    }
  }
}
