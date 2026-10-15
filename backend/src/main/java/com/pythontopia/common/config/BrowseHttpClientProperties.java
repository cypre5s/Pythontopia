package com.pythontopia.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Dedicated read timeout for the bounded 45-second Graph browser operation plus cleanup. */
@ConfigurationProperties(prefix = "pythontopia.http.browse")
public record BrowseHttpClientProperties(Integer readTimeoutMs) {

  private static final int DEFAULT_READ_TIMEOUT_MS = 50000;

  public BrowseHttpClientProperties {
    if (readTimeoutMs == null || readTimeoutMs <= 0) {
      readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
    }
  }
}
