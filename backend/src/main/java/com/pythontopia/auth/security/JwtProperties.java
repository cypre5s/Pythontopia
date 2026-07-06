package com.pythontopia.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pythontopia.security.jwt")
public record JwtProperties(String secret, long ttlMinutes) {

  public JwtProperties {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("PYT_JWT_SECRET must be set");
    }
    if (ttlMinutes <= 0) {
      throw new IllegalStateException("pythontopia.security.jwt.ttlMinutes must be positive");
    }
  }
}
