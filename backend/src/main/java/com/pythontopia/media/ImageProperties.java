package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 讯飞星火「文生图」(Text-To-Image) 接入配置。复用与 TTS / Spark LLM 相同的讯飞控制台应用凭据
 * （{@code PYT_SPARK_APP_ID} / {@code PYT_SPARK_API_KEY} / {@code PYT_SPARK_API_SECRET}）。
 *
 * <p>本项目仅启用 DeepSeek 文本与视觉输入，不启用讯飞文生图。
 */
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
