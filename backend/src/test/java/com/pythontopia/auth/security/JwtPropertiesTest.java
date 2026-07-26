package com.pythontopia.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 弱 / 占位 JWT 密钥必须在启动期就把应用拦死，而不是等到签名被伪造。 */
class JwtPropertiesTest {

  private static final String STRONG_SECRET = "6f1c0a9d4b7e2f8a3c5d1e9b0a7c4f2d8e6b1a3c5d7f9e0b2a4c6d8e0f1a3b5c";

  @Test
  @DisplayName("空密钥拒绝")
  void rejectsBlankSecret() {
    assertThatThrownBy(() -> new JwtProperties("   ", 720))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PYT_JWT_SECRET");
  }

  @Test
  @DisplayName("短于 32 字节的密钥拒绝（HS256 离线爆破面）")
  void rejectsShortSecret() {
    String tooShort = "a".repeat(JwtProperties.MIN_SECRET_BYTES - 1);

    assertThatThrownBy(() -> new JwtProperties(tooShort, 720))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 32 bytes");
  }

  @ParameterizedTest
  @DisplayName("仓库 / 文档里的公开占位值拒绝")
  @ValueSource(
      strings = {
        "replace-with-local-secret-at-least-32-chars",
        "REPLACE-WITH-LOCAL-SECRET-AT-LEAST-32-CHARS",
        "replace-with-32-char-secret-please",
        "change-me-change-me-change-me-change-me",
        "placeholder-placeholder-placeholder"
      })
  void rejectsKnownPlaceholders(String placeholder) {
    assertThatThrownBy(() -> new JwtProperties(placeholder, 720))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("placeholder");
  }

  @Test
  @DisplayName("异常信息不得回显密钥本身")
  void errorMessageNeverEchoesTheSecret() {
    String placeholder = "replace-with-local-secret-at-least-32-chars";

    assertThatThrownBy(() -> new JwtProperties(placeholder, 720))
        .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain(placeholder));
  }

  @Test
  @DisplayName("非正 TTL 拒绝")
  void rejectsNonPositiveTtl() {
    assertThatThrownBy(() -> new JwtProperties(STRONG_SECRET, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttlMinutes");
  }

  @Test
  @DisplayName("随机高熵密钥通过")
  void acceptsStrongSecret() {
    assertThatCode(() -> new JwtProperties(STRONG_SECRET, 720)).doesNotThrowAnyException();
  }
}
