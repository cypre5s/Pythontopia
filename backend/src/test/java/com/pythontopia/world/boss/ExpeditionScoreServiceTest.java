package com.pythontopia.world.boss;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.world.boss.dto.ExpeditionScoreSubmitDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class ExpeditionScoreServiceTest {

  private static final UUID RUN_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  @Mock JdbcTemplate jdbcTemplate;

  @Test
  void settlesOnlyScoresDerivedFromServerJudgedAttempts() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(1));
    when(jdbcTemplate.query(
            contains("expedition_verified_attempt"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(
            List.of(
                new ExpeditionScoreService.VerifiedAttempt(true, 90),
                new ExpeditionScoreService.VerifiedAttempt(false, 0),
                new ExpeditionScoreService.VerifiedAttempt(true, 80)));
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    service.record(7L, new ExpeditionScoreSubmitDto(RUN_ID.toString()));

    verify(jdbcTemplate)
        .update(
            contains("INSERT INTO expedition_score"),
            eq(7L),
            eq(RUN_ID),
            eq(170),
            eq(2),
            eq(1));
    verify(jdbcTemplate)
        .update(contains("SET status = 'SETTLED'"), eq(7L), eq(RUN_ID));
  }

  @Test
  void doesNotCreateScoreFromClientPayloadWhenNoServerAttemptExists() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(1));
    when(jdbcTemplate.query(
            contains("expedition_verified_attempt"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    service.record(7L, new ExpeditionScoreSubmitDto(RUN_ID.toString()));

    org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.never())
        .update(contains("INSERT INTO expedition_score"), any(Object[].class));
    verify(jdbcTemplate)
        .update(contains("SET status = 'SETTLED'"), eq(7L), eq(RUN_ID));
  }

  @Test
  void doesNotSettleAnInactiveOrDifferentRun() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    service.record(7L, new ExpeditionScoreSubmitDto(RUN_ID.toString()));

    org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.never())
        .query(contains("expedition_verified_attempt"), any(RowMapper.class), any(Object[].class));
    org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.never())
        .update(contains("SET status = 'SETTLED'"), any(Object[].class));
  }

  @Test
  void limitsVerifiedAttemptsToTheMaximumQuestionsInOneExpeditionMap() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(1));
    when(jdbcTemplate.query(
            contains("expedition_verified_attempt"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(
            java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> new ExpeditionScoreService.VerifiedAttempt(true, 80))
                .toList());
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    service.record(7L, new ExpeditionScoreSubmitDto(RUN_ID.toString()));

    verify(jdbcTemplate)
        .query(contains("LIMIT 12"), any(RowMapper.class), any(Object[].class));
    verify(jdbcTemplate)
        .update(
            contains("INSERT INTO expedition_score"),
            eq(7L),
            eq(RUN_ID),
            eq(960),
            eq(12),
            eq(12));
  }

  @Test
  void refusesToAddANewVerifiedAttemptAfterOneRunBudgetIsUsed() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(1));
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    service.recordVerifiedAttempt(7L, RUN_ID.toString(), "issue-one", true, 80);

    org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
    org.assertj.core.api.Assertions.assertThat(sql.getValue()).contains("< 12");
  }

  @Test
  void locksTheActiveRunBeforeCheckingItsAttemptBudget() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(1));
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    service.recordVerifiedAttempt(7L, RUN_ID.toString(), "issue-one", true, 80);

    verify(jdbcTemplate)
        .query(
            contains("WHERE user_id = ? AND run_id = ?::uuid AND status = 'ACTIVE' FOR UPDATE"),
            any(RowMapper.class),
            eq(7L),
            eq(RUN_ID));
  }

  @Test
  void derivesChapterAttemptSummaryFromTheUsersActiveRunAndIssuedProblems() {
    when(jdbcTemplate.query(contains("pregenerated_problem"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(new ExpeditionScoreService.VerifiedAttemptSummary(10, 8)));
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    org.assertj.core.api.Assertions.assertThat(
            service.summarizeVerifiedAttempts(7L, RUN_ID.toString(), 1))
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptSummary(10, 8));

    org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .query(sql.capture(), any(RowMapper.class), eq(7L), eq(RUN_ID), eq(1));
    org.assertj.core.api.Assertions.assertThat(sql.getValue())
        .contains("r.status = 'ACTIVE'")
        .contains("p.expedition_run_id = a.run_id AND p.chapter = ?");
  }
}
