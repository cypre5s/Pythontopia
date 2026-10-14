package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Legacy image understanding settings; Tutor image input goes through DeepSeek. */
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
    return false;
  }
}
