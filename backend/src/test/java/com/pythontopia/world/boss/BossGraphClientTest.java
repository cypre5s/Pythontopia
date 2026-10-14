package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class BossGraphClientTest {

  @Test
  void exposesGraphProblemFailuresAsRestClientExceptionsForFallbackConsumers() {
    RestTemplate graphRestTemplate = new RestTemplate();
    RestTemplate bossProblemRestTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(bossProblemRestTemplate).build();
    server
        .expect(ExpectedCount.times(2), requestTo("http://graph/agents/boss/problem"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    BossGraphClient client =
        new BossGraphClient(
            graphRestTemplate,
            bossProblemRestTemplate,
            new GraphServiceProperties("http://graph", "http://rag", ""));

    assertThatThrownBy(() -> client.requestProblem(7L, 1, 1, "PY43-KC-001", 1))
        .isInstanceOf(RestClientException.class)
        .hasCauseInstanceOf(BusinessException.class);

    server.verify();
  }

  @Test
  void exposesGraphEvaluationFailuresAsRestClientExceptionsForLocalJudgeFallback() {
    RestTemplate graphRestTemplate = new RestTemplate();
    RestTemplate bossProblemRestTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(graphRestTemplate).build();
    server
        .expect(ExpectedCount.times(2), requestTo("http://graph/agents/boss/evaluate"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    BossGraphClient client =
        new BossGraphClient(
            graphRestTemplate,
            bossProblemRestTemplate,
            new GraphServiceProperties("http://graph", "http://rag", ""));

    assertThatThrownBy(
            () ->
                client.evaluateAttempt(
                    "p1", "print(1)", List.of(new BossTestCasePayload("", "1", true)), 5))
        .isInstanceOf(RestClientException.class)
        .hasCauseInstanceOf(BusinessException.class);

    server.verify();
  }
}
