package com.pythontopia.classroom.quest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.classroom.quest.repository.ClassroomQuestRepository;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.learner.quest.repository.QuestRepository.QuestInsert;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ClassroomQuestRepositoryPostgresIntegrationTest {

  private static final long CLASSROOM_QUEST_ID = 987654L;

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
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(dataSource);
  }

  @Test
  void insertsAndRetriesMoreThanOneThousandCopiesAcrossSqlChunks() {
    jdbc.update(
        """
        INSERT INTO users (username, display_name, password_hash, email, role)
        SELECT 'quest-batch-' || n, '批量学生', 'test-hash', NULL, 'STUDENT'
        FROM generate_series(1, 1001) AS n
        """);
    List<Long> userIds =
        jdbc.queryForList(
            "SELECT id FROM users WHERE username LIKE 'quest-batch-%' ORDER BY id", Long.class);
    ObjectMapper objectMapper = new ObjectMapper();
    List<QuestInsert> inserts =
        userIds.stream()
            .map(
                userId ->
                    new QuestInsert(
                        userId,
                        QuestSource.CLASSROOM,
                        CLASSROOM_QUEST_ID,
                        "classroom task",
                        null,
                        null,
                        QuestNodeType.READ_RESOURCE,
                        objectMapper.createObjectNode(),
                        LocalDate.now().plusDays(1),
                        5,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        objectMapper.createObjectNode()))
            .toList();
    ClassroomQuestRepository repository = new ClassroomQuestRepository(jdbc, objectMapper);

    assertThat(userIds).hasSize(1001);
    assertThat(repository.insertStudentQuestsIfAbsent(inserts)).isEqualTo(1001);
    assertThat(repository.insertStudentQuestsIfAbsent(inserts)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM quest WHERE source = 'CLASSROOM' AND source_ref_id = ?",
                Integer.class,
                CLASSROOM_QUEST_ID))
        .isEqualTo(1001);
  }
}
