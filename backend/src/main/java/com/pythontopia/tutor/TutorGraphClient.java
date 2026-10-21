package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphHttpClientProperties;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.GraphStreamTransport;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.common.web.GraphInternalTokenSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class TutorGraphClient {

  /** 流式读不活动上限：与 {@code TutorService} 的 SseEmitter 120s 窗口对齐（复用既有流超时约定）。 */
  static final int STREAM_READ_TIMEOUT_MS = 120_000;

  private final GraphServiceProperties properties;
  private final SseRelayExecutor sseRelayExecutor;
  private final GraphStreamRelay relay;

  /** Spring 注入构造：连接超时复用 graphRestTemplate 的 {@code pythontopia.http.graph} 配置（fail fast）。 */
  @Autowired
  public TutorGraphClient(
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
  TutorGraphClient(
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
   * 从 Python graph 服务以 SSE 流式获取导师解答：所有事件经 {@code emitter} 转发，
   * 收到最终 {@code result} 事件时回调 {@code onResult}。
   *
   * <p>图片原样转发给 graph，由 DeepSeek 视觉模型直接理解；TutorAgent 仍负责文字回答和既有 RAG、
   * grounding、安全审查。
   *
   * @param onFinished 流终结（成功 / 失败 / 客户端断开）后恰好执行一次的资源释放回调
   */
  public void askStream(
      Long userId,
      String question,
      List<String> kcCodes,
      String context,
      List<String> images,
      String profileBrief,
      SseEmitter emitter,
      Consumer<String> onResult,
      Runnable onFinished) {
    String url = properties.graphUrl() + "/agents/tutor/ask/stream";
    String internalToken = GraphInternalTokenSupport.tokenOrNull(properties);

    sseRelayExecutor.submit(
        () ->
            relay.relay(
                url,
                () -> buildBody(userId, question, kcCodes, context, images, profileBrief),
                internalToken,
                emitter,
                (event, data) -> {
                  // graph 已迁 AG-UI：终态走 RUN_FINISHED（形状 {"result":{...}}），历史名 result 是裸结果。
                  // 两种都触发落库，否则 tutor_session 永远写不进、历史问答恒为空。
                  if ("result".equals(event) || "RUN_FINISHED".equals(event)) {
                    onResult.accept(data);
                  }
                },
                onFinished));
  }

  private Map<String, Object> buildBody(
      Long userId,
      String question,
      List<String> kcCodes,
      String context,
      List<String> images,
      String profileBrief) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("user_id", userId);
    body.put("question", question);
    // kc_codes 用复数，与 Python schema 对齐。
    if (kcCodes != null && !kcCodes.isEmpty()) {
      body.put("kc_codes", kcCodes);
    }
    if (context != null && !context.isBlank()) {
      body.put("context", context);
    }
    if (images != null && !images.isEmpty()) {
      body.put("images", images);
    }
    // 因材施教：把画像摘要（认知风格/易错点）下传 graph，TutorAgent 据此分支首屏讲法。
    if (profileBrief != null && !profileBrief.isBlank()) {
      body.put("profile_brief", profileBrief);
    }
    return body;
  }
}
