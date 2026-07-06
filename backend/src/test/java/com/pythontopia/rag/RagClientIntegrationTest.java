package com.pythontopia.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.rag.dto.RagQueryRequestDto;
import com.pythontopia.rag.dto.RagQueryResponseDto;
import java.util.List;
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

  @Test
  void queryCarriesInternalTokenWhenConfigured() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/rag/query"))
        .andExpect(MockRestRequestMatchers.header("X-Internal-Token", "secret"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                "{\"hits\": [], \"no_evidence_found\": true}", MediaType.APPLICATION_JSON));

    client.query(new RagQueryRequestDto("input 函数", 5, null, null, 0.3));

    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void querySendsGraphOptionsAndParsesTraceFields() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/rag/query"))
        .andExpect(
            MockRestRequestMatchers.content()
                .json(
                    """
                    {
                      "query": "为什么字典应用需要字典基本操作",
                      "top_k": 6,
                      "kc_codes": ["PY43-KC-041"],
                      "chapter_ids": [5],
                      "min_score": 0.2,
                      "graph_mode": "force",
                      "prereq_hops": 2,
                      "expand_prerequisites": true
                    }
                    """))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "hits": [
                    {
                      "chunk_id": "p1-c0",
                      "page_id": 11,
                      "page_no": 12,
                      "chapter_id": 5,
                      "kc_codes": ["PY43-KC-042"],
                      "text": "字典基本操作",
                      "score": 0.91,
                      "via_prerequisite": false,
                      "via_relation": "co_occur"
                    }
                  ],
                  "no_evidence_found": false,
                  "retrieval_mode": "graph",
                  "route": "graph",
                  "subgraph": [
                    {
                      "from_kc": "PY43-KC-041",
                      "to_kc": "PY43-KC-042",
                      "relation": "co_occur"
                    }
                  ],
                  "graph_trace": {
                    "route": "graph",
                    "retrieval_mode": "graph",
                    "top_k": 6,
                    "hit_count": 3,
                    "direct_hit_count": 2,
                    "supplemental_hit_count": 1,
                    "co_occur_hit_count": 1,
                    "supplemental_hit_limit": 2,
                    "supplemental_hit_ratio": 0.3333,
                    "relation_counts": {"co_occur": 1},
                    "noise_guard": "supplemental_hits_capped_at_top_k_div_3"
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    RagQueryResponseDto response =
        client.query(
            new RagQueryRequestDto(
                "为什么字典应用需要字典基本操作",
                6,
                List.of("PY43-KC-041"),
                List.of(5L),
                0.2,
                "force",
                2,
                true));

    assertThat(response.route()).isEqualTo("graph");
    assertThat(response.retrievalMode()).isEqualTo("graph");
    assertThat(response.subgraph()).hasSize(1);
    assertThat(response.subgraph().get(0).relation()).isEqualTo("co_occur");
    assertThat(response.graphTrace()).containsEntry("co_occur_hit_count", 1);
    assertThat(response.graphTrace()).containsEntry("supplemental_hit_limit", 2);
    assertThat(response.hits().get(0).viaRelation()).isEqualTo("co_occur");
    assertThat(response.hits().get(0).viaPrerequisite()).isFalse();
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void queryOmitsInternalTokenWhenBlank() {
    RagClient noTokenClient =
        new RagClient(
            restTemplate,
            new GraphServiceProperties("http://graph.test", "http://rag.test", ""));
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://rag.test/rag/query"))
        .andExpect(MockRestRequestMatchers.headerDoesNotExist("X-Internal-Token"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                "{\"hits\": [], \"no_evidence_found\": true}", MediaType.APPLICATION_JSON));

    noTokenClient.query(new RagQueryRequestDto("input 函数", 5, null, null, 0.3));

    server.verify(Duration.ofSeconds(2));
  }
}
