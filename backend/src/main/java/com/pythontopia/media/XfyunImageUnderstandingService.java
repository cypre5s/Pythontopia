package com.pythontopia.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 讯飞星火「图片理解」服务（看图答疑 B）：把学生上传的截图 / 手写代码图转成一段客观文字描述，
 * 再交给文本大模型（deepseek-v4-pro）结合课件 RAG 给出引导式答疑（视觉与文本职责分离）。
 *
 * <p>鉴权与 {@link XfyunTtsService} 同构（HMAC-SHA256，{@code host date request-line}，WebSocket）。
 * 协议：{@code wss://spark-api.cn-huabei-1.xf-yun.com/v2.1/image}，{@code payload.message.text} 首元素是
 * {@code content_type=image} 的图片 base64，次元素是 {@code content_type=text} 的提问。
 *
 * <p><b>降级</b>：凭据缺失（{@link ImageUnderstandingProperties#isEnabled()}=false）、图片非法 / 超限、
 * 限流或上游失败时一律返回 {@code null}，由调用方回退为纯文本答疑，绝不阻塞导师链路。
 */
@Service
public class XfyunImageUnderstandingService {

  private static final Logger log = LoggerFactory.getLogger(XfyunImageUnderstandingService.class);
  private static final HttpClient WS_CLIENT = HttpClient.newHttpClient();
  private static final int WS_TIMEOUT_SECONDS = 30;

  private final ImageUnderstandingProperties properties;
  private final ObjectMapper objectMapper;

  private final Map<Long, DailyCounter> rateLimitMap = new ConcurrentHashMap<>();

  public XfyunImageUnderstandingService(
      ImageUnderstandingProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  /**
   * 识别图片并返回一段客观描述；任何失败都返回 {@code null}（调用方据此回退为纯文本答疑）。
   *
   * @param userId 限流维度，可空
   * @param imageBase64 图片 base64（可带 {@code data:image/...;base64,} 前缀）
   * @param prompt 指导识别的中性提问（要求只描述、不解题）
   */
  public String understand(Long userId, String imageBase64, String prompt) {
    if (!properties.isEnabled() || imageBase64 == null || imageBase64.isBlank()) {
      return null;
    }
    String pureBase64 = stripDataUrlPrefix(imageBase64);
    final byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(pureBase64);
    } catch (IllegalArgumentException ex) {
      log.warn("Image understanding skipped: invalid base64");
      return null;
    }
    if (decoded.length > properties.maxImageBytes()) {
      log.warn(
          "Image understanding skipped: {} bytes exceeds limit {}",
          decoded.length,
          properties.maxImageBytes());
      return null;
    }
    if (userId != null && !withinRateLimit(userId)) {
      log.warn("Image understanding skipped: user {} hit daily limit", userId);
      return null;
    }

    try {
      URI wsUri = buildAuthUrl();
      String payload = buildPayload(userId, pureBase64, prompt);
      CompletableFuture<String> answerFuture = new CompletableFuture<>();
      StringBuilder answer = new StringBuilder();

      WebSocket ws =
          WS_CLIENT
              .newWebSocketBuilder()
              .buildAsync(
                  wsUri,
                  new WebSocket.Listener() {
                    private final StringBuilder frameBuf = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(
                        WebSocket webSocket, CharSequence data, boolean last) {
                      frameBuf.append(data);
                      if (last) {
                        processFrame(frameBuf.toString(), answer, answerFuture);
                        frameBuf.setLength(0);
                      }
                      webSocket.request(1);
                      return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(
                        WebSocket webSocket, int statusCode, String reason) {
                      if (!answerFuture.isDone()) {
                        answerFuture.complete(answer.toString());
                      }
                      return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                      if (!answerFuture.isDone()) {
                        answerFuture.completeExceptionally(error);
                      }
                    }
                  })
              .join();

      ws.sendText(payload, true);
      String result = answerFuture.get(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return (result == null || result.isBlank()) ? null : result.trim();
    } catch (Exception e) {
      log.warn("Image understanding failed, falling back to text-only: {}", e.getMessage());
      return null;
    }
  }

  private void processFrame(String json, StringBuilder answer, CompletableFuture<String> future) {
    try {
      JsonNode root = objectMapper.readTree(json);
      int code = root.path("header").path("code").asInt(-1);
      if (code != 0) {
        String msg = root.path("header").path("message").asText("unknown error");
        log.warn("Xfyun image understanding error: code={} message={}", code, msg);
        if (!future.isDone()) {
          future.completeExceptionally(
              new IllegalStateException("Xfyun image code=" + code + " message=" + msg));
        }
        return;
      }
      JsonNode textNode = root.path("payload").path("choices").path("text");
      if (textNode.isArray()) {
        for (JsonNode item : textNode) {
          answer.append(item.path("content").asText(""));
        }
      }
      int status = root.path("header").path("status").asInt(0);
      if (status == 2 && !future.isDone()) {
        future.complete(answer.toString());
      }
    } catch (Exception e) {
      log.warn("Failed to process image understanding frame", e);
      if (!future.isDone()) {
        future.completeExceptionally(e);
      }
    }
  }

  private URI buildAuthUrl() {
    URI base = URI.create(properties.baseUrl());
    String host = base.getHost();
    String path = base.getPath();
    String date =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            .format(ZonedDateTime.now(ZoneId.of("GMT")));
    String signatureOrigin =
        "host: " + host + "\n" + "date: " + date + "\n" + "GET " + path + " HTTP/1.1";
    String signature;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(properties.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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
    return URI.create(properties.baseUrl() + "?" + query);
  }

  private String buildPayload(Long userId, String imageBase64, String prompt) {
    try {
      ObjectNode root = objectMapper.createObjectNode();
      ObjectNode header = root.putObject("header");
      header.put("app_id", properties.appId());
      header.put("uid", userId == null ? "system" : String.valueOf(userId));

      ObjectNode chat = root.putObject("parameter").putObject("chat");
      chat.put("domain", properties.domain());
      chat.put("temperature", 0.3);
      chat.put("max_tokens", properties.maxTokens());

      ArrayNode text = root.putObject("payload").putObject("message").putArray("text");
      ObjectNode imageTurn = text.addObject();
      imageTurn.put("role", "user");
      imageTurn.put("content", imageBase64);
      imageTurn.put("content_type", "image");
      ObjectNode textTurn = text.addObject();
      textTurn.put("role", "user");
      textTurn.put(
          "content", prompt == null || prompt.isBlank() ? "请客观描述这张图片的内容。" : prompt);
      textTurn.put("content_type", "text");
      return objectMapper.writeValueAsString(root);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to build image understanding payload", ex);
    }
  }

  private boolean withinRateLimit(Long userId) {
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
    return counter.count.incrementAndGet() <= properties.dailyLimitPerUser();
  }

  private static String stripDataUrlPrefix(String value) {
    int comma = value.indexOf(',');
    if (value.startsWith("data:") && comma > 0) {
      return value.substring(comma + 1);
    }
    return value;
  }

  private long todayEpochDay() {
    return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
  }

  private record DailyCounter(long day, AtomicInteger count) {}
}
