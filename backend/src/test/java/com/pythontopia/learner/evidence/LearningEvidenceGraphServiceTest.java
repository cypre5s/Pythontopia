package com.pythontopia.learner.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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

/** SQL-native Learning Evidence Graph：把学习事件、资源、检查、页码、KC 关系连成可审计子图。 */
@Testcontainers
class LearningEvidenceGraphServiceTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static LearningEvidenceGraphService service;

  private Long userId;
  private Long resourceRunId;
  private Long resourceId;
  private Long checkId;

  @BeforeAll
  static void migrateAndWire() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());

    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    jdbc = new JdbcTemplate(dataSource);
    service =
        new LearningEvidenceGraphService(
            new LearningEvidenceGraphRepository(jdbc, new ObjectMapper()));
  }

  @BeforeEach
  void seedClosedLearningLoop() {
    jdbc.update("DELETE FROM resource_learning_check");
    jdbc.update("DELETE FROM learning_event_log WHERE source = 'evidence-test'");
    jdbc.update(
        "DELETE FROM kc_mastery_event WHERE reason LIKE 'evidence-test:%' OR reason LIKE 'resource_check:%'");
    jdbc.update("DELETE FROM learning_resource WHERE title LIKE 'evidence-%'");
    jdbc.update("DELETE FROM resource_run WHERE scenario LIKE 'EVIDENCE_%'");
    jdbc.update("DELETE FROM users WHERE username LIKE 'evidence-user-%'");

    userId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Evidence User', 'x', 'STUDENT') RETURNING id",
            Long.class,
            "evidence-user-" + System.nanoTime());
    resourceRunId =
        jdbc.queryForObject(
            """
            INSERT INTO resource_run (
              user_id, agent_name, scenario, input_summary, output_artifact, evidence_page_ids,
              safety_result, grounding_result, status, graph_trace
            ) VALUES (?, 'ResourceDesignerAgent', 'EVIDENCE_RESOURCE', '{}'::jsonb,
              '{"artifact":"handout"}'::jsonb, ARRAY[10707,10708]::bigint[],
              '{"status":"PASSED"}'::jsonb, '{"status":"PASSED"}'::jsonb, 'SUCCESS',
              '{"route":"graph","co_occur_hit_count":1}'::jsonb) RETURNING id
            """,
            Long.class,
            userId);
    resourceId =
        jdbc.queryForObject(
            """
            INSERT INTO learning_resource (
              user_id, pack_id, kc_codes, type, title, content_format, content, evidence_page_ids,
              created_by_run_id
            ) VALUES (?, 43, ARRAY['PY43-KC-001']::text[], 'HANDOUT', 'evidence-handout',
              'STRUCT_JSON', '{"keywords":["input","print"]}'::jsonb, ARRAY[10707,10708]::bigint[],
              ?) RETURNING id
            """,
            Long.class,
            userId,
            resourceRunId);
    jdbc.update(
        """
        INSERT INTO learning_event_log (user_id, event_type, payload, source, importance)
        VALUES (?, 'resource_consumed', ?::jsonb, 'evidence-test', 2),
               (?, 'practice_failed', ?::jsonb, 'evidence-test', 4)
        """,
        userId,
        "{\"resourceId\":" + resourceId + ",\"kcCode\":\"PY43-KC-001\"}",
        userId,
        "{\"problemId\":476,\"kcCode\":\"PY43-KC-001\",\"failureKind\":\"WA\"}");
    checkId =
        jdbc.queryForObject(
        """
        INSERT INTO resource_learning_check (
          user_id, resource_id, kc_code, evidence_type, answer_text, operation_evidence,
          status, mastery_delta, remediation_type, reason
        ) VALUES (?, ?, 'PY43-KC-001', 'MICRO_QUIZ', 'input print',
          '{"submissionId":476,"result":"checked"}'::jsonb, 'PASSED', 0.050, NULL,
          'evidence-test:hit keyword') RETURNING id
        """,
            Long.class,
        userId,
        resourceId);
    jdbc.update(
        """
        INSERT INTO kc_mastery_event (user_id, kc_code, source, delta, reason)
        VALUES (?, 'PY43-KC-001', 'MICRO_CHECK_PASS', 0.050, ?)
        """,
        userId,
        "resource_check:" + checkId);
  }

  @Test
  void buildsAuditableSubgraphAcrossResourceChecksMasteryPagesAndKcRelations() {
    LearningEvidenceGraphDto graph = service.buildForUser(userId, "PY43-KC-001", 80);

    assertThat(nodeTypes(graph))
        .contains("USER", "RESOURCE", "CHECK", "KC", "PAGE", "MASTERY_EVENT", "EVENT", "AGENT_RUN");
    assertThat(edgeRelations(graph))
        .contains(
            "ACTED",
            "COVERS_KC",
            "CHECKS_RESOURCE",
            "CHECKS_KC",
            "CAUSED_MASTERY_EVENT",
            "UPDATES_MASTERY",
            "GROUNDED_BY_PAGE",
            "GENERATED",
            "HAS_ERROR_KIND",
            "KC_RELATION");
    assertThat(graph.edges())
        .anySatisfy(
            edge -> {
              assertThat(edge.fromId()).isEqualTo("check:" + checkId);
              assertThat(edge.relation()).isEqualTo("CAUSED_MASTERY_EVENT");
              assertThat(edge.toId()).startsWith("mastery:");
            });

    assertThat(graph.metrics().nodeCount()).isEqualTo(graph.nodes().size());
    assertThat(graph.metrics().edgeCount()).isEqualTo(graph.edges().size());
    assertThat(graph.metrics().touchedKcCount()).isGreaterThanOrEqualTo(1);
    assertThat(graph.metrics().groundedPageCount()).isGreaterThanOrEqualTo(2);
    assertThat(graph.metrics().checkCoverageRate()).isEqualTo(1.0);
    assertThat(graph.metrics().masteryEvidenceRate()).isEqualTo(1.0);
    assertThat(graph.metrics().connectedEvidenceRate()).isGreaterThan(0.8);
    assertThat(graph.metrics().graphCompletenessScore()).isGreaterThanOrEqualTo(0.8);
  }

  @Test
  void graphBackedLearningLoopScoresHigherThanNaiveResourceOnlyBaseline() {
    LearningEvidenceGraphDto graphBacked = service.buildForUser(userId, "PY43-KC-001", 80);

    Long naiveUserId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Naive User', 'x', 'STUDENT') RETURNING id",
            Long.class,
            "evidence-user-naive-" + System.nanoTime());
    jdbc.update(
        """
        INSERT INTO learning_resource (
          user_id, pack_id, kc_codes, type, title, content_format, content, evidence_page_ids
        ) VALUES (?, 43, ARRAY['PY43-KC-001']::text[], 'HANDOUT', 'evidence-naive',
          'STRUCT_JSON', '{}'::jsonb, ARRAY[]::bigint[])
        """,
        naiveUserId);
    LearningEvidenceGraphDto naive = service.buildForUser(naiveUserId, "PY43-KC-001", 80);

    assertThat(graphBacked.metrics().graphCompletenessScore())
        .isGreaterThanOrEqualTo(naive.metrics().graphCompletenessScore() + 0.4);
    assertThat(graphBacked.metrics().connectedEvidenceRate())
        .isGreaterThan(naive.metrics().connectedEvidenceRate());
    assertThat(graphBacked.metrics().groundedPageCount()).isGreaterThan(naive.metrics().groundedPageCount());
  }

  @Test
  void graphBackedLearningLoopWinsEveryAbMetricAgainstResourceOnlyBaseline() {
    LearningEvidenceGraphDto graphBacked = service.buildForUser(userId, "PY43-KC-001", 80);

    Long naiveUserId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Naive Matrix User', 'x', 'STUDENT') RETURNING id",
            Long.class,
            "evidence-user-naive-matrix-" + System.nanoTime());
    jdbc.update(
        """
        INSERT INTO learning_resource (
          user_id, pack_id, kc_codes, type, title, content_format, content, evidence_page_ids
        ) VALUES (?, 43, ARRAY['PY43-KC-001']::text[], 'HANDOUT', 'evidence-naive-matrix',
          'STRUCT_JSON', '{}'::jsonb, ARRAY[]::bigint[])
        """,
        naiveUserId);
    LearningEvidenceGraphDto baseline = service.buildForUser(naiveUserId, "PY43-KC-001", 80);

    List<AbMetric> rows =
        List.of(
            new AbMetric(
                "graph_completeness",
                baseline.metrics().graphCompletenessScore(),
                graphBacked.metrics().graphCompletenessScore(),
                0.40),
            new AbMetric(
                "connected_evidence_rate",
                baseline.metrics().connectedEvidenceRate(),
                graphBacked.metrics().connectedEvidenceRate(),
                0.30),
            new AbMetric(
                "grounded_page_count",
                baseline.metrics().groundedPageCount(),
                graphBacked.metrics().groundedPageCount(),
                2.00),
            new AbMetric(
                "check_coverage_rate",
                baseline.metrics().checkCoverageRate(),
                graphBacked.metrics().checkCoverageRate(),
                0.80),
            new AbMetric(
                "mastery_evidence_rate",
                baseline.metrics().masteryEvidenceRate(),
                graphBacked.metrics().masteryEvidenceRate(),
                0.80));

    assertThat(rows).allSatisfy(row -> assertThat(row.uplift()).isGreaterThanOrEqualTo(row.minUplift()));
  }

  @Test
  void emitsTraceablePersonalizationSignalFromChecksEventsAndGroundedPages() {
    jdbc.update(
        """
        INSERT INTO resource_learning_check (
          user_id, resource_id, kc_code, evidence_type, answer_text, operation_evidence,
          status, mastery_delta, remediation_type, reason
        ) VALUES (?, ?, 'PY43-KC-001', 'MICRO_QUIZ', 'raw_input',
          '{"submissionId":477,"result":"missed_print"}'::jsonb, 'FAILED', -0.040,
          'PRACTICE', 'evidence-test:missed output formatting')
        """,
        userId,
        resourceId);

    LearningEvidenceGraphDto graph = service.buildForUser(userId, "PY43-KC-001", 80);

    assertThat(graph.personalizationSignals()).hasSize(1);
    EvidencePersonalizationSignalDto signal = graph.personalizationSignals().get(0);
    assertThat(signal.kcCode()).isEqualTo("PY43-KC-001");
    assertThat(signal.failedChecks()).isEqualTo(1);
    assertThat(signal.passedChecks()).isEqualTo(1);
    assertThat(signal.failedPracticeEvents()).isEqualTo(1);
    assertThat(signal.groundedPageCount()).isGreaterThanOrEqualTo(2);
    assertThat(signal.evidenceEventIds()).isNotEmpty();
    assertThat(signal.checkIds()).hasSize(2);
    assertThat(signal.pageIds()).contains(10707L, 10708L);
    assertThat(signal.priorityScore()).isGreaterThan(2.0);
    assertThat(signal.reason()).contains("微测失败", "练习失败", "证据事件", "检查证据", "课件页证据");
  }

  private static Set<String> nodeTypes(LearningEvidenceGraphDto graph) {
    return graph.nodes().stream().map(EvidenceNodeDto::type).collect(Collectors.toSet());
  }

  private static Set<String> edgeRelations(LearningEvidenceGraphDto graph) {
    return graph.edges().stream().map(EvidenceEdgeDto::relation).collect(Collectors.toSet());
  }

  private record AbMetric(String name, double baseline, double treatment, double minUplift) {
    double uplift() {
      return treatment - baseline;
    }
  }
}
