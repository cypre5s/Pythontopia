package com.pythontopia.tutor;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
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
 * 导师答疑 SSE 中继的故障语义契约（TDD 红线测试），与
 * {@code OrchestratorGraphClientFailureTest} 同一套约定：单条终止性净化 error、部分内容保留、
 * 读超时有界、客户端断开即清理；故障时绝不触发 {@code onResult}（不把故障当正常答案落库）。
 */
class TutorGraphClientFailureTest {

  private static final String INTERNAL_TOKEN = "unit-test-internal-token";
  private static final long AWAIT_MS = 8_000L;

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void connectionRefusedFailsFastWithSingleSanitizedTerminalError() throws Exception {
    int port = freeClosedPort();
    TutorGraphClient client = newClient(port, 1_000, 2_000);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    List<String> results = new CopyOnWriteArrayList<>();
    AtomicInteger finished = new AtomicInteger();

    client.askStream(
        7L, "讲讲 for 循环", List.of(), "ctx", null, "", emitter, results::add,
        finished::incrementAndGet);
    awaitTerminated(emitter);

    assertSingleSanitizedTerminalError(emitter, port, "AI_SERVICE_UNAVAILABLE");
    assertThat(eventsNamed(emitter, "result")).isEmpty();
    // 故障绝不触发结果回调（不把故障静默替换成普通聊天 / 不落库假答案）。
    assertThat(results).isEmpty();
    assertClosedExactlyOnce(emitter, finished);
  }

  @Test
  void readTimeoutOnStalledUpstreamEmitsSingleTimeoutError() throws Exception {
    int port = startServer(exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      OutputStream os = exchange.getResponseBody();
      os.write("event: token\ndata: {\"text\":\"先到的\"}\n\n".getBytes(UTF_8));
      os.flush();
      sleepQuietly(8_000);
      exchange.close();
    });
    TutorGraphClient client = newClient(port, 1_000, 500);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    List<String> results = new CopyOnWriteArrayList<>();
    AtomicInteger finished = new AtomicInteger();

    client.askStream(
        7L, "讲讲 for 循环", List.of(), "ctx", null, "", emitter, results::add,
        finished::incrementAndGet);
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "token")).hasSize(1);
    assertSingleSanitizedTerminalError(emitter, port, "AI_SERVICE_TIMEOUT");
    assertThat(results).isEmpty();
    assertClosedExactlyOnce(emitter, finished);
  }

  @Test
  void prematureUpstreamCloseKeepsPartialTokensThenSingleTerminalError() throws Exception {
    int port = startServer(exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      OutputStream os = exchange.getResponseBody();
      os.write(
          ("event: token\ndata: {\"text\":\"for 循环\"}\n\n"
                  + "event: token\ndata: {\"text\":\"用于遍历\"}\n\n")
              .getBytes(UTF_8));
      os.flush();
      exchange.close();
    });
    TutorGraphClient client = newClient(port, 1_000, 2_000);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    List<String> results = new CopyOnWriteArrayList<>();
    AtomicInteger finished = new AtomicInteger();

    client.askStream(
        7L, "讲讲 for 循环", List.of(), "ctx", null, "", emitter, results::add,
        finished::incrementAndGet);
    awaitTerminated(emitter);

    List<String> tokens = eventsNamed(emitter, "token");
    assertThat(tokens).hasSize(2);
    assertThat(tokens.get(0)).contains("for 循环");
    assertThat(tokens.get(1)).contains("用于遍历");
    assertSingleSanitizedTerminalError(emitter, port, "AI_STREAM_INTERRUPTED");
    assertThat(emitter.events().get(emitter.events().size() - 1)).startsWith("event:error");
    assertThat(results).isEmpty();
    assertClosedExactlyOnce(emitter, finished);
  }

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
        upstreamSawDisconnect.complete(true);
      } finally {
        upstreamSawDisconnect.complete(false);
        exchange.close();
      }
    });
    TutorGraphClient client = newClient(port, 1_000, 5_000);
    DisconnectedSseEmitter emitter = new DisconnectedSseEmitter();
    List<String> results = new CopyOnWriteArrayList<>();
    AtomicInteger finished = new AtomicInteger();

    client.askStream(
        7L, "讲讲 for 循环", List.of(), "ctx", null, "", emitter, results::add,
        finished::incrementAndGet);

    assertThat(upstreamSawDisconnect.get(AWAIT_MS, TimeUnit.MILLISECONDS))
        .as("客户端断开后必须关闭上游连接（服务端应观察到写失败）")
        .isTrue();
    assertThat(served.get()).isLessThan(maxEvents);
    awaitFinished(finished);
    assertThat(finished.get()).as("资源释放回调恰好一次").isEqualTo(1);
    assertThat(results).isEmpty();
  }

  // ---------- helpers ----------

  private TutorGraphClient newClient(int port, int connectTimeoutMs, int readTimeoutMs) {
    GraphServiceProperties properties =
        new GraphServiceProperties("http://127.0.0.1:" + port, "http://rag.test", INTERNAL_TOKEN);
    return new TutorGraphClient(
        properties,
        new ObjectMapper(),
        new SseRelayExecutor(),
        connectTimeoutMs,
        readTimeoutMs);
  }

  private int startServer(ThrowingHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/agents/tutor/ask/stream", exchange -> {
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

  private void assertSingleSanitizedTerminalError(
      RecordingSseEmitter emitter, int port, String expectedCode) throws Exception {
    List<String> errors = eventsNamed(emitter, "error");
    assertThat(errors).as("终止性 error 事件恰好一条").hasSize(1);
    JsonNode json = new ObjectMapper().readTree(dataOf(errors.get(0)));
    assertThat(json.path("code").asText()).isEqualTo(expectedCode);
    String message = json.path("message").asText();
    assertThat(message).matches(".*[\\u4e00-\\u9fa5].*");
    assertThat(message.length()).isLessThanOrEqualTo(40);
    assertThat(message).contains("重试");
    assertThat(errors.get(0))
        .doesNotContain("127.0.0.1")
        .doesNotContain(String.valueOf(port))
        .doesNotContain("http")
        .doesNotContain("Exception")
        .doesNotContain("at com.")
        .doesNotContain("at java.")
        .doesNotContain(INTERNAL_TOKEN);
  }

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
