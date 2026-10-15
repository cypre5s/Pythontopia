package com.pythontopia.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.media.domain.MediaAsset;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 讯飞星火「文生图」服务，把一段文字 prompt 生成知识点图解/插画。鉴权范式复刻
 * {@link XfyunTtsService}（HMAC-SHA256，{@code host date request-line}）。
 *
 * <p>未配置凭据或上游失败时 fail closed，不生成占位图片、不写伪造媒资。
 */
@Service
public class XfyunImageService {

  private static final Logger log = LoggerFactory.getLogger(XfyunImageService.class);
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private static final int HTTP_TIMEOUT_SECONDS = 40;

  private final ImageProperties properties;
  private final ObjectMapper objectMapper;
  private final MediaAssetStorage storage;
  private final MediaAssetRepository repository;

  private final Map<Long, DailyCounter> rateLimitMap = new ConcurrentHashMap<>();

  public XfyunImageService(
      ImageProperties properties,
      ObjectMapper objectMapper,
      MediaAssetStorage storage,
      MediaAssetRepository repository) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.storage = storage;
    this.repository = repository;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  /**
   * 生成真实图片并落库为 {@link MediaAsset}。{@code label} 为兼容现有客户端保留；{@code kcCodes}
   * 用于审计/检索关联。
   */
  public MediaAsset generate(Long userId, String prompt, String label, List<String> kcCodes) {
    if (prompt == null || prompt.isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "IMAGE_PROMPT_REQUIRED", "图片描述不能为空");
    }
    if (!isEnabled()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "IMAGE_PROVIDER_UNAVAILABLE",
          "图片生成服务尚未配置，请联系管理员");
    }
    if (userId != null) {
      checkRateLimit(userId);
    }
    try {
      byte[] jpeg = callXfyun(userId, prompt);
      String file = storage.write("jpg", jpeg);
      return repository.insert(
          userId, MediaAssetKind.IMAGE, prompt, file, "image/jpeg", false, "XFYUN", kcCodes, null);
    } catch (Exception ex) {
      log.warn("Xfyun image generation failed: {}", ex.getMessage());
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "IMAGE_PROVIDER_FAILED", "图片生成服务暂时不可用，请稍后重试");
    }
  }

  private byte[] callXfyun(Long userId, String prompt) throws Exception {
    URI signed = buildAuthUrl();
    String body = buildPayload(userId, prompt);
    HttpRequest request =
        HttpRequest.newBuilder(signed)
            .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException("Xfyun TTI HTTP " + response.statusCode());
    }
    JsonNode root = objectMapper.readTree(response.body());
    int code = root.path("header").path("code").asInt(-1);
    if (code != 0) {
      throw new IllegalStateException(
          "Xfyun TTI code=" + code + " message=" + root.path("header").path("message").asText());
    }
    JsonNode textNode = root.path("payload").path("choices").path("text");
    if (textNode.isArray() && !textNode.isEmpty()) {
      String base64 = textNode.get(0).path("content").asText("");
      if (!base64.isBlank()) {
        return Base64.getDecoder().decode(base64);
      }
    }
    throw new IllegalStateException("Xfyun TTI returned no image content");
  }

  private URI buildAuthUrl() {
    URI base = URI.create(properties.baseUrl());
    String host = base.getHost();
    String path = base.getPath();
    String date =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            .format(ZonedDateTime.now(ZoneId.of("GMT")));
    String signatureOrigin =
        "host: " + host + "\n" + "date: " + date + "\n" + "POST " + path + " HTTP/1.1";
    String signature;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(properties.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      signature =
          Base64.getEncoder()
              .encodeToString(mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
    }
    String authorizationOrigin =
        String.format(
            "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
            properties.apiKey(), signature);
    String authorization =
        Base64.getEncoder().encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));
    String encodedDate = java.net.URLEncoder.encode(date, StandardCharsets.UTF_8);
    String query = "authorization=" + authorization + "&date=" + encodedDate + "&host=" + host;
    return URI.create(base + "?" + query);
  }

  private String buildPayload(Long userId, String prompt) {
    try {
      ObjectNode root = objectMapper.createObjectNode();
      ObjectNode header = root.putObject("header");
      header.put("app_id", properties.appId());
      header.put("uid", userId == null ? "system" : String.valueOf(userId));

      ObjectNode chat = root.putObject("parameter").putObject("chat");
      chat.put("domain", "general");
      chat.put("width", 512);
      chat.put("height", 512);

      ArrayNode text = root.putObject("payload").putObject("message").putArray("text");
      ObjectNode turn = text.addObject();
      turn.put("role", "user");
      turn.put("content", prompt);
      return objectMapper.writeValueAsString(root);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to build TTI payload", ex);
    }
  }

  private void checkRateLimit(Long userId) {
    DailyCounter counter =
        rateLimitMap.compute(
            userId,
            (k, existing) -> {
              long today = todayEpochDay();
              if (existing == null || existing.day != today) {
                return new DailyCounter(today, new AtomicInteger(0));
              }
              return existing;
            });
    if (counter.count.incrementAndGet() > properties.dailyLimitPerUser()) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "IMAGE_RATE_LIMITED",
          "每日图片生成上限 " + properties.dailyLimitPerUser() + " 次");
    }
  }

  private long todayEpochDay() {
    return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
  }

  private record DailyCounter(long day, AtomicInteger count) {}
}
