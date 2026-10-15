package com.pythontopia.world.boss.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossProblemReferenceDto;
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
    assertThat(pending.problem().testCases()).hasSize(3);

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
  void generatedProblemCountsAsMasteryEvidenceOnlyAfterApproval() {
    BossInternalProblem candidate = problem("source-pending", 3);
    BossProblemReviewRecord pending = repository.create(requesterId, "debug", candidate);

    assertThat(repository.isApproved(candidate)).isFalse();

    repository.decide(
        pending.id(), 1, BossProblemReviewDecision.APPROVED, deciderId, "已审核");

    assertThat(repository.isApproved(candidate)).isTrue();
    assertThat(repository.isApproved(problem("issued-to-student", 3))).isTrue();
  }

  @Test
  void reviewedAiCandidateIsAutomaticallyApprovedAndReusedFromTheBank() {
    BossInternalProblem candidate = problem("ai-approved-1", 2);

    assertThat(
            repository.approveAutomaticallyIfNotDuplicate(
                requesterId, "debug", 1, 1, "PY43-KC-001", 2, candidate))
        .isTrue();

    BossProblemReviewRecord approved = repository.list(20).getFirst();
    assertThat(approved.decision()).isEqualTo(BossProblemReviewDecision.APPROVED);
    assertThat(approved.deciderId()).isNull();
    assertThat(approved.decidedAt()).isNotNull();
    assertThat(approved.decisionReason()).contains("KC 接地");
    assertThat(approved.version()).isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT review_source FROM boss_problem_review WHERE id = ?",
                String.class,
                approved.id()))
        .isEqualTo("AUTOMATED");
    assertThat(repository.findApproved(1, 1, "PY43-KC-001", "debug", 2)).isPresent();
    assertThat(repository.isApproved(candidate)).isTrue();
    assertThat(
            repository.approveAutomaticallyIfNotDuplicate(
                requesterId, "debug", 1, 1, "PY43-KC-001", 2, candidate))
        .isFalse();
    assertThat(repository.list(20)).hasSize(1);
  }

  @Test
  void similarityCandidatesExposeOnlyStatementsForTheRequestedKc() {
    repository.create(requesterId, "debug", problem("same-kc-stage", 2));

    assertThat(repository.findRecentSimilarityCandidates("PY43-KC-001"))
        .containsExactly(
            new BossProblemReferenceDto("same-kc-stage", "输入两个整数并输出和"));
    assertThat(repository.findRecentSimilarityCandidates("PY43-KC-002")).isEmpty();
  }

  @Test
  void similarityCandidatesIncludeCoursePackQuestionsForTheSameKc() {
    List<BossProblemReferenceDto> candidates =
        repository.findRecentSimilarityCandidates("PY43-KC-016");

    assertThat(candidates)
        .anySatisfy(
            candidate -> {
              assertThat(candidate.problemId()).isEqualTo("course:476");
              assertThat(candidate.statement()).contains("3.1415");
            });
  }

  @Test
  void similarityCandidatesIncludeReviewedQuestionsFromOtherStagesOfTheSameKc() {
    BossInternalProblem otherStage = withStage(problem("same-kc-other-stage", 2), 2);
    repository.create(requesterId, "debug", problem("same-kc-stage-one", 2));
    repository.create(requesterId, "debug", otherStage);

    assertThat(repository.findRecentSimilarityCandidates("PY43-KC-001"))
        .extracting(BossProblemReferenceDto::problemId)
        .containsExactlyInAnyOrder("same-kc-stage-one", "same-kc-other-stage");
  }

  @Test
  void automaticApprovalRejectsUnreviewedMismatchedAndIncompleteCandidates() {
    BossInternalProblem unreviewed = withReviewFlags(problem("unreviewed", 2), false, true);
    BossInternalProblem wrongKc = withKc(problem("wrong-kc", 2), "PY43-KC-002");
    BossInternalProblem incomplete = withCases(problem("incomplete", 2), 1);
    BossInternalProblem wrongDifficulty = problem("wrong-difficulty", 3);

    assertThat(repository.approveAutomaticallyIfNotDuplicate(
            requesterId, "debug", 1, 1, "PY43-KC-001", 2, unreviewed))
        .isFalse();
    assertThat(repository.approveAutomaticallyIfNotDuplicate(
            requesterId, "debug", 1, 1, "PY43-KC-001", 2, wrongKc))
        .isFalse();
    assertThat(repository.approveAutomaticallyIfNotDuplicate(
            requesterId, "debug", 1, 1, "PY43-KC-001", 2, incomplete))
        .isFalse();
    assertThat(repository.approveAutomaticallyIfNotDuplicate(
            requesterId, "debug", 1, 1, "PY43-KC-001", 2, wrongDifficulty))
        .isFalse();
    assertThat(repository.list(20)).isEmpty();
  }

  @Test
  void teacherCanRejectAutomaticApprovalButManualTerminalDecisionStaysImmutable() {
    BossInternalProblem candidate = problem("ai-review-override", 2);
    repository.approveAutomaticallyIfNotDuplicate(
        requesterId, "debug", 1, 1, "PY43-KC-001", 2, candidate);
    BossProblemReviewRecord autoApproved = repository.list(20).getFirst();
    assertThat(autoApproved.decision()).isEqualTo(BossProblemReviewDecision.APPROVED);

    BossProblemReviewRecord rejected =
        repository
            .decide(
                autoApproved.id(),
                autoApproved.version(),
                BossProblemReviewDecision.REJECTED,
                deciderId,
                "教师复核发现题面歧义")
            .orElseThrow();

    assertThat(rejected.decision()).isEqualTo(BossProblemReviewDecision.REJECTED);
    assertThat(rejected.deciderId()).isEqualTo(deciderId);
    assertThat(rejected.version()).isEqualTo(3);
    assertThat(repository.findApproved(1, 1, "PY43-KC-001", "debug", 2)).isEmpty();
    assertThat(
            repository.decide(
                rejected.id(), 2, BossProblemReviewDecision.APPROVED, deciderId, "stale"))
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
        repository.listMatching(
            BossProblemReviewDecision.PENDING,
            "search-loop",
            1,
            0,
            BossProblemReviewSortField.REQUESTED_AT,
            BossProblemReviewSortDirection.DESC);

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
    assertThat(first.problem().testCases()).hasSize(3);
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
        3,
        true,
        true,
        List.of(
            new BossTestCasePayload("2 3", "5", true),
            new BossTestCasePayload("-1 8", "7", false),
            new BossTestCasePayload("0 0", "0", false)),
        "a, b = map(int, input().split())\nprint(a + b)");
  }

  private static BossInternalProblem withReviewFlags(
      BossInternalProblem problem, boolean reviewed, boolean aiGenerated) {
    return new BossInternalProblem(
        problem.problemId(), problem.chapter(), problem.stage(), problem.difficulty(),
        problem.kcCode(), problem.statement(), problem.hints(), problem.sampleInput(),
        problem.sampleOutput(), problem.totalTestCases(), reviewed, aiGenerated,
        problem.testCases(), problem.referenceSolution());
  }

  private static BossInternalProblem withKc(BossInternalProblem problem, String kcCode) {
    return new BossInternalProblem(
        problem.problemId(), problem.chapter(), problem.stage(), problem.difficulty(), kcCode,
        problem.statement(), problem.hints(), problem.sampleInput(), problem.sampleOutput(),
        problem.totalTestCases(), problem.reviewed(), problem.aiGenerated(), problem.testCases(),
        problem.referenceSolution());
  }

  private static BossInternalProblem withStage(BossInternalProblem problem, int stage) {
    return new BossInternalProblem(
        problem.problemId(), problem.chapter(), stage, problem.difficulty(), problem.kcCode(),
        problem.statement(), problem.hints(), problem.sampleInput(), problem.sampleOutput(),
        problem.totalTestCases(), problem.reviewed(), problem.aiGenerated(), problem.testCases(),
        problem.referenceSolution());
  }

  private static BossInternalProblem withCases(BossInternalProblem problem, int caseCount) {
    return new BossInternalProblem(
        problem.problemId(), problem.chapter(), problem.stage(), problem.difficulty(),
        problem.kcCode(), problem.statement(), problem.hints(), problem.sampleInput(),
        problem.sampleOutput(), caseCount, problem.reviewed(), problem.aiGenerated(),
        problem.testCases().subList(0, caseCount), problem.referenceSolution());
  }
}
