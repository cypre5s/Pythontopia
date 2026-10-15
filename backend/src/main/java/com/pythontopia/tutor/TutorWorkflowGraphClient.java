package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.GraphSseConsumer;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.GraphInternalSubjectSupport;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
    runStream(userId, body, emitter, onResult, ignored -> {}, () -> {});
  }

  /** Relays results and records explicit Graph failures or interrupted streams for recovery. */
  public void runStream(
      Long userId,
      Map<String, Object> body,
      SseEmitter emitter,
      Consumer<String> onResult,
      Consumer<String> onError,
      Runnable onInterrupted) {
    String url = properties.graphUrl() + "/agents/tutor-workflow/run/stream";
    AtomicBoolean projectionFailed = new AtomicBoolean();
    AtomicBoolean terminalResult = new AtomicBoolean();
    AtomicBoolean terminalError = new AtomicBoolean();
    AtomicBoolean downstreamGone = new AtomicBoolean();
    emitter.onTimeout(() -> downstreamGone.set(true));
    emitter.onError(ignored -> downstreamGone.set(true));
    emitter.onCompletion(() -> downstreamGone.set(true));
    sseRelayExecutor.submit(
        () -> {
          try {
            GraphSseConsumer.consume(
                url,
                body,
                objectMapper,
                GraphInternalTokenSupport.tokenOrNull(properties),
                GraphInternalSubjectSupport.user(userId),
                downstreamGone::get,
                (event, data) -> {
                  if (projectionFailed.get()) {
                    return;
                  }
                  if ("result".equals(event) || "RUN_FINISHED".equals(event)) {
                    try {
                      onResult.accept(data);
                      terminalResult.set(true);
                    } catch (RuntimeException e) {
                      projectionFailed.set(true);
                      terminalError.set(true);
                      invokeError(onError, "Tutor workflow result projection failed");
                      log.error(
                          "Tutor workflow result projection failed errorType={}",
                          e.getClass().getSimpleName());
                      emitter.completeWithError(
                          new IllegalStateException("Tutor workflow result projection failed"));
                      return;
                    }
                  } else if ("error".equals(event) || "RUN_ERROR".equals(event)) {
                    terminalError.set(true);
                    invokeError(onError, data);
                  }
                  try {
                    emitter.send(SseEmitter.event().name(event).data(data));
                  } catch (IOException e) {
                    log.debug(
                        "Tutor workflow SSE write failed, client likely disconnected errorType={}",
                        e.getClass().getSimpleName());
                    throw new GraphSseConsumer.AbortSseConsumption(e);
                  }
                });
            if (!projectionFailed.get() && !terminalResult.get() && !terminalError.get()) {
              invokeInterrupted(onInterrupted);
            }
            if (!projectionFailed.get()) {
              emitter.complete();
            }
          } catch (GraphSseConsumer.AbortSseConsumption e) {
            if (!terminalResult.get() && !terminalError.get()) {
              invokeInterrupted(onInterrupted);
            }
            log.debug("Tutor workflow graph SSE cancelled after downstream disconnect");
            emitter.complete();
          } catch (IOException e) {
            if (!terminalResult.get() && !terminalError.get()) {
              invokeInterrupted(onInterrupted);
            }
            log.error(
                "Tutor workflow graph SSE stream failed errorType={}",
                e.getClass().getSimpleName());
            emitter.completeWithError(e);
          }
        });
  }

  private void invokeError(Consumer<String> callback, String detail) {
    try {
      callback.accept(detail);
    } catch (RuntimeException e) {
      log.error(
          "Tutor workflow failure checkpoint update failed errorType={}",
          e.getClass().getSimpleName());
    }
  }

  private void invokeInterrupted(Runnable callback) {
    try {
      callback.run();
    } catch (RuntimeException e) {
      log.error(
          "Tutor workflow interruption checkpoint update failed errorType={}",
          e.getClass().getSimpleName());
    }
  }
}
