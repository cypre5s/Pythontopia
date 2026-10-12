package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.GraphSseConsumer;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.GraphInternalSubjectSupport;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 导学工作流 SSE 中继：转发 graph /agents/tutor-workflow/run/stream，事件原样透传。 */
@Component
public class TutorWorkflowGraphClient {

  private static final Logger log = LoggerFactory.getLogger(TutorWorkflowGraphClient.class);

  private final GraphServiceProperties properties;
  private final ObjectMapper objectMapper;
  private final SseRelayExecutor sseRelayExecutor;

  public TutorWorkflowGraphClient(
      GraphServiceProperties properties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.sseRelayExecutor = sseRelayExecutor;
  }

  /**
   * 转发一次工作流事件 run：所有 SSE 事件经 {@code emitter} 透传；收到终态 {@code result}
   * （AG-UI RUN_FINISHED 已由 GraphSseConsumer 归一为 result）时回调 {@code onResult} 做投影落库。
   */
  public void runStream(
      Long userId,
      Map<String, Object> body,
      SseEmitter emitter,
      Consumer<String> onResult) {
    String url = properties.graphUrl() + "/agents/tutor-workflow/run/stream";
    sseRelayExecutor.submit(
        () -> {
          try {
            GraphSseConsumer.consume(
                url,
                body,
                objectMapper,
                GraphInternalTokenSupport.tokenOrNull(properties),
                GraphInternalSubjectSupport.user(userId),
                (event, data) -> {
                  try {
                    emitter.send(SseEmitter.event().name(event).data(data));
                    if ("result".equals(event) || "RUN_FINISHED".equals(event)) {
                      onResult.accept(data);
                    }
                  } catch (IOException e) {
                    log.debug("Tutor workflow SSE write failed, client likely disconnected", e);
                  }
                });
            emitter.complete();
          } catch (IOException e) {
            log.error("Tutor workflow graph SSE stream failed", e);
            emitter.completeWithError(e);
          }
        });
  }
}
