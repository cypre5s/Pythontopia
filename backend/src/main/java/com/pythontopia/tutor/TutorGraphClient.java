package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.GraphSseConsumer;
import com.pythontopia.common.sse.SseRelayExecutor;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class TutorGraphClient {

  private static final Logger log = LoggerFactory.getLogger(TutorGraphClient.class);

  private final GraphServiceProperties properties;
  private final ObjectMapper objectMapper;
  private final SseRelayExecutor sseRelayExecutor;

  public TutorGraphClient(
      GraphServiceProperties properties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.sseRelayExecutor = sseRelayExecutor;
  }

  /**
   * Streams the tutor ask response via SSE from the Python graph service.
   * Relays all events through the emitter; calls onResult when the final result arrives.
   */
  public void askStream(
      Long userId,
      String question,
      List<String> kcCodes,
      String context,
      SseEmitter emitter,
      Consumer<String> onResult) {
    String url = properties.graphUrl() + "/agents/tutor/ask/stream";
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("user_id", userId);
    body.put("question", question);
    // 复数 kc_codes，与 Python schema 对齐（修正此前 kc_code 单复数错配的丢参 bug）。
    if (kcCodes != null && !kcCodes.isEmpty()) {
      body.put("kc_codes", kcCodes);
    }
    if (context != null && !context.isBlank()) {
      body.put("context", context);
    }

    sseRelayExecutor.submit(() -> {
      try {
        GraphSseConsumer.consume(url, body, objectMapper, (event, data) -> {
          try {
            emitter.send(SseEmitter.event().name(event).data(data));
            if ("result".equals(event)) {
              onResult.accept(data);
            }
          } catch (IOException e) {
            log.debug("Tutor SSE emitter write failed, client likely disconnected", e);
          }
        });
        emitter.complete();
      } catch (IOException e) {
        log.error("Tutor graph SSE stream failed", e);
        emitter.completeWithError(e);
      }
    });
  }
}
