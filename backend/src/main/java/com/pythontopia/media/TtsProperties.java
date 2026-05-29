package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pythontopia.tts")
public record TtsProperties(
    String appId,
    String apiKey,
    String apiSecret,
    int dailyLimitPerUser) {

  public TtsProperties {
    if (dailyLimitPerUser <= 0) {
      dailyLimitPerUser = 100;
    }
  }

  public boolean isEnabled() {
    return appId != null && !appId.isBlank()
        && apiKey != null && !apiKey.isBlank()
        && apiSecret != null && !apiSecret.isBlank();
  }
}
