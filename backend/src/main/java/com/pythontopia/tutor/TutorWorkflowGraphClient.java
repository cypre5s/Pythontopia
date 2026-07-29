package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphHttpClientProperties;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.GraphStreamTransport;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 导学工作流 SSE 中继：转发 graph /agents/tutor-workflow/run/stream，事件原样透传。 */
@Component
public class TutorWorkflowGraphClient {

  /** 流式读不活动上限：与 {@code TutorWorkflowService} 的 SseEmitter 180s 窗口对齐。 */
  static final int STREAM_READ_TIMEOUT_MS = 180_000;

  private final GraphServiceProperties properties;
  private final SseRelayExecutor sseRelayExecutor;
  private final GraphStreamTransport relay;

  @Autowired
  public TutorWorkflowGraphClient(
      GraphServiceProperties properties,
      GraphHttpClientProperties httpProperties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor) {
    this(
        properties,
        objectMapper,
        sseRelayExecutor,
        httpProperties.connectTimeoutMs(),
        STREAM_READ_TIMEOUT_MS);
  }

  /** 包内可见：测试用可控超时构造。 */
  TutorWorkflowGraphClient(
      GraphServiceProperties properties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor,
      int connectTimeoutMs,
      int readTimeoutMs) {
    this.properties = properties;
    this.sseRelayExecutor = sseRelayExecutor;
    this.relay = new GraphStreamTransport(objectMapper, connectTimeoutMs, readTimeoutMs);
  }

  /**
   * 转发一次工作流事件 run：所有 SSE 事件经 {@code emitter} 原样透传；收到旧 {@code result} 或
   * AG-UI {@code RUN_FINISHED} 时，以原始 data 回调 {@code onResult} 做投影落库。
   */
  public void runStream(Map<String, Object> body, SseEmitter emitter, Consumer<String> onResult) {
    String url = properties.graphUrl() + "/agents/tutor-workflow/run/stream";
    String internalToken = GraphInternalTokenSupport.tokenOrNull(properties);
    sseRelayExecutor.submit(
        () ->
            relay.relay(
                url,
                () -> body,
                internalToken,
                emitter,
                (event, data) -> {
                  if ("result".equals(event) || "RUN_FINISHED".equals(event)) {
                    onResult.accept(data);
                  }
                },
                null));
  }
}
