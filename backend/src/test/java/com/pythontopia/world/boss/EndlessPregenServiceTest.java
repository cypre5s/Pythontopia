package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.config.BossProblemHttpClientProperties;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossPrefetchStatusDto;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import com.pythontopia.world.boss.review.ApprovedBossProblem;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class EndlessPregenServiceTest {

  @Mock BossGraphClient graphClient;
  @Mock JdbcTemplate jdbcTemplate;
  @Mock com.pythontopia.world.boss.review.BossProblemReviewRepository reviewRepository;
  private EndlessPregenService service;

  @BeforeEach
  void setUp() {
    service = new EndlessPregenService(graphClient, jdbcTemplate, new ObjectMapper(), null, reviewRepository);
    lenient()
        .when(jdbcTemplate.update(
            contains("WHERE id = ? AND user_id = ? AND status = 'PENDING' AND attempt_count = ?"),
            anyLong(), anyLong(), anyInt()))
        .thenReturn(1);
  }

  @Test
  void statusQueryExpiresPendingRowsAfterGraphTimeoutPlusRecoveryMargin() {
    service =
        new EndlessPregenService(
            graphClient,
            jdbcTemplate,
            new ObjectMapper(),
            null,
            reviewRepository,
            new BossProblemHttpClientProperties(90_000));
    BossPrefetchStatusDto failed =
        new BossPrefetchStatusDto(19L, "FAILED", 1, "预加载超时，可重试", true, null);
    org.mockito.Mockito.doReturn(List.of(failed))
        .when(jdbcTemplate)
        .query(contains("SELECT id, status"), any(RowMapper.class), any(Object[].class));

    BossPrefetchStatusDto status =
        service.getPrefetchStatus(7L, 1, 2, "PY43-KC-001", 2, "dual_track", null, null);

    assertThat(status.status()).isEqualTo("FAILED");
    assertThat(status.retryable()).isTrue();
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sql.capture(), eq(7L), eq(150_000L));
    assertThat(sql.getValue())
        .contains("SET status = 'FAILED'", "status = 'PENDING'", "updated_at < now()");
  }

  @Test
  void generationRenewsPendingLeaseBeforeCallingGraph() {
    when(jdbcTemplate.update(
            contains("WHERE id = ? AND user_id = ? AND status = 'PENDING' AND attempt_count = ?"),
            eq(19L), eq(7L), eq(1)))
        .thenReturn(1);
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenReturn(reviewedProblem());
    when(reviewRepository.approveAutomaticallyIfNotDuplicate(
            7L, "dual_track", 1, 2, "PY43-KC-001", 2, reviewedProblem()))
        .thenReturn(true);
    when(jdbcTemplate.update(
            contains("status = 'READY'"), anyString(), eq("ai-1"), anyString(), eq(19L), eq(7L),
            eq(1), eq(2), isNull(), isNull(), eq(1)))
        .thenReturn(1);

    service.generatePrefetch(
        19L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, null, 1);

    InOrder order = inOrder(jdbcTemplate, graphClient);
    order.verify(jdbcTemplate)
        .update(
            contains("WHERE id = ? AND user_id = ? AND status = 'PENDING' AND attempt_count = ?"),
            eq(19L), eq(7L), eq(1));
    order.verify(graphClient)
        .requestProblem(7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null);
  }

  @Test
  void approvedBankProblemMovesPendingJobToReadyWithoutCallingGraph() {
    BossInternalProblem bankProblem = reviewedProblem().issueAs("approved-12-v2", "PY43-KC-001");
    when(reviewRepository.findApproved(1, 2, "PY43-KC-001", "dual_track", 2))
        .thenReturn(Optional.of(new ApprovedBossProblem(12L, 2, bankProblem)));
    when(jdbcTemplate.update(
            contains("status = 'READY'"), anyString(), anyString(), anyString(),
            eq(19L), eq(7L), eq(1), eq(2), isNull(), isNull(), eq(1)))
        .thenReturn(1);

    service.generatePrefetch(
        19L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, null, 1);

    verify(reviewRepository).findApproved(1, 2, "PY43-KC-001", "dual_track", 2);
    verify(reviewRepository, never())
        .approveAutomaticallyIfNotDuplicate(anyLong(), anyString(), anyInt(), anyInt(), anyString(),
            anyInt(), any(BossInternalProblem.class));
    verify(jdbcTemplate)
        .update(
            contains("status = 'READY'"), anyString(), eq("approved-12-v2"), anyString(),
            eq(19L), eq(7L), eq(1), eq(2), isNull(), isNull(), eq(1));
    org.mockito.Mockito.verifyNoInteractions(graphClient);
  }

  @Test
  void reviewedAiProblemMovesPendingJobToReadyAndAddsItToSharedBank() {
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenReturn(reviewedProblem());
    when(reviewRepository.approveAutomaticallyIfNotDuplicate(
            7L, "dual_track", 1, 2, "PY43-KC-001", 2, reviewedProblem()))
        .thenReturn(true);
    when(jdbcTemplate.update(
            contains("status = 'READY'"), anyString(), eq("ai-1"), anyString(), eq(19L), eq(7L),
            eq(1), eq(2), isNull(), isNull(), eq(1)))
        .thenReturn(1);

    service.generatePrefetch(
        19L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, null, 1);

    verify(reviewRepository)
        .approveAutomaticallyIfNotDuplicate(
            7L, "dual_track", 1, 2, "PY43-KC-001", 2, reviewedProblem());
    verify(jdbcTemplate)
        .update(
            contains("status = 'READY'"), anyString(), eq("ai-1"), anyString(), eq(19L), eq(7L),
            eq(1), eq(2), isNull(), isNull(), eq(1));
  }

  @Test
  void rejectedAutomaticApprovalFailsPrefetchInsteadOfMarkingItReady() {
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenReturn(reviewedProblem());
    when(reviewRepository.approveAutomaticallyIfNotDuplicate(
            7L, "dual_track", 1, 2, "PY43-KC-001", 2, reviewedProblem()))
        .thenReturn(false);

    service.generatePrefetch(
        19L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, null, 1);

    verify(jdbcTemplate)
        .update(
            contains("status = 'FAILED'"), eq("题目预加载失败，可重试"), eq(19L), eq(1));
    verify(jdbcTemplate, never())
        .update(
            contains("status = 'READY'"), anyString(), eq("ai-1"), anyString(), eq(19L),
            eq(7L), eq(1), eq(2), isNull(), isNull(), eq(1));
  }

  @Test
  void approvalPersistenceFailureFailsPrefetchInsteadOfMarkingItReady() {
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenReturn(reviewedProblem());
    when(reviewRepository.approveAutomaticallyIfNotDuplicate(
            7L, "dual_track", 1, 2, "PY43-KC-001", 2, reviewedProblem()))
        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("review store down"));

    service.generatePrefetch(
        19L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, null, 1);

    verify(jdbcTemplate)
        .update(
            contains("status = 'FAILED'"), eq("题目预加载失败，可重试"), eq(19L), eq(1));
    verify(jdbcTemplate, never())
        .update(
            contains("status = 'READY'"), anyString(), eq("ai-1"), anyString(), eq(19L),
            eq(7L), eq(1), eq(2), isNull(), isNull(), eq(1));
  }

  @Test
  void generationFailurePersistsOnlySafeRetryMessage() {
    String sentinel = "private-boss-status-error-sentinel";
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(4),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenThrow(new RestClientException(sentinel));

    service.generatePrefetch(
        23L, 7L, 1, 2, "PY43-KC-001", 4, "PY43-KC-001", "dual_track", null, null, null, 1);

    verify(jdbcTemplate)
        .update(
            contains("status = 'FAILED'"), eq("题目预加载失败，可重试"), eq(23L), eq(1));
  }

  @Test
  void generationFailureDoesNotLogUpstreamResponseText() {
    String sentinel = "private-boss-question-error-sentinel";
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(4),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenThrow(new RestClientException(sentinel));
    Logger logger = (Logger) LoggerFactory.getLogger(EndlessPregenService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      service.generatePrefetch(
          23L, 7L, 1, 2, "PY43-KC-001", 4, "PY43-KC-001", "dual_track", null, null, null, 1);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message).contains("errorType=RestClientException"))
        .noneMatch(message -> message.contains(sentinel));
  }

  @Test
  void claimReadyFailureDoesNotLogStudentOrDatabaseDetails() {
    long userId = 87654321L;
    String sentinel = "private-boss-claim-error-sentinel";
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
        .thenThrow(new IllegalStateException(sentinel));
    Logger logger = (Logger) LoggerFactory.getLogger(EndlessPregenService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThat(service.claimReady(userId, 1, 2, "PY43-KC-001", 2, "dual_track", null, null))
          .isEmpty();
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("errorType=IllegalStateException"))
        .noneMatch(
            message ->
                message.contains(sentinel)
                    || message.contains(Long.toString(userId))
                    || message.contains("PY43-KC-001"));
  }

  @Test
  void persistConsumedFailureDoesNotLogStudentOrDatabaseDetails() {
    long userId = 87654321L;
    String problemId = "private-boss-problem-id";
    String sentinel = "private-boss-persist-error-sentinel";
    BossInternalProblem problem = reviewedProblem().issueAs(problemId, "PY43-KC-001");
    when(jdbcTemplate.update(anyString(), any(Object[].class)))
        .thenThrow(new IllegalStateException(sentinel));
    Logger logger = (Logger) LoggerFactory.getLogger(EndlessPregenService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThat(
              service.persistConsumed(
                  userId, 1, "PY43-KC-001", "dual_track", null, 2, problem, null))
          .isSameAs(problem);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("errorType=IllegalStateException"))
        .noneMatch(
            message ->
                message.contains(sentinel)
                    || message.contains(Long.toString(userId))
                    || message.contains(problemId));
  }

  @Test
  void unreviewedGoldenFallbackNeverBecomesReady() {
    BossInternalProblem golden =
        new BossInternalProblem(
            "golden-1", 1, 2, 2, "PY43-KC-001", "fallback", List.of(), "", "", 1,
            false, false, List.of(new BossTestCasePayload("", "", false)), "print(0)");
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenReturn(golden);

    service.generatePrefetch(
        29L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, null, 1);

    verify(jdbcTemplate)
        .update(
            contains("status = 'FAILED'"),
            eq("题目预加载失败，可重试"),
            eq(29L), eq(1));
  }

  @Test
  void claimingReadyProblemPersistsFreshProblemIdWithPayload() throws Exception {
    BossInternalProblem original = reviewedProblem();
    String payload = new ObjectMapper().writeValueAsString(original);
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
        .thenReturn(List.of(Map.of("id", 19L, "payload", payload)));
    when(jdbcTemplate.update(
            contains("status = 'CONSUMED'"), anyString(), anyString(), isNull(), isNull(), anyString(),
            org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.isNull(), isNull()))
        .thenReturn(1);

    var claimed = service.claimReady(7L, 1, 2, "PY43-KC-001", 2, "dual_track", null, null);

    assertThat(claimed).isPresent();
    assertThat(claimed.orElseThrow().problemId()).startsWith("issue-").isNotEqualTo("ai-1");
    org.mockito.ArgumentCaptor<String> savedPayload = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .update(
            contains("status = 'CONSUMED'"),
            org.mockito.ArgumentMatchers.eq(claimed.orElseThrow().problemId()),
            savedPayload.capture(),
            isNull(),
            isNull(),
            anyString(),
            org.mockito.ArgumentMatchers.eq(19L),
            isNull(),
            isNull());
    assertThat(new ObjectMapper().readTree(savedPayload.getValue()).path("problemId").asText())
        .isEqualTo(claimed.orElseThrow().problemId());
  }

  @Test
  void storedProblemPayloadErrorsNeverLogPayloadOrOwnerIdentifiers() {
    String payloadSentinel = "private-boss-payload-log-sentinel";
    String databaseSentinel = "private-boss-database-log-sentinel";
    when(jdbcTemplate.query(
            anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(payloadSentinel))
        .thenThrow(new IllegalStateException(databaseSentinel));
    Logger logger = (Logger) LoggerFactory.getLogger(EndlessPregenService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThat(service.findByProblemId(987654321L, "private-problem-id")).isEmpty();
      assertThat(service.findByProblemId(987654321L, "private-problem-id")).isEmpty();
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("errorType=JsonParseException"))
        .anyMatch(message -> message.contains("errorType=IllegalStateException"))
        .noneMatch(
            message ->
                message.contains(payloadSentinel)
                    || message.contains(databaseSentinel)
                    || message.contains("987654321")
                    || message.contains("private-problem-id"));
  }

  @Test
  void rejectsNewPrefetchWhenUserAlreadyHasFourOutstandingProblems() {
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Integer.class), eq(7L)))
        .thenReturn(4);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.beginPrefetch(7L, 1, 2, "PY43-KC-001", 2, "dual_track", null, null))
        .isInstanceOf(com.pythontopia.common.error.BusinessException.class)
        .hasMessageContaining("队列已满");

    verify(jdbcTemplate)
        .queryForObject(contains("pg_advisory_xact_lock"), eq(Object.class), eq("7"));
    verify(jdbcTemplate).update(contains("status = 'FAILED'"), eq(7L), eq(240_000L));
    verify(jdbcTemplate).update(contains("status = 'READY'"), eq(7L));
  }

  @Test
  void staleGenerationCannotOverwriteRetryAfterItsAttemptWasSuperseded() {
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenReturn(reviewedProblem());
    when(reviewRepository.approveAutomaticallyIfNotDuplicate(
            7L, "dual_track", 1, 2, "PY43-KC-001", 2, reviewedProblem()))
        .thenReturn(true);
    when(jdbcTemplate.update(
            contains("status = 'READY'"), anyString(), eq("ai-1"), anyString(), eq(19L), eq(7L),
            eq(1), eq(2), isNull(), isNull(), eq(1)))
        .thenReturn(0);

    service.generatePrefetch(
        19L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, null, 1);

    verify(jdbcTemplate)
        .update(
            contains("attempt_count = ?"), anyString(), eq("ai-1"), anyString(), eq(19L), eq(7L),
            eq(1), eq(2), isNull(), isNull(), eq(1));
  }

  @Test
  void cancelledQueuedAttemptDoesNotCallGraphWhenDequeued() {
    when(jdbcTemplate.update(
            contains("WHERE id = ? AND user_id = ? AND status = 'PENDING' AND attempt_count = ?"),
            eq(19L), eq(7L), eq(1)))
        .thenReturn(0);

    service.generatePrefetch(
        19L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, null, 1);

    verify(graphClient, never())
        .requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null));
  }

  @Test
  void startupFailsPendingRowsLeftByPreviousProcess() {
    when(jdbcTemplate.queryForObject(
            eq("SELECT to_regclass('public.pregenerated_problem') IS NOT NULL"), eq(Boolean.class)))
        .thenReturn(true);

    service.recoverAbandonedPendingAtStartup();

    verify(jdbcTemplate)
        .update(contains("status = 'FAILED'"));
  }

  @Test
  void startupRecoverySkipsWhenThePrefetchTableHasNotBeenMigratedYet() {
    when(jdbcTemplate.queryForObject(
            eq("SELECT to_regclass('public.pregenerated_problem') IS NOT NULL"), eq(Boolean.class)))
        .thenReturn(false);

    service.recoverAbandonedPendingAtStartup();

    verify(jdbcTemplate, never()).update(anyString());
  }

  @Test
  void chapterOrStageMismatchFromGraphIsPersistedAsFailedInsteadOfReady() {
    when(graphClient.requestProblem(eq(7L), eq(1), eq(2), eq("PY43-KC-001"), eq(2),
            eq("PY43-KC-001"), eq("dual_track"), eq(null)))
        .thenReturn(new BossInternalProblem(
            "ai-1", 2, 1, 2, "PY43-KC-001", "wrong chapter", List.of(), "", "", 1,
            true, true, List.of(new BossTestCasePayload("", "", false)), "print(5)"));

    service.generatePrefetch(
        31L, 7L, 1, 2, "PY43-KC-001", 2, "PY43-KC-001", "dual_track", null, null, null, 1);

    verify(jdbcTemplate)
        .update(
            contains("status = 'FAILED'"),
            eq("题目预加载失败，可重试"),
            eq(31L), eq(1));
    verify(jdbcTemplate, org.mockito.Mockito.never())
        .update(
            contains("status = 'READY'"), anyString(), anyString(), anyString(), eq(31L), eq(7L),
            eq(1), eq(2), isNull(), isNull(), eq(1));
  }

  private BossInternalProblem reviewedProblem() {
    return new BossInternalProblem(
        "ai-1", 1, 2, 2, "PY43-KC-001", "statement", List.of(), "2 3", "5", 3,
        true, true,
        List.of(
            new BossTestCasePayload("2 3", "5", true),
            new BossTestCasePayload("-1 8", "7", false),
            new BossTestCasePayload("0 0", "0", false)),
        "a, b = map(int, input().split())\nprint(a + b)");
  }
}
