package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 讯飞星火「文生图」(Text-To-Image) 接入配置。复用与 TTS / Spark LLM 相同的讯飞控制台应用凭据
 * （{@code PYT_SPARK_APP_ID} / {@code PYT_SPARK_API_KEY} / {@code PYT_SPARK_API_SECRET}）。
 *
 * <p>未配置任一凭据时 {@link #isEnabled()} 为 false：{@code XfyunImageService} 走本地占位降级，
 * 保证无 apikey 时多模态链路仍可跑通；配置后自动切换为真实生成。
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
    return appId != null && !appId.isBlank()
        && apiKey != null && !apiKey.isBlank()
        && apiSecret != null && !apiSecret.isBlank();
  }
}
