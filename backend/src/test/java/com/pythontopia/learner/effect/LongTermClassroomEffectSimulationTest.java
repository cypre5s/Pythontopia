package com.pythontopia.learner.effect;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 长期学习效果仿真：用真实班级日志表观察 KC 掌握、复习完成率、资源使用率。 */
@Testcontainers
class LongTermClassroomEffectSimulationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;
  private static SingleConnectionDataSource dataSource;

  @BeforeAll
  static void migrateAndWire() {
    dataSource = new SingleConnectionDataSource(POSTGRES.getJdbcUrl(), true);
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void closeDatabaseConnection() {
    dataSource.destroy();
  }

  @Test
  void evidenceMemoryStyleCohortWinsLongTermClassroomMetricsFromLogs() {
    ClassroomLearningEffectSimulator simulator = new ClassroomLearningEffectSimulator(jdbc);

    LongTermSimulationReport report = simulator.runSixWeekSimulation();

    assertThat(report.classroomId()).isNotNull();
    assertThat(report.baseline().studentCount()).isEqualTo(12);
    assertThat(report.treatment().studentCount()).isEqualTo(12);
    assertThat(report.rawLogCounts().masteryEvents()).isEqualTo(720);
    assertThat(report.rawLogCounts().reviewEvents()).isEqualTo(1128);
    assertThat(report.rawLogCounts().resourceEvents()).isEqualTo(234);

    assertThat(report.baseline().avgMastery()).isEqualTo(0.280);
    assertThat(report.treatment().avgMastery()).isEqualTo(0.482);
    assertThat(report.baseline().reviewCompletionRate()).isEqualTo(0.333);
    assertThat(report.treatment().reviewCompletionRate()).isEqualTo(0.800);
    assertThat(report.baseline().resourceUsageRate()).isEqualTo(0.625);
    assertThat(report.treatment().resourceUsageRate()).isEqualTo(1.000);

    assertThat(report.treatment().avgMastery() - report.baseline().avgMastery())
        .isGreaterThanOrEqualTo(0.12);
    assertThat(report.treatment().reviewCompletionRate() - report.baseline().reviewCompletionRate())
        .isGreaterThanOrEqualTo(0.25);
    assertThat(report.treatment().resourceUsageRate() - report.baseline().resourceUsageRate())
        .isGreaterThanOrEqualTo(0.20);
    assertThat(report.treatment().winsAllMetricsAgainst(report.baseline())).isTrue();
  }
}
