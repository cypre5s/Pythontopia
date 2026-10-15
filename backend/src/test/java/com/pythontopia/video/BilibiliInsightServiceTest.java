package com.pythontopia.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.common.error.BusinessException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Drives {@link BilibiliInsightService} against a mocked graph (via {@link MockRestServiceServer}
 * bound to {@code graphRestTemplate}), verifying the snake→camel field mapping ({@code key_points →
 * keyPoints}, {@code t_sec → tSec}, {@code based_on → basedOn}), the snake_case request body
 * ({@code kc_codes}) + {@code X-Internal-Token} header sent upstream, null question/kcCodes
 * defaulting, and the {@code VIDEO_INSIGHT_UNAVAILABLE} error wrapping. Mirrors
 * {@link com.pythontopia.workshop.WorkshopGraphClientTest}.
 */
class BilibiliInsightServiceTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final BilibiliInsightService service =
      new BilibiliInsightService(restTemplate, properties);

  @Test
  void mapsSnakeCaseGraphResponseToCamelCase() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo("http://graph.test/agents/video/bilibili/insight"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Internal-Token", "secret"))
        .andExpect(jsonPath("$.bvid").value("BV1xx411c7XD"))
        .andExpect(jsonPath("$.question").value("for 循环怎么用"))
        .andExpect(jsonPath("$.kc_codes[0]").value("PY43-KC-030"))
        .andRespond(
            withSuccess(
                """
                {
                  "bvid": "BV1xx411c7XD",
                  "overview": "前 2 分钟讲 for 循环语法",
                  "key_points": [
                    {"t_sec": 30, "label": "for 循环语法", "source": "ai_conclusion"},
                    {"t_sec": 95, "label": "range 用法", "source": "pages"}
                  ],
                  "based_on": "ai_conclusion",
                  "degraded": false
                }
                """,
                MediaType.APPLICATION_JSON));

    BilibiliInsightResponse response =
        service.insight(
            new BilibiliInsightRequest("BV1xx411c7XD", "for 循环怎么用", List.of("PY43-KC-030")));

    assertThat(response.bvid()).isEqualTo("BV1xx411c7XD");
    assertThat(response.overview()).contains("for 循环语法");
    assertThat(response.keyPoints()).hasSize(2);
    assertThat(response.keyPoints().get(0).tSec()).isEqualTo(30);
    assertThat(response.keyPoints().get(0).label()).isEqualTo("for 循环语法");
    assertThat(response.keyPoints().get(0).source()).isEqualTo("ai_conclusion");
    assertThat(response.keyPoints().get(1).tSec()).isEqualTo(95);
    assertThat(response.keyPoints().get(1).source()).isEqualTo("pages");
    assertThat(response.basedOn()).isEqualTo("ai_conclusion");
    assertThat(response.degraded()).isFalse();
    server.verify();
  }

  @Test
  void emptyKeyPointsWhenNoTimeline() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo("http://graph.test/agents/video/bilibili/insight"))
        .andRespond(
            withSuccess(
                """
                {
                  "bvid": "BV1onlymeta",
                  "overview": "只有标题与简介，无可用时间轴",
                  "key_points": [],
                  "based_on": "metadata_only",
                  "degraded": true
                }
                """,
                MediaType.APPLICATION_JSON));

    BilibiliInsightResponse response =
        service.insight(new BilibiliInsightRequest("BV1onlymeta", "这视频讲啥", List.of()));

    assertThat(response.keyPoints()).isEmpty();
    assertThat(response.basedOn()).isEqualTo("metadata_only");
    assertThat(response.degraded()).isTrue();
    server.verify();
  }

  @Test
  void defaultsNullQuestionAndKcCodesInGraphPayload() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo("http://graph.test/agents/video/bilibili/insight"))
        .andExpect(jsonPath("$.bvid").value("BV1"))
        .andExpect(jsonPath("$.question").value(""))
        .andExpect(jsonPath("$.kc_codes").isArray())
        .andRespond(
            withSuccess(
                "{\"bvid\":\"BV1\",\"overview\":\"\",\"key_points\":[],\"based_on\":\"metadata_only\",\"degraded\":false}",
                MediaType.APPLICATION_JSON));

    BilibiliInsightResponse response =
        service.insight(new BilibiliInsightRequest("BV1", null, null));

    assertThat(response.bvid()).isEqualTo("BV1");
    server.verify();
  }

  @Test
  void wrapsUpstreamFailureWithoutLoggingResponseBody() {
    String privateContent = "private-video-question-sentinel";
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(requestTo("http://graph.test/agents/video/bilibili/insight"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body(privateContent));

    Logger logger = (Logger) LoggerFactory.getLogger(BilibiliInsightService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      assertThatThrownBy(
              () ->
                  service.insight(
                      new BilibiliInsightRequest(
                          "BV1xx411c7XD", "q", Arrays.asList("PY43-KC-030"))))
          .isInstanceOfSatisfying(
              BusinessException.class,
              exception -> {
                assertThat(exception.code()).isEqualTo("VIDEO_INSIGHT_UNAVAILABLE");
                assertThat(exception.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
              });
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(message -> assertThat(message).doesNotContain(privateContent));
      server.verify();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
