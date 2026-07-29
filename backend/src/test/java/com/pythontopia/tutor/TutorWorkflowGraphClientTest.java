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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class TutorWorkflowGraphClientTest {

  private static final long AWAIT_MS = 5_000L;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void prematureCloseKeepsPartialEventThenEmitsOneInterruptedTerminalError() throws Exception {
    int port = startServer(exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write("event: trace\ndata: {\"node\":\"coach\"}\n\n".getBytes(UTF_8));
      }
    });
    TutorWorkflowGraphClient client = newClient(port);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    List<String> results = new CopyOnWriteArrayList<>();

    client.runStream(Map.of("session_id", "wf-7-1"), emitter, results::add);
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "trace")).hasSize(1);
    List<String> errors = eventsNamed(emitter, "error");
    assertThat(errors).as("无终态 EOF 必须有且仅有一条中断 error").hasSize(1);
    JsonNode error = objectMapper.readTree(dataOf(errors.get(0)));
    assertThat(error.path("code").asText()).isEqualTo("AI_STREAM_INTERRUPTED");
    assertThat(results).isEmpty();
    assertThat(emitter.completeCalls()).isEqualTo(1);
    assertThat(emitter.completeWithErrorCalls()).isZero();
  }

  @Test
  void successfulRunPreservesRequestAuthTerminalEventAndResultCallback() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    AtomicReference<String> internalToken = new AtomicReference<>();
    String terminalData = "{\"result\":{\"phase\":\"READING\",\"seq\":3}}";
    int port = startServer(exchange -> {
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
      internalToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(
            ("event: RUN_FINISHED\ndata: " + terminalData + "\n\n").getBytes(UTF_8));
      }
    });
    TutorWorkflowGraphClient client = newClient(port);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    List<String> results = new CopyOnWriteArrayList<>();

    client.runStream(Map.of("session_id", "wf-7-1"), emitter, results::add);
    awaitTerminated(emitter);

    assertThat(requestBody.get()).contains("\"session_id\":\"wf-7-1\"");
    assertThat(internalToken.get()).isEqualTo("unit-test-internal-token");
    assertThat(eventsNamed(emitter, "RUN_FINISHED")).hasSize(1);
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    assertThat(results).containsExactly(terminalData);
    assertThat(emitter.completeCalls()).isEqualTo(1);
    assertThat(emitter.completeWithErrorCalls()).isZero();
  }

  @Test
  void stalledUpstreamKeepsPartialEventThenEmitsOneTimeoutError() throws Exception {
    int port = startServer(exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      OutputStream output = exchange.getResponseBody();
      output.write("event: trace\ndata: {\"node\":\"coach\"}\n\n".getBytes(UTF_8));
      output.flush();
      sleepQuietly(1_500L);
      exchange.close();
    });
    TutorWorkflowGraphClient client = newClient(port, 1_000, 250);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    List<String> results = new CopyOnWriteArrayList<>();

    client.runStream(Map.of("session_id", "wf-7-1"), emitter, results::add);
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "trace")).hasSize(1);
    List<String> errors = eventsNamed(emitter, "error");
    assertThat(errors).as("读不活动超时必须有且仅有一条 error").hasSize(1);
    JsonNode error = objectMapper.readTree(dataOf(errors.get(0)));
    assertThat(error.path("code").asText()).isEqualTo("AI_SERVICE_TIMEOUT");
    assertThat(results).isEmpty();
    assertThat(emitter.completeCalls()).isEqualTo(1);
    assertThat(emitter.completeWithErrorCalls()).isZero();
  }

  @Test
  void upstreamRunErrorIsTheOnlyTerminalErrorAndDoesNotTriggerResultCallback() throws Exception {
    String upstreamError = "{\"code\":\"TRANSITION_VIOLATION\",\"message\":\"bad step\"}";
    int port = startServer(exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(("event: RUN_ERROR\ndata: " + upstreamError + "\n\n").getBytes(UTF_8));
      }
    });
    TutorWorkflowGraphClient client = newClient(port);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    List<String> results = new CopyOnWriteArrayList<>();

    client.runStream(Map.of("session_id", "wf-7-1"), emitter, results::add);
    awaitTerminated(emitter);

    List<String> errors = eventsNamed(emitter, "RUN_ERROR");
    assertThat(errors).hasSize(1);
    assertThat(dataOf(errors.get(0))).isEqualTo(upstreamError);
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    assertThat(results).isEmpty();
    assertThat(emitter.completeCalls()).isEqualTo(1);
    assertThat(emitter.completeWithErrorCalls()).isZero();
  }

  private TutorWorkflowGraphClient newClient(int port) {
    GraphServiceProperties properties = properties(port);
    return new TutorWorkflowGraphClient(
        properties, objectMapper, new SseRelayExecutor(), 1_000, 2_000);
  }

  private TutorWorkflowGraphClient newClient(
      int port, int connectTimeoutMs, int readTimeoutMs) {
    GraphServiceProperties properties = properties(port);
    return new TutorWorkflowGraphClient(
        properties,
        objectMapper,
        new SseRelayExecutor(),
        connectTimeoutMs,
        readTimeoutMs);
  }

  private GraphServiceProperties properties(int port) {
    GraphServiceProperties properties =
        new GraphServiceProperties(
            "http://127.0.0.1:" + port, "http://rag.test", "unit-test-internal-token");
    return properties;
  }

  private int startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/agents/tutor-workflow/run/stream", handler);
    server.start();
    return server.getAddress().getPort();
  }

  private static List<String> eventsNamed(RecordingSseEmitter emitter, String name) {
    return emitter.events().stream().filter(event -> event.startsWith("event:" + name + "\n")).toList();
  }

  private static String dataOf(String event) {
    int index = event.indexOf("data:");
    assertThat(index).isGreaterThanOrEqualTo(0);
    return event.substring(index + "data:".length()).trim();
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

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static final class RecordingSseEmitter extends SseEmitter {

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final AtomicInteger completeCalls = new AtomicInteger();
    private final AtomicInteger completeWithErrorCalls = new AtomicInteger();

    private RecordingSseEmitter() {
      super(180_000L);
    }

    @Override
    public void send(SseEventBuilder builder) throws IOException {
      StringBuilder event = new StringBuilder();
      for (ResponseBodyEmitter.DataWithMediaType part : builder.build()) {
        event.append(part.getData());
      }
      events.add(event.toString());
    }

    @Override
    public void complete() {
      completeCalls.incrementAndGet();
    }

    @Override
    public void completeWithError(Throwable ex) {
      completeWithErrorCalls.incrementAndGet();
    }

    private List<String> events() {
      return events;
    }

    private int completeCalls() {
      return completeCalls.get();
    }

    private int completeWithErrorCalls() {
      return completeWithErrorCalls.get();
    }
  }
}
