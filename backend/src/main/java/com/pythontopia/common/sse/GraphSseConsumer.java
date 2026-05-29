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
  // Pin HTTP/1.1: the JDK HttpClient defaults to HTTP/2 and tries an h2c cleartext upgrade
  // (Connection: Upgrade, HTTP2-Settings) on plain http://, which the uvicorn SSE server rejects
  // as "Unsupported upgrade request / Invalid HTTP request" — the relay then fails (HTTP 400) and
  // the tutor/workshop stream breaks. SSE is inherently HTTP/1.1, so force it.
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
    String json;
    try {
      json = mapper.writeValueAsString(body);
    } catch (IOException e) {
      throw new IOException("Failed to serialize SSE request body", e);
    }

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofMinutes(3))
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();

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
      String currentEvent = "message";
      StringBuilder dataBuffer = new StringBuilder();

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("event:")) {
          currentEvent = line.substring("event:".length()).trim();
        } else if (line.startsWith("data:")) {
          dataBuffer.append(line.substring("data:".length()).trim());
        } else if (line.isEmpty() && dataBuffer.length() > 0) {
          try {
            onEvent.accept(currentEvent, dataBuffer.toString());
          } catch (Exception e) {
            log.warn("SSE event handler error for event={}", currentEvent, e);
          }
          currentEvent = "message";
          dataBuffer.setLength(0);
        }
      }
      if (dataBuffer.length() > 0) {
        try {
          onEvent.accept(currentEvent, dataBuffer.toString());
        } catch (Exception e) {
          log.warn("SSE event handler error for trailing event={}", currentEvent, e);
        }
      }
    }
  }
}
