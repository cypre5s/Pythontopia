package com.pythontopia.common.sse;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GraphSseConsumerTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void joinsMultilineDataAndDiscardsUnterminatedTrailingFrame() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/stream", exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(
            ("event: token\n"
                    + ": heartbeat\n"
                    + "data: first\n"
                    + "data: second\n\n"
                    + "event: result\n"
                    + "data: {\"ok\":")
                .getBytes(UTF_8));
      }
    });
    server.start();
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/stream";
    List<ReceivedEvent> received = new ArrayList<>();

    GraphSseConsumer.consume(
        url,
        Map.of("question", "for"),
        new ObjectMapper(),
        (event, data) -> received.add(new ReceivedEvent(event, data)));

    assertThat(received).containsExactly(new ReceivedEvent("token", "first\nsecond"));
  }

  private record ReceivedEvent(String event, String data) {}
}
