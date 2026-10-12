package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 讯飞「在线语音合成」(TTS) 配置。本项目不启用配音，服务始终返回 {@code TTS_DISABLED}。
 */
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
    return false;
  }
}
