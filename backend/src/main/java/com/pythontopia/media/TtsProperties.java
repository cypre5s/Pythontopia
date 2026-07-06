package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 讯飞「在线语音合成」(TTS) 接入配置。三项凭据齐备时 {@link #isEnabled()} 为 true；缺失时
 * {@code XfyunTtsService} 抛 {@code TTS_DISABLED}，由调用方决定回退纯文本还是跳过音轨。
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
    return appId != null && !appId.isBlank()
        && apiKey != null && !apiKey.isBlank()
        && apiSecret != null && !apiSecret.isBlank();
  }
}
