package com.pythontopia.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 百晓工坊资源图的专用读取超时。资源生成包含课件检索、结构化设计和语义接地审查，
 * 因此独立于普通 Graph 30 秒窗口；连接失败仍沿用普通 Graph 的快速失败阈值。
 */
@ConfigurationProperties(prefix = "pythontopia.http.workshop")
public record WorkshopHttpClientProperties(Integer readTimeoutMs) {

  private static final int DEFAULT_READ_TIMEOUT_MS = 180000;

  public WorkshopHttpClientProperties {
    if (readTimeoutMs == null || readTimeoutMs <= 0) {
      readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
    }
  }
}
