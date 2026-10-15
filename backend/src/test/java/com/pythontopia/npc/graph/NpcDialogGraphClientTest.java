package com.pythontopia.npc.graph;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.npc.dto.NpcDialogResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

class NpcDialogGraphClientTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final NpcDialogGraphClient client = new NpcDialogGraphClient(restTemplate, properties);

  private static MemoryItemDto memory() {
    return new MemoryItemDto(
        9L,
        "practice_failed",
        "练习失败：PY43-KC-016",
        List.of("PY43-KC-016"),
        "2026-06-09T00:00:00Z",
        4,
        1.0,
        0.8,
        0.9,
        0.91);
  }

  @Test
  void postsToAgentsPathWithSnakeCaseBodyAndMapsSnakeCaseResponse() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/npc/dialog"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.npc_key").value("coach_q"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.persona_core").value("core"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.user_message").value("我又错了"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.memories[0].event_id").value(9))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.memories[0].summary").value("练习失败：PY43-KC-016"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.memories[0].kc_codes[0]").value("PY43-KC-016"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {"reply_md": "记得你卡过 KC-016", "cited_event_ids": [9], "grounded": true}
                """,
                MediaType.APPLICATION_JSON));

    Optional<NpcDialogResponse> response =
        client.talk("coach_q", "core", List.of(memory()), "我又错了");

    assertThat(response).isPresent();
    assertThat(response.get().replyMd()).contains("KC-016");
    assertThat(response.get().citedEventIds()).containsExactly(9L);
    assertThat(response.get().grounded()).isTrue();
    server.verify(Duration.ofSeconds(2));
  }

  @Test
  void upstreamFailureYieldsEmptyWithoutLoggingResponseBody() {
    String privateContent = "private-npc-dialog-sentinel";
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/npc/dialog"))
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_PLAIN)
                .body(privateContent));

    Logger logger = (Logger) LoggerFactory.getLogger(NpcDialogGraphClient.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      Optional<NpcDialogResponse> response =
          client.talk("coach_q", "core", List.of(memory()), "你好");

      assertThat(response).isEmpty();
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .allSatisfy(message -> assertThat(message).doesNotContain(privateContent));
      server.verify(Duration.ofSeconds(2));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
