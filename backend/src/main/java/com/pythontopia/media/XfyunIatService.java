package com.pythontopia.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.media.dto.AsrResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 讯飞「语音听写」(IAT / ASR)：随身助教语音输入在浏览器 Web Speech API 不可用时的服务端回退。
 *
 * <p>流程：浏览器 {@code MediaRecorder} 上传的音频（webm/ogg/mp3…）→ 本机 ffmpeg 转码为
 * 16kHz 单声道 16bit PCM → 经 WebSocket（{@code wss://iat-api.xfyun.cn/v2/iat}，HMAC-SHA256 鉴权，
 * 与 {@link XfyunTtsService}/{@link XfyunImageUnderstandingService} 同构）分帧上送并收集识别文本。
 *
 * <p><b>降级</b>：凭据缺失（{@link AsrProperties#isEnabled()}=false）、无 ffmpeg、转码失败或空音频时返回
 * {@link AsrResponse#degraded()}（前端提示改用键盘），绝不阻塞答疑；上游错误抛 {@code ASR_FAILED}(502)、
 * 超额抛 {@code ASR_RATE_LIMITED}(429)。
 */
@Service
public class XfyunIatService {

  private static final Logger log = LoggerFactory.getLogger(XfyunIatService.class);
  private static final HttpClient WS_CLIENT = HttpClient.newHttpClient();
  private static final int WS_TIMEOUT_SECONDS = 60;
  private static final int TRANSCODE_TIMEOUT_SECONDS = 30;
  /** 讯飞 IAT 一帧 = 40ms 的 16k/16bit 单声道音频 = 1280 字节。 */
  private static final int FRAME_BYTES = 1280;
  /** 帧间隔（毫秒），按讯飞建议匀速上送，过快会被判限速。 */
  private static final long FRAME_INTERVAL_MS = 40;
  /** 上送音频上限 ≈ 60s（16000 * 2 * 60），超出截断，避免超长请求拖垮请求线程。 */
  private static final int MAX_PCM_BYTES = 16_000 * 2 * 60;

  private final AsrProperties properties;
  private final ObjectMapper objectMapper;
  private final String ffmpegPath;

  private final Map<Long, DailyCounter> rateLimitMap = new ConcurrentHashMap<>();

  public XfyunIatService(
      AsrProperties properties,
      ObjectMapper objectMapper,
      @Value("${pythontopia.media.ffmpegPath:ffmpeg}") String ffmpegPath) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.ffmpegPath = ffmpegPath;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  /**
   * 把上传音频转写为文本。
   *
   * @param userId 每日限流维度，可空
   * @param audio 浏览器上传的原始音频字节（任意 ffmpeg 可解码的容器/编码）
   * @param contentType 上传 MIME（仅用于猜测临时文件后缀，最终由 ffmpeg 自行嗅探）
   * @return 识别结果；不可用时返回 {@link AsrResponse#degraded()}
   * @throws BusinessException {@code ASR_RATE_LIMITED}(429)、{@code ASR_FAILED}(502)
   */
  public AsrResponse transcribe(Long userId, byte[] audio, String contentType) {
    if (!properties.isEnabled() || audio == null || audio.length == 0) {
      return AsrResponse.unavailable();
    }
    checkRateLimit(userId);

    byte[] pcm = transcodeToPcm(audio, contentType);
    if (pcm == null || pcm.length == 0) {
      // 无 ffmpeg / 转码失败：降级而非报错，前端提示改用键盘输入。
      return AsrResponse.unavailable();
    }
    if (pcm.length > MAX_PCM_BYTES) {
      pcm = Arrays.copyOf(pcm, MAX_PCM_BYTES);
    }

    try {
      String text = recognize(pcm);
      return AsrResponse.of(text);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("ASR transcription failed", e);
      throw new BusinessException(HttpStatus.BAD_GATEWAY, "ASR_FAILED", "语音转写失败");
    }
  }

  private String recognize(byte[] pcm) throws Exception {
    URI wsUri = buildAuthUrl();
    CompletableFuture<String> future = new CompletableFuture<>();
    StringBuilder result = new StringBuilder();

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
                      processFrame(frameBuf.toString(), result, future);
                      frameBuf.setLength(0);
                    }
                    webSocket.request(1);
                    return null;
                  }

                  @Override
                  public CompletionStage<?> onClose(
                      WebSocket webSocket, int statusCode, String reason) {
                    if (!future.isDone()) {
                      future.complete(result.toString());
                    }
                    return null;
                  }

                  @Override
                  public void onError(WebSocket webSocket, Throwable error) {
                    if (!future.isDone()) {
                      future.completeExceptionally(error);
                    }
                  }
                })
            .join();

    sendAudioFrames(ws, pcm);
    return future.get(WS_TIMEOUT_SECONDS, TimeUnit.SECONDS).trim();
  }

  private void sendAudioFrames(WebSocket ws, byte[] pcm) throws InterruptedException {
    int offset = 0;
    boolean first = true;
    while (offset < pcm.length) {
      int len = Math.min(FRAME_BYTES, pcm.length - offset);
      byte[] chunk = Arrays.copyOfRange(pcm, offset, offset + len);
      ws.sendText(buildAudioFrame(first ? 0 : 1, chunk), true);
      first = false;
      offset += len;
      Thread.sleep(FRAME_INTERVAL_MS);
    }
    // 收尾帧：status=2、空音频，通知讯飞音频结束。
    ws.sendText(buildAudioFrame(2, new byte[0]), true);
  }

  private void processFrame(String json, StringBuilder result, CompletableFuture<String> future) {
    try {
      JsonNode root = objectMapper.readTree(json);
      int code = root.path("code").asInt(-1);
      if (code != 0) {
        String msg = root.path("message").asText("unknown error");
        log.warn("Xfyun IAT error: code={} message={}", code, msg);
        if (!future.isDone()) {
          future.completeExceptionally(
              new IllegalStateException("Xfyun IAT code=" + code + " message=" + msg));
        }
        return;
      }
      JsonNode ws = root.path("data").path("result").path("ws");
      if (ws.isArray()) {
        for (JsonNode wordEntry : ws) {
          for (JsonNode cw : wordEntry.path("cw")) {
            result.append(cw.path("w").asText(""));
          }
        }
      }
      int status = root.path("data").path("status").asInt(0);
      if (status == 2 && !future.isDone()) {
        future.complete(result.toString());
      }
    } catch (Exception e) {
      log.warn("Failed to process IAT frame", e);
      if (!future.isDone()) {
        future.completeExceptionally(e);
      }
    }
  }

  /** 用 ffmpeg 把任意上传音频转 16k 单声道 s16le 裸 PCM；无 ffmpeg / 失败返回 null（调用方降级）。 */
  private byte[] transcodeToPcm(byte[] input, String contentType) {
    Path tmpIn = null;
    Path tmpOut = null;
    try {
      tmpIn = Files.createTempFile("pyt-asr-", suffixFor(contentType));
      Files.write(tmpIn, input);
      tmpOut = Files.createTempFile("pyt-asr-", ".pcm");
      List<String> cmd =
          List.of(
              ffmpegPath,
              "-y",
              "-i",
              tmpIn.toString(),
              "-ac",
              "1",
              "-ar",
              "16000",
              "-f",
              "s16le",
              "-acodec",
              "pcm_s16le",
              tmpOut.toString());
      Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      boolean done = process.waitFor(TRANSCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!done) {
        process.destroyForcibly();
        log.warn("ffmpeg ASR transcode timed out");
        return null;
      }
      if (process.exitValue() != 0) {
        log.warn("ffmpeg ASR transcode exit {}", process.exitValue());
        return null;
      }
      return Files.readAllBytes(tmpOut);
    } catch (IOException ex) {
      // ffmpeg 未安装也走这里（IOException: Cannot run program）。
      log.warn("ffmpeg ASR transcode unavailable: {}", ex.getMessage());
      return null;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return null;
    } finally {
      deleteQuietly(tmpIn);
      deleteQuietly(tmpOut);
    }
  }

  private static String suffixFor(String contentType) {
    if (contentType == null) {
      return ".bin";
    }
    String ct = contentType.toLowerCase(Locale.ROOT);
    if (ct.contains("webm")) {
      return ".webm";
    }
    if (ct.contains("ogg")) {
      return ".ogg";
    }
    if (ct.contains("wav")) {
      return ".wav";
    }
    if (ct.contains("mpeg") || ct.contains("mp3")) {
      return ".mp3";
    }
    if (ct.contains("mp4") || ct.contains("m4a") || ct.contains("aac")) {
      return ".m4a";
    }
    return ".bin";
  }

  private static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // 临时文件尽力清理，失败忽略
    }
  }

  private void checkRateLimit(Long userId) {
    if (userId == null) {
      return;
    }
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
          "ASR_RATE_LIMITED",
          "每日语音转写上限 " + properties.dailyLimitPerUser() + " 次");
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

  private String buildAudioFrame(int status, byte[] chunk) {
    try {
      ObjectNode root = objectMapper.createObjectNode();
      if (status == 0) {
        root.putObject("common").put("app_id", properties.appId());
        ObjectNode business = root.putObject("business");
        business.put("language", "zh_cn");
        business.put("domain", "iat");
        business.put("accent", "mandarin");
        business.put("vad_eos", 3000);
      }
      ObjectNode data = root.putObject("data");
      data.put("status", status);
      data.put("format", "audio/L16;rate=16000");
      data.put("encoding", "raw");
      data.put("audio", Base64.getEncoder().encodeToString(chunk));
      return objectMapper.writeValueAsString(root);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to build IAT frame", ex);
    }
  }

  private long todayEpochDay() {
    return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
  }

  private record DailyCounter(long day, AtomicInteger count) {}
}
