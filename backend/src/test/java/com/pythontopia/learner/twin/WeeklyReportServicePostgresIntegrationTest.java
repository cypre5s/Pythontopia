package com.pythontopia.learner.twin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.event.LearningEventLogRepository;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasteryProperties;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySource;
import com.pythontopia.learner.mastery.review.ReviewScheduler;
import com.pythontopia.learner.mastery.review.ReviewStateRepository;
import com.pythontopia.learner.mastery.review.ReviewStateService;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.twin.dto.WeeklyReportDto;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class WeeklyReportServicePostgresIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static int migratedOpenInterviewCount;
  private static int migratedAbortedInterviewCount;
  private static int openInterviewIndexCount;
  private WeeklyReportService service;

  @BeforeAll
  static void migrate() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    jdbc = new JdbcTemplate((DataSource) dataSource);
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("197"))
        .load()
        .migrate();
    long duplicateOwner = insertMigrationUser();
    jdbc.update(
        """
        INSERT INTO profile_interview (user_id, session_id, status, started_at)
        VALUES (?, ?, 'OPEN', now() - interval '1 day'), (?, ?, 'OPEN', now())
        """,
        duplicateOwner,
        UUID.randomUUID(),
        duplicateOwner,
        UUID.randomUUID());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    migratedOpenInterviewCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM profile_interview WHERE user_id = ? AND status = 'OPEN'",
        Integer.class,
        duplicateOwner);
    migratedAbortedInterviewCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM profile_interview WHERE user_id = ? AND status = 'ABORTED'",
        Integer.class,
        duplicateOwner);
    openInterviewIndexCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'profile_interview' AND indexname = 'uq_profile_interview_one_open_per_user'",
        Integer.class);
  }

  @BeforeEach
  void setUp() {
    jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    MemoryRetrievalService memoryService = mock(MemoryRetrievalService.class);
    LearnerInsightRepository insightRepository = mock(LearnerInsightRepository.class);
    MasteryRepository masteryRepository = new MasteryRepository(jdbc);
    LearningEventLogRepository eventRepository = new LearningEventLogRepository(jdbc, new ObjectMapper());
    when(memoryService.retrieveAuthoritative(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(java.util.List.of());
    when(insightRepository.findByUser(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(java.util.List.of());
    service = new WeeklyReportService(
        memoryService, insightRepository, masteryRepository, eventRepository, jdbc);
  }

  @Test
  void readsRecentResourceRunsFromPostgresTimestamptz() {
    long userId = insertUser("weekly-owner");
    long otherUserId = insertUser("weekly-other");
    insertRun(userId, "RecentAgent", "RESOURCE_HANDOUT", "ARRAY[11,12]::bigint[]", "1 day");
    insertRun(userId, "OldAgent", "RESOURCE_PRACTICE", "ARRAY[13]::bigint[]", "8 days");
    insertRun(otherUserId, "OtherAgent", "RESOURCE_HANDOUT", "ARRAY[14]::bigint[]", "1 hour");

    WeeklyReportDto report = service.build(userId);

    assertThat(report.resourceRuns())
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.agentName()).isEqualTo("RecentAgent");
              assertThat(run.scenario()).isEqualTo("RESOURCE_HANDOUT");
              assertThat(run.evidencePageCount()).isEqualTo(2);
              assertThat(run.createdAt()).isNotBlank();
            });
  }

  @Test
  void weeklyCountsIgnoreFrontendPracticeClaimsAndExcludeOldPracticeAndMasteryEvents() {
    long userId = insertUser("weekly-evidence-owner");
    insertLearningEvent(userId, "practice_failed", "backend", "1 day");
    insertLearningEvent(userId, "practice_succeeded", "frontend", "1 day");
    insertLearningEvent(userId, "practice_succeeded", "backend", "8 days");
    jdbc.update(
        "INSERT INTO kc_mastery (user_id, kc_code, mastery_score, confidence, updated_at)"
            + " VALUES (?, 'PY43-KC-001', 0.700, 0.800, now())",
        userId);
    insertMasteryEvent(userId, "PRACTICE_PASS", new java.math.BigDecimal("0.100"), "1 day");
    insertMasteryEvent(userId, "PRACTICE_FAIL", new java.math.BigDecimal("-0.050"), "8 days");

    WeeklyReportDto report = service.build(userId);

    assertThat(report.memoryBrief().totalEvents()).isEqualTo(1);
    assertThat(report.memoryBrief().failedPractices()).isEqualTo(1);
    assertThat(report.memoryBrief().succeededPractices()).isZero();
    assertThat(report.masteryDeltas())
        .singleElement()
        .satisfies(
            delta -> {
              assertThat(delta.kcCode()).isEqualTo("PY43-KC-001");
              assertThat(delta.weeklyDelta()).isEqualTo(0.100);
              assertThat(delta.evidenceCount()).isEqualTo(1);
              assertThat(delta.evidenceSources()).containsExactly("PRACTICE_PASS");
              assertThat(delta.currentScore()).isEqualTo(0.700);
            });
  }

  @Test
  void concurrentMasteryEventsForSameKcKeepSnapshotAndLedgerInSync() throws Exception {
    long userId = insertUser("concurrent-mastery-owner");
    int eventCount = 24;
    MasteryEvaluator evaluator = new MasteryEvaluator(
        new MasteryRepository(jdbc),
        new MasteryProperties(
            14,
            new BigDecimal("0.05"),
            new BigDecimal("0.02"),
            Map.of("PRACTICE_PASS", new BigDecimal("0.010"))),
        new ReviewStateService(new ReviewStateRepository(jdbc), new ReviewScheduler()));
    TransactionTemplate transaction =
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    CountDownLatch ready = new CountDownLatch(eventCount);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(eventCount)) {
      var futures = java.util.stream.IntStream.range(0, eventCount)
          .mapToObj(index -> executor.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
              throw new IllegalStateException("mastery concurrency test did not start");
            }
            return transaction.execute(status -> evaluator.applyEvent(
                userId,
                "PY43-KC-001",
                MasterySource.PRACTICE_PASS,
                new BigDecimal("0.010"),
                "concurrent:" + index));
          }))
          .toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (var future : futures) {
        future.get(20, TimeUnit.SECONDS);
      }
    }

    BigDecimal score = jdbc.queryForObject(
        "SELECT mastery_score FROM kc_mastery WHERE user_id = ? AND kc_code = ?",
        BigDecimal.class,
        userId,
        "PY43-KC-001");
    Integer persistedEvents = jdbc.queryForObject(
        "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND kc_code = ?",
        Integer.class,
        userId,
        "PY43-KC-001");

    assertThat(persistedEvents).isEqualTo(eventCount);
    assertThat(score).isEqualByComparingTo(new BigDecimal("0.240"));
  }

  @Test
  void concurrentInterviewStartsResumeTheSameOpenSession() throws Exception {
    long userId = insertUser("concurrent-interview-owner");
    ProfileRepository repository = new ProfileRepository(jdbc, new ObjectMapper());
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> {
        start.await();
        return repository.createInterview(userId);
      });
      var second = executor.submit(() -> {
        start.await();
        return repository.createInterview(userId);
      });
      start.countDown();

      assertThat(first.get(10, TimeUnit.SECONDS).interviewSessionId())
          .isEqualTo(second.get(10, TimeUnit.SECONDS).interviewSessionId());
    }

    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM profile_interview WHERE user_id = ? AND status = 'OPEN'",
            Integer.class,
            userId))
        .isEqualTo(1);
  }

  @Test
  void migrationClosesOlderDuplicateOpenInterviewsBeforeAddingTheUniqueIndex() {
    assertThat(migratedOpenInterviewCount).isEqualTo(1);
    assertThat(migratedAbortedInterviewCount).isEqualTo(1);
    assertThat(openInterviewIndexCount).isEqualTo(1);
  }

  private static long insertMigrationUser() {
    return jdbc.queryForObject(
        """
        INSERT INTO users(username, display_name, password_hash, role)
        VALUES ('duplicate-interview-migration', 'Migration user', 'test-hash', 'STUDENT')
        RETURNING id
        """,
        Long.class);
  }

  private long insertUser(String username) {
    return jdbc.queryForObject(
        """
        INSERT INTO users(username, display_name, password_hash, role)
        VALUES (?, ?, 'test-hash', 'STUDENT')
        RETURNING id
        """,
        Long.class,
        username,
        username);
  }

  private void insertRun(
      long userId, String agent, String scenario, String evidenceArraySql, String age) {
    jdbc.update(
        "INSERT INTO resource_run"
            + " (user_id, agent_name, scenario, status, evidence_page_ids, created_at)"
            + " VALUES (?, ?, ?, 'SUCCESS', "
            + evidenceArraySql
            + ", now() - (?::interval))",
        userId,
        agent,
        scenario,
        age);
  }

  private void insertLearningEvent(long userId, String eventType, String source, String age) {
    jdbc.update(
        "INSERT INTO learning_event_log (user_id, event_type, payload, source, created_at)"
            + " VALUES (?, ?, '{}'::jsonb, ?, now() - (?::interval))",
        userId,
        eventType,
        source,
        age);
  }

  private void insertMasteryEvent(
      long userId, String source, java.math.BigDecimal delta, String age) {
    jdbc.update(
        "INSERT INTO kc_mastery_event (user_id, kc_code, source, delta, reason, created_at)"
            + " VALUES (?, 'PY43-KC-001', ?, ?, 'integration evidence', now() - (?::interval))",
        userId,
        source,
        delta,
        age);
  }
}
