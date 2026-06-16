package com.pythontopia.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 章节试卷生成 HTTP 客户端超时配置。大体量试卷需要多轮模型生成，因此读超时独立于普通 Graph 调用。
 */
@ConfigurationProperties(prefix = "pythontopia.http.chapter-test")
public record ChapterTestHttpClientProperties(Integer readTimeoutMs) {

  private static final int DEFAULT_READ_TIMEOUT_MS = 600000;

  public ChapterTestHttpClientProperties {
    if (readTimeoutMs == null || readTimeoutMs <= 0) {
      readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
    }
  }
}
