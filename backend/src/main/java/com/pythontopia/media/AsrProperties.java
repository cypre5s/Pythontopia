package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 讯飞「语音听写」(IAT / ASR) 接入配置——随身助教语音输入在浏览器 Web Speech API 不可用时的服务端回退。
 * 复用与 TTS / 图片理解相同的讯飞控制台应用凭据（{@code appId}/{@code apiKey}/{@code apiSecret}）。
 *
 * <p>本项目只启用文字与图片输入，语音识别始终关闭；服务返回空转写，前端使用键盘输入。
 */
@ConfigurationProperties(prefix = "pythontopia.asr")
public record AsrProperties(
    String appId,
    String apiKey,
    String apiSecret,
    String baseUrl,
    int dailyLimitPerUser) {

  public AsrProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "wss://iat-api.xfyun.cn/v2/iat";
    }
    if (dailyLimitPerUser <= 0) {
      dailyLimitPerUser = 100;
    }
  }

  public boolean isEnabled() {
    return false;
  }
}
