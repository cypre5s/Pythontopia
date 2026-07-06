package com.pythontopia.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 讯飞「语音听写」(IAT / ASR) 接入配置——随身助教语音输入在浏览器 Web Speech API 不可用时的服务端回退。
 * 复用与 TTS / 图片理解相同的讯飞控制台应用凭据（{@code appId}/{@code apiKey}/{@code apiSecret}）。
 *
 * <p>任一凭据缺失时 {@link #isEnabled()}=false：{@code XfyunIatService} 直接返回 {@code degraded=true}
 * 的空转写，由前端提示「语音转写暂不可用，请直接输入」，绝不阻塞答疑链路；配置真实密钥后自动启用。
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
    return appId != null
        && !appId.isBlank()
        && apiKey != null
        && !apiKey.isBlank()
        && apiSecret != null
        && !apiSecret.isBlank();
  }
}
