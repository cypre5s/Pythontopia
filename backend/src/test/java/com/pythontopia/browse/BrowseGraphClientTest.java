package com.pythontopia.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.browse.graph.BrowseGraphClient;
import com.pythontopia.browse.graph.BrowseGraphResult;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

/**
 * {@link BrowseGraphClient} 单测（MockRestServiceServer，零外网）。验证：snake_case 请求体（含服务端注入
 * 的 allowed_domains）、成功响应解析、空响应→502、传输失败→502 且<b>不重试</b>（非幂等，浏览器有副作用）。
 */
class BrowseGraphClientTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final BrowseGraphClient client = new BrowseGraphClient(restTemplate, properties);

  @Test
  void browseSendsSnakeCaseBodyAndParsesResult() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/browse/run"))
        .andExpect(MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
        .andExpect(MockRestRequestMatchers.header("X-Internal-Token", "secret"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.goal").value("查 sorted 稳定性"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.max_steps").value(6))
        .andExpect(MockRestRequestMatchers.jsonPath("$.user_id").value(7))
        .andExpect(MockRestRequestMatchers.jsonPath("$.allowed_domains[0]").value("docs.python.org"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "steps": [
                    {"index": 0, "action": "navigate", "url": "https://docs.python.org/3/howto/sorting.html", "summary": "打开排序指南", "title": "Sorting HOW TO", "snippet": "打开排序指南"},
                    {"index": 1, "action": "read", "url": "https://docs.python.org/3/howto/sorting.html", "summary": "sorted 稳定", "title": "Sorting HOW TO", "snippet": "sorts are guaranteed to be stable"}
                  ],
                  "final_summary": "sorted 是稳定排序",
                  "citations": ["https://docs.python.org/3/howto/sorting.html"],
                  "truncated": false,
                  "error": ""
                }
                """,
                MediaType.APPLICATION_JSON));

    BrowseGraphResult result =
        client.browse("查 sorted 稳定性", List.of("docs.python.org", "pypi.org"), 6, 7L);

    assertThat(result.steps()).hasSize(2);
    assertThat(result.steps().get(0).action()).isEqualTo("navigate");
    assertThat(result.steps().get(0).title()).isEqualTo("Sorting HOW TO");
    assertThat(result.steps().get(1).snippet()).isEqualTo("sorts are guaranteed to be stable");
    assertThat(result.finalSummary()).isEqualTo("sorted 是稳定排序");
    assertThat(result.citations()).containsExactly("https://docs.python.org/3/howto/sorting.html");
    assertThat(result.truncated()).isFalse();
    assertThat(result.error()).isEmpty();
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void browseKeepsBackwardCompatibleSummaryWhenGraphOmitsNewEvidenceFields() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/browse/run"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "steps": [{"index": 0, "action": "read", "url": "https://docs.python.org/3/", "summary": "legacy summary"}],
                  "final_summary": "legacy summary",
                  "citations": ["https://docs.python.org/3/"],
                  "truncated": false,
                  "error": ""
                }
                """,
                MediaType.APPLICATION_JSON));

    BrowseGraphResult result = client.browse("goal", List.of("docs.python.org"), 1, 1L);

    assertThat(result.steps().get(0).title()).isEmpty();
    assertThat(result.steps().get(0).snippet()).isEqualTo("legacy summary");
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void browseParsesGraphFailFastResultWithoutThrowing() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/browse/run"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "steps": [{"index": 0, "action": "navigate", "url": "https://docs.python.org/3/", "summary": "打开"}],
                  "final_summary": "",
                  "citations": [],
                  "truncated": false,
                  "error": "目标站点超时"
                }
                """,
                MediaType.APPLICATION_JSON));

    BrowseGraphResult result = client.browse("goal", List.of("docs.python.org"), 6, 1L);

    assertThat(result.error()).isEqualTo("目标站点超时");
    assertThat(result.steps()).hasSize(1);
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void browseWrapsTransportFailureWithoutRetryOrLoggingRemoteContent() {
    String privateContent = "private-browse-response-sentinel";
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    // 仅允许 1 次请求：BROWSE 非幂等不重试，重试会触发 MockServer「未预期请求」失败。
    server
        .expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo("http://graph.test/agents/browse/run"))
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.TEXT_PLAIN)
                .body(privateContent));

    Logger logger = (Logger) LoggerFactory.getLogger(BrowseGraphClient.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      assertThatThrownBy(() -> client.browse("goal", List.of("docs.python.org"), 6, 1L))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex -> {
                BusinessException be = (BusinessException) ex;
                assertThat(be.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(be.code()).isEqualTo("BROWSE_GRAPH_UNAVAILABLE");
                assertThat(be.getMessage()).doesNotContain(privateContent);
              });
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(message -> assertThat(message).doesNotContain(privateContent));
      server.verify(Duration.ofSeconds(2));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  @Test
  void browseTreatsEmptyBodyAsBadGateway() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/browse/run"))
        .andRespond(MockRestResponseCreators.withSuccess());

    assertThatThrownBy(() -> client.browse("goal", List.of("docs.python.org"), 6, 1L))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).status())
        .isEqualTo(HttpStatus.BAD_GATEWAY);
    server.verify(Duration.ofSeconds(2));
  }
}
