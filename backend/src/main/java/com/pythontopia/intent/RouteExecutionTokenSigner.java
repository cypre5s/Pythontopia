package com.pythontopia.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.intent.dto.WorkshopRouteDecision;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 为路由决策签发短期执行凭证，防止客户端篡改多 Agent 拓扑、工具权限或预算。 */
@Component
public class RouteExecutionTokenSigner {

  private static final String VERSION = "v1";
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final long MAX_AGE_SECONDS = 600;
  private static final long MAX_FUTURE_SKEW_SECONDS = 30;
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private final byte[] secret;
  private final boolean signingEnabled;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Autowired
  public RouteExecutionTokenSigner(
      WorkshopRouteSecurityProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, Clock.systemUTC());
  }

  RouteExecutionTokenSigner(
      WorkshopRouteSecurityProperties properties, ObjectMapper objectMapper, Clock clock) {
    this.secret = properties.executionSecret().getBytes(StandardCharsets.UTF_8);
    this.signingEnabled = properties.signingEnabled();
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public String issue(
      Long userId,
      WorkshopRouteDecision decision,
      String message,
      IntentRouteRequest.InteractionContext interaction) {
    if (userId == null || decision == null || message == null || !signingEnabled) {
      return null;
    }
    long issuedAt = Instant.now(clock).getEpochSecond();
    String digest = URL_ENCODER.encodeToString(executionDigest(decision, message, interaction));
    String payload = VERSION + "." + issuedAt + "." + userId + "." + digest;
    return payload + "." + URL_ENCODER.encodeToString(hmac(payload));
  }

  public boolean verify(
      Long userId,
      WorkshopRouteDecision decision,
      String message,
      IntentRouteRequest.InteractionContext interaction,
      String token) {
    if (userId == null
        || decision == null
        || message == null
        || token == null
        || token.isBlank()
        || !signingEnabled) {
      return false;
    }
    try {
      String[] parts = token.split("\\.", -1);
      if (parts.length != 5 || !VERSION.equals(parts[0])) {
        return false;
      }
      String payload = String.join(".", parts[0], parts[1], parts[2], parts[3]);
      byte[] providedSignature = URL_DECODER.decode(parts[4]);
      if (!MessageDigest.isEqual(hmac(payload), providedSignature)) {
        return false;
      }
      long issuedAt = Long.parseLong(parts[1]);
      long tokenUserId = Long.parseLong(parts[2]);
      long now = Instant.now(clock).getEpochSecond();
      if (tokenUserId != userId
          || issuedAt > now + MAX_FUTURE_SKEW_SECONDS
          || now - issuedAt > MAX_AGE_SECONDS) {
        return false;
      }
      return MessageDigest.isEqual(
          executionDigest(decision, message, interaction), URL_DECODER.decode(parts[3]));
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private byte[] executionDigest(
      WorkshopRouteDecision decision,
      String message,
      IntentRouteRequest.InteractionContext interaction) {
    try {
      var payload = objectMapper.createObjectNode();
      payload.set("routeDecision", objectMapper.valueToTree(decision));
      payload.put("message", message.strip());
      payload.set("interaction", objectMapper.valueToTree(interaction));
      return MessageDigest.getInstance("SHA-256")
          .digest(objectMapper.writeValueAsBytes(payload));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to encode route decision", exception);
    }
  }

  private byte[] hmac(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to sign route execution token", exception);
    }
  }
}
