package com.pythontopia.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final JwtProperties properties;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Autowired
  public JwtService(JwtProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, Clock.systemUTC());
  }

  JwtService(JwtProperties properties, ObjectMapper objectMapper, Clock clock) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public IssuedToken issue(CurrentUserPrincipal principal) {
    Instant expiresAt = Instant.now(clock).plus(properties.ttlMinutes(), ChronoUnit.MINUTES);
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", "HS256");
    header.put("typ", "JWT");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sub", principal.userId().toString());
    payload.put("username", principal.username());
    payload.put("role", principal.role().name());
    payload.put("exp", expiresAt.getEpochSecond());
    String headerPart = encodeJson(header);
    String payloadPart = encodeJson(payload);
    String signingInput = headerPart + "." + payloadPart;
    return new IssuedToken(signingInput + "." + sign(signingInput), expiresAt);
  }

  public CurrentUserPrincipal parse(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        throw new JwtValidationException("Malformed token");
      }
      String signingInput = parts[0] + "." + parts[1];
      if (!constantTimeEquals(sign(signingInput), parts[2])) {
        throw new JwtValidationException("Invalid token signature");
      }
      Map<String, Object> payload =
          objectMapper.readValue(URL_DECODER.decode(parts[1]), MAP_TYPE);
      long exp = ((Number) payload.get("exp")).longValue();
      if (Instant.ofEpochSecond(exp).isBefore(Instant.now(clock))) {
        throw new JwtValidationException("Token expired");
      }
      return new CurrentUserPrincipal(
          Long.valueOf((String) payload.get("sub")),
          (String) payload.get("username"),
          Role.valueOf((String) payload.get("role")));
    } catch (JwtValidationException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new JwtValidationException("Invalid token", exception);
    }
  }

  private String encodeJson(Map<String, Object> value) {
    try {
      return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to encode JWT", exception);
    }
  }

  private String sign(String signingInput) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to sign JWT", exception);
    }
  }

  private boolean constantTimeEquals(String left, String right) {
    return MessageDigestUtil.constantTimeEquals(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  public record IssuedToken(String token, Instant expiresAt) {}
}
