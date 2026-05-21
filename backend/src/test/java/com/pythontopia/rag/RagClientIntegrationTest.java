package com.pythontopia.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.rag.dto.RagQueryRequestDto;
import com.pythontopia.rag.dto.RagQueryResponseDto;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

class RagClientIntegrationTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties(
          "http://graph.test", "http://rag.test", "secret");
  private final RagClient client = new RagClient(restTemplate, properties);

  @Test
  void queryReturnsHits() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/rag/query"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "hits": [
                    {
                      "chunk_id": "p1-c0",
                      "page_id": 11,
                      "page_no": 12,
                      "chapter_id": 3,
                      "kc_codes": ["PY43-KC-012"],
                      "text": "input 函数",
                      "score": 0.91
                    }
                  ],
                  "no_evidence_found": false
                }
                """,
                MediaType.APPLICATION_JSON));

    RagQueryResponseDto response =
        client.query(new RagQueryRequestDto("input 函数", 5, null, null, 0.3));

    assertThat(response.hits()).hasSize(1);
    assertThat(response.hits().get(0).pageId()).isEqualTo(11);
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void queryWrapsUpstream5xx() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/rag/query"))
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .body("upstream down"));

    assertThatThrownBy(
            () -> client.query(new RagQueryRequestDto("query", 5, null, null, 0.3)))
        .isInstanceOf(RagUnavailableException.class)
        .hasMessageContaining("RAG 服务暂不可用");
  }

  @Test
  void queryWrapsTimeout() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/rag/query"))
        .andRespond(
            request -> {
              throw new org.springframework.web.client.ResourceAccessException("timeout");
            });

    assertThatThrownBy(
            () -> client.query(new RagQueryRequestDto("query", 5, null, null, 0.3)))
        .isInstanceOf(RagUnavailableException.class);
  }
}
