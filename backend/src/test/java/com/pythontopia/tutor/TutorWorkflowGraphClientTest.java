package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class TutorWorkflowGraphClientTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void forwardsAuthenticatedLearnerAsInternalSubject() throws Exception {
    CompletableFuture<String> capturedSubject = new CompletableFuture<>();
    CompletableFuture<String> capturedResult = new CompletableFuture<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/agents/tutor-workflow/run/stream",
        exchange -> {
          capturedSubject.complete(
              String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Subject")));
          exchange.getRequestBody().readAllBytes();
          byte[] payload = "event: result\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, payload.length);
          try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
          }
        });
    server.start();

    GraphServiceProperties properties =
        new GraphServiceProperties(
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "http://rag.test",
            "secret");
    TutorWorkflowGraphClient client =
        new TutorWorkflowGraphClient(properties, new ObjectMapper(), new SseRelayExecutor());

    client.runStream(
        73L,
        Map.of("user_id", 73L, "session_id", "wf-shared"),
        new SseEmitter(5_000L),
        capturedResult::complete);

    assertThat(capturedSubject.get(5, TimeUnit.SECONDS)).isEqualTo("user:73");
    assertThat(capturedResult.get(5, TimeUnit.SECONDS)).isEqualTo("{}");
  }

  @Test
  void persistsTerminalResultBeforeForwardingIt() throws Exception {
    startGraphServer("event: result\ndata: {}\n\n");
    List<String> order = new CopyOnWriteArrayList<>();
    RecordingEmitter emitter = new RecordingEmitter(order);
    TutorWorkflowGraphClient client = newClient();

    client.runStream(
        73L,
        Map.of("user_id", 73L, "session_id", "wf-shared"),
        emitter,
        ignored -> order.add("persist"));

    assertThat(emitter.finished.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(order).containsSubsequence("persist", "send");
  }

  @Test
  void doesNotForwardTerminalSuccessWhenProjectionFails() throws Exception {
    startGraphServer("event: result\ndata: {}\n\n");
    List<String> order = new CopyOnWriteArrayList<>();
    RecordingEmitter emitter = new RecordingEmitter(order);
    TutorWorkflowGraphClient client = newClient();

    client.runStream(
        73L,
        Map.of("user_id", 73L, "session_id", "wf-shared"),
        emitter,
        ignored -> {
          throw new IllegalStateException("projection failed");
        });

    assertThat(emitter.finished.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(order).contains("error").doesNotContain("send");
  }

  private TutorWorkflowGraphClient newClient() {
    GraphServiceProperties properties =
        new GraphServiceProperties(
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "http://rag.test",
            "secret");
    return new TutorWorkflowGraphClient(properties, new ObjectMapper(), new SseRelayExecutor());
  }

  private void startGraphServer(String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/agents/tutor-workflow/run/stream",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          byte[] payload = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, payload.length);
          try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
          }
        });
    server.start();
  }

  private static final class RecordingEmitter extends SseEmitter {
    private final List<String> order;
    private final CountDownLatch finished = new CountDownLatch(1);

    private RecordingEmitter(List<String> order) {
      super(5_000L);
      this.order = order;
    }

    @Override
    public void send(SseEventBuilder builder) {
      order.add("send");
    }

    @Override
    public void complete() {
      order.add("complete");
      finished.countDown();
    }

    @Override
    public void completeWithError(Throwable failure) {
      order.add("error");
      finished.countDown();
    }
  }
}
