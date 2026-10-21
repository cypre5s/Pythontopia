package com.pythontopia.workshop.graph;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class WorkshopGraphClientStreamTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void streamClosedBeforeTerminalUsesSyncFallbackAsTheOnlyResult() throws Exception {
    int port = startStreamServer("event: trace\ndata: {\"node\":\"designer\"}\n\n");
    RestTemplate restTemplate = mock(RestTemplate.class);
    ResourceProduceResponseDto fallback = successfulGraphResponse();
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(ResourceProduceResponseDto.class)))
        .thenReturn(ResponseEntity.ok(fallback));
    WorkshopGraphClient client = newClient(port, restTemplate);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    Function<String, String> onResult = mock(Function.class);
    when(onResult.apply(anyString()))
        .thenReturn("{\"runId\":42,\"resourceId\":7,\"status\":\"SUCCESS\"}");

    client.produceStream(request(), emitter, onResult);
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "trace")).hasSize(1);
    assertThat(eventsNamed(emitter, "result")).hasSize(1);
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    verify(restTemplate)
        .exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(ResourceProduceResponseDto.class));
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void canonicalRunFinishedIsReplacedByThePersistedLegacyResult() throws Exception {
    ResourceProduceResponseDto graphResponse = successfulGraphResponse();
    String wrapped = objectMapper.writeValueAsString(Map.of("result", graphResponse));
    int port = startStreamServer("event: RUN_FINISHED\ndata: " + wrapped + "\n\n");
    RestTemplate restTemplate = mock(RestTemplate.class);
    WorkshopGraphClient client = newClient(port, restTemplate);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    AtomicReference<String> receivedGraphResult = new AtomicReference<>();
    String persisted = "{\"runId\":42,\"resourceId\":7,\"status\":\"SUCCESS\"}";

    client.produceStream(
        request(),
        emitter,
        data -> {
          receivedGraphResult.set(data);
          return persisted;
        });
    awaitTerminated(emitter);

    assertThat(objectMapper.readTree(receivedGraphResult.get()))
        .isEqualTo(objectMapper.valueToTree(graphResponse));
    assertThat(eventsNamed(emitter, "result")).hasSize(1);
    assertThat(dataOf(eventsNamed(emitter, "result").get(0))).isEqualTo(persisted);
    assertThat(eventsNamed(emitter, "RUN_FINISHED")).isEmpty();
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    verifyNoInteractions(restTemplate);
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void failedSyncFallbackEmitsOneDomainErrorWithoutBareEmitterFailure() throws Exception {
    int port = startHttpErrorServer(503);
    RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(ResourceProduceResponseDto.class)))
        .thenThrow(new ResourceAccessException("internal graph endpoint failed"));
    WorkshopGraphClient client = newClient(port, restTemplate);
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    client.produceStream(request(), emitter, data -> data);
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "result")).isEmpty();
    List<String> errors = eventsNamed(emitter, "error");
    assertThat(errors).hasSize(1);
    assertThat(objectMapper.readTree(dataOf(errors.get(0))).path("code").asText())
        .isEqualTo("RESOURCE_STREAM_FALLBACK_FAILED");
    assertThat(errors.get(0))
        .doesNotContain("internal graph endpoint")
        .doesNotContain("127.0.0.1");
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void upstreamRunErrorTerminatesWithoutSyncFallbackOrResultProjection() throws Exception {
    String upstreamError = "{\"code\":\"SAFETY_FAILED\",\"message\":\"blocked\"}";
    int port = startStreamServer("event: RUN_ERROR\ndata: " + upstreamError + "\n\n");
    RestTemplate restTemplate = mock(RestTemplate.class);
    WorkshopGraphClient client = newClient(port, restTemplate);
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    @SuppressWarnings("unchecked")
    Function<String, String> onResult = mock(Function.class);

    client.produceStream(request(), emitter, onResult);
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "RUN_ERROR")).hasSize(1);
    assertThat(dataOf(eventsNamed(emitter, "RUN_ERROR").get(0))).isEqualTo(upstreamError);
    assertThat(eventsNamed(emitter, "result")).isEmpty();
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    verify(onResult, never()).apply(anyString());
    verifyNoInteractions(restTemplate);
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void resultProjectionFailureEmitsOneDomainErrorWithoutSyncFallback() throws Exception {
    ResourceProduceResponseDto graphResponse = successfulGraphResponse();
    String wrapped = objectMapper.writeValueAsString(Map.of("result", graphResponse));
    int port = startStreamServer("event: RUN_FINISHED\ndata: " + wrapped + "\n\n");
    RestTemplate restTemplate = mock(RestTemplate.class);
    WorkshopGraphClient client = newClient(port, restTemplate);
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    client.produceStream(
        request(),
        emitter,
        data -> {
          throw new IllegalStateException("internal database detail");
        });
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "result")).isEmpty();
    List<String> errors = eventsNamed(emitter, "error");
    assertThat(errors).hasSize(1);
    assertThat(objectMapper.readTree(dataOf(errors.get(0))).path("code").asText())
        .isEqualTo("RESOURCE_STREAM_RESULT_FAILED");
    assertThat(errors.get(0)).doesNotContain("internal database detail");
    verifyNoInteractions(restTemplate);
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void stalledStreamUsesConfiguredReadTimeoutThenSyncFallback() throws Exception {
    int port = startTwoPartStreamServer(
        "event: trace\ndata: {\"node\":\"designer\"}\n\n", null, 1_000L);
    RestTemplate restTemplate = mock(RestTemplate.class);
    ResourceProduceResponseDto fallback = successfulGraphResponse();
    when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(ResourceProduceResponseDto.class)))
        .thenReturn(ResponseEntity.ok(fallback));
    WorkshopGraphClient client =
        new WorkshopGraphClient(
            restTemplate,
            new GraphServiceProperties(
                "http://127.0.0.1:" + port, "http://rag.test", "internal-token"),
            objectMapper,
            new SseRelayExecutor(),
            1_000,
            250);
    RecordingSseEmitter emitter = new RecordingSseEmitter();

    client.produceStream(
        request(),
        emitter,
        data -> "{\"runId\":42,\"resourceId\":7,\"status\":\"SUCCESS\"}");
    awaitTerminated(emitter);

    assertThat(eventsNamed(emitter, "trace")).hasSize(1);
    assertThat(eventsNamed(emitter, "result")).hasSize(1);
    assertThat(eventsNamed(emitter, "error")).isEmpty();
    assertClosedExactlyOnce(emitter);
  }

  @Test
  void clientDisconnectStopsBeforeResultProjectionAndDoesNotFallback() throws Exception {
    ResourceProduceResponseDto graphResponse = successfulGraphResponse();
    String wrapped = objectMapper.writeValueAsString(Map.of("result", graphResponse));
    int port =
        startTwoPartStreamServer(
            "event: trace\ndata: {\"node\":\"designer\"}\n\n",
            "event: RUN_FINISHED\ndata: " + wrapped + "\n\n",
            200L);
    RestTemplate restTemplate = mock(RestTemplate.class);
    WorkshopGraphClient client = newClient(port, restTemplate);
    FailingSseEmitter emitter = new FailingSseEmitter();
    @SuppressWarnings("unchecked")
    Function<String, String> onResult = mock(Function.class);

    client.produceStream(request(), emitter, onResult);
    awaitTerminated(emitter);

    verify(onResult, never()).apply(anyString());
    verifyNoInteractions(restTemplate);
    assertClosedExactlyOnce(emitter);
  }

  private WorkshopGraphClient newClient(int port, RestTemplate restTemplate) {
    return new WorkshopGraphClient(
        restTemplate,
        new GraphServiceProperties(
            "http://127.0.0.1:" + port, "http://rag.test", "internal-token"),
        objectMapper,
        new SseRelayExecutor());
  }

  private ResourceProduceRequestDto request() {
    return new ResourceProduceRequestDto(
        1L,
        List.of("PY43-KC-001"),
        "HANDOUT",
        null,
        new ResourceProduceRequestDto.ProfileSnapshotDto(
            "OTHER", 2, List.of(), "", List.of(), "", List.of(), List.of()),
        null,
        null);
  }

  private ResourceProduceResponseDto successfulGraphResponse() throws Exception {
    return objectMapper.readValue(
        """
        {
          "artifact": {
            "title": "讲义",
            "summary": "ok",
            "content_format": "MARKDOWN",
            "handout": {"sections": []}
          },
          "type": "HANDOUT",
          "evidence_page_ids": [101],
          "grounding_result": {"passed": true, "reasons": [], "no_evidence_found": false},
          "safety_result": {"passed": true, "reasons": []},
          "graph_trace": [],
          "status": "SUCCESS"
        }
        """,
        ResourceProduceResponseDto.class);
  }

  private int startStreamServer(String body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/agents/resource/produce/stream",
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

  private int startHttpErrorServer(int status) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/agents/resource/produce/stream",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    server.start();
    return server.getAddress().getPort();
  }

  private int startTwoPartStreamServer(String first, String second, long delayMs)
      throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/agents/resource/produce/stream",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          try (OutputStream output = exchange.getResponseBody()) {
            output.write(first.getBytes(UTF_8));
            output.flush();
            try {
              Thread.sleep(delayMs);
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
            if (second != null) {
              output.write(second.getBytes(UTF_8));
            }
          }
        });
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
    long deadline = System.currentTimeMillis() + 5_000L;
    while (System.currentTimeMillis() < deadline) {
      if (emitter.completeCalls() + emitter.completeWithErrorCalls() > 0) {
        return;
      }
      Thread.sleep(20L);
    }
    assertThat(emitter.completeCalls() + emitter.completeWithErrorCalls()).isPositive();
  }

  private static void assertClosedExactlyOnce(RecordingSseEmitter emitter) {
    assertThat(emitter.completeCalls()).isEqualTo(1);
    assertThat(emitter.completeWithErrorCalls()).isZero();
  }

  private static class RecordingSseEmitter extends SseEmitter {

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

  private static final class FailingSseEmitter extends RecordingSseEmitter {

    @Override
    public void send(SseEventBuilder builder) throws java.io.IOException {
      throw new java.io.IOException("client disconnected");
    }
  }
}
