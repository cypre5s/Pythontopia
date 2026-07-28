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
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.feynman.dto.FeynmanDtos.TurnResponse;
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
  private final FeynmanGraphClient graphClient = mock(FeynmanGraphClient.class);
  private final FeynmanSessionService service =
      new FeynmanSessionService(
          jdbc,
          new ObjectMapper(),
          mock(MisconceptionExtractor.class),
          graphClient,
          mock(PublicProblemRepository.class));

  @Test
  void teachFailsFastWhenTuteeAgentIsUnavailable() {
    givenSession(OPEN_MISCONCEPTION);
    when(graphClient.step(anyString(), any(), anyString(), any())).thenReturn(Optional.empty());

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
  void emptyMisconceptionSetRemainsOpenAndReturnsHonestNotice() {
    givenSession(NO_MISCONCEPTION);
    when(graphClient.step(anyString(), any(), anyString(), any()))
        .thenReturn(Optional.of(new FeynmanGraphClient.FeynmanOutput("我听懂了", List.of(), null)));

    TurnResponse response = service.teach(7L, 10L, "range 的右边界不包含在结果里");

    assertThat(capturedSessionStatus()).isEqualTo("OPEN");
    assertThat(response.noticeMd()).contains("先去做题");
  }

  @Test
  void resolvedNonEmptyMisconceptionsStillMarkSessionTaught() {
    givenSession(OPEN_MISCONCEPTION);
    when(graphClient.step(anyString(), any(), anyString(), any()))
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
    when(graphClient.step(anyString(), any(), anyString(), any()))
        .thenReturn(Optional.of(new FeynmanGraphClient.FeynmanOutput("继续讲", List.of(), null)));

    service.teach(7L, 10L, "第二轮学生解释");

    ArgumentCaptor<List<FeynmanGraphClient.ConversationTurn>> history =
        ArgumentCaptor.forClass(List.class);
    verify(graphClient).step(eq("PY43-KC-016"), any(), eq("第二轮学生解释"), history.capture());
    assertThat(history.getValue())
        .extracting(
            FeynmanGraphClient.ConversationTurn::role,
            FeynmanGraphClient.ConversationTurn::content)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("user", "第一轮学生解释"),
            org.assertj.core.groups.Tuple.tuple("assistant", "第一轮学弟追问"));

    InOrder order = inOrder(jdbc);
    order.verify(jdbc).query(contains("FROM feynman_turn"), any(RowMapper.class), eq(10L), anyInt());
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
    when(graphClient.step(anyString(), any(), anyString(), any()))
        .thenReturn(Optional.of(new FeynmanGraphClient.FeynmanOutput("继续讲", List.of(), null)));

    service.teach(7L, 10L, "本轮解释");

    ArgumentCaptor<List<FeynmanGraphClient.ConversationTurn>> history =
        ArgumentCaptor.forClass(List.class);
    verify(graphClient).step(anyString(), any(), anyString(), history.capture());
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
        .update(contains("UPDATE feynman_session SET tutee_state"), any(), status.capture(), eq(10L));
    return status.getValue();
  }
}
