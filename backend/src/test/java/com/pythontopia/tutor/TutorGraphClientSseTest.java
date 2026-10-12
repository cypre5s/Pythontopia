package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 验证导师答疑 SSE 代理对上游 graph 事件「原样透传」：Python 侧新增、Java 侧未知的 trace 节点
 * （如 {@code code_runner}，含 phase/status/elapsed_ms）必须 VERBATIM 转发到浏览器，
 * 不被任何节点名白名单过滤或改写。用 JDK 自带 {@link HttpServer} 起本地假 graph 服务端到端验证，
 * 不依赖数据库 / 外部服务，可在任意环境稳定运行。
 */
class TutorGraphClientSseTest {

  private static final String CODE_RUNNER_START =
      "{\"node\":\"code_runner\",\"phase\":\"run\",\"status\":\"start\",\"elapsed_ms\":0}";
  private static final String CODE_RUNNER_OK =
      "{\"node\":\"code_runner\",\"phase\":\"run\",\"status\":\"ok\",\"elapsed_ms\":42}";

  private static final String SSE_BODY =
      "event: trace\n"
          + "data: "
          + CODE_RUNNER_START
          + "\n\n"
          + "event: trace\n"
          + "data: "
          + CODE_RUNNER_OK
          + "\n\n"
          + "event: result\n"
          + "data: {\"answer_md\":\"done\",\"grounding_status\":\"grounded\"}\n\n";

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void forwardsUnknownCodeRunnerTraceEventsVerbatim() throws Exception {
    int port = startFakeGraph();
    TutorGraphClient client = newClient(port);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    List<String> results = new CopyOnWriteArrayList<>();

    client.askStream(
        7L,
        "讲一下 for 循环",
        List.of("PY43-KC-001"),
        "ctx",
        null,
        "认知风格: visual",
        emitter,
        results::add,
        () -> {});

    awaitEvent(emitter, "event:result");

    // 未知 trace 节点 code_runner 被原样透传：事件名仍是 trace，数据负载逐字节一致（含 elapsed_ms）。
    assertThat(emitter.events())
        .anyMatch(e -> e.contains("event:trace") && e.contains(CODE_RUNNER_START));
    assertThat(emitter.events())
        .anyMatch(e -> e.contains("event:trace") && e.contains(CODE_RUNNER_OK));
    // result 事件照常透传，并触发持久化回调。
    assertThat(emitter.events())
        .anyMatch(e -> e.contains("event:result") && e.contains("answer_md"));
    assertThat(results).anyMatch(r -> r.contains("answer_md"));
  }

  private TutorGraphClient newClient(int port) {
    GraphServiceProperties properties =
        new GraphServiceProperties("http://127.0.0.1:" + port, "http://rag.test", "secret");
    return new TutorGraphClient(
        properties, new ObjectMapper(), new SseRelayExecutor(), 2_000, 10_000);
  }

  private int startFakeGraph() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/agents/tutor/ask/stream",
        (HttpExchange exchange) -> {
          exchange.getRequestBody().readAllBytes();
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

  /** 捕获每次 {@code send} 的事件文本，断言转发内容而不依赖真实 HTTP 异步分发。 */
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
