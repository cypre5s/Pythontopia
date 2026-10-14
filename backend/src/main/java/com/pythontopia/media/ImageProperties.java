package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Legacy image provider settings; the image generation route is hard-disabled. */
@ConfigurationProperties(prefix = "pythontopia.image")
public record ImageProperties(
    String appId,
    String apiKey,
    String apiSecret,
    String baseUrl,
    int dailyLimitPerUser) {

  public ImageProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://spark-api.cn-huabei-1.xf-yun.com/v2.1/tti";
    }
    if (dailyLimitPerUser <= 0) {
      dailyLimitPerUser = 50;
    }
  }

  public boolean isEnabled() {
    return false;
  }
}
