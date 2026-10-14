package com.pythontopia.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-21T08:00:00Z");

  @Test
  void issuesAndParsesSignedToken() {
    JwtService service = serviceAt(NOW, 60);

    JwtService.IssuedToken issued =
        service.issue(new CurrentUserPrincipal(42L, "stu01", Role.STUDENT));

    assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    assertThat(service.parse(issued.token()))
        .isEqualTo(new CurrentUserPrincipal(42L, "stu01", Role.STUDENT));
  }

  @Test
  void rejectsExpiredToken() {
    JwtService issuer = serviceAt(NOW, 1);
    String token = issuer.issue(new CurrentUserPrincipal(7L, "teacher01", Role.TEACHER)).token();
    JwtService parser = serviceAt(NOW.plusSeconds(61), 1);

    assertThatThrownBy(() -> parser.parse(token))
        .isInstanceOf(JwtValidationException.class)
        .hasMessage("Token expired");
  }

  @Test
  void rejectsTokenPastAbsoluteSessionLifetimeEvenWhenAccessExpiryIsFuture() {
    JwtService service = serviceAt(NOW, 60);
    String token = signedToken(
        "{\"sub\":\"42\",\"username\":\"stu01\",\"role\":\"STUDENT\",\"tv\":0,\"exp\":"
            + NOW.plusSeconds(3600).getEpochSecond()
            + ",\"auth_at\":" + NOW.minus(31, ChronoUnit.DAYS).getEpochSecond() + "}");

    assertThatThrownBy(() -> service.parse(token))
        .isInstanceOf(JwtValidationException.class)
        .hasMessage("Session expired");
  }

  @Test
  void refreshPreservesSessionStartAndCapsExpiryAtAbsoluteDeadline() throws Exception {
    Instant sessionStartedAt = NOW.minus(29, ChronoUnit.DAYS);
    String token = signedToken(
        "{\"sub\":\"42\",\"username\":\"stu01\",\"role\":\"STUDENT\",\"tv\":0,\"exp\":"
            + NOW.plusSeconds(3600).getEpochSecond()
            + ",\"auth_at\":" + sessionStartedAt.getEpochSecond() + "}");
    JwtService service = serviceAt(NOW, 60 * 24 * 30);

    JwtService.IssuedToken refreshed =
        service.issueForRefresh(new CurrentUserPrincipal(42L, "stu01", Role.STUDENT), token);
    Map<String, Object> payload = payloadOf(refreshed.token());

    assertThat(((Number) payload.get("auth_at")).longValue())
        .isEqualTo(sessionStartedAt.getEpochSecond());
    assertThat(refreshed.expiresAt()).isEqualTo(sessionStartedAt.plus(30, ChronoUnit.DAYS));
  }

  @Test
  void legacyTokenGetsBoundedSessionStartWhenRefreshed() throws Exception {
    String token = signedToken(
        "{\"sub\":\"42\",\"username\":\"stu01\",\"role\":\"STUDENT\",\"tv\":0,\"exp\":"
            + NOW.plusSeconds(3600).getEpochSecond() + "}");
    JwtService service = serviceAt(NOW, 60);

    JwtService.IssuedToken refreshed =
        service.issueForRefresh(new CurrentUserPrincipal(42L, "stu01", Role.STUDENT), token);
    Map<String, Object> payload = payloadOf(refreshed.token());

    assertThat(((Number) payload.get("auth_at")).longValue()).isEqualTo(NOW.getEpochSecond());
  }

  @Test
  void rejectsTamperedToken() {
    JwtService service = serviceAt(NOW, 60);
    String token = service.issue(new CurrentUserPrincipal(42L, "stu01", Role.STUDENT)).token();
    String tampered = token.substring(0, token.length() - 2) + "xx";

    assertThatThrownBy(() -> service.parse(tampered))
        .isInstanceOf(JwtValidationException.class)
        .hasMessage("Invalid token signature");
  }

  @Test
  void carriesTokenVersionThroughIssueAndParse() {
    JwtService service = serviceAt(NOW, 60);

    String token =
        service.issue(new CurrentUserPrincipal(42L, "stu01", Role.STUDENT, 7)).token();

    assertThat(service.parse(token).tokenVersion()).isEqualTo(7);
  }

  /** 升级前签发的旧 token 没有 tv 声明，必须按默认代次 0 解读，避免升级即把在线用户全部踢下线。 */
  @Test
  void treatsMissingTokenVersionAsZero() {
    JwtService service = serviceAt(NOW, 60);
    String legacyPayload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"sub\":\"42\",\"username\":\"stu01\",\"role\":\"STUDENT\",\"exp\":"
                        + NOW.plusSeconds(600).getEpochSecond()
                        + "}")
                    .getBytes(StandardCharsets.UTF_8));
    String header =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String legacyToken = header + "." + legacyPayload + "." + hmac(header + "." + legacyPayload);

    assertThat(service.parse(legacyToken))
        .isEqualTo(new CurrentUserPrincipal(42L, "stu01", Role.STUDENT, 0));
  }

  private String hmac(String signingInput) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              "test-secret-must-be-long-enough-for-hs256".getBytes(StandardCharsets.UTF_8),
              "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String signedToken(String payloadJson) {
    String header = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String payload = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    String signingInput = header + "." + payload;
    return signingInput + "." + hmac(signingInput);
  }

  private Map<String, Object> payloadOf(String token) throws Exception {
    return new ObjectMapper()
        .readValue(
            Base64.getUrlDecoder().decode(token.split("\\.")[1]),
            new com.fasterxml.jackson.core.type.TypeReference<>() {});
  }

  private JwtService serviceAt(Instant instant, long ttlMinutes) {
    return new JwtService(
        new JwtProperties("test-secret-must-be-long-enough-for-hs256", ttlMinutes),
        new ObjectMapper(),
        Clock.fixed(instant, ZoneOffset.UTC));
  }
}
