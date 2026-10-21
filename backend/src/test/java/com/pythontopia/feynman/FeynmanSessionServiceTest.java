package com.pythontopia.feynman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.feynman.dto.FeynmanDtos.TurnResponse;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings("unchecked")
class FeynmanSessionServiceTest {

  private static final String OPEN_MISCONCEPTION =
      "{\"misconceptions\":[{\"tag\":\"off_by_one_range\",\"evidenceEventIds\":[501],"
          + "\"resolved\":false}]}";
  private static final String NO_MISCONCEPTION = "{\"misconceptions\":[]}";

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final FeynmanGraphClient graphClient = mock(FeynmanGraphClient.class);
  private final ProfileRepository profileRepository = mock(ProfileRepository.class);
  private final FeynmanBugBankRepository bugBank = mock(FeynmanBugBankRepository.class);
  private final FeynmanSessionService service =
      new FeynmanSessionService(
          jdbc,
          objectMapper,
          mock(MisconceptionExtractor.class),
          graphClient,
          mock(PublicProblemRepository.class),
          profileRepository,
          bugBank);

  @Test
  void teachFailsFastWhenTuteeAgentIsUnavailable() {
    givenSession(OPEN_MISCONCEPTION);
    when(graphClient.step(anyString(), any(), any(), any(), anyString(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.teach(7L, 10L, "range 的右边界不包含在结果里"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
              assertThat(exception.code()).isEqualTo("FEYNMAN_TUTEE_UNAVAILABLE");
            });

    verify(jdbc, never()).queryForObject(contains("'TUTEE'"), eq(Long.class), any(Object[].class));
    verify(jdbc, never())
        .update(contains("UPDATE feynman_session SET tutee_state"), any(), any(), any());
  }

  @Test
  void emptyMisconceptionSetAllowsTeachingWithoutCreatingMistakeEvidence() {
    givenSession(NO_MISCONCEPTION);
    ObjectNode dimensions = objectMapper.createObjectNode();
    dimensions.putObject("knowledge_base").put("python_self_rated_level", 4);
    dimensions
        .putObject("learning_preference")
        .set("media_priority", objectMapper.valueToTree(List.of("code", "text")));
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dimensions));
    when(jdbc.query(contains("FROM course_kc"), any(RowMapper.class), eq("PY43-KC-016")))
        .thenReturn(List.of("for 循环与遍历"));
    when(graphClient.step(anyString(), any(), any(), any(), anyString(), any()))
        .thenReturn(Optional.of(new FeynmanGraphClient.FeynmanOutput("我听懂了", List.of(), null)));

    TurnResponse response = service.teach(7L, 10L, "range 的右边界不包含在结果里");

    assertThat(capturedSessionStatus()).isEqualTo("OPEN");
    assertThat(response.noticeMd()).isNull();
    verify(graphClient)
        .step(
            eq("PY43-KC-016"),
            eq("for 循环与遍历"),
            eq(new FeynmanGraphClient.LearnerContext("advanced", "practice")),
            eq(List.of()),
            anyString(),
            any());
    ArgumentCaptor<Object> state = ArgumentCaptor.forClass(Object.class);
    verify(jdbc)
        .update(
            contains("UPDATE feynman_session SET tutee_state"),
            state.capture(),
            eq("OPEN"),
            eq(10L));
    assertThat(state.getValue()).isEqualTo("{\"misconceptions\":[]}");
  }

  @Test
  void noMistakeBugHuntReusesSharedSampleAtLearnerDifficulty() {
    givenSession(NO_MISCONCEPTION);
    ObjectNode dimensions = objectMapper.createObjectNode();
    dimensions.putObject("knowledge_base").put("python_self_rated_level", 5);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.of(dimensions));
    when(bugBank.findOne("PY43-KC-016", "hard"))
        .thenReturn(
            Optional.of(
                new FeynmanBugBankRepository.BugSample(
                    "topic_specific_bug",
                    "a = 1\nb = 2\nc = 3\nd = 4\ne = a - b\nprint(a)\nprint(e)",
                    "a = 1\nb = 2\nc = 3\nd = 4\ne = a + b\nprint(a)\nprint(e)",
                    List.of(4),
                    "检查运算符。",
                    "hard")));

    var response = service.bugHunt(7L, 10L);

    assertThat(response.source()).isEqualTo("generic_beginner_bug");
    assertThat(response.buggyCode()).contains("e = a - b");
    verify(graphClient, never()).bugExposure(anyString(), anyString(), any(), any());
  }

  @Test
  void noMistakeAiSampleIsSavedOnlyWithMechanicalValidationAndCourseTopic() {
    givenSession(NO_MISCONCEPTION);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.empty());
    when(bugBank.findOne("PY43-KC-016", "easy")).thenReturn(Optional.empty());
    when(jdbc.query(contains("FROM course_kc"), any(RowMapper.class), eq("PY43-KC-016")))
        .thenReturn(List.of("for 循环与遍历"));
    FeynmanGraphClient.BugExposure generated =
        new FeynmanGraphClient.BugExposure(
            "generic_beginner_bug",
            "topic_specific_bug",
            "a = 1\nb = 2\nc = 3\nd = 4\ne = a - b\nprint(a)\nprint(e)",
            "a = 1\nb = 2\nc = 3\nd = 4\ne = a + b\nprint(a)\nprint(e)",
            List.of(4),
            "检查运算符。",
            "easy",
            true);
    when(graphClient.bugExposure(anyString(), anyString(), any(), any()))
        .thenReturn(Optional.of(generated));

    service.bugHunt(7L, 10L);

    verify(bugBank).insertIfAbsent("PY43-KC-016", "for 循环与遍历", generated);
  }

  @Test
  void actualMisconceptionSamplesAreNeverSavedToSharedBank() {
    givenSession(OPEN_MISCONCEPTION);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.empty());
    when(jdbc.query(contains("FROM course_kc"), any(RowMapper.class), eq("PY43-KC-016")))
        .thenReturn(List.of("for 循环与遍历"));
    FeynmanGraphClient.BugExposure generated =
        new FeynmanGraphClient.BugExposure(
            "real_misconception",
            "off_by_one_range",
            "a = 1\nb = 2\nc = 3\nd = 4\ne = a - b\nprint(a)\nprint(e)",
            "a = 1\nb = 2\nc = 3\nd = 4\ne = a + b\nprint(a)\nprint(e)",
            List.of(4),
            "检查运算符。",
            "easy",
            true);
    when(graphClient.bugExposure(anyString(), anyString(), any(), any()))
        .thenReturn(Optional.of(generated));

    service.bugHunt(7L, 10L);

    verify(bugBank, never()).insertIfAbsent(anyString(), anyString(), any());
  }

  @Test
  void genericSampleWithForgedBugLineIndicesIsNotSaved() {
    givenSession(NO_MISCONCEPTION);
    when(profileRepository.findDimensions(7L)).thenReturn(Optional.empty());
    when(bugBank.findOne("PY43-KC-016", "easy")).thenReturn(Optional.empty());
    when(jdbc.query(contains("FROM course_kc"), any(RowMapper.class), eq("PY43-KC-016")))
        .thenReturn(List.of("for 循环与遍历"));
    FeynmanGraphClient.BugExposure invalid =
        new FeynmanGraphClient.BugExposure(
            "generic_beginner_bug",
            "topic_specific_bug",
            "a = 1\nb = 2\nc = 3\nd = 4\ne = a - b\nprint(a)\nprint(e)",
            "a = 1\nb = 2\nc = 3\nd = 4\ne = a + b\nprint(a)\nprint(e)",
            List.of(3),
            "检查运算符。",
            "easy",
            true);
    when(graphClient.bugExposure(anyString(), anyString(), any(), any()))
        .thenReturn(Optional.of(invalid));

    service.bugHunt(7L, 10L);

    verify(bugBank, never()).insertIfAbsent(anyString(), anyString(), any());
  }

  @Test
  void resolvedNonEmptyMisconceptionsStillMarkSessionTaught() {
    givenSession(OPEN_MISCONCEPTION);
    when(graphClient.step(anyString(), any(), any(), any(), anyString(), any()))
        .thenReturn(
            Optional.of(
                new FeynmanGraphClient.FeynmanOutput(
                    "我听懂了",
                    List.of(new Misconception("off_by_one_range", List.of(501L), true)),
                    null)));

    TurnResponse response = service.teach(7L, 10L, "range 的右边界不包含在结果里");

    assertThat(capturedSessionStatus()).isEqualTo("TAUGHT");
    assertThat(response.noticeMd()).isNull();
  }

  @Test
  void teachMapsStoredTurnsToChronologicalGraphHistoryWithoutDuplicatingCurrentTeaching()
      throws Exception {
    givenSession(OPEN_MISCONCEPTION);
    when(jdbc.query(contains("FROM feynman_turn"), any(RowMapper.class), eq(10L), anyInt()))
        .thenAnswer(
            invocation -> {
              RowMapper<FeynmanGraphClient.ConversationTurn> mapper = invocation.getArgument(1);
              ResultSet newest = mock(ResultSet.class);
              when(newest.getString("role")).thenReturn("TUTEE");
              when(newest.getString("content")).thenReturn("第一轮学弟追问");
              ResultSet oldest = mock(ResultSet.class);
              when(oldest.getString("role")).thenReturn("STUDENT");
              when(oldest.getString("content")).thenReturn("第一轮学生解释");
              return List.of(mapper.mapRow(newest, 0), mapper.mapRow(oldest, 1));
            });
    when(graphClient.step(anyString(), any(), any(), any(), anyString(), any()))
        .thenReturn(Optional.of(new FeynmanGraphClient.FeynmanOutput("继续讲", List.of(), null)));

    service.teach(7L, 10L, "第二轮学生解释");

    ArgumentCaptor<List<FeynmanGraphClient.ConversationTurn>> history =
        ArgumentCaptor.forClass(List.class);
    verify(graphClient)
        .step(eq("PY43-KC-016"), any(), any(), any(), eq("第二轮学生解释"), history.capture());
    assertThat(history.getValue())
        .extracting(
            FeynmanGraphClient.ConversationTurn::role, FeynmanGraphClient.ConversationTurn::content)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("user", "第一轮学生解释"),
            org.assertj.core.groups.Tuple.tuple("assistant", "第一轮学弟追问"));

    InOrder order = inOrder(jdbc);
    order
        .verify(jdbc)
        .query(contains("FROM feynman_turn"), any(RowMapper.class), eq(10L), anyInt());
    order.verify(jdbc).update(contains("'STUDENT'"), eq(10L), eq("第二轮学生解释"));
  }

  @Test
  void historyKeepsNewestTurnsWithinCharacterBudget() {
    givenSession(OPEN_MISCONCEPTION);
    when(jdbc.query(contains("FROM feynman_turn"), any(RowMapper.class), eq(10L), anyInt()))
        .thenReturn(
            List.of(
                new FeynmanGraphClient.ConversationTurn("assistant", "新".repeat(4000)),
                new FeynmanGraphClient.ConversationTurn("user", "旧".repeat(2500))));
    when(graphClient.step(anyString(), any(), any(), any(), anyString(), any()))
        .thenReturn(Optional.of(new FeynmanGraphClient.FeynmanOutput("继续讲", List.of(), null)));

    service.teach(7L, 10L, "本轮解释");

    ArgumentCaptor<List<FeynmanGraphClient.ConversationTurn>> history =
        ArgumentCaptor.forClass(List.class);
    verify(graphClient).step(anyString(), any(), any(), any(), anyString(), history.capture());
    assertThat(history.getValue())
        .singleElement()
        .extracting(FeynmanGraphClient.ConversationTurn::content)
        .asString()
        .hasSize(4000);
  }

  private void givenSession(String tuteeState) {
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(10L), eq(7L)))
        .thenReturn(
            new FeynmanSessionService.SessionData(
                10L, 7L, "PY43-KC-016", 476L, "OPEN", tuteeState));
    when(jdbc.query(contains("FROM feynman_turn"), any(RowMapper.class), eq(10L), anyInt()))
        .thenReturn(List.of());
    when(jdbc.queryForObject(contains("'TUTEE'"), eq(Long.class), any(Object[].class)))
        .thenReturn(99L);
  }

  private Object capturedSessionStatus() {
    ArgumentCaptor<Object> status = ArgumentCaptor.forClass(Object.class);
    verify(jdbc)
        .update(
            contains("UPDATE feynman_session SET tutee_state"), any(), status.capture(), eq(10L));
    return status.getValue();
  }
}
