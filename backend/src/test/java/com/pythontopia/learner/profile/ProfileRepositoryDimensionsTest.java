package com.pythontopia.learner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.profile.dto.ProfileInterviewStartResponse;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * learner_profile.dimensions 读写 SQL 形状回归：
 *
 * <ul>
 *   <li>写路径的读必须带 {@code FOR UPDATE}（否则并发事件整块覆盖 JSONB，丢更新）；
 *   <li>读路径不得带 {@code FOR UPDATE}（{@code readOnly} 事务里 PostgreSQL 会直接报错）；
 *   <li>维度写入必须是 upsert（否则未 bootstrap 的新学生写入命中 0 行、静默丢弃）。
 * </ul>
 */
class ProfileRepositoryDimensionsTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final ProfileRepository repository =
      new ProfileRepository(jdbcTemplate, new ObjectMapper());

  @Test
  void findDimensionsForUpdateLocksTheProfileRow() {
    repository.findDimensionsForUpdate(7L);

    assertThat(capturedQuerySql()).contains("FOR UPDATE");
  }

  @Test
  void findDimensionsDoesNotLockSoReadOnlyTransactionsStayValid() {
    repository.findDimensions(7L);

    assertThat(capturedQuerySql()).doesNotContain("FOR UPDATE");
  }

  @Test
  void saveDimensionsUpsertsSoMissingProfileRowIsNotSilentlyDropped() {
    repository.saveDimensions(7L, new ObjectMapper().createObjectNode());

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture(), eq(7L), eq("{}"));

    String sql = normalize(sqlCaptor.getValue());
    assertThat(sql)
        .contains("INSERT INTO learner_profile")
        .contains("ON CONFLICT (user_id) DO UPDATE")
        .contains("dimensions = EXCLUDED.dimensions");
  }

  @Test
  void interviewCanOnlyBeClaimedOnceWhileOpen() {
    when(jdbcTemplate.update(anyString(), eq(7L), eq("session-uuid"))).thenReturn(1);

    assertThat(repository.completeInterview(7L, "session-uuid")).isTrue();

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sqlCaptor.capture(), eq(7L), eq("session-uuid"));
    assertThat(normalize(sqlCaptor.getValue()))
        .contains("WHERE user_id = ? AND session_id = ?::uuid AND status = 'OPEN'");
  }

  @Test
  void completedInterviewCannotBeReopenedForTurnsOrAborted() {
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(7L), eq("session-uuid")))
        .thenReturn(0);
    when(jdbcTemplate.update(anyString(), eq(7L), eq("session-uuid"))).thenReturn(0);

    assertThat(repository.ownsInterview(7L, "session-uuid")).isFalse();
    repository.abortInterview(7L, "session-uuid");

    ArgumentCaptor<String> querySql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .queryForObject(querySql.capture(), eq(Integer.class), eq(7L), eq("session-uuid"));
    assertThat(normalize(querySql.getValue())).contains("AND status = 'OPEN'");

    ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(updateSql.capture(), eq(7L), eq("session-uuid"));
    assertThat(normalize(updateSql.getValue())).contains("AND status = 'OPEN'");
  }

  @Test
  void concurrentInterviewStartsReturnTheSingleOpenSession() {
    when(jdbcTemplate.query(
            anyString(),
            ArgumentMatchers.<RowMapper<ProfileInterviewStartResponse>>any(),
            eq(7L),
            org.mockito.ArgumentMatchers.any(java.util.UUID.class)))
        .thenReturn(java.util.List.of(
            new ProfileInterviewStartResponse("existing-session", "OPEN", java.util.List.of(), null)));

    ProfileInterviewStartResponse result = repository.createInterview(7L);

    assertThat(result.interviewSessionId()).isEqualTo("existing-session");
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .query(
            sqlCaptor.capture(),
            ArgumentMatchers.<RowMapper<ProfileInterviewStartResponse>>any(),
            eq(7L),
            org.mockito.ArgumentMatchers.any(java.util.UUID.class));
    assertThat(normalize(sqlCaptor.getValue()))
        .contains("ON CONFLICT (user_id) WHERE status = 'OPEN' DO UPDATE")
        .contains("RETURNING session_id::text, status");
  }

  private String capturedQuerySql() {
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .query(
            sqlCaptor.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<String>>any(),
            eq(7L));
    return normalize(sqlCaptor.getValue());
  }

  private static String normalize(String sql) {
    return sql.replaceAll("\\s+", " ").trim();
  }
}
