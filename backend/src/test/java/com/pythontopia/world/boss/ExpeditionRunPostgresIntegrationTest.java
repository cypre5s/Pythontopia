package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import com.pythontopia.world.boss.dto.ExpeditionScoreSubmitDto;
import com.pythontopia.world.boss.dto.FrontierPickDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 真 PostgreSQL：快照不能换活动 runId，分数从判题账本读取，结算后新局获取新 id。 */
@Testcontainers
class ExpeditionRunPostgresIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrate() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(source);
  }

  @Test
  void serverOwnsRunIdentityAndSettlementState() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto initial = startRun(runs, userId, 1, false);

    assertThat(initial.runId()).isNotBlank();
    assertThat(initial.score()).isZero();
    assertThatThrownBy(() -> runs.validateProblemRequest(userId, initial.runId(), 2))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("章节");
    assertThatThrownBy(() -> runs.save(userId, snapshot(UUID.randomUUID().toString(), 0)))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> runs.save(userId, snapshot(initial.runId(), 0, 2)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("章节");

    jdbc.update(
        "INSERT INTO expedition_verified_attempt (user_id, run_id, problem_id, passed, score)"
            + " VALUES (?, ?::uuid, 'issued-problem', TRUE, 80)",
        userId,
        initial.runId());
    jdbc.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, stage, kc_code, difficulty, status, attempt_count, expedition_run_id)"
            + " VALUES (?, 1, 1, 'PY43-KC-001', 1, 'PENDING', 1, ?::uuid)",
        userId,
        initial.runId());
    jdbc.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, stage, kc_code, difficulty, problem_id, payload, status,"
            + " attempt_count, expedition_run_id)"
            + " VALUES (?, 1, 1, 'PY43-KC-002', 1, 'ready-problem', '{}'::jsonb, 'READY', 1, ?::uuid)",
        userId,
        initial.runId());
    ExpeditionRunDto updated = runs.save(userId, withScore(initial, 999_999));
    assertThat(updated.score()).isEqualTo(80);

    new ExpeditionScoreService(jdbc).record(userId, new ExpeditionScoreSubmitDto(initial.runId()));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM expedition_run WHERE user_id = ?", String.class, userId))
        .isEqualTo("SETTLED");
    assertThat(
            jdbc.queryForObject(
                "SELECT score FROM expedition_score WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                userId,
                initial.runId()))
        .isEqualTo(80);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pregenerated_problem WHERE user_id = ?"
                    + " AND expedition_run_id = ?::uuid AND status = 'FAILED'",
                Integer.class,
                userId,
                initial.runId()))
        .isEqualTo(2);

    new ExpeditionScoreService(jdbc)
        .recordVerifiedAttempt(
            userId, initial.runId(), "late-submission", "a".repeat(64), true, 100);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM expedition_verified_attempt WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                userId,
                initial.runId()))
        .isEqualTo(1);

    ExpeditionRunDto next = startRun(runs, userId, 1, false);
    assertThat(next.runId()).isNotEqualTo(initial.runId());
    assertThat(next.score()).isZero();
  }

  @Test
  void activeSnapshotCannotReturnAStoredClientTerminalPhase() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    jdbc.update(
        "UPDATE expedition_run SET phase = 'cleared' WHERE user_id = ? AND run_id = ?::uuid",
        userId,
        active.runId());

    assertThat(runs.findActive(userId).orElseThrow().phase()).isEqualTo("map");
  }

  @Test
  void activeSnapshotCannotRewriteTheServerIssuedMap() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    List<Map<String, Object>> alteredNodes =
        active.nodes().stream().<Map<String, Object>>map(HashMap::new).toList();
    alteredNodes.getFirst().put("kcCode", "PY43-KC-065");

    assertThatThrownBy(() -> runs.save(userId, withSnapshot(active, alteredNodes, 0)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("地图配置");
    assertThat(runs.findActive(userId).orElseThrow().nodes().getFirst().get("kcCode"))
        .isEqualTo(active.nodes().getFirst().get("kcCode"));
  }

  @Test
  void expeditionLearningEvidenceStopsAfterFirstFailureThenFirstPass() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    ExpeditionScoreService scores = new ExpeditionScoreService(jdbc);
    String fingerprint = "b".repeat(64);

    var firstFailure =
        scores.recordVerifiedAttempt(
            userId, active.runId(), "issued-problem", fingerprint, false, 0);
    var repeatedFailure =
        scores.recordVerifiedAttempt(
            userId, active.runId(), "issued-problem", fingerprint, false, 0);
    var firstPass =
        scores.recordVerifiedAttempt(
            userId, active.runId(), "issued-problem", fingerprint, true, 100);
    var repeatedPass =
        scores.recordVerifiedAttempt(
            userId, active.runId(), "issued-problem", fingerprint, true, 100);
    var failureAfterPass =
        scores.recordVerifiedAttempt(
            userId, active.runId(), "issued-problem", fingerprint, false, 0);

    assertThat(firstFailure)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    assertThat(repeatedFailure)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, false));
    assertThat(firstPass).isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    assertThat(repeatedPass)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, false));
    assertThat(failureAfterPass)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, false));
    assertThat(
            jdbc.queryForObject(
                "SELECT passed FROM expedition_verified_attempt WHERE user_id = ? AND run_id = ?::uuid"
                    + " AND problem_id = 'issued-problem'",
                Boolean.class,
                userId,
                active.runId()))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT score FROM expedition_verified_attempt WHERE user_id = ? AND run_id = ?::uuid"
                    + " AND problem_id = 'issued-problem'",
                Integer.class,
                userId,
                active.runId()))
        .isEqualTo(100);
  }

  @Test
  void sameQuestionWithDifferentIssuedIdsUsesOneVerifiedLedgerEntry() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    ExpeditionScoreService scores = new ExpeditionScoreService(jdbc);
    String fingerprint = "a".repeat(64);

    var firstFailure =
        scores.recordVerifiedAttempt(
            userId, active.runId(), "issued-problem-1", fingerprint, false, 0);
    var duplicateFailure =
        scores.recordVerifiedAttempt(
            userId, active.runId(), "issued-problem-2", fingerprint, false, 0);
    var firstPass =
        scores.recordVerifiedAttempt(
            userId, active.runId(), "issued-problem-2", fingerprint, true, 100);
    var duplicatePass =
        scores.recordVerifiedAttempt(
            userId, active.runId(), "issued-problem-3", fingerprint, true, 100);

    assertThat(firstFailure)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    assertThat(duplicateFailure)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, false));
    assertThat(firstPass).isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    assertThat(duplicatePass)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, false));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM expedition_verified_attempt WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                userId,
                active.runId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT passed FROM expedition_verified_attempt WHERE user_id = ? AND run_id = ?::uuid",
                Boolean.class,
                userId,
                active.runId()))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT score FROM expedition_verified_attempt WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                userId,
                active.runId()))
        .isEqualTo(100);
  }

  @Test
  void globalDailyRunCanIssueProblemsFromAnyCourseChapter() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto daily = startRun(runs, userId, null, true);

    org.assertj.core.api.Assertions.assertThatNoException()
        .isThrownBy(() -> runs.validateProblemRequest(userId, daily.runId(), 1));
    org.assertj.core.api.Assertions.assertThatNoException()
        .isThrownBy(() -> runs.validateProblemRequest(userId, daily.runId(), 7));
  }

  @Test
  void abandoningRunReleasesItsOutstandingPrefetchJobs() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    jdbc.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, stage, kc_code, difficulty, status, attempt_count, expedition_run_id)"
            + " VALUES (?, 1, 1, 'PY43-KC-001', 1, 'PENDING', 1, ?::uuid)",
        userId,
        active.runId());

    runs.abandon(userId);

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pregenerated_problem WHERE user_id = ?"
                    + " AND expedition_run_id = ?::uuid AND status IN ('PENDING', 'READY')",
                Integer.class,
                userId,
                active.runId()))
        .isZero();
    assertThat(runs.findActive(userId)).isEmpty();
  }

  private long createStudent() {
    return jdbc.queryForObject(
        "INSERT INTO users (username, display_name, password_hash, role)"
            + " VALUES (?, 'Student', 'x', 'STUDENT') RETURNING id",
        Long.class,
        "expedition-" + UUID.randomUUID());
  }

  private static ExpeditionRunDto snapshot(String runId, int score) {
    return snapshot(runId, score, 1);
  }

  private static ExpeditionRunDto snapshot(String runId, int score, int chapter) {
    return new ExpeditionRunDto(
        "map", chapter, List.of(), 10, null, 5, 5, 0, 0, score, List.of(), false, List.of(), runId);
  }

  private static ExpeditionRunDto withScore(ExpeditionRunDto run, int score) {
    return withSnapshot(run, run.nodes(), score);
  }

  private static ExpeditionRunDto withSnapshot(
      ExpeditionRunDto run, List<Map<String, Object>> nodes, int score) {
    return new ExpeditionRunDto(
        run.phase(),
        run.focusChapter(),
        nodes,
        run.layers(),
        run.currentNodeId(),
        run.hp(),
        run.maxHp(),
        run.momentum(),
        run.bestMomentum(),
        score,
        run.relics(),
        run.isDaily(),
        run.history(),
        run.runId());
  }

  private static ExpeditionRunDto startRun(
      ExpeditionRunService runs, long userId, Integer focusChapter, boolean isDaily) {
    return runs.start(
        userId, focusChapter, isDaily, List.of(new FrontierPickDto("PY43-KC-001", 1, 2, "weak")));
  }
}
