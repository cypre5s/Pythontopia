package com.pythontopia.learner.twin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.event.LearningEventLogRepository;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.twin.dto.WeeklyReportDto;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@Testcontainers
class WeeklyReportResourceRunIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbc;

  private WeeklyReportService service;

  @BeforeAll
  static void migrateDatabase() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());

    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(dataSource);
  }

  @BeforeEach
  void cleanAndWireService() {
    jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");

    MemoryRetrievalService memoryService = mock(MemoryRetrievalService.class);
    LearnerInsightRepository insightRepository = mock(LearnerInsightRepository.class);
    MasteryRepository masteryRepository = mock(MasteryRepository.class);
    when(memoryService.retrieve(anyLong(), isNull(), anyInt())).thenReturn(List.of());
    when(insightRepository.findByUser(anyLong(), anyInt())).thenReturn(List.of());
    when(masteryRepository.findAll(anyLong())).thenReturn(List.of());
    LearningEventLogRepository eventRepository = mock(LearningEventLogRepository.class);
    when(eventRepository.summarizeAuthoritativeBetween(
            anyLong(), any(Instant.class), any(Instant.class)))
        .thenReturn(new LearningEventLogRepository.AuthoritativeEventCounts(0, 0, 0));
    service =
        new WeeklyReportService(
            memoryService,
            insightRepository,
            masteryRepository,
            eventRepository,
            jdbc);
  }

  @Test
  void returnsOnlyRecentRunsForCurrentUserAndMapsEvidenceArray() {
    long currentUserId = insertStudent("weekly-current");
    long otherUserId = insertStudent("weekly-other");
    Instant now = Instant.now();

    insertRun(
        currentUserId,
        "MindMapAgent",
        "recent-latest",
        "SUCCESS",
        new long[] {101L, 102L},
        now.minus(1, ChronoUnit.HOURS));
    insertRun(
        currentUserId,
        "PracticeAgent",
        "recent-earlier",
        "PARTIAL",
        new long[] {},
        now.minus(2, ChronoUnit.HOURS));
    insertRun(
        currentUserId,
        "ResourceDesignerAgent",
        "outside-window",
        "SUCCESS",
        new long[] {103L},
        now.minus(8, ChronoUnit.DAYS));
    insertRun(
        otherUserId,
        "StoryboardAgent",
        "other-user",
        "SUCCESS",
        new long[] {104L},
        now.minus(30, ChronoUnit.MINUTES));

    WeeklyReportDto report = service.build(currentUserId);

    assertThat(report.resourceRuns())
        .extracting(
            WeeklyReportDto.ResourceRunBrief::agentName,
            WeeklyReportDto.ResourceRunBrief::scenario,
            WeeklyReportDto.ResourceRunBrief::status,
            WeeklyReportDto.ResourceRunBrief::evidencePageCount)
        .containsExactly(
            tuple("MindMapAgent", "recent-latest", "SUCCESS", 2),
            tuple("PracticeAgent", "recent-earlier", "PARTIAL", 0));
    assertThat(report.resourceRuns())
        .allSatisfy(run -> assertThat(run.createdAt()).isNotBlank());
  }

  @Test
  void limitsRecentRunsToTen() {
    long currentUserId = insertStudent("weekly-limit");
    Instant now = Instant.now();
    for (int index = 0; index < 11; index++) {
      insertRun(
          currentUserId,
          "ResourceDesignerAgent",
          "run-" + index,
          "SUCCESS",
          new long[] {},
          now.minus(index, ChronoUnit.MINUTES));
    }

    WeeklyReportDto report = service.build(currentUserId);

    assertThat(report.resourceRuns()).hasSize(10);
    assertThat(report.resourceRuns())
        .extracting(WeeklyReportDto.ResourceRunBrief::scenario)
        .containsExactly(
            "run-0",
            "run-1",
            "run-2",
            "run-3",
            "run-4",
            "run-5",
            "run-6",
            "run-7",
            "run-8",
            "run-9");
  }

  private long insertStudent(String username) {
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
      long userId,
      String agentName,
      String scenario,
      String status,
      long[] evidencePageIds,
      Instant createdAt) {
    jdbc.update(
        """
        INSERT INTO resource_run(
          user_id, agent_name, scenario, status, evidence_page_ids, created_at
        )
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        userId,
        agentName,
        scenario,
        status,
        evidencePageIds,
        Timestamp.from(createdAt));
  }
}
