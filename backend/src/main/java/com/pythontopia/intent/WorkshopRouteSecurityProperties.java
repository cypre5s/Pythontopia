package com.pythontopia.intent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 百晓工坊签名路由的独立密钥配置；与 graph/rag 服务间令牌严格分离。 */
@ConfigurationProperties(prefix = "pythontopia.workshop-routing")
public record WorkshopRouteSecurityProperties(String executionSecret) {

  private static final int MIN_SECRET_LENGTH = 32;

  public WorkshopRouteSecurityProperties {
    executionSecret = executionSecret == null ? "" : executionSecret.strip();
  }

  public boolean signingEnabled() {
    if (executionSecret.length() < MIN_SECRET_LENGTH) {
      return false;
    }
    String normalized = executionSecret.toLowerCase();
    return !normalized.contains("change-me")
        && !normalized.contains("placeholder")
        && !normalized.equals("pytopia-rag-admin")
        && !normalized.equals("pythontopia-route-secret");
  }
}
