package com.pythontopia.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
  void rejectsTamperedToken() {
    JwtService service = serviceAt(NOW, 60);
    String token = service.issue(new CurrentUserPrincipal(42L, "stu01", Role.STUDENT)).token();
    String tampered = token.substring(0, token.length() - 2) + "xx";

    assertThatThrownBy(() -> service.parse(tampered))
        .isInstanceOf(JwtValidationException.class)
        .hasMessage("Invalid token signature");
  }

  private JwtService serviceAt(Instant instant, long ttlMinutes) {
    return new JwtService(
        new JwtProperties("test-secret-must-be-long-enough-for-hs256", ttlMinutes),
        new ObjectMapper(),
        Clock.fixed(instant, ZoneOffset.UTC));
  }
}
