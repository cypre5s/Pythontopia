package com.pythontopia.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class XfyunTtsService {

  private static final Logger log = LoggerFactory.getLogger(XfyunTtsService.class);
  private static final String TTS_HOST = "tts-api.xfyun.cn";
  private static final String TTS_PATH = "/v2/tts";
  private static final HttpClient WS_CLIENT = HttpClient.newHttpClient();
  private static final int WS_TIMEOUT_SECONDS = 30;

  private final TtsProperties properties;
  private final ObjectMapper objectMapper;

  private final Map<Long, DailyCounter> rateLimitMap = new ConcurrentHashMap<>();

  public XfyunTtsService(TtsProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public byte[] synthesize(Long userId, String text, String voice) {
    if (!properties.isEnabled()) {
      throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "TTS_DISABLED", "TTS 服务未配置");
    }

    checkRateLimit(userId);

    try {
      URI wsUri = buildAuthUrl();
      String payload = buildPayload(text, voice);
      CompletableFuture<byte[]> audioFuture = new CompletableFuture<>();
      ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

      WebSocket ws = WS_CLIENT.newWebSocketBuilder()
          .buildAsync(wsUri, new WebSocket.Listener() {
            private final StringBuilder textBuf = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
              textBuf.append(data);
              if (last) {
                processFrame(textBuf.toString(), audioBuffer, audioFuture);
                textBuf.setLength(0);
              }
              webSocket.request(1);
              return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
              if (!audioFuture.isDone()) {
                audioFuture.complete(audioBuffer.toByteArray());
              }
              return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
              if (!audioFuture.isDone()) {
                audioFuture.completeExceptionally(error);
              }
            }
          })
          .join();

      ws.sendText(payload, true);
      return audioFuture.get(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("TTS synthesis failed", e);
      throw new BusinessException(HttpStatus.BAD_GATEWAY, "TTS_FAILED", "语音合成失败");
    }
  }

  private void processFrame(String json, ByteArrayOutputStream buffer, CompletableFuture<byte[]> future) {
    try {
      JsonNode root = objectMapper.readTree(json);
      int code = root.path("code").asInt(-1);
      if (code != 0) {
        String msg = root.path("message").asText("unknown error");
        log.warn("Xfyun TTS error: code={} message={}", code, msg);
        if (!future.isDone()) {
          future.completeExceptionally(
              new BusinessException(HttpStatus.BAD_GATEWAY, "TTS_FAILED", "TTS 服务返回错误: " + msg));
        }
        return;
      }

      JsonNode audioNode = root.path("data").path("audio");
      if (!audioNode.isMissingNode() && !audioNode.isNull()) {
        byte[] chunk = Base64.getDecoder().decode(audioNode.asText());
        buffer.write(chunk);
      }

      int status = root.path("data").path("status").asInt(0);
      if (status == 2 && !future.isDone()) {
        future.complete(buffer.toByteArray());
      }
    } catch (Exception e) {
      log.warn("Failed to process TTS frame", e);
      if (!future.isDone()) {
        future.completeExceptionally(e);
      }
    }
  }

  private void checkRateLimit(Long userId) {
    DailyCounter counter = rateLimitMap.compute(userId, (k, existing) -> {
      long today = todayEpochDay();
      if (existing == null || existing.day != today) {
        return new DailyCounter(today, new AtomicInteger(0));
      }
      return existing;
    });
    if (counter.count.incrementAndGet() > properties.dailyLimitPerUser()) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS, "TTS_RATE_LIMITED",
          "每日 TTS 请求上限 " + properties.dailyLimitPerUser() + " 次");
    }
  }

  private URI buildAuthUrl() {
    String date = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        .format(ZonedDateTime.now(ZoneId.of("GMT")));

    String signatureOrigin = "host: " + TTS_HOST + "\n"
        + "date: " + date + "\n"
        + "GET " + TTS_PATH + " HTTP/1.1";

    String signature;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(properties.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      signature = Base64.getEncoder().encodeToString(mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }

    String authorizationOrigin = String.format(
        "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
        properties.apiKey(), signature);
    String authorization = Base64.getEncoder().encodeToString(
        authorizationOrigin.getBytes(StandardCharsets.UTF_8));

    String encodedDate = java.net.URLEncoder.encode(date, StandardCharsets.UTF_8);
    String query = "authorization=" + authorization + "&date=" + encodedDate + "&host=" + TTS_HOST;
    return URI.create("wss://" + TTS_HOST + TTS_PATH + "?" + query);
  }

  private String buildPayload(String text, String voice) {
    try {
      ObjectNode root = objectMapper.createObjectNode();

      ObjectNode common = root.putObject("common");
      common.put("app_id", properties.appId());

      ObjectNode business = root.putObject("business");
      business.put("aue", "lame");
      business.put("sfl", 1);
      business.put("auf", "audio/L16;rate=16000");
      business.put("vcn", voice);
      business.put("tte", "UTF8");

      ObjectNode data = root.putObject("data");
      data.put("status", 2);
      data.put("text", Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));

      return objectMapper.writeValueAsString(root);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build TTS payload", e);
    }
  }

  private long todayEpochDay() {
    return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
  }

  private record DailyCounter(long day, AtomicInteger count) {}
}
