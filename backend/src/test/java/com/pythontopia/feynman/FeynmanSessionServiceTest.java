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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** 费曼教学回合：Agent 不可达必须 fail fast，且「无误解」不得被判成「已教会」。 */
@SuppressWarnings("unchecked")
class FeynmanSessionServiceTest {

  private static final String ONE_OPEN_MISCONCEPTION =
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

  /** 捕获 {@code UPDATE feynman_session SET tutee_state = ?, status = ?} 里写入的 status。 */
  private Object capturedSessionStatus() {
    ArgumentCaptor<Object> status = ArgumentCaptor.forClass(Object.class);
    verify(jdbc)
        .update(contains("UPDATE feynman_session SET tutee_state"), any(), status.capture(), eq(10L));
    return status.getValue();
  }

  @Test
  void teachFailsFastWhenTuteeAgentIsUnreachableInsteadOfFakingSuccessByLength() {
    givenSession(ONE_OPEN_MISCONCEPTION);
    when(graphClient.step(anyString(), any(), anyString(), any())).thenReturn(Optional.empty());

    // 旧的 fallbackStep 只要解释长度 >= 12 就把全部误解置 resolved、伪造 taught_fix=print('ok')、
    // 把会话标 TAUGHT，学生端毫无 AI 不可用提示，随后真判题必然 WA 并记 teach_back_failed。
    assertThatThrownBy(() -> service.teach(7L, 10L, "。。。。。。。。。。。。"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
              assertThat(exception.code()).isEqualTo("FEYNMAN_TUTEE_UNAVAILABLE");
            });

    // 既不写 TUTEE 轮，也不改 tutee_state / status。
    verify(jdbc, never()).queryForObject(contains("'TUTEE'"), eq(Long.class), any(Object[].class));
    verify(jdbc, never())
        .update(contains("UPDATE feynman_session SET tutee_state"), any(), any(), any());
  }

  @Test
  void emptyMisconceptionSetNeverCountsAsTaught() {
    givenSession(NO_MISCONCEPTION);
    when(graphClient.step(anyString(), any(), anyString(), any()))
        .thenReturn(Optional.of(new FeynmanGraphClient.FeynmanOutput("听懂了", List.of(), "print('hi')")));

    TurnResponse response = service.teach(7L, 10L, "range 右端不包含，所以要写 range(1, 6)。");

    // 空集合上 allMatch 恒真：修复前这里是 TAUGHT，新生第一句话就被标「已教会」。
    assertThat(capturedSessionStatus()).isEqualTo("OPEN");
    assertThat(response.noticeMd()).contains("先去做题");
  }

  @Test
  void resolvedMisconceptionsStillMarkSessionTaught() {
    givenSession(ONE_OPEN_MISCONCEPTION);
    when(graphClient.step(anyString(), any(), anyString(), any()))
        .thenReturn(
            Optional.of(
                new FeynmanGraphClient.FeynmanOutput(
                    "懂了",
                    List.of(new Misconception("off_by_one_range", List.of(501L), true)),
                    "print('hi')")));

    TurnResponse response = service.teach(7L, 10L, "range 右端不包含，所以要写 range(1, 6)。");

    assertThat(capturedSessionStatus()).isEqualTo("TAUGHT");
    assertThat(response.noticeMd()).isNull();
  }

  @Test
  void teachReplaysPriorTurnsAsGraphHistorySoMultiTurnContextSurvives() {
    givenSession(ONE_OPEN_MISCONCEPTION);
    when(jdbc.query(contains("FROM feynman_turn"), any(RowMapper.class), eq(10L), anyInt()))
        .thenReturn(
            // 仓库按 created_at DESC 取，服务需要反转成时间正序再送 graph。
            List.of(
                new FeynmanGraphClient.ConversationTurn("assistant", "第二轮学弟回复"),
                new FeynmanGraphClient.ConversationTurn("user", "第一轮我讲的")));
    when(graphClient.step(anyString(), any(), anyString(), any()))
        .thenReturn(Optional.of(new FeynmanGraphClient.FeynmanOutput("好", List.of(), null)));

    service.teach(7L, 10L, "第三轮我讲的");

    ArgumentCaptor<List<FeynmanGraphClient.ConversationTurn>> history =
        ArgumentCaptor.forClass(List.class);
    verify(graphClient).step(eq("PY43-KC-016"), any(), eq("第三轮我讲的"), history.capture());
    assertThat(history.getValue())
        .extracting(FeynmanGraphClient.ConversationTurn::content)
        .containsExactly("第一轮我讲的", "第二轮学弟回复");
    assertThat(history.getValue())
        .extracting(FeynmanGraphClient.ConversationTurn::role)
        .containsExactly("user", "assistant");
  }

  @Test
  void historyIsReadBeforeCurrentTurnIsPersistedSoItNeverDuplicatesThisTurn() {
    givenSession(NO_MISCONCEPTION);
    when(graphClient.step(anyString(), any(), anyString(), any()))
        .thenReturn(Optional.of(new FeynmanGraphClient.FeynmanOutput("好", List.of(), null)));

    service.teach(7L, 10L, "本轮教学");

    InOrder order = inOrder(jdbc);
    order.verify(jdbc).query(contains("FROM feynman_turn"), any(RowMapper.class), eq(10L), anyInt());
    order.verify(jdbc).update(contains("'STUDENT'"), eq(10L), eq("本轮教学"));
  }

  @Test
  void answerDumpStillRefusedWithoutCallingTheAgent() {
    givenSession(NO_MISCONCEPTION);

    TurnResponse response = service.teach(7L, 10L, "```python\ndef solve():\n    pass\n```");

    assertThat(response.content()).contains("请不要直接把完整代码或答案贴给我");
    verify(graphClient, never()).step(anyString(), any(), anyString(), any());
    verify(jdbc).update(contains("status = 'OPEN'"), any(), eq(10L));
  }
}
