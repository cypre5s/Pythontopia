package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.web.GraphInternalSubjectSupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * tutor / orchestrator 共用的有界 graph SSE 中继（学习总管按既有「镜像 tutor」约定复用本类；
 * 共享代码放本包是因为 {@code OrchestratorGraphClient} 自诞生起就是 {@code TutorGraphClient} 的镜像）。
 *
 * <p>相对直接用 {@code GraphSseConsumer} 的旧路径，收紧了故障语义：
 *
 * <ul>
 *   <li><b>连接失败 / 上游 HTTP ≥400</b>：在 connect/read 超时阈值内快速失败，绝不无限挂起；
 *   <li><b>读超时</b>：上游卡死时按 {@code readTimeoutMs}（socket 不活动上限）切断；
 *   <li><b>终止性 error 恰好一条</b>：任何故障只向客户端发一条 {@code event: error}（形状
 *       {@code {"code","message"}}，与 WorkshopGraphClient / MediaJobService 既有约定一致），
 *       文案中文、简短、可执行，<b>绝不泄漏</b>内部 URL / 端口 / 堆栈 / 内部 token（详情只进服务端日志）；
 *   <li><b>部分内容保留</b>：故障前已透传的 trace / token 原样保留，error 总在其后；
 *   <li><b>坏损流</b>：未以空行收束的半截事件帧按 SSE 规范丢弃（防止半截 result 被当正常结果）；
 *       上游未发终态事件（{@code result} / {@code error} / AG-UI {@code RUN_FINISHED} /
 *       {@code RUN_ERROR}）即关闭时补一条终止性 error，绝不静默当成功收尾；
 *   <li><b>客户端断开</b>：emitter 写失败立即停止消费上游并关闭连接，不再空转；
 *   <li><b>资源恰好关闭一次</b>：所有路径汇聚到同一 finally——关上游连接、{@code complete()} 一次
 *       （不裸 {@code completeWithError} 断流）、执行一次 {@code onFinished} 释放回调。
 * </ul>
 */
public final class GraphStreamRelay {

  static final String CODE_UNAVAILABLE = "AI_SERVICE_UNAVAILABLE";
  static final String CODE_TIMEOUT = "AI_SERVICE_TIMEOUT";
  static final String CODE_INTERRUPTED = "AI_STREAM_INTERRUPTED";

  static final String MSG_UNAVAILABLE = "AI 服务暂时不可用，请稍后重试";
  static final String MSG_TIMEOUT = "AI 服务响应超时，请稍后重试";
  static final String MSG_INTERRUPTED = "回答传输中断，请重试";

  /** 上游终态事件名：旧约定 result / error 与 AG-UI RUN_FINISHED / RUN_ERROR 都接。 */
  private static final Set<String> TERMINAL_EVENTS =
      Set.of("result", "error", "RUN_FINISHED", "RUN_ERROR");

  private static final Logger log = LoggerFactory.getLogger(GraphStreamRelay.class);

  private final ObjectMapper objectMapper;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;

  public GraphStreamRelay(ObjectMapper objectMapper, int connectTimeoutMs, int readTimeoutMs) {
    this.objectMapper = objectMapper;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
  }

  private enum StreamEnd {
    /** 上游正常发出终态事件。 */
    TERMINAL,
    /** 客户端断开（emitter 写失败），停止消费。 */
    CLIENT_GONE,
    /** 上游在终态事件前关闭（坏损 / 截断）。 */
    TRUNCATED
  }

  /**
   * 在当前（中继）线程同步执行一次转发。返回前保证：上游连接已关闭、{@code emitter} 恰好被
   * complete 一次、{@code onFinished} 恰好执行一次。
   *
   * @param bodySupplier 请求体装配（在中继线程内求值，失败按服务不可用处理）
   * @param onEvent 每个成功透传事件的旁路回调（如 result 落库），可空；回调异常不打断流
   * @param onFinished 资源释放回调，可空
   */
  public void relay(
      String url,
      Supplier<?> bodySupplier,
      String internalToken,
      SseEmitter emitter,
      BiConsumer<String, String> onEvent,
      Runnable onFinished) {
    relay(url, bodySupplier, internalToken, null, emitter, onEvent, onFinished);
  }

  /**
   * Stateful Graph 中继：除服务 token 外携带由 Java JWT 身份派生的可信主体。
   */
  public void relay(
      String url,
      Supplier<?> bodySupplier,
      String internalToken,
      String internalSubject,
      SseEmitter emitter,
      BiConsumer<String, String> onEvent,
      Runnable onFinished) {
    HttpURLConnection connection = null;
    try {
      byte[] payload = objectMapper.writeValueAsBytes(bodySupplier.get());
      connection = open(url, internalToken, internalSubject, payload);
      int status = connection.getResponseCode();
      if (status >= 400) {
        log.error("Graph SSE endpoint {} returned HTTP {}", url, status);
        sendTerminalError(emitter, CODE_UNAVAILABLE, MSG_UNAVAILABLE);
        return;
      }
      StreamEnd end = relayEvents(connection, emitter, onEvent);
      if (end == StreamEnd.TRUNCATED) {
        log.error("Graph SSE stream {} closed before terminal event", url);
        sendTerminalError(emitter, CODE_INTERRUPTED, MSG_INTERRUPTED);
      }
    } catch (SocketTimeoutException e) {
      log.error(
          "Graph SSE relay timed out readTimeoutMs={} errorType={}",
          readTimeoutMs,
          e.getClass().getSimpleName());
      sendTerminalError(emitter, CODE_TIMEOUT, MSG_TIMEOUT);
    } catch (IOException | RuntimeException e) {
      log.error("Graph SSE relay failed errorType={}", e.getClass().getSimpleName());
      sendTerminalError(emitter, CODE_UNAVAILABLE, MSG_UNAVAILABLE);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
      completeQuietly(emitter);
      runQuietly(onFinished);
    }
  }

  /** 逐帧透传上游 SSE；解析规则与 {@code GraphSseConsumer} 一致（event:/data:/空行分发）。 */
  private StreamEnd relayEvents(
      HttpURLConnection connection, SseEmitter emitter, BiConsumer<String, String> onEvent)
      throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
      String currentEvent = "message";
      StringBuilder dataBuffer = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("event:")) {
          currentEvent = line.substring("event:".length()).trim();
        } else if (line.startsWith("data:")) {
          dataBuffer.append(line.substring("data:".length()).trim());
        } else if (line.isEmpty() && dataBuffer.length() > 0) {
          String eventName = currentEvent;
          String data = dataBuffer.toString();
          currentEvent = "message";
          dataBuffer.setLength(0);
          try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
          } catch (IOException | IllegalStateException sendFailure) {
            log.debug(
                "SSE client gone, aborting graph relay errorType={}",
                sendFailure.getClass().getSimpleName());
            return StreamEnd.CLIENT_GONE;
          }
          notifyPeek(onEvent, eventName, data);
          if (TERMINAL_EVENTS.contains(eventName)) {
            return StreamEnd.TERMINAL;
          }
        }
      }
      if (dataBuffer.length() > 0) {
        // 未以空行收束的半截帧：按 SSE 规范丢弃，防止把截断 JSON 当正常事件透传。
        log.warn(
            "Discarding unterminated trailing SSE frame (event={}, {} chars)",
            currentEvent,
            dataBuffer.length());
      }
      return StreamEnd.TRUNCATED;
    }
  }

  private HttpURLConnection open(
      String url, String internalToken, String internalSubject, byte[] payload)
      throws IOException {
    HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
    connection.setConnectTimeout(connectTimeoutMs);
    connection.setReadTimeout(readTimeoutMs);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setUseCaches(false);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Accept", "text/event-stream");
    if (internalToken != null && !internalToken.isBlank()) {
      connection.setRequestProperty("X-Internal-Token", internalToken);
    }
    if (internalSubject != null && !internalSubject.isBlank()) {
      connection.setRequestProperty(GraphInternalSubjectSupport.HEADER, internalSubject);
    }
    connection.setFixedLengthStreamingMode(payload.length);
    try (OutputStream out = connection.getOutputStream()) {
      out.write(payload);
    }
    return connection;
  }

  /** 终止性 error 事件：固定中文短文案 + 错误码，内部细节只进服务端日志。 */
  private void sendTerminalError(SseEmitter emitter, String code, String message) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("code", code);
      payload.put("message", message);
      emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(payload)));
    } catch (Exception sendFailure) {
      log.debug(
          "Failed to deliver terminal SSE error {} errorType={}",
          code,
          sendFailure.getClass().getSimpleName());
    }
  }

  private void notifyPeek(BiConsumer<String, String> onEvent, String event, String data) {
    if (onEvent == null) {
      return;
    }
    try {
      onEvent.accept(event, data);
    } catch (RuntimeException e) {
      log.warn(
          "SSE relay peek callback failed for event={} errorType={}",
          event,
          e.getClass().getSimpleName());
    }
  }

  private static void completeQuietly(SseEmitter emitter) {
    try {
      emitter.complete();
    } catch (Exception e) {
      log.debug("SSE emitter already terminated errorType={}", e.getClass().getSimpleName());
    }
  }

  private static void runQuietly(Runnable onFinished) {
    if (onFinished == null) {
      return;
    }
    try {
      onFinished.run();
    } catch (RuntimeException e) {
      log.warn("SSE relay onFinished callback failed errorType={}", e.getClass().getSimpleName());
    }
  }
}
