package com.pythontopia.learner.effect;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.evidence.LearningEvidenceGraphRepository;
import com.pythontopia.learner.evidence.LearningEvidenceGraphService;
import java.math.BigDecimal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 学习效果闭环 trace 汇总：消费、微测、掌握度、补救必须能用数字串起来。 */
@Testcontainers
class LearningEffectTraceServiceTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static LearningEffectTraceService service;

  private Long userId;
  private Long resourceId;

  @BeforeAll
  static void migrateAndWire() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());

    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    jdbc = new JdbcTemplate(dataSource);
    LearningEvidenceGraphService graphService =
        new LearningEvidenceGraphService(
            new LearningEvidenceGraphRepository(jdbc, new ObjectMapper()));
    service =
        new LearningEffectTraceService(
            new LearningEffectTraceRepository(jdbc, new ObjectMapper()), graphService);
  }

  @BeforeEach
  void seedLearningLoop() {
    jdbc.update("DELETE FROM resource_learning_check");
    jdbc.update("DELETE FROM learning_event_log WHERE source = 'test'");
    jdbc.update("DELETE FROM kc_mastery_event WHERE kc_code = 'PY43-KC-001'");
    jdbc.update("DELETE FROM learning_resource WHERE title LIKE 'trace-%'");
    jdbc.update("DELETE FROM users WHERE username LIKE 'effect-trace-%'");
    userId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Effect Trace', 'x', 'STUDENT') RETURNING id",
            Long.class,
            "effect-trace-" + System.nanoTime());
    resourceId =
        jdbc.queryForObject(
            """
            INSERT INTO learning_resource (
              user_id, pack_id, kc_codes, type, title, content_format, content, evidence_page_ids
            ) VALUES (?, 43, ARRAY['PY43-KC-001']::text[], 'HANDOUT', 'trace-handout',
              'STRUCT_JSON', '{}'::jsonb, ARRAY[101]::bigint[]) RETURNING id
            """,
            Long.class,
            userId);
    jdbc.update(
        """
        INSERT INTO learning_event_log (user_id, event_type, payload, source, importance)
        VALUES (?, 'resource_consumed', ?::jsonb, 'test', 1)
        """,
        userId,
        "{\"resourceId\":" + resourceId + ",\"kcCode\":\"PY43-KC-001\"}");
    jdbc.update(
        """
        INSERT INTO resource_learning_check (
          user_id, resource_id, kc_code, evidence_type, answer_text, operation_evidence,
          status, mastery_delta, remediation_type, reason
        ) VALUES (?, ?, 'PY43-KC-001', 'MICRO_QUIZ', 'input 读取用户输入', '{}'::jsonb,
          'PASSED', 0.050, NULL, 'hit keyword')
        """,
        userId,
        resourceId);
    jdbc.update(
        """
        INSERT INTO resource_learning_check (
          user_id, resource_id, kc_code, evidence_type, answer_text, operation_evidence,
          status, mastery_delta, remediation_type, reason
        ) VALUES (?, ?, 'PY43-KC-001', 'MICRO_QUIZ', '看完了', '{}'::jsonb,
          'FAILED', -0.020, 'PRACTICE', 'missing keyword')
        """,
        userId,
        resourceId);
    jdbc.update(
        """
        INSERT INTO kc_mastery_event (user_id, kc_code, source, delta, reason)
        VALUES (?, 'PY43-KC-001', 'MICRO_CHECK_PASS', 0.050, 'resource_check:1'),
               (?, 'PY43-KC-001', 'MICRO_CHECK_FAIL', -0.020, 'resource_check:2')
        """,
        userId,
        userId);
  }

  @Test
  void summaryQuantifiesConsumptionCheckMasteryAndRemediation() {
    LearningEffectSummaryDto summary = service.summary(userId);

    assertThat(summary.resourceCount()).isEqualTo(1);
    assertThat(summary.consumedResourceCount()).isEqualTo(1);
    assertThat(summary.checkedResourceCount()).isEqualTo(1);
    assertThat(summary.checkCount()).isEqualTo(2);
    assertThat(summary.passedCheckCount()).isEqualTo(1);
    assertThat(summary.failedCheckCount()).isEqualTo(1);
    assertThat(summary.checkPassRate()).isEqualTo(0.5);
    assertThat(summary.evidenceCompletionRate()).isEqualTo(1.0);
    // 补救率 = 已补救的失败微测 / 失败微测。1 次失败已补救 → 1.0。
    // 分母若用「全部微测」，这里会算成 0.5，而随着通过次数增加会被稀释到接近 0
    //（1 次失败已补救 + 99 次通过 = 0.01），与「补救率」的语义相差两个量级。
    assertThat(summary.remediationRate()).isEqualTo(1.0);
    assertThat(summary.totalMasteryDelta()).isEqualByComparingTo(new BigDecimal("0.030"));
    assertThat(summary.positiveResourceReadEvents()).isZero();
    assertThat(summary.checkMasteryEvents()).isEqualTo(2);
    assertThat(summary.evidenceGraphMetrics().nodeCount()).isGreaterThan(0);
    assertThat(summary.evidenceGraphMetrics().edgeCount()).isGreaterThan(0);
    assertThat(summary.evidenceGraphMetrics().checkCoverageRate()).isEqualTo(1.0);
    assertThat(summary.evidenceGraphMetrics().masteryEvidenceRate()).isEqualTo(1.0);
    assertThat(summary.evidenceGraphMetrics().graphCompletenessScore()).isGreaterThan(0.6);
    assertThat(summary.personalizationSignalCount()).isGreaterThanOrEqualTo(1);
  }

  /** 补救率不能被「通过的微测」稀释：唯一的失败已补救 → 恒为 1.0，与通过多少次无关。 */
  @Test
  void remediationRateIsNotDilutedByPassedChecks() {
    for (int i = 0; i < 8; i++) {
      jdbc.update(
          """
          INSERT INTO resource_learning_check (
            user_id, resource_id, kc_code, evidence_type, answer_text, operation_evidence,
            status, mastery_delta, remediation_type, reason
          ) VALUES (?, ?, 'PY43-KC-001', 'MICRO_QUIZ', '答对了', '{}'::jsonb,
            'PASSED', 0.050, NULL, 'hit keyword')
          """,
          userId,
          resourceId);
    }

    LearningEffectSummaryDto summary = service.summary(userId);

    assertThat(summary.checkCount()).isEqualTo(10);
    assertThat(summary.failedCheckCount()).isEqualTo(1);
    // 分母若是全部微测，这里会是 0.1，并随通过次数继续趋近 0。
    assertThat(summary.remediationRate()).isEqualTo(1.0);
  }

  /** 从未失败过微测：补救率分母为 0，返回 0.0 表示「不适用」，消费方须结合 failedCheckCount 判读。 */
  @Test
  void remediationRateIsZeroWhenNothingEverFailed() {
    jdbc.update("DELETE FROM resource_learning_check WHERE user_id = ? AND status = 'FAILED'", userId);

    LearningEffectSummaryDto summary = service.summary(userId);

    assertThat(summary.failedCheckCount()).isZero();
    assertThat(summary.remediationRate()).isZero();
  }
}
