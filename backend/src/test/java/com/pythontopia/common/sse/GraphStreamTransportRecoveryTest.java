package com.pythontopia.common.sse;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
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

class GraphStreamTransportRecoveryTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void unavailableRecoveryCanReplaceTheStandardErrorWithOneTerminalResult() throws Exception {
    int port = startHttpErrorServer(503);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    AtomicReference<GraphStreamTransport.FailureKind> recoveredFailure = new AtomicReference<>();

    transport()
        .relay(
            url(port),
            () -> Map.of("message", "hello"),
            null,
            emitter,
            null,
            failure -> {
              recoveredFailure.set(failure);
              return new GraphStreamTransport.TerminalEvent("result", "{\"done\":true}");
            },
            null);

    assertThat(recoveredFailure.get()).isEqualTo(GraphStreamTransport.FailureKind.UNAVAILABLE);
    assertThat(eventsNamed(emitter, "result")).hasSize(1);
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void interruptedStreamReportsItsKindToRecoveryAfterPreservingPriorEvents() throws Exception {
    int port = startStreamServer("event: trace\ndata: working\n\n");
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    AtomicReference<GraphStreamTransport.FailureKind> recoveredFailure = new AtomicReference<>();

    transport()
        .relay(
            url(port),
            () -> Map.of("message", "hello"),
            null,
            emitter,
            null,
            failure -> {
              recoveredFailure.set(failure);
              return new GraphStreamTransport.TerminalEvent("result", "{\"done\":true}");
            },
            null);

    assertThat(recoveredFailure.get()).isEqualTo(GraphStreamTransport.FailureKind.INTERRUPTED);
    assertThat(eventsNamed(emitter, "trace")).hasSize(1);
    assertThat(eventsNamed(emitter, "result")).hasSize(1);
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void recoveryFailureFallsBackToOneSanitizedTerminalError() throws Exception {
    int port = startHttpErrorServer(503);
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    transport()
        .relay(
            url(port),
            () -> Map.of("message", "hello"),
            null,
            emitter,
            null,
            failure -> {
              throw new IllegalStateException("secret internal recovery failure");
            },
            null);

    assertThat(eventsNamed(emitter, "result")).isEmpty();
    List<String> errors = eventsNamed(emitter, "error");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0))
        .contains("AI_SERVICE_UNAVAILABLE")
        .doesNotContain("secret internal recovery failure")
        .doesNotContain("127.0.0.1");
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void upstreamTerminalNeverInvokesRecovery() throws Exception {
    int port = startStreamServer("event: RUN_ERROR\ndata: {\"code\":\"BAD_INPUT\"}\n\n");
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    AtomicInteger recoveryCalls = new AtomicInteger();

    transport()
        .relay(
            url(port),
            () -> Map.of("message", "hello"),
            null,
            emitter,
            null,
            failure -> {
              recoveryCalls.incrementAndGet();
              return new GraphStreamTransport.TerminalEvent("result", "{}");
            },
            null);

    assertThat(recoveryCalls.get()).isZero();
    assertThat(eventsNamed(emitter, "RUN_ERROR")).hasSize(1);
    assertThat(eventsNamed(emitter, "result")).isEmpty();
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    assertClosedExactlyOnce(emitter);
  }

  private GraphStreamTransport transport() {
    return new GraphStreamTransport(objectMapper, 1_000, 2_000);
  }

  private int startHttpErrorServer(int status) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/stream",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
    return server.getAddress().getPort();
  }

  private int startStreamServer(String body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/stream",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          try (OutputStream output = exchange.getResponseBody()) {
            output.write(body.getBytes(UTF_8));
          }
        });
    server.start();
    return server.getAddress().getPort();
  }

  private static String url(int port) {
    return "http://127.0.0.1:" + port + "/stream";
  }

  private static List<String> eventsNamed(RecordingSseEmitter emitter, String name) {
    return emitter.events().stream().filter(event -> event.startsWith("event:" + name + "\n")).toList();
  }

  private static void assertClosedExactlyOnce(RecordingSseEmitter emitter) {
    assertThat(emitter.completeCalls()).isEqualTo(1);
    assertThat(emitter.completeWithErrorCalls()).isZero();
  }

  private static final class RecordingSseEmitter extends SseEmitter {

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final AtomicInteger completeCalls = new AtomicInteger();
    private final AtomicInteger completeWithErrorCalls = new AtomicInteger();

    @Override
    public void send(SseEventBuilder builder) throws java.io.IOException {
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
