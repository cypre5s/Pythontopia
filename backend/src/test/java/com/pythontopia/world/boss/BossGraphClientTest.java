package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import java.util.List;
import java.util.Optional;
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
    CoursePackRepository coursePackRepository = mock(CoursePackRepository.class);
    when(coursePackRepository.findKcByCode(43L, "PY43-KC-001"))
        .thenReturn(Optional.of(kc("PY43-KC-001")));
    MockRestServiceServer server = MockRestServiceServer.bindTo(bossProblemRestTemplate).build();
    server
        .expect(ExpectedCount.times(2), requestTo("http://graph/agents/boss/problem"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    BossGraphClient client =
        new BossGraphClient(
            graphRestTemplate,
            bossProblemRestTemplate,
            new GraphServiceProperties("http://graph", "http://rag", ""),
            coursePackRepository);

    assertThatThrownBy(() -> client.requestProblem(7L, 1, 1, "PY43-KC-001", 1))
        .isInstanceOf(RestClientException.class)
        .hasCauseInstanceOf(BusinessException.class);

    server.verify();
  }

  @Test
  void sendsAuthoritativeCourseKcContextToGraph() {
    RestTemplate graphRestTemplate = new RestTemplate();
    RestTemplate bossProblemRestTemplate = new RestTemplate();
    CoursePackRepository coursePackRepository = mock(CoursePackRepository.class);
    when(coursePackRepository.findKcByCode(43L, "PY43-KC-001"))
        .thenReturn(
            Optional.of(
                new CourseKc(
                    1L,
                    43L,
                    "PY43-KC-001",
                    "中国计算机发展成就",
                    1L,
                    1,
                    "Basic",
                    "了解银河一号、龙芯 CPU、神威·太湖之光等发展成果。")));
    MockRestServiceServer server = MockRestServiceServer.bindTo(bossProblemRestTemplate).build();
    server
        .expect(requestTo("http://graph/agents/boss/problem"))
        .andExpect(jsonPath("$.kcName").value("中国计算机发展成就"))
        .andExpect(jsonPath("$.kcSummary").value("了解银河一号、龙芯 CPU、神威·太湖之光等发展成果。"))
        .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));
    BossGraphClient client =
        new BossGraphClient(
            graphRestTemplate,
            bossProblemRestTemplate,
            new GraphServiceProperties("http://graph", "http://rag", ""),
            coursePackRepository);

    client.requestProblem(7L, 1, 1, "PY43-KC-001", 1);

    server.verify();
  }

  @Test
  void exposesGraphEvaluationFailuresAsRestClientExceptionsForLocalJudgeFallback() {
    RestTemplate graphRestTemplate = new RestTemplate();
    RestTemplate bossProblemRestTemplate = new RestTemplate();
    CoursePackRepository coursePackRepository = mock(CoursePackRepository.class);
    MockRestServiceServer server = MockRestServiceServer.bindTo(graphRestTemplate).build();
    server
        .expect(ExpectedCount.times(2), requestTo("http://graph/agents/boss/evaluate"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    BossGraphClient client =
        new BossGraphClient(
            graphRestTemplate,
            bossProblemRestTemplate,
            new GraphServiceProperties("http://graph", "http://rag", ""),
            coursePackRepository);

    assertThatThrownBy(
            () ->
                client.evaluateAttempt(
                    "p1", "print(1)", List.of(new BossTestCasePayload("", "1", true)), 5))
        .isInstanceOf(RestClientException.class)
        .hasCauseInstanceOf(BusinessException.class);

    server.verify();
  }

  private static CourseKc kc(String kcCode) {
    return new CourseKc(1L, 43L, kcCode, "变量", 1L, 1, "Basic", "课程知识点摘要");
  }
}
