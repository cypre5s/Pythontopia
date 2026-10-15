package com.pythontopia.learner.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

/** Evidence Memory 2.0：从证据图派生选择性长期记忆，并沿 KC 图关系扩散弱点压力。 */
@Testcontainers
class EvidenceMemoryServiceTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static EvidenceMemoryService service;

  private Long userId;
  private Long resourceId;
  private Long failedEventId;

  @BeforeAll
  static void migrateAndWire() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    jdbc = new JdbcTemplate(dataSource);
    ObjectMapper mapper = new ObjectMapper();
    LearningEvidenceGraphService graphService =
        new LearningEvidenceGraphService(new LearningEvidenceGraphRepository(jdbc, mapper));
    service = new EvidenceMemoryService(graphService, new EvidenceMemoryRepository(jdbc, mapper), jdbc);
  }

  @BeforeEach
  void seedEvidenceLoop() {
    jdbc.update("DELETE FROM learner_evidence_memory_snapshot");
    jdbc.update("DELETE FROM resource_learning_check");
    jdbc.update("DELETE FROM learning_event_log WHERE source = 'evidence-memory-test'");
    jdbc.update("DELETE FROM kc_mastery_event WHERE reason LIKE 'evidence-memory-test:%'");
    jdbc.update("DELETE FROM learning_resource WHERE title LIKE 'evidence-memory-%'");
    jdbc.update("DELETE FROM users WHERE username LIKE 'evidence-memory-%'");
    jdbc.update(
        """
        INSERT INTO course_kc_edge (
          pack_id, subject_type, subject_id, relation, object_type, object_id, source, confidence
        ) VALUES (43, 'kc', 'PY43-KC-001', 'co_occurs_with', 'kc', 'PY43-KC-002',
          'evidence-memory-test', 0.8000)
        ON CONFLICT DO NOTHING
        """);

    userId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Evidence Memory User', 'x', 'STUDENT') RETURNING id",
            Long.class,
            "evidence-memory-" + System.nanoTime());
    resourceId =
        jdbc.queryForObject(
            """
            INSERT INTO learning_resource (
              user_id, pack_id, kc_codes, type, title, content_format, content, evidence_page_ids
            ) VALUES (?, 43, ARRAY['PY43-KC-001']::text[], 'HANDOUT', 'evidence-memory-handout',
              'STRUCT_JSON', '{"keywords":["input"]}'::jsonb, ARRAY[10707]::bigint[]) RETURNING id
            """,
            Long.class,
            userId);
    failedEventId =
        jdbc.queryForObject(
            """
            INSERT INTO learning_event_log (user_id, event_type, payload, source, importance)
            VALUES (?, 'practice_failed', ?::jsonb, 'evidence-memory-test', 5) RETURNING id
            """,
            Long.class,
            userId,
            "{\"kcCode\":\"PY43-KC-001\",\"problemId\":476,\"failureKind\":\"WA\"}");
    jdbc.update(
        """
        INSERT INTO resource_learning_check (
          user_id, resource_id, kc_code, evidence_type, answer_text, operation_evidence,
          status, mastery_delta, remediation_type, reason
        ) VALUES (?, ?, 'PY43-KC-001', 'MICRO_QUIZ', '没提 input',
          '{}'::jsonb, 'FAILED', -0.040, 'PRACTICE', 'evidence-memory-test:missing keyword')
        """,
        userId,
        resourceId);
  }

  @Test
  void persistsSelectiveSnapshotWithRelationExpandedSignals() {
    EvidenceMemorySnapshotDto snapshot = service.createSnapshot(userId, null, 120, true);

    assertThat(snapshot.snapshotId()).isNotNull();
    assertThat(snapshot.directSignalCount()).isGreaterThanOrEqualTo(1);
    assertThat(snapshot.propagatedSignalCount()).isGreaterThanOrEqualTo(1);
    assertThat(snapshot.retainedEvidenceCount()).isGreaterThanOrEqualTo(3);
    assertThat(snapshot.graphMemoryScore()).isGreaterThan(snapshot.naiveMemoryScore());

    EvidenceMemorySignalDto direct =
        snapshot.signals().stream()
            .filter(signal -> signal.kcCode().equals("PY43-KC-001") && signal.signalKind().equals("DIRECT"))
            .findFirst()
            .orElseThrow();
    assertThat(direct.evidenceEventIds()).contains(failedEventId);
    assertThat(direct.checkIds()).isNotEmpty();
    assertThat(direct.pageIds()).contains(10707L);
    assertThat(direct.retentionPolicy()).isEqualTo("retain");

    EvidenceMemorySignalDto propagated =
        snapshot.signals().stream()
            .filter(signal -> signal.kcCode().equals("PY43-KC-002") && signal.signalKind().equals("PROPAGATED"))
            .findFirst()
            .orElseThrow();
    assertThat(propagated.propagatedScore()).isGreaterThan(0.0);
    assertThat(propagated.reason()).contains("co_occurs_with", "PY43-KC-001");

    Integer persisted =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM learner_evidence_memory_snapshot WHERE user_id = ?",
            Integer.class,
            userId);
    assertThat(persisted).isEqualTo(1);
  }

  @Test
  void retainsOnlyThe200MostRecentSnapshotsPerUser() {
    EvidenceMemoryRepository repository = new EvidenceMemoryRepository(jdbc, new ObjectMapper());
    EvidenceMemorySnapshotDto snapshot =
        new EvidenceMemorySnapshotDto(null, userId, null, 0, 0, 0, 0.0, 0.0, List.of());

    Long oldestSnapshotId = null;
    Long latestSnapshotId = null;
    for (int i = 0; i < 201; i++) {
      Long snapshotId = repository.insert(snapshot);
      if (oldestSnapshotId == null) {
        oldestSnapshotId = snapshotId;
      }
      latestSnapshotId = snapshotId;
    }

    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM learner_evidence_memory_snapshot WHERE user_id = ?",
            Integer.class,
            userId);
    Integer oldestCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM learner_evidence_memory_snapshot WHERE id = ?",
            Integer.class,
            oldestSnapshotId);
    Integer latestCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM learner_evidence_memory_snapshot WHERE id = ?",
            Integer.class,
            latestSnapshotId);

    assertThat(count).isEqualTo(200);
    assertThat(oldestCount).isZero();
    assertThat(latestCount).isEqualTo(1);
  }

  @Test
  void graphBackedMemoryBeatsNaiveRecentEventMemoryByWideMargin() {
    EvidenceMemorySnapshotDto graphBacked = service.createSnapshot(userId, null, 120, false);

    Long naiveUserId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Naive Evidence Memory', 'x', 'STUDENT') RETURNING id",
            Long.class,
            "evidence-memory-naive-" + System.nanoTime());
    jdbc.update(
        """
        INSERT INTO learning_event_log (user_id, event_type, payload, source, importance)
        VALUES (?, 'resource_consumed', '{"kcCode":"PY43-KC-001"}'::jsonb, 'evidence-memory-test', 1)
        """,
        naiveUserId);

    EvidenceMemorySnapshotDto naive = service.createSnapshot(naiveUserId, null, 120, false);

    assertThat(graphBacked.graphMemoryScore()).isGreaterThanOrEqualTo(naive.graphMemoryScore() + 0.35);
    assertThat(graphBacked.retainedEvidenceCount()).isGreaterThan(naive.retainedEvidenceCount());
    assertThat(graphBacked.signals()).hasSizeGreaterThan(naive.signals().size());
  }

  @Test
  void evidenceMemoryWinsEveryAbMetricAgainstRecentEventOnlyBaseline() {
    EvidenceMemorySnapshotDto treatment = service.createSnapshot(userId, null, 120, false);

    Long baselineUserId =
        jdbc.queryForObject(
            "INSERT INTO users (username, display_name, password_hash, role)"
                + " VALUES (?, 'Recent Event Only Memory', 'x', 'STUDENT') RETURNING id",
            Long.class,
            "evidence-memory-ab-baseline-" + System.nanoTime());
    jdbc.update(
        """
        INSERT INTO learning_event_log (user_id, event_type, payload, source, importance)
        VALUES (?, 'resource_consumed', '{"kcCode":"PY43-KC-001"}'::jsonb, 'evidence-memory-test', 1)
        """,
        baselineUserId);
    EvidenceMemorySnapshotDto baseline = service.createSnapshot(baselineUserId, null, 120, false);

    List<AbMetric> rows =
        List.of(
            new AbMetric(
                "graph_memory_score",
                baseline.graphMemoryScore(),
                treatment.graphMemoryScore(),
                0.35),
            new AbMetric(
                "retained_evidence_count",
                baseline.retainedEvidenceCount(),
                treatment.retainedEvidenceCount(),
                2.00),
            new AbMetric(
                "signal_count",
                baseline.signals().size(),
                treatment.signals().size(),
                1.00),
            new AbMetric(
                "direct_signal_count",
                baseline.directSignalCount(),
                treatment.directSignalCount(),
                1.00),
            new AbMetric(
                "propagated_signal_count",
                baseline.propagatedSignalCount(),
                treatment.propagatedSignalCount(),
                1.00));

    assertThat(rows).allSatisfy(row -> assertThat(row.uplift()).isGreaterThanOrEqualTo(row.minUplift()));
  }

  private record AbMetric(String name, double baseline, double treatment, double minUplift) {
    double uplift() {
      return treatment - baseline;
    }
  }
}
