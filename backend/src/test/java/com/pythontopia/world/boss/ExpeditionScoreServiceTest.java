package com.pythontopia.world.boss;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
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
  private static final String QUESTION_FINGERPRINT = "a".repeat(64);

  @Mock JdbcTemplate jdbcTemplate;

  @Test
  void settlesOnlyScoresDerivedFromServerJudgedAttempts() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(new ExpeditionScoreService.LockedRun(false, false)));
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
            eq(1),
            eq(false),
            eq(false));
    verify(jdbcTemplate)
        .update(contains("SET status = 'SETTLED'"), eq(7L), eq(RUN_ID));
  }

  @Test
  void preservesTheDailyFlagFromTheLockedServerRun() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(new ExpeditionScoreService.LockedRun(true, false)));
    when(jdbcTemplate.query(
            contains("expedition_verified_attempt"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(new ExpeditionScoreService.VerifiedAttempt(true, 80)));
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    service.record(7L, new ExpeditionScoreSubmitDto(RUN_ID.toString()));

    verify(jdbcTemplate)
        .update(
            contains("INSERT INTO expedition_score"),
            eq(7L),
            eq(RUN_ID),
            eq(80),
            eq(1),
            eq(1),
            eq(false),
            eq(true));
  }

  @Test
  void recordsBossClearFromTheLockedServerRun() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(new ExpeditionScoreService.LockedRun(false, true)));
    when(jdbcTemplate.query(
            contains("expedition_verified_attempt"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(new ExpeditionScoreService.VerifiedAttempt(true, 80)));
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    service.record(7L, new ExpeditionScoreSubmitDto(RUN_ID.toString()));

    verify(jdbcTemplate)
        .update(
            contains("INSERT INTO expedition_score"),
            eq(7L),
            eq(RUN_ID),
            eq(80),
            eq(1),
            eq(1),
            eq(true),
            eq(false));
  }

  @Test
  void doesNotCreateScoreFromClientPayloadWhenNoServerAttemptExists() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(new ExpeditionScoreService.LockedRun(false, false)));
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
  void includesBoundedRetriesWhenSettlingVerifiedAttempts() {
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(new ExpeditionScoreService.LockedRun(false, false)));
    when(jdbcTemplate.query(
            contains("expedition_verified_attempt"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(
            java.util.stream.IntStream.range(0, 36)
                .mapToObj(index -> new ExpeditionScoreService.VerifiedAttempt(true, 80))
                .toList());
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    service.record(7L, new ExpeditionScoreSubmitDto(RUN_ID.toString()));

    verify(jdbcTemplate)
        .query(contains("LIMIT 36"), any(RowMapper.class), any(Object[].class));
    verify(jdbcTemplate)
        .update(
            contains("INSERT INTO expedition_score"),
            eq(7L),
            eq(RUN_ID),
            eq(2880),
            eq(36),
            eq(36),
            eq(false),
            eq(false));
  }

  @Test
  void refusesToAddANewVerifiedAttemptAfterOneRunBudgetIsUsed() {
    stubCurrentOpenIssue();
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(false));
    when(jdbcTemplate.query(contains("question_fingerprint = ?"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    when(jdbcTemplate.query(contains("problem_id = ?"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    var result =
        service.recordVerifiedAttempt(
            7L, RUN_ID.toString(), "issue-one", QUESTION_FINGERPRINT, true, 80);

    assertThat(result.accepted()).isFalse();
    assertThat(result.publishLearningEvent()).isFalse();

    org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
    org.assertj.core.api.Assertions.assertThat(sql.getValue()).contains("< 36");
  }

  @Test
  void locksTheActiveRunBeforeCheckingItsAttemptBudget() {
    stubCurrentOpenIssue();
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(false));
    when(jdbcTemplate.query(contains("question_fingerprint = ?"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    when(jdbcTemplate.query(contains("problem_id = ?"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    service.recordVerifiedAttempt(
        7L, RUN_ID.toString(), "issue-one", QUESTION_FINGERPRINT, true, 80);

    verify(jdbcTemplate)
        .query(
            contains("WHERE user_id = ? AND run_id = ?::uuid AND status = 'ACTIVE' FOR UPDATE"),
            any(RowMapper.class),
            eq(7L),
            eq(RUN_ID));
  }

  @Test
  void publishesLearningEvidenceOnlyForFirstFailureAndFirstPass() {
    stubCurrentOpenIssue();
    when(jdbcTemplate.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(false));
    when(jdbcTemplate.query(contains("question_fingerprint = ?"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(
            List.of(),
            List.of(new ExpeditionScoreService.ExistingAttempt(1, false)),
            List.of(new ExpeditionScoreService.ExistingAttempt(1, false)),
            List.of(new ExpeditionScoreService.ExistingAttempt(1, true)),
            List.of(new ExpeditionScoreService.ExistingAttempt(1, true)));
    when(jdbcTemplate.query(contains("problem_id = ?"), any(RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    when(jdbcTemplate.update(contains("INSERT INTO expedition_verified_attempt"), any(Object[].class)))
        .thenReturn(1);
    when(jdbcTemplate.update(contains("UPDATE expedition_verified_attempt"), any(Object[].class)))
        .thenReturn(1, 1, 1, 1);
    ExpeditionScoreService service = new ExpeditionScoreService(jdbcTemplate);

    var firstFailure =
        service.recordVerifiedAttempt(
            7L, RUN_ID.toString(), "issue-one", QUESTION_FINGERPRINT, false, 0);
    var repeatedFailure =
        service.recordVerifiedAttempt(
            7L, RUN_ID.toString(), "issue-one", QUESTION_FINGERPRINT, false, 0);
    var firstPass =
        service.recordVerifiedAttempt(
            7L, RUN_ID.toString(), "issue-one", QUESTION_FINGERPRINT, true, 100);
    var repeatedPass =
        service.recordVerifiedAttempt(
            7L, RUN_ID.toString(), "issue-one", QUESTION_FINGERPRINT, true, 100);
    var failureAfterPass =
        service.recordVerifiedAttempt(
            7L, RUN_ID.toString(), "issue-one", QUESTION_FINGERPRINT, false, 0);

    assertThat(firstFailure.accepted()).isTrue();
    assertThat(firstFailure.publishLearningEvent()).isTrue();
    assertThat(repeatedFailure.accepted()).isTrue();
    assertThat(repeatedFailure.publishLearningEvent()).isFalse();
    assertThat(firstPass.accepted()).isTrue();
    assertThat(firstPass.publishLearningEvent()).isTrue();
    assertThat(repeatedPass.accepted()).isTrue();
    assertThat(repeatedPass.publishLearningEvent()).isFalse();
    assertThat(failureAfterPass.accepted()).isTrue();
    assertThat(failureAfterPass.publishLearningEvent()).isFalse();
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

  private void stubCurrentOpenIssue() {
    when(jdbcTemplate.queryForObject(
            contains("FROM pregenerated_problem p"),
            eq(Boolean.class),
            eq(7L),
            eq(RUN_ID),
            eq("issue-one")))
        .thenReturn(true);
  }
}
