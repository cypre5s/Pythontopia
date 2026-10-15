package com.pythontopia.mainline.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.GraphServiceProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ChapterTestGraphClientTest {

  @Test
  void upstreamFailureDoesNotExposeRemoteResponseBody() {
    String privateContent = "private-chapter-test-response-sentinel";
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    ObjectMapper mapper = new ObjectMapper();
    ChapterTestGraphClient client =
        new ChapterTestGraphClient(
            restTemplate,
            new GraphServiceProperties("http://graph.test", "http://rag.test", "secret"),
            mapper);
    server
        .expect(requestTo("http://graph.test/agents/chapter-test/generate"))
        .andRespond(
            withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_PLAIN)
                .body(privateContent));

    assertThatThrownBy(
            () ->
                client.generate(
                    new ChapterTestGraphClient.GenerateRequest(
                        7L,
                        2,
                        List.of(),
                        mapper.createObjectNode(),
                        mapper.createObjectNode(),
                        new ChapterTestGraphClient.GenerateRequest.Counts(1, 0, 0, 0),
                        List.of(),
                        List.of())))
        .isInstanceOfSatisfying(
            ChapterTestGenerationException.class,
            exception -> {
              assertThat(exception.getMessage()).contains("chapter-test graph 调用失败");
              assertThat(exception.getMessage()).doesNotContain(privateContent);
            });
    server.verify();
  }

  @Test
  void sendsInternalTokenAndMapsNestedGraphResponse() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    ObjectMapper mapper = new ObjectMapper();
    ChapterTestGraphClient client =
        new ChapterTestGraphClient(
            restTemplate,
            new GraphServiceProperties("http://graph.test", "http://rag.test", "internal-secret"),
            mapper);
    server
        .expect(requestTo("http://graph.test/agents/chapter-test/generate"))
        .andExpect(header("X-Internal-Token", "internal-secret"))
        .andExpect(jsonPath("$.counts.choice").value(10))
        .andExpect(jsonPath("$.counts.coding").value(3))
        .andExpect(jsonPath("$.preferred_coding_questions[0].source_problem_id").value(476))
        .andRespond(
            withSuccess(
                """
                {
                  "status":"SUCCESS",
                  "questions":{"items":[{"qid":"c1","type":"CHOICE"}]},
                  "profile_snapshot":{"chapter":2},
                  "generator_meta":{"run_id":"chtest-abc","agent":"PaperDesignerAgent"}
                }
                """,
                MediaType.APPLICATION_JSON));

    var bank =
        new ChapterProblemBankRepository.PreferredCodingQuestion(
            476L,
            "圆面积计算",
            "读入半径并输出面积",
            List.of(new ChapterProblemBankRepository.CodingCase("1", "3.1415")),
            List.of(new ChapterProblemBankRepository.CodingCase("2", "12.5660")),
            "print('3.1415')",
            "PY43-KC-016");
    var paper =
        client.generate(
            new ChapterTestGraphClient.GenerateRequest(
                7L,
                2,
                List.of("PY43-KC-016"),
                mapper.createObjectNode(),
                mapper.createObjectNode(),
                new ChapterTestGraphClient.GenerateRequest.Counts(10, 10, 10, 3),
                List.of(),
                List.of(bank)));

    assertThat(paper.questions().path("items")).hasSize(1);
    assertThat(paper.generatorMeta().path("run_id").asText()).isEqualTo("chtest-abc");
    assertThat(paper.profileSnapshot().path("chapter").asInt()).isEqualTo(2);
    server.verify();
  }
}
