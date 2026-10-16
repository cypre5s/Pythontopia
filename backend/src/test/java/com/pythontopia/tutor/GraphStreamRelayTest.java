package com.pythontopia.tutor;

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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class GraphStreamRelayTest {

  @Test
  void upstreamFailuresDoNotLogGraphUrlQuery() throws Exception {
    String sentinel = "private-graph-url-token-sentinel";
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/events", exchange -> exchange.sendResponseHeaders(503, -1));
    server.createContext(
        "/truncated",
        exchange -> {
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          exchange.close();
        });
    server.start();

    Logger logger = (Logger) LoggerFactory.getLogger(GraphStreamRelay.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      new GraphStreamRelay(new ObjectMapper(), 1000, 1000)
          .relay(
              "http://127.0.0.1:" + server.getAddress().getPort() + "/events?token=" + sentinel,
              Map::of,
              null,
              new SseEmitter(),
              null,
              null);
      new GraphStreamRelay(new ObjectMapper(), 1000, 1000)
          .relay(
              "http://127.0.0.1:" + server.getAddress().getPort() + "/truncated?token=" + sentinel,
              Map::of,
              null,
              new SseEmitter(),
              null,
              null);
    } finally {
      logger.detachAppender(appender);
      server.stop(0);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("HTTP 503"))
        .anySatisfy(message -> assertThat(message).contains("closed before terminal event"))
        .noneMatch(message -> message.contains(sentinel));
  }

  @Test
  void peekCallbackFailureDoesNotLogModelStreamPayloadOrExceptionBody() throws Exception {
    String sentinel = "private-tutor-stream-log-sentinel";
    byte[] body = ("event: result\ndata: " + sentinel + "\n\n").getBytes(StandardCharsets.UTF_8);
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

    Logger logger = (Logger) LoggerFactory.getLogger(GraphStreamRelay.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      new GraphStreamRelay(new ObjectMapper(), 1000, 1000)
          .relay(
              "http://127.0.0.1:" + server.getAddress().getPort() + "/events",
              Map::of,
              null,
              new SseEmitter(),
              (event, data) -> {
                throw new IllegalStateException(sentinel);
              },
              null);
    } finally {
      logger.detachAppender(appender);
      server.stop(0);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=IllegalStateException"))
        .noneMatch(message -> message.contains(sentinel));
    assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
  }
}
