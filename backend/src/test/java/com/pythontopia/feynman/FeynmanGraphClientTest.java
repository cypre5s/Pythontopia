package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pythontopia.common.config.GraphServiceProperties;
import com.pythontopia.feynman.FeynmanGraphClient.FeynmanOutput;
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

class FeynmanGraphClientTest {

  private final RestTemplate restTemplate = new RestTemplate();
  private final GraphServiceProperties properties =
      new GraphServiceProperties("http://graph.test", "http://rag.test", "secret");
  private final FeynmanGraphClient client = new FeynmanGraphClient(restTemplate, properties);

  @Test
  void teachingStepUsesDedicatedTuteeClientInsteadOfShortGraphClient() {
    RestTemplate shortGraphTemplate = new RestTemplate();
    RestTemplate tuteeTemplate = new RestTemplate();
    MockRestServiceServer shortGraphServer =
        MockRestServiceServer.bindTo(shortGraphTemplate).build();
    MockRestServiceServer tuteeServer = MockRestServiceServer.bindTo(tuteeTemplate).build();
    FeynmanGraphClient isolatedClient =
        new FeynmanGraphClient(shortGraphTemplate, tuteeTemplate, properties);
    tuteeServer
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/feynman/tutee/step"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.topic").value("for 循环与遍历"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.learner_context.level").value("beginner"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "reply_md": "继续讲",
                  "updated_misconceptions": [],
                  "produced_code": null
                }
                """,
                MediaType.APPLICATION_JSON));

    assertThat(
            isolatedClient.step(
                "PY43-KC-031",
                "for 循环与遍历",
                new FeynmanGraphClient.LearnerContext("beginner", "examples"),
                List.of(),
                "解释循环",
                List.of()))
        .isPresent();
    tuteeServer.verify(Duration.ofSeconds(2));
    shortGraphServer.verify(Duration.ofSeconds(2));
  }

  @Test
  void postsToAgentsPathWithSnakeCaseMisconceptionsAndMapsResponse() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/feynman/tutee/step"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.kc_code").value("PY43-KC-016"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.topic").value("循环"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.learner_context.response_style")
                .value("step_by_step"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.student_teaching").value("range 右端不包含"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.misconceptions[0].tag").value("off_by_one_range"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.misconceptions[0].evidence_event_ids[0]")
                .value(501))
        .andExpect(MockRestRequestMatchers.jsonPath("$.history[0].role").value("user"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.history[0].content").value("第一轮学生解释"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.history[1].role").value("assistant"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.history[1].content").value("第一轮学弟追问"))
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
            "循环",
            new FeynmanGraphClient.LearnerContext("intermediate", "step_by_step"),
            List.of(new Misconception("off_by_one_range", List.of(501L), false)),
            "range 右端不包含",
            List.of(
                new FeynmanGraphClient.ConversationTurn("user", "第一轮学生解释"),
                new FeynmanGraphClient.ConversationTurn("assistant", "第一轮学弟追问")));

    assertThat(output).isPresent();
    assertThat(output.get().replyMd()).isEqualTo("我懂了");
    assertThat(output.get().updatedMisconceptions()).hasSize(1);
    assertThat(output.get().updatedMisconceptions().get(0).tag()).isEqualTo("off_by_one_range");
    assertThat(output.get().updatedMisconceptions().get(0).evidenceEventIds())
        .containsExactly(501L);
    assertThat(output.get().updatedMisconceptions().get(0).resolved()).isTrue();
    assertThat(output.get().producedCode()).contains("range(1, 6)");
    server.verify(Duration.ofSeconds(2));
  }

  /** 下游不可达只返回 empty；是否降级由调用方决定（teach 侧一律 fail fast）。 */
  @Test
  void upstreamFailuresYieldEmptyWithoutLoggingStudentOrRemoteContent() {
    String privateContent = "private-feynman-response-sentinel";
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/feynman/tutee/step"))
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_PLAIN)
                .body(privateContent));
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/feynman/bug-exposure"))
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_PLAIN)
                .body(privateContent));

    Logger logger = (Logger) LoggerFactory.getLogger(FeynmanGraphClient.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);
    try {
      Optional<FeynmanOutput> output =
          client.step(
              "PY43-KC-016",
              null,
              new FeynmanGraphClient.LearnerContext("beginner", "examples"),
              List.of(new Misconception("off_by_one_range", List.of(501L), false)),
              "随便讲",
              List.of());
      Optional<FeynmanGraphClient.BugExposure> bugExposure =
          client.bugExposure(
              "PY43-KC-016",
              privateContent,
              new FeynmanGraphClient.LearnerContext("beginner", "examples"),
              List.of());

      assertThat(output).isEmpty();
      assertThat(bugExposure).isEmpty();
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
  void bugExposureMapsBugLinesAndKeepsAnswerOffWire() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server
        .expect(MockRestRequestMatchers.requestTo("http://graph.test/agents/feynman/bug-exposure"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.kc_code").value("PY43-KC-004"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.topic").value("控制台输入与数据类型转换"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.learner_context.level").value("beginner"))
        .andExpect(
            MockRestRequestMatchers.jsonPath("$.misconceptions[0].tag").value("input_type_string"))
        .andRespond(
            MockRestResponseCreators.withSuccess(
                """
                {
                  "source": "real_misconception",
                  "misconception_tag": "input_type_string",
                  "buggy_code": "age = input('age: ')\\nprint(age + 1)",
                  "fixed_code": "age = int(input('age: '))\\nprint(age + 1)",
                  "bug_line_indices": [0],
                  "hint_md": "input() 返回什么类型？",
                  "difficulty": "easy",
                  "generated_by_ai": true
                }
                """,
                MediaType.APPLICATION_JSON));

    Optional<FeynmanGraphClient.BugExposure> exposure =
        client.bugExposure(
            "PY43-KC-004",
            "控制台输入与数据类型转换",
            new FeynmanGraphClient.LearnerContext("beginner", "examples"),
            List.of(new Misconception("input_type_string", List.of(601L), false)));

    assertThat(exposure).isPresent();
    assertThat(exposure.get().source()).isEqualTo("real_misconception");
    assertThat(exposure.get().misconceptionTag()).isEqualTo("input_type_string");
    assertThat(exposure.get().buggyCode()).contains("input('age: ')");
    assertThat(exposure.get().bugLineIndices()).containsExactly(0);
    assertThat(exposure.get().hintMd()).isNotBlank();
    assertThat(exposure.get().difficulty()).isEqualTo("easy");
    assertThat(exposure.get().generatedByAi()).isTrue();
    server.verify(Duration.ofSeconds(2));
  }
}
