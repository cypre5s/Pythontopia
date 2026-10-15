package com.pythontopia.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class BilibiliVideoServiceTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final BilibiliVideoService service = new BilibiliVideoService(restTemplate, properties);

  @Test
  void sendsInternalTokenAndMapsGraphEnvelope() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(
            requestTo(
                "http://graph.test/agents/video/bilibili/search?keyword=for%E5%BE%AA%E7%8E%AF%20Python&limit=3"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-Internal-Token", "secret"))
        .andRespond(
            withSuccess(
                """
                {
                  "items": [
                    {
                      "bvid": "BV1xx411c7XD",
                      "title": "for 循环",
                      "author": "UP主小明",
                      "description": "讲解 for 循环",
                      "ai_summary": "速览 for 循环",
                      "cover": "https://i2.hdslb.com/bfs/archive/abc.jpg",
                      "duration_sec": 300,
                      "play": 123,
                      "danmaku": 4,
                      "pubdate": 1600000000,
                      "url": "https://www.bilibili.com/video/BV1xx411c7XD"
                    }
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    var items = service.search("for循环 Python", 3);

    assertThat(items).hasSize(1);
    assertThat(items.get(0).bvid()).isEqualTo("BV1xx411c7XD");
    assertThat(items.get(0).coverUrl()).startsWith("https://");
    assertThat(items.get(0).durationSec()).isEqualTo(300);
    server.verify();
  }

  @Test
  void wrapsGraphFailureWithoutLoggingRemoteContent() {
    String privateContent = "private-bilibili-response-sentinel";
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(
            requestTo("http://graph.test/agents/video/bilibili/search?keyword=x&limit=8"))
        .andRespond(
            withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.TEXT_PLAIN)
                .body(privateContent));

    Logger logger = (Logger) LoggerFactory.getLogger(BilibiliVideoService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      assertThatThrownBy(() -> service.search("x", 8))
          .isInstanceOfSatisfying(
              BusinessException.class,
              exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(exception.code()).isEqualTo("VIDEO_SEARCH_UNAVAILABLE");
                assertThat(exception.getMessage()).doesNotContain(privateContent);
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
