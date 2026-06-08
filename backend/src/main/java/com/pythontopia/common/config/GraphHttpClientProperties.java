package com.pythontopia.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * graph/rag 阻塞 HTTP 客户端（{@code graphRestTemplate}）的超时配置。键：{@code
 * pythontopia.http.graph.connect-timeout-ms} / {@code read-timeout-ms}。缺省 connect=3000ms、
 * read=30000ms；缺失或非正值一律回落默认，避免下游不可达时无限挂起占满 Tomcat 线程。
 */
@ConfigurationProperties(prefix = "pythontopia.http.graph")
public record GraphHttpClientProperties(Integer connectTimeoutMs, Integer readTimeoutMs) {

  private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
  private static final int DEFAULT_READ_TIMEOUT_MS = 30000;

  public GraphHttpClientProperties {
    if (connectTimeoutMs == null || connectTimeoutMs <= 0) {
      connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
    }
    if (readTimeoutMs == null || readTimeoutMs <= 0) {
      readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
    }
  }
}
