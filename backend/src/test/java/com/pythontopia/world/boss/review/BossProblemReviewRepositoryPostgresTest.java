package com.pythontopia.world.boss.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
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

/** Repository 的真库往返：hidden cases 不丢、决策乐观锁、approved bank 选择只读。 */
@Testcontainers
class BossProblemReviewRepositoryPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("pythontopia")
          .withUsername("pytopia")
          .withPassword("pytopia");

  private static JdbcTemplate jdbcTemplate;
  private static BossProblemReviewRepository repository;
  private Long requesterId;
  private Long deciderId;

  @BeforeAll
  static void migrateAndWire() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbcTemplate = new JdbcTemplate(dataSource);
    repository =
        new BossProblemReviewRepository(
            jdbcTemplate, new ObjectMapper().findAndRegisterModules());
  }

  @BeforeEach
  void seedReviewers() {
    jdbcTemplate.update("DELETE FROM boss_problem_review");
    jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'problem-review-%'");
    requesterId = insertUser("problem-review-requester", "TEACHER");
    deciderId = insertUser("problem-review-decider", "ADMIN");
  }

  @Test
  void fullPayloadRoundTripsAndDecisionAuditIsVersioned() {
    BossProblemReviewRecord pending =
        repository.create(requesterId, "dual_track", problem("source-a", 3));

    assertThat(pending.id()).isPositive();
    assertThat(pending.decision()).isEqualTo(BossProblemReviewDecision.PENDING);
    assertThat(pending.version()).isEqualTo(1);
    assertThat(pending.problem().referenceSolution()).contains("print");
    assertThat(pending.problem().testCases()).hasSize(2);

    BossProblemReviewRecord approved =
        repository
            .decide(
                pending.id(),
                pending.version(),
                BossProblemReviewDecision.APPROVED,
                deciderId,
                "题面与隐藏用例均已复核")
            .orElseThrow();

    assertThat(approved.decision()).isEqualTo(BossProblemReviewDecision.APPROVED);
    assertThat(approved.deciderId()).isEqualTo(deciderId);
    assertThat(approved.deciderUsername()).isEqualTo("problem-review-decider");
    assertThat(approved.decidedAt()).isNotNull();
    assertThat(approved.decisionReason()).isEqualTo("题面与隐藏用例均已复核");
    assertThat(approved.version()).isEqualTo(2);
    assertThat(repository.decide(
            pending.id(),
            1,
            BossProblemReviewDecision.REJECTED,
            deciderId,
            "stale"))
        .isEmpty();
  }

  @Test
  void pageFiltersByStatusAndSearchesReviewerVisibleFields() {
    repository.create(requesterId, "debug", problem("search-loop-a", 2));
    repository.create(requesterId, "debug", problem("search-loop-b", 3));
    BossProblemReviewRecord approved =
        repository.create(requesterId, "debug", problem("other-topic", 4));
    repository.decide(
        approved.id(), 1, BossProblemReviewDecision.APPROVED, deciderId, "已审核");

    List<BossProblemReviewRecord> firstPage =
        repository.listMatching(BossProblemReviewDecision.PENDING, "search-loop", 1, 0);

    assertThat(firstPage).hasSize(1);
    assertThat(repository.countMatching(BossProblemReviewDecision.PENDING, "search-loop"))
        .isEqualTo(2);
    assertThat(repository.countMatching(BossProblemReviewDecision.APPROVED, "search-loop"))
        .isZero();
  }

  @Test
  void approvedSelectionMatchesDimensionsUsesClosestDifficultyAndDoesNotConsume() {
    BossProblemReviewRecord difficultyOne =
        repository.create(requesterId, "debug", problem("source-easy", 1));
    repository.decide(
        difficultyOne.id(),
        1,
        BossProblemReviewDecision.APPROVED,
        deciderId,
        "easy approved");
    BossProblemReviewRecord difficultyFour =
        repository.create(requesterId, "debug", problem("source-hard", 4));
    repository.decide(
        difficultyFour.id(),
        1,
        BossProblemReviewDecision.APPROVED,
        deciderId,
        "hard approved");

    ApprovedBossProblem first =
        repository.findApproved(1, 1, "PY43-KC-001", "debug", 5).orElseThrow();
    ApprovedBossProblem second =
        repository.findApproved(1, 1, "PY43-KC-001", "debug", 5).orElseThrow();

    assertThat(first.reviewId()).isEqualTo(difficultyFour.id());
    assertThat(first.problem().problemId())
        .isEqualTo("approved-" + difficultyFour.id() + "-v2");
    assertThat(first.problem().testCases()).hasSize(2);
    assertThat(second).isEqualTo(first);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT decision FROM boss_problem_review WHERE id = ?",
                String.class,
                difficultyFour.id()))
        .isEqualTo("APPROVED");
  }

  private Long insertUser(String username, String role) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (username, display_name, password_hash, role)"
            + " VALUES (?, ?, 'x', ?) RETURNING id",
        Long.class,
        username,
        username,
        role);
  }

  private BossInternalProblem problem(String problemId, int difficulty) {
    return new BossInternalProblem(
        problemId,
        1,
        1,
        difficulty,
        "PY43-KC-001",
        "输入两个整数并输出和",
        List.of("读取输入"),
        "2 3",
        "5",
        2,
        true,
        true,
        List.of(
            new BossTestCasePayload("2 3", "5", true),
            new BossTestCasePayload("-1 8", "7", false)),
        "a, b = map(int, input().split())\nprint(a + b)");
  }
}
