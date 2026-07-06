package com.pythontopia.courseware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.courseware.dto.CoursewareAnswerRequestDto;
import com.pythontopia.courseware.dto.CoursewareAnswerResponseDto;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class CoursewareGraphClientTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final CoursewareGraphClient client = new CoursewareGraphClient(restTemplate, properties);

  @Test
  void answerParsesGraphTraceFields() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo("http://graph.test/agents/courseware/answer"))
        .andExpect(header("X-Internal-Token", "secret"))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "query": "为什么字典词频统计要先理解 get items keys values",
                      "kc_codes": ["PY43-KC-041"],
                      "top_k": 6
                    }
                    """))
        .andRespond(
            withSuccess(
                """
                {
                  "answer_md": "字典词频统计需要先掌握 get/items/keys/values。[页 62]",
                  "citations": [
                    {
                      "page_id": 4201,
                      "page_no": 62,
                      "chapter_id": 5,
                      "quote": "字典 get、items、keys、values 是完成词频统计前需要掌握的基本操作。"
                    }
                  ],
                  "no_evidence": false,
                  "route": "graph",
                  "retrieval_mode": "graph",
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

    CoursewareAnswerResponseDto response =
        client.answer(
            new CoursewareAnswerRequestDto(
                "为什么字典词频统计要先理解 get items keys values",
                List.of("PY43-KC-041"),
                6));

    assertThat(response.answerMd()).contains("get/items/keys/values");
    assertThat(response.route()).isEqualTo("graph");
    assertThat(response.retrievalMode()).isEqualTo("graph");
    assertThat(response.subgraph()).hasSize(1);
    assertThat(response.subgraph().get(0).relation()).isEqualTo("co_occur");
    assertThat(response.graphTrace()).containsEntry("co_occur_hit_count", 1);
    assertThat(response.graphTrace()).containsEntry("supplemental_hit_limit", 2);
    server.verify(Duration.ofSeconds(2));
  }
}
