package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.feynman.FeynmanGraphClient.FeynmanOutput;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

class FeynmanGraphClientTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final FeynmanGraphClient client = new FeynmanGraphClient(restTemplate, properties);

  @Test
  void postsToAgentsPathWithSnakeCaseMisconceptionsAndMapsResponse() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/feynman/tutee/step"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.kc_code").value("PY43-KC-016"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.student_teaching").value("range 右端不包含"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.misconceptions[0].tag").value("off_by_one_range"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.misconceptions[0].evidence_event_ids[0]").value(501))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "reply_md": "我懂了",
                  "updated_misconceptions": [
                    {"tag": "off_by_one_range", "evidence_event_ids": [501], "resolved": true}
                  ],
                  "produced_code": "for i in range(1, 6):\\n    print(i)"
                }
                """,
                MediaType.APPLICATION_JSON));

    Optional<FeynmanOutput> output =
        client.step(
            "PY43-KC-016",
            List.of(new Misconception("off_by_one_range", List.of(501L), false)),
            "range 右端不包含");

    assertThat(output).isPresent();
    assertThat(output.get().replyMd()).isEqualTo("我懂了");
    assertThat(output.get().updatedMisconceptions()).hasSize(1);
    assertThat(output.get().updatedMisconceptions().get(0).tag()).isEqualTo("off_by_one_range");
    assertThat(output.get().updatedMisconceptions().get(0).evidenceEventIds()).containsExactly(501L);
    assertThat(output.get().updatedMisconceptions().get(0).resolved()).isTrue();
    assertThat(output.get().producedCode()).contains("range(1, 6)");
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void upstreamFailureYieldsEmptyForRuleFallback() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/feynman/tutee/step"))
        .andRespond(MockRestResponseCreators.withServerError());

    Optional<FeynmanOutput> output =
        client.step(
            "PY43-KC-016",
            List.of(new Misconception("off_by_one_range", List.of(501L), false)),
            "随便讲");

    assertThat(output).isEmpty();
    server.verify(Duration.ofSeconds(2));
  }
}
