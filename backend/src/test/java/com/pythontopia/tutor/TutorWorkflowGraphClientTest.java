package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
}
