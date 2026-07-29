package com.pythontopia.common.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes an SSE stream from the Python graph service and relays parsed events.
 * Uses JDK HttpClient so no WebFlux dependency is required.
 */
public final class GraphSseConsumer {

  private static final Logger log = LoggerFactory.getLogger(GraphSseConsumer.class);
  // 强制 HTTP/1.1：JDK HttpClient 默认 HTTP/2，会对明文 http:// 发起 h2c 升级，被 uvicorn
  // SSE 服务端拒绝（HTTP 400），导致答疑/工坊流断开；SSE 本就基于 HTTP/1.1。
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(10))
          .build();

  private GraphSseConsumer() {}

  /**
   * Opens a POST SSE connection and relays every (event, data) pair to the callback.
   * Blocks until the stream ends or an error occurs.
   *
   * @param url        full URL of the streaming endpoint
   * @param body       JSON request body
   * @param mapper     ObjectMapper for serializing the body
   * @param onEvent    callback receiving (eventName, dataPayload)
   */
  public static void consume(
      String url,
      Object body,
      ObjectMapper mapper,
      BiConsumer<String, String> onEvent) throws IOException {
    consume(url, body, mapper, null, onEvent);
  }

  /**
   * Opens a POST SSE connection and relays every (event, data) pair to the callback.
   * When {@code internalToken} is non-blank, sends {@code X-Internal-Token} for graph auth.
   */
  public static void consume(
      String url,
      Object body,
      ObjectMapper mapper,
      String internalToken,
      BiConsumer<String, String> onEvent) throws IOException {
    String json;
    try {
      json = mapper.writeValueAsString(body);
    } catch (IOException e) {
      throw new IOException("Failed to serialize SSE request body", e);
    }

    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofMinutes(3));
    if (internalToken != null && !internalToken.isBlank()) {
      requestBuilder.header("X-Internal-Token", internalToken);
    }
    HttpRequest request =
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();

    HttpResponse<java.io.InputStream> response;
    try {
      response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("SSE connection interrupted", e);
    }

    if (response.statusCode() >= 400) {
      throw new IOException("Graph SSE returned HTTP " + response.statusCode());
    }

    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
      SseFrameDecoder.DecodeResult result =
          SseFrameDecoder.decode(reader, frame -> {
            try {
              onEvent.accept(frame.event(), frame.data());
            } catch (Exception e) {
              log.warn("SSE event handler error for event={}", frame.event(), e);
            }
            return true;
          });
      if (result == SseFrameDecoder.DecodeResult.TRUNCATED) {
        log.warn("Discarding unterminated trailing Graph SSE frame");
      }
    }
  }
}
