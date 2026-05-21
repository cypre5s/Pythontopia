package com.pythontopia.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pythontopia.services")
public record GraphServiceProperties(String graphUrl, String ragUrl, String ragAdminToken) {

  public GraphServiceProperties {
    if (graphUrl == null || graphUrl.isBlank()) {
      throw new IllegalStateException("pythontopia.services.graphUrl must be set");
    }
    if (ragUrl == null || ragUrl.isBlank()) {
      ragUrl = "http://localhost:18092";
    }
    if (ragAdminToken == null) {
      ragAdminToken = "";
    }
  }
}
