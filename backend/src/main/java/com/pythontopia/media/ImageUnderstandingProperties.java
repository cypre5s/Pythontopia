package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 讯飞星火「图片理解」(看图答疑) 接入配置。复用与 TTS / TTI / Spark LLM 相同的讯飞控制台应用凭据
 * （{@code PYT_SPARK_APP_ID} / {@code PYT_SPARK_API_KEY} / {@code PYT_SPARK_API_SECRET}）。
 *
 * <p>任一凭据缺失时 {@link #isEnabled()}=false：{@code XfyunImageUnderstandingService} 直接返回 null，
 * 导师答疑回退为纯文本而不阻塞链路；配置真实密钥后自动启用看图答疑。
 */
@ConfigurationProperties(prefix = "pythontopia.image-understanding")
public record ImageUnderstandingProperties(
    String appId,
    String apiKey,
    String apiSecret,
    String baseUrl,
    String domain,
    int maxTokens,
    int dailyLimitPerUser,
    int maxImageBytes) {

  public ImageUnderstandingProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "wss://spark-api.cn-huabei-1.xf-yun.com/v2.1/image";
    }
    if (domain == null || domain.isBlank()) {
      domain = "imagev3";
    }
    if (maxTokens <= 0) {
      maxTokens = 1024;
    }
    if (dailyLimitPerUser <= 0) {
      dailyLimitPerUser = 50;
    }
    if (maxImageBytes <= 0) {
      // 讯飞图片理解单图上限 4M（base64 解码后字节数）。
      maxImageBytes = 4 * 1024 * 1024;
    }
  }

  public boolean isEnabled() {
    return appId != null && !appId.isBlank()
        && apiKey != null && !apiKey.isBlank()
        && apiSecret != null && !apiSecret.isBlank();
  }
}
