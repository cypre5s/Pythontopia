package com.pythontopia.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.GraphSseConsumer;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import com.pythontopia.orchestrator.dto.OrchestratorTurnMessage;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 学习总管 SSE 代理客户端，镜像 {@code TutorGraphClient}：把前端请求转成 Python graph 服务期望的
 * snake_case body，并在后台线程里把上游 SSE 事件（trace / token / result / error）原样转发给浏览器。
 * 不做任何落库——会话记忆全部在 Python 服务侧。
 */
@Component
public class OrchestratorGraphClient {

  private static final Logger log = LoggerFactory.getLogger(OrchestratorGraphClient.class);

  private final GraphServiceProperties properties;
  private final ObjectMapper objectMapper;
  private final SseRelayExecutor sseRelayExecutor;

  public OrchestratorGraphClient(
      GraphServiceProperties properties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.sseRelayExecutor = sseRelayExecutor;
  }

  /**
   * 打开到 Python graph 服务的 SSE 连接并把每个事件按名字转发给 {@code emitter}。
   * 仅把非空的可选字段写入 body，避免给下游传一堆 null。
   */
  public void turnStream(
      Long userId,
      OrchestratorTurnRequest request,
      List<String> decayingKcs,
      SseEmitter emitter) {
    String url = properties.graphUrl() + "/agents/orchestrator/turn/stream";

    String sessionId = request.sessionId();
    if (sessionId == null || sessionId.isBlank()) {
      sessionId = "u" + userId + "-" + System.currentTimeMillis();
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("user_id", userId);
    body.put("session_id", sessionId);
    body.put("user_message", request.message());

    List<Map<String, Object>> history = buildHistory(request.history());
    if (!history.isEmpty()) {
      body.put("history", history);
    }
    if (request.profileBrief() != null && !request.profileBrief().isBlank()) {
      body.put("profile_brief", request.profileBrief());
    }
    if (request.currentChapter() != null) {
      body.put("current_chapter", request.currentChapter());
    }
    if (request.recentKcCodes() != null && !request.recentKcCodes().isEmpty()) {
      body.put("recent_kc_codes", request.recentKcCodes());
    }
    if (request.recentFailures() != null && !request.recentFailures().isEmpty()) {
      body.put("recent_failures", request.recentFailures());
    }
    if (request.courseKcCodes() != null && !request.courseKcCodes().isEmpty()) {
      body.put("course_kc_index", request.courseKcCodes());
    }
    // 记忆矿脉回声：服务端从 ReviewSignal 算出的到期 KC，供总管把计划优先锚定到待复习知识点。
    if (decayingKcs != null && !decayingKcs.isEmpty()) {
      body.put("decaying_kcs", decayingKcs);
    }

    sseRelayExecutor.submit(
        () -> {
          try {
            GraphSseConsumer.consume(
                url,
                body,
                objectMapper,
                GraphInternalTokenSupport.tokenOrNull(properties),
                (event, data) -> {
                  try {
                    emitter.send(SseEmitter.event().name(event).data(data));
                  } catch (IOException e) {
                    log.debug(
                        "Orchestrator SSE emitter write failed, client likely disconnected", e);
                  }
                });
            emitter.complete();
          } catch (IOException e) {
            log.error("Orchestrator graph SSE stream failed", e);
            emitter.completeWithError(e);
          }
        });
  }

  private List<Map<String, Object>> buildHistory(List<OrchestratorTurnMessage> history) {
    List<Map<String, Object>> payload = new ArrayList<>();
    if (history == null) {
      return payload;
    }
    for (OrchestratorTurnMessage message : history) {
      if (message == null) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("role", message.role());
      entry.put("content", message.content());
      payload.add(entry);
    }
    return payload;
  }
}
