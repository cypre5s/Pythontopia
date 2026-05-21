package com.pythontopia.workshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.workshop.graph.ResourceProduceRequestDto;
import com.pythontopia.workshop.graph.ResourceProduceResponseDto;
import com.pythontopia.workshop.graph.WorkshopGraphClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

class WorkshopGraphClientTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final WorkshopGraphClient client = new WorkshopGraphClient(restTemplate, properties);

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
                new ResourceProduceRequestDto.ProfileSnapshotDto("OTHER", 2, List.of())));

    assertThat(response.status()).isEqualTo("SUCCESS");
    assertThat(response.evidencePageIds()).containsExactly(101L);
    assertThat(response.artifact().contentPayload()).isNotNull();
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void produceWrapsUpstreamFailure() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/resource/produce"))
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("down"));

    assertThatThrownBy(
            () ->
                client.produce(
                    new ResourceProduceRequestDto(
                        1L,
                        List.of("PY43-KC-001"),
                        "HANDOUT",
                        null,
                        new ResourceProduceRequestDto.ProfileSnapshotDto("OTHER", 2, List.of()))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("资源生成服务暂不可用");
  }
}
