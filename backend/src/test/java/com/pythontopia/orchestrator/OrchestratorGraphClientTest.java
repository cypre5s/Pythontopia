package com.pythontopia.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.intent.LearnerRoutingContext;
import com.pythontopia.intent.RouteExecutionTokenSignerTest;
import com.pythontopia.intent.dto.IntentRouteRequest;
import com.pythontopia.orchestrator.dto.OrchestratorTurnMessage;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用 JDK 自带的 {@link HttpServer} 起一个本地假 graph 服务，端到端验证：
 *
 * <ul>
 *   <li>转发给 Python 的 body 全部是约定好的 snake_case 字段，值正确，可选字段为空时不下发；
 *   <li>上游 SSE 事件（trace / token / result）被原样按事件名转发到 {@link SseEmitter}。
 * </ul>
 *
 * 全程不依赖数据库或外部服务，因此可在任意环境稳定运行。
 */
class OrchestratorGraphClientTest {

  private static final String SSE_BODY =
      "event: trace\n"
          + "data: {\"node\":\"planner\"}\n\n"
          + "event: token\n"
          + "data: {\"text\":\n"
          + "data: \"hi\"}\n\n"
          + "event: result\n"
          + "data: {\"reply_md\":\"done\"}\n\n";

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void streamReadTimeoutCoversBoundedAgenticLoop() {
    assertThat(OrchestratorGraphClient.STREAM_READ_TIMEOUT_MS)
        .as("orchestrator 需要容纳有界 agentic loop 的看/学/练/测工具链")
        .isGreaterThanOrEqualTo(300_000);
  }

  @Test
  void forwardsSnakeCaseBodyAndRelaysAllEvents() throws Exception {
    CompletableFuture<String> capturedBody = new CompletableFuture<>();
    CompletableFuture<String> capturedPath = new CompletableFuture<>();
    int port = startFakeGraph(capturedBody, capturedPath);

    OrchestratorTurnRequest request =
        new OrchestratorTurnRequest(
            "讲一下 for 循环",
            "   ",
            List.of(
                new OrchestratorTurnMessage("user", "你好"),
                new OrchestratorTurnMessage("assistant", "我是学习总管")),
            3,
            List.of("PY43-KC-010"),
            List.of("sub-99"),
            "偏好示例驱动",
            List.of("PY43-KC-001", "PY43-KC-010"),
            RouteExecutionTokenSignerTest.multiDecision("route_0123456789abcdef"),
            "signed-token-never-forwarded",
            new IntentRouteRequest.InteractionContext(
                "workshop-sess",
                "town_hub",
                "practice",
                41L,
                "print(1 / 0)",
                901L,
                List.of(22L),
                "RE: ZeroDivisionError",
                "debug",
                77L),
            learnerContext());
    OrchestratorRunRecorder recorder = mock(OrchestratorRunRecorder.class);
    @SuppressWarnings("unchecked")
    BiConsumer<String, String> peek = mock(BiConsumer.class);
    when(recorder.open(eq(7L), eq(request), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(
            new OrchestratorRunRecorder.RecordingHandle(
                99L,
                "{\"name\":\"runtime_run\",\"value\":{\"loop_run_id\":99}}",
                peek));
    OrchestratorGraphClient client = newClient(port, recorder);
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    client.turnStream(7L, request, List.of("PY43-KC-016", "PY43-KC-022"), emitter, () -> {});

    String body = capturedBody.get(5, TimeUnit.SECONDS);
    assertThat(capturedPath.get(5, TimeUnit.SECONDS))
        .isEqualTo("/agents/orchestrator/turn/stream");

    JsonNode json = new ObjectMapper().readTree(body);
    assertThat(json.get("user_id").asLong()).isEqualTo(7L);
    assertThat(json.get("user_message").asText()).isEqualTo("讲一下 for 循环");
    // 空白 sessionId -> 服务端生成 "u{userId}-{timestamp}"
    assertThat(json.get("session_id").asText()).startsWith("u7-");
    assertThat(json.get("current_chapter").asInt()).isEqualTo(3);
    assertThat(json.get("profile_brief").asText()).isEqualTo("偏好示例驱动");
    assertThat(json.get("recent_kc_codes")).hasSize(1);
    assertThat(json.get("recent_kc_codes").get(0).asText()).isEqualTo("PY43-KC-010");
    assertThat(json.get("recent_failures").get(0).asText()).isEqualTo("sub-99");
    assertThat(json.get("course_kc_index")).hasSize(2);
    assertThat(json.get("decaying_kcs")).hasSize(2);
    assertThat(json.get("decaying_kcs").get(0).asText()).isEqualTo("PY43-KC-016");
    assertThat(json.get("history")).hasSize(2);
    assertThat(json.get("history").get(0).get("role").asText()).isEqualTo("user");
    assertThat(json.get("history").get(0).get("content").asText()).isEqualTo("你好");
    assertThat(json.get("history").get(1).get("role").asText()).isEqualTo("assistant");
    assertThat(json.get("route_decision").get("routeId").asText())
        .isEqualTo("route_0123456789abcdef");
    assertThat(json.get("route_decision").get("task").get("subIntents").get(0).asText())
        .isEqualTo("HANDOUT");
    assertThat(json.has("route_execution_token")).isFalse();
    assertThat(json.get("interaction").get("latest_submission_id").asLong()).isEqualTo(901L);
    assertThat(json.get("interaction").get("current_code").asText()).isEqualTo("print(1 / 0)");
    assertThat(json.get("learner_context").get("cognitive_load").asText()).isEqualTo("HIGH");
    assertThat(
            json.get("learner_context")
                .get("kc_mastery")
                .get("PY43-KC-010")
                .asDouble())
        .isEqualTo(0.27);
    assertThat(
            json.get("learner_context")
                .get("recent_resources")
                .get(0)
                .get("type")
                .asText())
        .isEqualTo("MINDMAP");

    awaitEvent(emitter, "event:result");
    assertThat(emitter.events())
        .anyMatch(e -> e.contains("event:CUSTOM") && e.contains("loop_run_id"));
    assertThat(emitter.events()).anyMatch(e -> e.contains("event:trace"));
    assertThat(emitter.events()).anyMatch(e -> e.contains("event:token"));
    assertThat(emitter.events())
        .anyMatch(e -> e.contains("event:result") && e.contains("reply_md"));
    verify(peek).accept("token", "{\"text\":\n\"hi\"}");
    verify(peek).accept("result", "{\"reply_md\":\"done\"}");
  }

  @Test
  void passesThroughSessionIdAndOmitsNullOptionalFields() throws Exception {
    CompletableFuture<String> capturedBody = new CompletableFuture<>();
    CompletableFuture<String> capturedPath = new CompletableFuture<>();
    int port = startFakeGraph(capturedBody, capturedPath);

    OrchestratorGraphClient client = newClient(port);
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    OrchestratorTurnRequest request =
        new OrchestratorTurnRequest("hi", "sess-abc", null, null, null, null, null, null);

    client.turnStream(42L, request, List.of(), emitter, () -> {});

    String body = capturedBody.get(5, TimeUnit.SECONDS);
    JsonNode json = new ObjectMapper().readTree(body);

    assertThat(json.get("user_id").asLong()).isEqualTo(42L);
    assertThat(json.get("user_message").asText()).isEqualTo("hi");
    assertThat(json.get("session_id").asText()).isEqualTo("sess-abc");
    assertThat(json.has("history")).isFalse();
    assertThat(json.has("current_chapter")).isFalse();
    assertThat(json.has("profile_brief")).isFalse();
    assertThat(json.has("recent_kc_codes")).isFalse();
    assertThat(json.has("recent_failures")).isFalse();
    assertThat(json.has("course_kc_index")).isFalse();
    assertThat(json.has("decaying_kcs")).isFalse();

    awaitEvent(emitter, "event:result");
  }

  @Test
  void toolLoopForwardsSnakeCaseBodyToToolLoopPathAndRelaysEvents() throws Exception {
    CompletableFuture<String> capturedBody = new CompletableFuture<>();
    CompletableFuture<String> capturedPath = new CompletableFuture<>();
    int port =
        startFakeGraph("/agents/orchestrator/tool-loop/stream", capturedBody, capturedPath);

    OrchestratorGraphClient client = newClient(port);
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    OrchestratorTurnRequest request =
        new OrchestratorTurnRequest(
            "把这章梳理成一张图",
            "sess-loop",
            List.of(new OrchestratorTurnMessage("user", "你好")),
            2,
            List.of("PY43-KC-010"),
            List.of("sub-1"),
            "偏好图示驱动",
            List.of("PY43-KC-001", "PY43-KC-010"));

    client.toolLoopStream(7L, request, emitter, () -> {});

    String body = capturedBody.get(5, TimeUnit.SECONDS);
    assertThat(capturedPath.get(5, TimeUnit.SECONDS))
        .isEqualTo("/agents/orchestrator/tool-loop/stream");

    JsonNode json = new ObjectMapper().readTree(body);
    assertThat(json.get("user_id").asLong()).isEqualTo(7L);
    assertThat(json.get("user_message").asText()).isEqualTo("把这章梳理成一张图");
    assertThat(json.get("session_id").asText()).isEqualTo("sess-loop");
    assertThat(json.get("current_chapter").asInt()).isEqualTo(2);
    assertThat(json.get("profile_brief").asText()).isEqualTo("偏好图示驱动");
    assertThat(json.get("recent_kc_codes")).hasSize(1);
    assertThat(json.get("recent_kc_codes").get(0).asText()).isEqualTo("PY43-KC-010");
    assertThat(json.get("recent_failures").get(0).asText()).isEqualTo("sub-1");
    assertThat(json.get("course_kc_index")).hasSize(2);
    assertThat(json.get("history")).hasSize(1);
    assertThat(json.get("history").get(0).get("role").asText()).isEqualTo("user");
    // 工具编排不透传记忆矿脉到期 KC（toolLoopStream 固定传空 decaying_kcs）。
    assertThat(json.has("decaying_kcs")).isFalse();

    awaitEvent(emitter, "event:result");
    assertThat(emitter.events()).anyMatch(e -> e.contains("event:trace"));
    assertThat(emitter.events()).anyMatch(e -> e.contains("event:token"));
    assertThat(emitter.events())
        .anyMatch(e -> e.contains("event:result") && e.contains("reply_md"));
  }

  private OrchestratorGraphClient newClient(int port) {
    return newClient(port, null);
  }

  private static LearnerRoutingContext learnerContext() {
    return new LearnerRoutingContext(
        "偏好图示；循环边界薄弱",
        java.util.Map.of("PY43-KC-010", 0.27),
        java.util.Map.of("PY43-KC-010", 0.74),
        List.of("PY43-KC-010"),
        List.of("PY43-KC-010"),
        "HIGH",
        "HIGH",
        "visual",
        List.of("MINDMAP"),
        "NON_CS_STEM",
        2,
        List.of(
            new LearnerRoutingContext.RecentResource(
                "PY43-KC-010", "MINDMAP", "2026-07-22T16:00:00Z")),
        List.of(new LearnerRoutingContext.RecentSubmission(476L, "PY43-KC-010", false)),
        List.of("range 右边界连续失败"),
        List.of(),
        List.of("分步讲解"),
        "2026-07-22T16:01:00Z");
  }

  private OrchestratorGraphClient newClient(int port, OrchestratorRunRecorder recorder) {
    GraphServiceProperties properties =
        new GraphServiceProperties("http://127.0.0.1:" + port, "http://rag.test", "secret");
    return new OrchestratorGraphClient(
        properties, new ObjectMapper(), new SseRelayExecutor(), recorder, 2_000, 10_000);
  }

  private int startFakeGraph(
      CompletableFuture<String> capturedBody, CompletableFuture<String> capturedPath)
      throws IOException {
    return startFakeGraph("/agents/orchestrator/turn/stream", capturedBody, capturedPath);
  }

  private int startFakeGraph(
      String path,
      CompletableFuture<String> capturedBody,
      CompletableFuture<String> capturedPath)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        path,
        (HttpExchange exchange) -> {
          capturedPath.complete(exchange.getRequestURI().getPath());
          String reqBody =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          capturedBody.complete(reqBody);

          byte[] payload = SSE_BODY.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, payload.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
          }
        });
    server.start();
    return server.getAddress().getPort();
  }

  private static void awaitEvent(RecordingSseEmitter emitter, String marker)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5_000L;
    while (System.currentTimeMillis() < deadline) {
      if (emitter.events().stream().anyMatch(e -> e.contains(marker))) {
        return;
      }
      Thread.sleep(20L);
    }
    assertThat(emitter.events()).anyMatch(e -> e.contains(marker));
  }

  /** 捕获每次 {@code send} 的事件文本，避免依赖真实的 HTTP 异步分发即可断言转发内容。 */
  private static final class RecordingSseEmitter extends SseEmitter {

    private final List<String> events = new CopyOnWriteArrayList<>();

    RecordingSseEmitter() {
      super(120_000L);
    }

    @Override
    public void send(SseEmitter.SseEventBuilder builder) {
      StringBuilder sb = new StringBuilder();
      for (ResponseBodyEmitter.DataWithMediaType part : builder.build()) {
        sb.append(part.getData());
      }
      events.add(sb.toString());
    }

    List<String> events() {
      return events;
    }
  }
}
