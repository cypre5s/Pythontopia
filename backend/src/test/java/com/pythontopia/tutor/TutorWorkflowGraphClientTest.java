package com.pythontopia.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
        result -> {
          capturedResult.complete(result);
          return result;
        });

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
        ignored -> {
          order.add("persist");
          return "safe";
        });

    assertThat(emitter.finished.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(order).containsSubsequence("persist", "send");
  }

  @Test
  void doesNotForwardTerminalSuccessWhenProjectionFails() throws Exception {
    startGraphServer("event: result\ndata: {}\n\n");
    List<String> order = new CopyOnWriteArrayList<>();
    RecordingEmitter emitter = new RecordingEmitter(order);
    TutorWorkflowGraphClient client = newClient();
    String privateResult = "privateTutorResultSentinel";
    Logger logger = (Logger) LoggerFactory.getLogger(TutorWorkflowGraphClient.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.ERROR);
    logger.addAppender(appender);
    try {
      client.runStream(
          73L,
          Map.of("user_id", 73L, "session_id", "wf-shared"),
          emitter,
          ignored -> {
            throw new IllegalStateException(privateResult);
          });

      assertThat(emitter.finished.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(order).contains("error").doesNotContain("send");
      assertThat(emitter.failure.getMessage()).doesNotContain(privateResult);
      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain(privateResult);
      assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  @Test
  void downstreamDisconnectAbortsGraphStreamAndMarksRunInterrupted() throws Exception {
    CompletableFuture<Void> upstreamClosed = new CompletableFuture<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/agents/tutor-workflow/run/stream",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          try (OutputStream output = exchange.getResponseBody()) {
            output.write("event: trace\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
            for (int i = 0; i < 40; i++) {
              try {
                Thread.sleep(25);
                output.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
              } catch (IOException expectedAfterClientAbort) {
                upstreamClosed.complete(null);
                break;
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
              }
            }
          } catch (IOException expectedAfterClientAbort) {
            upstreamClosed.complete(null);
          }
        });
    server.start();
    AtomicBoolean interrupted = new AtomicBoolean();
    CompletableFuture<Void> markedInterrupted = new CompletableFuture<>();
    TutorWorkflowGraphClient client = newClient();

    client.runStream(
        73L,
        Map.of("user_id", 73L, "session_id", "wf-shared"),
        new DisconnectedEmitter(),
        ignored -> ignored,
        ignored -> {},
        () -> {
          interrupted.set(true);
          markedInterrupted.complete(null);
        });

    markedInterrupted.get(5, TimeUnit.SECONDS);
    upstreamClosed.get(5, TimeUnit.SECONDS);
    assertThat(interrupted).isTrue();
  }

  @Test
  void emitterTimeoutAbortsGraphStreamAndMarksRunInterrupted() throws Exception {
    CompletableFuture<Void> streamOpened = new CompletableFuture<>();
    CompletableFuture<Void> upstreamClosed = new CompletableFuture<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/agents/tutor-workflow/run/stream",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          streamOpened.complete(null);
          try (OutputStream output = exchange.getResponseBody()) {
            for (int i = 0; i < 40; i++) {
              try {
                Thread.sleep(25);
                output.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
              } catch (IOException expectedAfterTimeout) {
                upstreamClosed.complete(null);
                break;
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
              }
            }
          } catch (IOException expectedAfterTimeout) {
            upstreamClosed.complete(null);
          }
        });
    server.start();
    CompletableFuture<Void> markedInterrupted = new CompletableFuture<>();
    TimeoutEmitter emitter = new TimeoutEmitter();

    newClient().runStream(
        73L,
        Map.of("user_id", 73L, "session_id", "wf-shared"),
        emitter,
        ignored -> ignored,
        ignored -> {},
        () -> markedInterrupted.complete(null));

    streamOpened.get(5, TimeUnit.SECONDS);
    emitter.triggerTimeout();
    markedInterrupted.get(5, TimeUnit.SECONDS);
    upstreamClosed.get(5, TimeUnit.SECONDS);
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
    private Throwable failure;

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
      this.failure = failure;
      finished.countDown();
    }
  }

  private static final class DisconnectedEmitter extends SseEmitter {
    private DisconnectedEmitter() {
      super(5_000L);
    }

    @Override
    public void send(SseEventBuilder builder) throws IOException {
      throw new IOException("browser disconnected");
    }
  }

  private static final class TimeoutEmitter extends SseEmitter {
    private Runnable timeoutCallback;

    private TimeoutEmitter() {
      super(5_000L);
    }

    @Override
    public void onTimeout(Runnable callback) {
      timeoutCallback = callback;
    }

    private void triggerTimeout() {
      timeoutCallback.run();
    }
  }
}
