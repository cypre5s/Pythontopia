package com.pythontopia.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphHttpClientProperties;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import com.pythontopia.orchestrator.dto.OrchestratorTurnMessage;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import com.pythontopia.tutor.GraphStreamRelay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 学习总管 SSE 代理客户端，镜像 {@code TutorGraphClient}：把前端请求转成 Python graph 服务期望的
 * snake_case body，并在后台线程里经 {@link GraphStreamRelay} 把上游 SSE 事件（trace / token /
 * result / error）原样转发给浏览器；故障语义（快速失败 / 读超时 / 单条终止性 error / 断开清理）
 * 由中继统一保证。不做任何落库——会话记忆全部在 Python 服务侧。
 */
@Component
public class OrchestratorGraphClient {

  /** 流式读不活动上限：与 {@code OrchestratorService} 的长任务窗口对齐，覆盖有界 agentic loop。 */
  static final int STREAM_READ_TIMEOUT_MS = 300_000;

  private final GraphServiceProperties properties;
  private final SseRelayExecutor sseRelayExecutor;
  private final GraphStreamRelay relay;

  /** Spring 注入构造：连接超时复用 graphRestTemplate 的 {@code pythontopia.http.graph} 配置（fail fast）。 */
  @Autowired
  public OrchestratorGraphClient(
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
  OrchestratorGraphClient(
      GraphServiceProperties properties,
      ObjectMapper objectMapper,
      SseRelayExecutor sseRelayExecutor,
      int connectTimeoutMs,
      int readTimeoutMs) {
    this.properties = properties;
    this.sseRelayExecutor = sseRelayExecutor;
    this.relay = new GraphStreamRelay(objectMapper, connectTimeoutMs, readTimeoutMs);
  }

  /**
   * 打开到 Python graph 服务的 SSE 连接并把每个事件按名字转发给 {@code emitter}。
   * 仅把非空的可选字段写入 body，避免给下游传一堆 null。
   *
   * @param onFinished 流终结（成功 / 失败 / 客户端断开）后恰好执行一次的资源释放回调
   */
  public void turnStream(
      Long userId,
      OrchestratorTurnRequest request,
      List<String> decayingKcs,
      SseEmitter emitter,
      Runnable onFinished) {
    relayStream(
        properties.graphUrl() + "/agents/orchestrator/turn/stream",
        userId,
        request,
        decayingKcs,
        emitter,
        onFinished);
  }

  /**
   * 混合工具编排（Phase F）SSE 代理：转发 graph {@code /agents/orchestrator/tool-loop/stream}
   * （{@code PYT_TOOL_LOOP} flag-gated；flag off 时 graph 返回 404，由中继按错误处理）。
   * 工具编排不需要记忆矿脉到期 KC，故 {@code decaying_kcs} 传空。
   */
  public void toolLoopStream(
      Long userId, OrchestratorTurnRequest request, SseEmitter emitter, Runnable onFinished) {
    relayStream(
        properties.graphUrl() + "/agents/orchestrator/tool-loop/stream",
        userId,
        request,
        List.of(),
        emitter,
        onFinished);
  }

  private void relayStream(
      String url,
      Long userId,
      OrchestratorTurnRequest request,
      List<String> decayingKcs,
      SseEmitter emitter,
      Runnable onFinished) {
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

    String internalToken = GraphInternalTokenSupport.tokenOrNull(properties);
    sseRelayExecutor.submit(
        () -> relay.relay(url, () -> body, internalToken, emitter, null, onFinished));
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
