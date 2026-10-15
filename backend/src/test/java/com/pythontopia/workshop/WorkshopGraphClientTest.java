package com.pythontopia.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.sse.SseRelayExecutor;
import com.pythontopia.workshop.graph.ResourceProduceRequestDto;
import com.pythontopia.workshop.graph.ResourceProduceResponseDto;
import com.pythontopia.workshop.graph.WorkshopGraphClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class WorkshopGraphClientTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final SseRelayExecutor sseRelayExecutor = new SseRelayExecutor();
  private final WorkshopGraphClient client =
      new WorkshopGraphClient(restTemplate, properties, objectMapper, sseRelayExecutor);

  @Test
  void produceParsesSuccessResponse() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/resource/produce"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "artifact": {
                    "title": "讲义",
                    "summary": "ok",
                    "content_format": "MARKDOWN",
                    "handout": {"sections": [
                      {"heading": "a", "paragraphs": ["b"], "page_refs": []}
                    ]}
                  },
                  "type": "HANDOUT",
                  "evidence_page_ids": [101],
                  "grounding_result": {"passed": true, "reasons": [], "no_evidence_found": false},
                  "safety_result": {"passed": true, "reasons": []},
                  "graph_trace": [{"node": "evidence_fetcher", "elapsed_ms": 1, "status": "ok"}],
                  "status": "SUCCESS"
                }
                """,
                MediaType.APPLICATION_JSON));

    ResourceProduceResponseDto response =
        client.produce(
            new ResourceProduceRequestDto(
                1L,
                List.of("PY43-KC-001"),
                "HANDOUT",
                null,
                new ResourceProduceRequestDto.ProfileSnapshotDto(
                    "OTHER", 2, List.of(), "", List.of(), "", List.of(), List.of()), null, null));

    assertThat(response.status()).isEqualTo("SUCCESS");
    assertThat(response.evidencePageIds()).containsExactly(101L);
    assertThat(response.artifact().contentPayload()).isNotNull();
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void contentPayloadSkipsExplicitNullBodyFields() throws Exception {
    ResourceProduceResponseDto response =
        objectMapper.readValue(
            """
            {
              "artifact": {
                "title": "练习题",
                "summary": "ok",
                "content_format": "STRUCT_JSON",
                "handout": null,
                "mindmap": null,
                "practice": {"problems": [{"title": "边界条件"}]},
                "code_lab": null,
                "reading": null,
                "storyboard": null
              },
              "type": "PRACTICE",
              "evidence_page_ids": [10705],
              "grounding_result": {"passed": true, "reasons": [], "no_evidence_found": false},
              "safety_result": {"passed": true, "reasons": []},
              "graph_trace": [],
              "status": "SUCCESS"
            }
            """,
            ResourceProduceResponseDto.class);

    assertThat(response.artifact().contentPayload()).isEqualTo(response.artifact().practice());
    assertThat(response.artifact().contentPayload().isNull()).isFalse();
    assertThat(response.artifact().contentPayload().path("problems")).hasSize(1);
  }

  @Test
  void produceWrapsUpstreamFailure() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    // 重试一次 + 熔断前初始调用 = 最多 2 次请求
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/resource/produce"))
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("down"));
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/resource/produce"))
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("down"));

    assertThatThrownBy(
            () -> client.produce(
                new ResourceProduceRequestDto(
                    1L, List.of("PY43-KC-001"), "HANDOUT", null,
                    new ResourceProduceRequestDto.ProfileSnapshotDto(
                        "OTHER", 2, List.of(), "", List.of(), "", List.of(), List.of()),
                    null, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("workshop 服务暂不可用");
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void streamResultFailureDoesNotLogStudentContent() throws Exception {
    String sentinel = "private-workshop-result-log-sentinel";
    HttpServer sseServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    sseServer.createContext(
        "/agents/resource/produce/stream",
        exchange -> {
          byte[] body =
              "event: result\ndata: {\"result\":{\"artifact\":\"safe\"}}\n\n"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    sseServer.start();

    SseRelayExecutor inlineExecutor = mock(SseRelayExecutor.class);
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(0).run();
              return null;
            })
        .when(inlineExecutor)
        .submit(any(Runnable.class));
    WorkshopGraphClient localClient =
        new WorkshopGraphClient(
            restTemplate,
            new GraphServiceProperties(
                "http://127.0.0.1:" + sseServer.getAddress().getPort(),
                "http://rag.test",
                "secret"),
            objectMapper,
            inlineExecutor);
    SseEmitter emitter = mock(SseEmitter.class);
    Logger logger = (Logger) LoggerFactory.getLogger(WorkshopGraphClient.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      localClient.produceStream(null, emitter, ignored -> { throw new IllegalStateException(sentinel); });
    } finally {
      logger.detachAppender(appender);
      sseServer.stop(0);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=IllegalStateException"));
    assertThat(appender.list)
        .extracting(ILoggingEvent::getThrowableProxy)
        .allSatisfy(throwable -> assertThat(throwable).isNull());
    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
  }
}
