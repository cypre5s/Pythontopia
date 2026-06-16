package com.pythontopia.orchestrator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.orchestrator.dto.OrchestratorTurnRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 学习总管 SSE 中继的故障语义契约（TDD 红线测试）：
 *
 * <ul>
 *   <li>graph 连接失败 / 上游 HTTP 错误：有界时间内快速失败，向客户端只发一条终止性 {@code error}
 *       事件（中文短文案），随后恰好正常终结一次，绝不裸 {@code completeWithError} 断流；
 *   <li>graph 读超时：卡死的上游在读超时阈值内被切断，补一条终止性超时 error；
 *   <li>SSE 流坏损 / 提前关闭：已透传的部分内容保留（顺序在 error 之前），半截事件帧按规范丢弃；
 *   <li>客户端断开：立即停止消费上游并关闭连接，资源释放回调恰好执行一次；
 *   <li>不泄漏内部 URL / 端口 / 堆栈 / 内部 token；不伪造 result 把故障静默替换成普通聊天。
 * </ul>
 *
 * 用 JDK 自带 {@link HttpServer} 起本地假 graph 服务，不依赖数据库 / 外部服务。
 */
class OrchestratorGraphClientFailureTest {

  private static final String INTERNAL_TOKEN = "unit-test-internal-token";
  private static final long AWAIT_MS = 8_000L;

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  // ---------- 1. graph 连接失败：快速失败 + 单条净化 error ----------

  @Test
  void connectionRefusedFailsFastWithSingleSanitizedTerminalError() throws Exception {
    int port = freeClosedPort();
    OrchestratorGraphClient client = newClient(port, 1_000, 2_000);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    AtomicInteger finished = new AtomicInteger();

    long startedAt = System.currentTimeMillis();
    client.turnStream(7L, sampleRequest(), List.of(), emitter, finished::incrementAndGet);
    awaitTerminated(emitter);

    // 快速失败：有界时间内终结，不无限挂起。
    assertThat(System.currentTimeMillis() - startedAt).isLessThan(AWAIT_MS);
    assertSingleSanitizedTerminalError(emitter, port, "AI_SERVICE_UNAVAILABLE");
    // 绝不伪造 token / result 把故障静默替换成普通聊天。
    assertThat(eventsNamed(emitter, "token")).isEmpty();
    assertThat(eventsNamed(emitter, "result")).isEmpty();
    assertClosedExactlyOnce(emitter, finished);
  }

  // ---------- 1+7. 上游 HTTP 500：快速失败，不透传上游内部报错体 ----------

  @Test
  void upstreamHttpErrorFailsFastWithoutLeakingUpstreamBody() throws Exception {
    int port = startServer(exchange -> {
      exchange.getRequestBody().readAllBytes();
      byte[] body =
          ("{\"detail\":\"Traceback (most recent call last): http://internal-llm:8000 "
                  + INTERNAL_TOKEN
                  + "\"}")
              .getBytes(UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(500, body.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(body);
      }
    });
    OrchestratorGraphClient client = newClient(port, 1_000, 2_000);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    AtomicInteger finished = new AtomicInteger();

    client.turnStream(7L, sampleRequest(), List.of(), emitter, finished::incrementAndGet);
    awaitTerminated(emitter);

    assertSingleSanitizedTerminalError(emitter, port, "AI_SERVICE_UNAVAILABLE");
    String all = String.join("\n", emitter.events());
    assertThat(all).doesNotContain("Traceback").doesNotContain("internal-llm");
    assertThat(eventsNamed(emitter, "result")).isEmpty();
    assertClosedExactlyOnce(emitter, finished);
  }

  // ---------- 2. graph 读超时：阈值内补一条终止性超时 error ----------

  @Test
  void readTimeoutOnStalledUpstreamEmitsSingleTimeoutErrorAndCloses() throws Exception {
    int port = startServer(exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      OutputStream os = exchange.getResponseBody();
      os.write("event: token\ndata: {\"text\":\"部分\"}\n\n".getBytes(UTF_8));
      os.flush();
      sleepQuietly(8_000);
      exchange.close();
    });
    OrchestratorGraphClient client = newClient(port, 1_000, 500);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    AtomicInteger finished = new AtomicInteger();

    client.turnStream(7L, sampleRequest(), List.of(), emitter, finished::incrementAndGet);
    awaitTerminated(emitter);

    // 超时前已透传的部分内容保留。
    assertThat(eventsNamed(emitter, "token")).hasSize(1);
    assertSingleSanitizedTerminalError(emitter, port, "AI_SERVICE_TIMEOUT");
    assertClosedExactlyOnce(emitter, finished);
  }

  // ---------- 3+6. 上游提前关闭：部分内容保留，再补恰好一条终止性 error ----------

  @Test
  void prematureUpstreamCloseKeepsPartialTokensThenSingleTerminalError() throws Exception {
    int port = startServer(exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      OutputStream os = exchange.getResponseBody();
      os.write(
          ("event: trace\ndata: {\"node\":\"planner\"}\n\n"
                  + "event: token\ndata: {\"text\":\"你好\"}\n\n"
                  + "event: token\ndata: {\"text\":\"，世界\"}\n\n")
              .getBytes(UTF_8));
      os.flush();
      // 未发 result / error 即关闭：模拟上游进程崩溃。
      exchange.close();
    });
    OrchestratorGraphClient client = newClient(port, 1_000, 2_000);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    AtomicInteger finished = new AtomicInteger();

    client.turnStream(7L, sampleRequest(), List.of(), emitter, finished::incrementAndGet);
    awaitTerminated(emitter);

    // 终止性错误前已收到的部分 SSE 内容必须保留，且顺序在 error 之前。
    List<String> tokens = eventsNamed(emitter, "token");
    assertThat(tokens).hasSize(2);
    assertThat(tokens.get(0)).contains("你好");
    assertThat(tokens.get(1)).contains("，世界");
    assertSingleSanitizedTerminalError(emitter, port, "AI_STREAM_INTERRUPTED");
    assertThat(emitter.events().get(emitter.events().size() - 1)).startsWith("event:error");
    assertClosedExactlyOnce(emitter, finished);
  }

  // ---------- 3. 坏损尾帧：未以空行收束的半截事件按 SSE 规范丢弃，不透传残缺 result ----------

  @Test
  void corruptUnterminatedTailIsDiscardedAndYieldsSingleTerminalError() throws Exception {
    int port = startServer(exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      OutputStream os = exchange.getResponseBody();
      os.write(
          ("event: token\ndata: {\"text\":\"早\"}\n\n"
                  + "event: result\ndata: {\"reply_md\":\"被截断的半截 JSO")
              .getBytes(UTF_8));
      os.flush();
      exchange.close();
    });
    OrchestratorGraphClient client = newClient(port, 1_000, 2_000);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    AtomicInteger finished = new AtomicInteger();

    client.turnStream(7L, sampleRequest(), List.of(), emitter, finished::incrementAndGet);
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "token")).hasSize(1);
    // 残缺 result 帧不得透传（防止前端拿到坏 JSON 当正常结果）。
    assertThat(eventsNamed(emitter, "result")).isEmpty();
    assertSingleSanitizedTerminalError(emitter, port, "AI_STREAM_INTERRUPTED");
    assertClosedExactlyOnce(emitter, finished);
  }

  // ---------- 4. 客户端断开：停止消费上游并关闭连接，资源恰好释放一次 ----------

  @Test
  void clientDisconnectStopsUpstreamConsumptionAndCleansUpOnce() throws Exception {
    int maxEvents = 800;
    AtomicInteger served = new AtomicInteger();
    CompletableFuture<Boolean> upstreamSawDisconnect = new CompletableFuture<>();
    int port = startServer(exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      try (OutputStream os = exchange.getResponseBody()) {
        for (int i = 0; i < maxEvents; i++) {
          os.write(("event: token\ndata: {\"text\":\"t" + i + "\"}\n\n").getBytes(UTF_8));
          os.flush();
          served.incrementAndGet();
          sleepQuietly(15);
        }
      } catch (IOException e) {
        // 中继关闭上游连接后，服务端写入失败 → 资源确实被清理。
        upstreamSawDisconnect.complete(true);
      } finally {
        upstreamSawDisconnect.complete(false);
        exchange.close();
      }
    });
    OrchestratorGraphClient client = newClient(port, 1_000, 5_000);
    DisconnectedSseEmitter emitter = new DisconnectedSseEmitter();
    AtomicInteger finished = new AtomicInteger();

    client.turnStream(7L, sampleRequest(), List.of(), emitter, finished::incrementAndGet);

    assertThat(upstreamSawDisconnect.get(AWAIT_MS, TimeUnit.MILLISECONDS))
        .as("客户端断开后必须关闭上游连接（服务端应观察到写失败）")
        .isTrue();
    // 没有把整条上游流白白消费完。
    assertThat(served.get()).isLessThan(maxEvents);
    awaitFinished(finished);
    assertThat(finished.get()).as("资源释放回调恰好一次").isEqualTo(1);
  }

  // ---------- helpers ----------

  private static OrchestratorTurnRequest sampleRequest() {
    return new OrchestratorTurnRequest("讲讲 for 循环", "sess-1", null, null, null, null, null, null);
  }

  private OrchestratorGraphClient newClient(int port, int connectTimeoutMs, int readTimeoutMs) {
    GraphServiceProperties properties =
        new GraphServiceProperties("http://127.0.0.1:" + port, "http://rag.test", INTERNAL_TOKEN);
    return new OrchestratorGraphClient(
        properties, new ObjectMapper(), new SseRelayExecutor(), connectTimeoutMs, readTimeoutMs);
  }

  private int startServer(ThrowingHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/agents/orchestrator/turn/stream", exchange -> {
      try {
        handler.handle(exchange);
      } catch (IOException ignored) {
        // 连接被对端关闭等：测试服务器静默收尾。
      }
    });
    server.start();
    return server.getAddress().getPort();
  }

  private static int freeClosedPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  /** 断言：恰好一条终止性 error 事件，{"code","message"} 形状、中文短文案、不泄漏内部细节。 */
  private void assertSingleSanitizedTerminalError(
      RecordingSseEmitter emitter, int port, String expectedCode) throws Exception {
    List<String> errors = eventsNamed(emitter, "error");
    assertThat(errors).as("终止性 error 事件恰好一条").hasSize(1);
    String payload = dataOf(errors.get(0));
    JsonNode json = new ObjectMapper().readTree(payload);
    assertThat(json.path("code").asText()).isEqualTo(expectedCode);
    String message = json.path("message").asText();
    assertThat(message).as("中文短文案").matches(".*[\\u4e00-\\u9fa5].*");
    assertThat(message.length()).isLessThanOrEqualTo(40);
    assertThat(message).as("文案可执行（告知重试）").contains("重试");
    // 不向客户端泄漏内部 URL / 端口 / 堆栈 / 内部 token / 凭证。
    assertThat(errors.get(0))
        .doesNotContain("127.0.0.1")
        .doesNotContain(String.valueOf(port))
        .doesNotContain("http")
        .doesNotContain("Exception")
        .doesNotContain("at com.")
        .doesNotContain("at java.")
        .doesNotContain(INTERNAL_TOKEN);
  }

  /** 断言：流恰好正常终结一次（complete），绝不 completeWithError 裸断连；释放回调恰好一次。 */
  private void assertClosedExactlyOnce(RecordingSseEmitter emitter, AtomicInteger finished) {
    assertThat(emitter.completeCalls()).as("complete 恰好一次").isEqualTo(1);
    assertThat(emitter.completeWithErrorCalls()).as("不得 completeWithError 裸断连").isZero();
    awaitFinished(finished);
    assertThat(finished.get()).as("资源释放回调恰好一次").isEqualTo(1);
  }

  private List<String> eventsNamed(RecordingSseEmitter emitter, String name) {
    return emitter.events().stream().filter(e -> e.startsWith("event:" + name + "\n")).toList();
  }

  private static String dataOf(String eventText) {
    int idx = eventText.indexOf("data:");
    assertThat(idx).isGreaterThanOrEqualTo(0);
    return eventText.substring(idx + "data:".length()).trim();
  }

  private static void awaitTerminated(RecordingSseEmitter emitter) throws InterruptedException {
    long deadline = System.currentTimeMillis() + AWAIT_MS;
    while (System.currentTimeMillis() < deadline) {
      if (emitter.completeCalls() + emitter.completeWithErrorCalls() > 0) {
        return;
      }
      Thread.sleep(20L);
    }
    assertThat(emitter.completeCalls() + emitter.completeWithErrorCalls())
        .as("流必须在有界时间内终结")
        .isPositive();
  }

  private static void awaitFinished(AtomicInteger finished) {
    long deadline = System.currentTimeMillis() + AWAIT_MS;
    while (System.currentTimeMillis() < deadline && finished.get() == 0) {
      sleepQuietly(20);
    }
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @FunctionalInterface
  private interface ThrowingHandler {
    void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
  }

  /** 录制事件 + 终结调用次数；终结后再 send 模拟真实 emitter 抛 IllegalStateException。 */
  private static class RecordingSseEmitter extends SseEmitter {

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final AtomicInteger completeCalls = new AtomicInteger();
    private final AtomicInteger completeWithErrorCalls = new AtomicInteger();

    RecordingSseEmitter() {
      super(120_000L);
    }

    @Override
    public void send(SseEmitter.SseEventBuilder builder) throws IOException {
      if (completeCalls.get() + completeWithErrorCalls.get() > 0) {
        throw new IllegalStateException("SseEmitter already completed");
      }
      StringBuilder sb = new StringBuilder();
      for (ResponseBodyEmitter.DataWithMediaType part : builder.build()) {
        sb.append(part.getData());
      }
      events.add(sb.toString());
    }

    @Override
    public void complete() {
      completeCalls.incrementAndGet();
    }

    @Override
    public void completeWithError(Throwable ex) {
      completeWithErrorCalls.incrementAndGet();
    }

    List<String> events() {
      return events;
    }

    int completeCalls() {
      return completeCalls.get();
    }

    int completeWithErrorCalls() {
      return completeWithErrorCalls.get();
    }
  }

  /** 模拟已断开的客户端：任何 send 一律抛 IOException。 */
  private static final class DisconnectedSseEmitter extends RecordingSseEmitter {

    @Override
    public void send(SseEmitter.SseEventBuilder builder) throws IOException {
      throw new IOException("simulated client disconnect");
    }
  }
}
