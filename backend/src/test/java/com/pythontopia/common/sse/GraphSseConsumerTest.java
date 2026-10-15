package com.pythontopia.common.sse;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class GraphSseConsumerTest {

  @Test
  void eventHandlerFailureDoesNotLogSsePayloadOrExceptionMessage() throws Exception {
    String sentinel = "private-model-stream-payload-sentinel";
    byte[] body =
        ("event: answer\ndata: " + sentinel + "\n\nevent: final\ndata: " + sentinel)
            .getBytes(StandardCharsets.UTF_8);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/events",
        exchange -> {
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();

    Logger logger = (Logger) LoggerFactory.getLogger(GraphSseConsumer.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      GraphSseConsumer.consume(
          "http://127.0.0.1:" + server.getAddress().getPort() + "/events",
          Map.of(),
          new ObjectMapper(),
          (event, data) -> {
            throw new IllegalArgumentException(sentinel);
          });
    } finally {
      logger.detachAppender(appender);
      server.stop(0);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=IllegalArgumentException"))
        .anySatisfy(message -> assertThat(message).contains("trailing event=final"))
        .noneMatch(message -> message.contains(sentinel));
    assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
  }
}
