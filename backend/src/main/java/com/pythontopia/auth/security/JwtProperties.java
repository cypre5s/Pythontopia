package com.pythontopia.auth.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 签名配置。密钥在构造期硬校验并 fail fast：部署文档统一指引 {@code cp .env.example .env}，
 * 而样板里的占位密钥是公开的——一旦原样进生产，任何人都能自签任意角色的 token。宁可启动失败。
 */
@ConfigurationProperties(prefix = "pythontopia.security.jwt")
public record JwtProperties(String secret, long ttlMinutes) {

  /** HS256 的安全强度上限即摘要长度；短于 32 字节的密钥可被离线爆破。 */
  static final int MIN_SECRET_BYTES = 32;

  /** 仓库 / 文档 / compose 兜底里出现过的公开占位值，逐字命中即拒。 */
  private static final Set<String> KNOWN_PLACEHOLDER_SECRETS =
      Set.of(
          "replace-with-local-secret-at-least-32-chars",
          "replace-with-32-char-secret-please",
          "pytopia-teacher",
          "pytopia-rag-admin",
          "pythontopia-route-secret");

  /** 占位值特征串：覆盖上面没穷举到的变体（大小写无关）。 */
  private static final List<String> PLACEHOLDER_MARKERS =
      List.of("replace-with", "replace_with", "change-me", "changeme", "placeholder", "your-secret");

  public JwtProperties {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("PYT_JWT_SECRET must be set");
    }
    if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "PYT_JWT_SECRET must be at least "
              + MIN_SECRET_BYTES
              + " bytes; generate one with `openssl rand -hex 32`");
    }
    String normalized = secret.trim().toLowerCase(Locale.ROOT);
    if (KNOWN_PLACEHOLDER_SECRETS.contains(normalized)
        || PLACEHOLDER_MARKERS.stream().anyMatch(normalized::contains)) {
      throw new IllegalStateException(
          "PYT_JWT_SECRET is a publicly known placeholder value; "
              + "generate a random secret with `openssl rand -hex 32`");
    }
    if (ttlMinutes <= 0) {
      throw new IllegalStateException("pythontopia.security.jwt.ttlMinutes must be positive");
    }
  }
}
