package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.event.LearningEventListener;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasteryProperties;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.review.ReviewScheduler;
import com.pythontopia.learner.mastery.review.ReviewStateRepository;
import com.pythontopia.learner.mastery.review.ReviewStateService;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import com.pythontopia.world.boss.dto.BossEvaluateResultDto;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossTestCasePayload;
import com.pythontopia.world.boss.dto.ExpeditionRunAction;
import com.pythontopia.world.boss.dto.ExpeditionRunActionRequest;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import com.pythontopia.world.boss.dto.ExpeditionScoreSubmitDto;
import com.pythontopia.world.boss.dto.FrontierPickDto;
import com.pythontopia.world.boss.review.BossProblemReviewRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
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
  private static TransactionTemplate transactions;

  @BeforeAll
  static void migrate() {
    DriverManagerDataSource source = new DriverManagerDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setUrl(POSTGRES.getJdbcUrl());
    source.setUsername(POSTGRES.getUsername());
    source.setPassword(POSTGRES.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(source);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
  }

  private static ExpeditionScoreService scoreService() {
    return new ExpeditionScoreService(
        jdbc,
        new ExpeditionRunService(jdbc, new ObjectMapper()),
        mock(ApplicationEventPublisher.class));
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

    String rootNodeId = (String) initial.nodes().getFirst().get("id");
    jdbc.update(
        "INSERT INTO expedition_verified_attempt"
            + " (user_id, run_id, node_id, problem_id, passed, score, score_awarded)"
            + " VALUES (?, ?::uuid, ?, 'issued-problem', TRUE, 80, TRUE)",
        userId,
        initial.runId(),
        rootNodeId);
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

    ExpeditionScoreService scores = scoreService();
    scores.record(userId, new ExpeditionScoreSubmitDto(initial.runId()));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM expedition_run WHERE user_id = ?", String.class, userId))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM expedition_score WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                userId,
                initial.runId()))
        .isZero();

    runs.performAction(
        userId,
        new ExpeditionRunActionRequest(initial.runId(), null, ExpeditionRunAction.FINISH, null));
    scores.record(userId, new ExpeditionScoreSubmitDto(initial.runId()));
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
                "SELECT cleared FROM expedition_score WHERE user_id = ? AND run_id = ?::uuid",
                Boolean.class,
                userId,
                initial.runId()))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pregenerated_problem WHERE user_id = ?"
                    + " AND expedition_run_id = ?::uuid AND status = 'FAILED'",
                Integer.class,
                userId,
                initial.runId()))
        .isEqualTo(2);

    scoreService()
        .recordVerifiedAttempt(
            userId, initial.runId(), rootNodeId, null, "late-submission", "a".repeat(64), true, 100);
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
  void revokedAiJudgmentCanSettleExpeditionWithoutTrustedRewards() {
    RevokedSettlementFixture fixture = createRevokedSettlementFixture();
    when(fixture.graph().evaluateAttempt(
            eq(fixture.problemId()), anyString(), anyList(), anyInt()))
        .thenReturn(
            new BossEvaluateResultDto(fixture.problemId(), true, 1, 1, null, null, List.of()));
    when(fixture.reviews().isApproved(fixture.problem())).thenReturn(false);

    BossAttemptResultDto judged =
        fixture.bossService().evaluateAttempt(
            new BossAttemptRequest(
                fixture.problemId(), 1, 1, "print('output')", 1, UUID.randomUUID()),
            fixture.userId());
    ExpeditionRunDto settled =
        fixture.runs().performAction(
            fixture.userId(),
            new ExpeditionRunActionRequest(
                fixture.active().runId(),
                fixture.nodeId(),
                ExpeditionRunAction.SETTLE_CHALLENGE,
                null,
                fixture.problemId()));

    assertThat(judged.passed()).isTrue();
    assertThat(judged.score()).isZero();
    assertThat(fixture.runs().findActive(fixture.userId()).orElseThrow().verifiedDepth()).isZero();
    assertThat(
            fixture
                .scores()
                .summarizeVerifiedAttempts(fixture.userId(), fixture.active().runId(), 1))
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptSummary(0, 0));
    assertThat(settled.phase()).isEqualTo("map");
    assertThat(
            jdbc.queryForObject(
                "SELECT score FROM expedition_challenge_settlement"
                    + " WHERE user_id = ? AND run_id = ?::uuid AND problem_id = ?",
                Integer.class,
                fixture.userId(),
                fixture.active().runId(),
                fixture.problemId()))
        .isZero();

    fixture.runs().performAction(
        fixture.userId(),
        new ExpeditionRunActionRequest(
            fixture.active().runId(), null, ExpeditionRunAction.FINISH, null));
    fixture
        .scores()
        .record(fixture.userId(), new ExpeditionScoreSubmitDto(fixture.active().runId()));

    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM expedition_run WHERE user_id = ? AND run_id = ?::uuid",
                String.class,
                fixture.userId(),
                fixture.active().runId()))
        .isEqualTo("SETTLED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM expedition_score WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                fixture.userId(),
                fixture.active().runId()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT score FROM expedition_verified_attempt"
                    + " WHERE user_id = ? AND run_id = ?::uuid AND problem_id = ?",
                Integer.class,
                fixture.userId(),
                fixture.active().runId(),
                fixture.problemId()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT reward_eligible FROM expedition_verified_attempt"
                    + " WHERE user_id = ? AND run_id = ?::uuid AND problem_id = ?",
                Boolean.class,
                fixture.userId(),
                fixture.active().runId(),
                fixture.problemId()))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT settlement_passed FROM expedition_verified_attempt"
                    + " WHERE user_id = ? AND run_id = ?::uuid AND problem_id = ?",
                Boolean.class,
                fixture.userId(),
                fixture.active().runId(),
                fixture.problemId()))
        .isTrue();
    verifyNoInteractions(fixture.events());
  }

  @Test
  void revokedPassDoesNotPromotePreviouslyTrustedFailureToRewards() {
    RevokedSettlementFixture fixture = createRevokedSettlementFixture();
    String fingerprint =
        BossService.expeditionAttemptFingerprint(
            BossService.questionFingerprint(fixture.problem()), fixture.nodeId(), null);
    jdbc.update(
        "INSERT INTO expedition_verified_attempt"
            + " (user_id, run_id, problem_id, question_fingerprint, passed, score, reward_eligible)"
            + " VALUES (?, ?::uuid, ?, ?, FALSE, 0, TRUE)",
        fixture.userId(),
        fixture.active().runId(),
        fixture.problemId(),
        fingerprint);
    when(fixture.graph().evaluateAttempt(
            eq(fixture.problemId()), anyString(), anyList(), anyInt()))
        .thenReturn(
            new BossEvaluateResultDto(fixture.problemId(), true, 1, 1, null, null, List.of()));
    when(fixture.reviews().isApproved(fixture.problem())).thenReturn(false);

    BossAttemptResultDto revokedPass =
        fixture.bossService().evaluateAttempt(
            new BossAttemptRequest(
                fixture.problemId(), 1, 1, "print('output')", 1, UUID.randomUUID()),
            fixture.userId());

    assertThat(revokedPass.passed()).isTrue();
    assertThat(fixture.runs().findActive(fixture.userId()).orElseThrow().verifiedDepth()).isZero();
    assertThat(
            fixture
                .scores()
                .summarizeVerifiedAttempts(fixture.userId(), fixture.active().runId(), 1))
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptSummary(1, 0));
    assertThat(
            jdbc.queryForObject(
                "SELECT passed FROM expedition_verified_attempt"
                    + " WHERE user_id = ? AND run_id = ?::uuid AND problem_id = ?",
                Boolean.class,
                fixture.userId(),
                fixture.active().runId(),
                fixture.problemId()))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT settlement_passed FROM expedition_verified_attempt"
                    + " WHERE user_id = ? AND run_id = ?::uuid AND problem_id = ?",
                Boolean.class,
                fixture.userId(),
                fixture.active().runId(),
                fixture.problemId()))
        .isTrue();

    ExpeditionRunDto settled =
        fixture.runs().performAction(
            fixture.userId(),
            new ExpeditionRunActionRequest(
                fixture.active().runId(),
                fixture.nodeId(),
                ExpeditionRunAction.SETTLE_CHALLENGE,
                null,
                fixture.problemId()));
    assertThat(settled.phase()).isEqualTo("map");
    assertThat(
            fixture
                .scores()
                .summarizeVerifiedAttempts(fixture.userId(), fixture.active().runId(), 1))
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptSummary(1, 0));
    verifyNoInteractions(fixture.events());
  }

  @Test
  void startupRecoveryFailsOnlyPendingPrefetchRows() {
    long userId = createStudent();
    jdbc.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, stage, kc_code, difficulty, problem_id, payload, status, attempt_count)"
            + " VALUES (?, 1, 1, 'PY43-KC-001', 1, NULL, NULL, 'PENDING', 1),"
            + " (?, 1, 1, 'PY43-KC-002', 1, 'ready', '{}'::jsonb, 'READY', 1)",
        userId,
        userId);
    EndlessPregenService pregen =
        new EndlessPregenService(mock(BossGraphClient.class), jdbc, new ObjectMapper());

    pregen.recoverAbandonedPendingAtStartup();

    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM pregenerated_problem WHERE user_id = ? AND kc_code = 'PY43-KC-001'",
                String.class,
                userId))
        .isEqualTo("FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM pregenerated_problem WHERE user_id = ? AND kc_code = 'PY43-KC-002'",
                String.class,
                userId))
        .isEqualTo("READY");
    assertThat(
            jdbc.queryForObject(
                "SELECT error_message FROM pregenerated_problem WHERE user_id = ? AND kc_code = 'PY43-KC-001'",
                String.class,
                userId))
        .contains("重启");
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
  void actionCommandsRequireReachabilityAndPersistTheFinishedPhase() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String rootNodeId = (String) active.nodes().getFirst().get("id");
    String lockedNodeId =
        (String)
            active.nodes().stream()
                .filter(node -> ((Number) node.get("layer")).intValue() == 1)
                .findFirst()
                .orElseThrow()
                .get("id");

    assertThatThrownBy(
            () ->
                runs.performAction(
                    userId,
                    new ExpeditionRunActionRequest(
                        active.runId(), lockedNodeId, ExpeditionRunAction.ENTER_NODE, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("不可达");

    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), rootNodeId, ExpeditionRunAction.ENTER_NODE, null));
    assertThat(challenge.phase()).isEqualTo("challenge");
    assertThat(challenge.currentNodeId()).isEqualTo(rootNodeId);

    ExpeditionRunDto finished =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(active.runId(), null, ExpeditionRunAction.FINISH, null));
    assertThat(finished.phase()).isEqualTo("over");
    assertThat(runs.findActive(userId).orElseThrow().phase()).isEqualTo("over");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM expedition_run WHERE user_id = ?", String.class, userId))
        .isEqualTo("ACTIVE");
  }

  @Test
  void restAndEventActionsApplyServerOwnedEffects() {
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());

    long restUserId = createStudent();
    ExpeditionRunDto restRun = startRun(runs, restUserId, 1, false);
    Map<String, Object> restNode = findNode(restRun, "rest");
    seedReachableNode(restUserId, restRun, restNode, "map", 2, 5, List.of("focus_lens"));
    ExpeditionRunDto rested =
        runs.performAction(
            restUserId,
            new ExpeditionRunActionRequest(
                restRun.runId(),
                (String) restNode.get("id"),
                ExpeditionRunAction.ENTER_NODE,
                null));
    assertThat(rested.hp()).isEqualTo(4);
    assertThat(nodeById(rested, (String) restNode.get("id")).get("state")).isEqualTo("cleared");

    RunWithNode review = startRunWithNode(runs, "event");
    long reviewUserId = review.userId();
    ExpeditionRunDto reviewRun = review.run();
    Map<String, Object> eventNode = review.node();
    seedReachableNode(reviewUserId, reviewRun, eventNode, "map", 2, 5, List.of("focus_lens"));
    ExpeditionRunDto event =
        runs.performAction(
            reviewUserId,
            new ExpeditionRunActionRequest(
                reviewRun.runId(),
                (String) eventNode.get("id"),
                ExpeditionRunAction.ENTER_NODE,
                null));
    assertThat(event.phase()).isEqualTo("event");
    ExpeditionRunDto reviewed =
        runs.performAction(
            reviewUserId,
            new ExpeditionRunActionRequest(
                reviewRun.runId(),
                (String) eventNode.get("id"),
                ExpeditionRunAction.EVENT_REVIEW,
                null));
    assertThat(reviewed.phase()).isEqualTo("map");
    int expectedReviewHp = Math.min(reviewed.maxHp(), event.hp() + 2);
    if (((Number) eventNode.get("layer")).intValue() == Math.floorDiv(reviewRun.layers() - 1, 2)) {
      expectedReviewHp = Math.min(reviewed.maxHp(), expectedReviewHp + 1);
    }
    assertThat(reviewed.hp()).isEqualTo(expectedReviewHp);
    assertThat(nodeById(reviewed, (String) eventNode.get("id")).get("state")).isEqualTo("cleared");

    for (ExpeditionRunAction choice :
        List.of(ExpeditionRunAction.EVENT_STUDY, ExpeditionRunAction.EVENT_STEADY)) {
      RunWithNode runWithEvent = startRunWithNode(runs, "event");
      long userId = runWithEvent.userId();
      ExpeditionRunDto run = runWithEvent.run();
      Map<String, Object> node = runWithEvent.node();
      seedReachableNode(userId, run, node, "map", 3, 5, List.of());
      ExpeditionRunDto entered =
          runs.performAction(
              userId,
              new ExpeditionRunActionRequest(
                  run.runId(), (String) node.get("id"), ExpeditionRunAction.ENTER_NODE, null));
      assertThat(entered.phase()).isEqualTo("event");
      ExpeditionRunDto challenged =
          runs.performAction(
              userId,
              new ExpeditionRunActionRequest(run.runId(), (String) node.get("id"), choice, null));
      assertThat(challenged.phase()).isEqualTo("challenge");
      assertThat(challenged.currentNodeId()).isEqualTo(node.get("id"));
    }
  }

  @Test
  void clearingOneBranchFailsPendingAndReadyPrefetchesForBypassedSiblings() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    Map<String, Object> chosen =
        active.nodes().stream()
            .filter(node -> ((Number) node.get("layer")).intValue() == 1)
            .findFirst()
            .orElseThrow();
    seedReachableNode(userId, active, chosen, "challenge", 5, 5, List.of());
    List<Map<String, Object>> nodes =
        active.nodes().stream().<Map<String, Object>>map(HashMap::new).toList();
    nodes.stream()
        .filter(node -> ((Number) node.get("layer")).intValue() == 1)
        .forEach(node -> node.put("state", "available"));
    try {
      jdbc.update(
          "UPDATE expedition_run SET nodes = ?::jsonb WHERE user_id = ?",
          new ObjectMapper().writeValueAsString(nodes),
          userId);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new AssertionError(e);
    }
    ExpeditionRunDto beforeSettlement = runs.findActive(userId).orElseThrow();
    List<Map<String, Object>> branches =
        beforeSettlement.nodes().stream()
            .filter(node -> "available".equals(node.get("state")))
            .filter(node -> ((Number) node.get("layer")).intValue() == 1)
            .toList();
    assertThat(branches).hasSizeGreaterThan(1);
    for (int index = 0; index < branches.size(); index++) {
      Map<String, Object> branch = branches.get(index);
      String nodeId = (String) branch.get("id");
      String status = index == 0 ? "PENDING" : "READY";
      jdbc.update(
          "INSERT INTO pregenerated_problem"
              + " (user_id, chapter, stage, kc_code, kind, difficulty, problem_id, payload,"
              + " status, attempt_count, expedition_run_id, expedition_node_id)"
              + " VALUES (?, 1, 1, ?, ?, 1, ?, '{}'::jsonb, ?, 1, ?::uuid, ?)",
          userId,
          branch.get("kcCode"),
          branch.get("challengeKind"),
          status.equals("READY") ? "ready-" + nodeId : null,
          status,
          active.runId(),
          nodeId);
    }
    for (int index = branches.size(); index < 4; index++) {
      jdbc.update(
          "INSERT INTO pregenerated_problem"
              + " (user_id, chapter, stage, kc_code, kind, difficulty, status, attempt_count)"
              + " VALUES (?, 1, 1, ?, 'dual_track', 1, 'PENDING', 1)",
          userId,
          "PY43-KC-0" + (20 + index));
    }

    String chosenId = (String) chosen.get("id");
    String chosenProblemId = "branch-chosen-" + UUID.randomUUID();
    insertIssuedProblem(
        userId,
        active.runId(),
        chosenId,
        chosenProblemId,
        (String) chosen.get("kcCode"),
        "d".repeat(64),
        true,
        80);

    ExpeditionRunDto afterChosen =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                beforeSettlement.runId(),
                chosenId,
                ExpeditionRunAction.SETTLE_CHALLENGE,
                null,
                chosenProblemId));

    List<String> bypassedIds =
        afterChosen.nodes().stream()
            .filter(node -> "bypassed".equals(node.get("state")))
            .map(node -> (String) node.get("id"))
            .toList();
    assertThat(bypassedIds).isNotEmpty();
    for (String bypassedId : bypassedIds) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM pregenerated_problem"
                      + " WHERE user_id = ? AND expedition_run_id = ?::uuid"
                      + " AND expedition_node_id = ?",
                  String.class,
                  userId,
                  active.runId(),
                  bypassedId))
          .isEqualTo("FAILED");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pregenerated_problem"
                    + " WHERE user_id = ? AND status IN ('PENDING', 'READY')",
                Integer.class,
                userId))
        .isLessThan(4);

    Map<String, Object> nextChallenge =
        afterChosen.nodes().stream()
            .filter(node -> ((Number) node.get("layer")).intValue() == 2)
            .filter(node -> "available".equals(node.get("state")))
            .filter(node -> node.get("kcCode") != null && node.get("challengeKind") != null)
            .findFirst()
            .orElseThrow();
    var nextPrefetch =
        new EndlessPregenService(null, jdbc, new ObjectMapper(), runs)
            .beginPrefetch(
                userId,
                1,
                1,
                (String) nextChallenge.get("kcCode"),
                ((Number) nextChallenge.get("difficulty")).intValue(),
                (String) nextChallenge.get("challengeKind"),
                null,
                active.runId(),
                (String) nextChallenge.get("id"));
    assertThat(nextPrefetch.shouldGenerate()).isTrue();
    assertThat(nextPrefetch.status().status()).isEqualTo("PENDING");
  }

  @Test
  void resumedChallengeAndEventAcceptLegacyCurrentNodeState() {
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());

    long challengeUserId = createStudent();
    ExpeditionRunDto challengeRun = startRun(runs, challengeUserId, 1, false);
    String challengeNodeId = (String) challengeRun.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            challengeUserId,
            new ExpeditionRunActionRequest(
                challengeRun.runId(), challengeNodeId, ExpeditionRunAction.ENTER_NODE, null));
    markNodeCurrent(challengeUserId, challenge, challengeNodeId);

    ExpeditionRunDto resumedChallenge = runs.findActive(challengeUserId).orElseThrow();
    ExpeditionRunDto reenteredChallenge =
        runs.performAction(
            challengeUserId,
            new ExpeditionRunActionRequest(
                challengeRun.runId(), challengeNodeId, ExpeditionRunAction.ENTER_NODE, null));
    assertThat(reenteredChallenge.phase()).isEqualTo("challenge");
    assertThat(reenteredChallenge.nodes()).isEqualTo(resumedChallenge.nodes());
    assertThat(nodeById(reenteredChallenge, challengeNodeId).get("state")).isEqualTo("current");
    org.assertj.core.api.Assertions.assertThatNoException()
        .isThrownBy(
            () ->
                runs.validateProblemRequest(
                    challengeUserId,
                    challengeRun.runId(),
                    challengeNodeId,
                    1,
                    false,
                    (String) nodeById(reenteredChallenge, challengeNodeId).get("kcCode")));

    long eventUserId = createStudent();
    ExpeditionRunDto eventRun = startRun(runs, eventUserId, 1, false);
    Map<String, Object> eventNode = findNode(eventRun, "event");
    seedReachableNode(eventUserId, eventRun, eventNode, "map", 3, 5, List.of());
    ExpeditionRunDto map = runs.findActive(eventUserId).orElseThrow();
    ExpeditionRunDto event =
        runs.performAction(
            eventUserId,
            new ExpeditionRunActionRequest(
                map.runId(), (String) eventNode.get("id"), ExpeditionRunAction.ENTER_NODE, null));
    markNodeCurrent(eventUserId, event, event.currentNodeId());

    ExpeditionRunDto resumedEvent = runs.findActive(eventUserId).orElseThrow();
    ExpeditionRunDto reenteredEvent =
        runs.performAction(
            eventUserId,
            new ExpeditionRunActionRequest(
                eventRun.runId(), event.currentNodeId(), ExpeditionRunAction.ENTER_NODE, null));
    assertThat(reenteredEvent.phase()).isEqualTo("event");
    assertThat(reenteredEvent.nodes()).isEqualTo(resumedEvent.nodes());
    assertThat(nodeById(reenteredEvent, event.currentNodeId()).get("state")).isEqualTo("current");
  }

  @Test
  void rewardActionOnlyClaimsKnownUnownedRelicsAndAppliesSturdyNotes() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    Map<String, Object> chestNode = findRewardNode(active);
    List<String> sixRelics =
        List.of(
            "calm_mind",
            "combo_scholar",
            "second_wind",
            "focus_lens",
            "quick_thinker",
            "first_aid");
    seedReachableNode(userId, active, chestNode, "reward", 2, 5, sixRelics);

    assertThatThrownBy(
            () ->
                runs.performAction(
                    userId,
                    new ExpeditionRunActionRequest(
                        active.runId(),
                        (String) chestNode.get("id"),
                        ExpeditionRunAction.CLAIM_RELIC,
                        "not-a-relic")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("遗物");

    ExpeditionRunDto claimed =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(),
                (String) chestNode.get("id"),
                ExpeditionRunAction.CLAIM_RELIC,
                "sturdy_notes"));
    assertThat(claimed.phase()).isEqualTo("map");
    assertThat(claimed.relics()).hasSize(7).contains("sturdy_notes");
    assertThat(claimed.maxHp()).isEqualTo(6);
    assertThat(claimed.hp()).isEqualTo(3);
  }

  @Test
  void rewardCandidatesAreServerIssuedAndUnofferedRelicsCannotBeClaimed() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto started = startRun(runs, userId, 1, false);
    Map<String, Object> chestNode = findRewardNode(started);
    String nodeId = (String) chestNode.get("id");
    seedReachableNode(userId, started, chestNode, "reward", 2, 5, List.of());

    ExpeditionRunDto reward = runs.findActive(userId).orElseThrow();
    Object rawChoices = nodeById(reward, nodeId).get("rewardChoices");
    assertThat(rawChoices).isInstanceOf(List.class);
    List<?> offeredChoices = (List<?>) rawChoices;
    assertThat(offeredChoices).hasSize(3);
    assertThat(offeredChoices).allMatch(String.class::isInstance);
    List<String> choices = offeredChoices.stream().map(String.class::cast).toList();
    assertThat(nodeById(runs.findActive(userId).orElseThrow(), nodeId).get("rewardChoices"))
        .isEqualTo(choices);
    String offered = choices.getFirst();
    String unoffered =
        List.of(
                "sturdy_notes",
                "calm_mind",
                "combo_scholar",
                "second_wind",
                "focus_lens",
                "quick_thinker",
                "first_aid")
            .stream()
            .filter(id -> !choices.contains(id))
            .findFirst()
            .orElseThrow();

    assertThatThrownBy(
            () ->
                runs.performAction(
                    userId,
                    new ExpeditionRunActionRequest(
                        reward.runId(), nodeId, ExpeditionRunAction.CLAIM_RELIC, unoffered)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("遗物");

    ExpeditionRunDto claimed =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                reward.runId(), nodeId, ExpeditionRunAction.CLAIM_RELIC, offered));
    assertThat(claimed.phase()).isEqualTo("map");
    assertThat(claimed.relics()).containsExactly(offered);
  }

  @Test
  void snapshotPutReturnsTheCanonicalStateInsteadOfReplayingClientProgress() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    ExpeditionRunDto changed =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(),
                (String) active.nodes().getFirst().get("id"),
                ExpeditionRunAction.ENTER_NODE,
                null));
    List<Map<String, Object>> spoofedNodes =
        changed.nodes().stream().<Map<String, Object>>map(HashMap::new).toList();
    spoofedNodes.getFirst().put("state", "cleared");
    ExpeditionRunDto spoofed =
        new ExpeditionRunDto(
            "map",
            changed.focusChapter(),
            spoofedNodes,
            changed.layers(),
            null,
            1,
            5,
            12,
            12,
            999_999,
            List.of("sturdy_notes"),
            changed.isDaily(),
            List.of(),
            changed.runId());

    ExpeditionRunDto canonical = runs.save(userId, spoofed);

    assertThat(canonical.phase()).isEqualTo("challenge");
    assertThat(canonical.currentNodeId()).isEqualTo(changed.currentNodeId());
    assertThat(canonical.hp()).isEqualTo(changed.hp());
    assertThat(canonical.momentum()).isEqualTo(changed.momentum());
    assertThat(canonical.score()).isZero();
    assertThat(canonical.relics()).isEmpty();
    assertThat(canonical.nodes()).isEqualTo(changed.nodes());
  }

  @Test
  void problemRequestsMustMatchTheCurrentOrReachableServerNode() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String rootNodeId = (String) active.nodes().getFirst().get("id");
    String rootKcCode = (String) active.nodes().getFirst().get("kcCode");
    Map<String, Object> lockedNode =
        active.nodes().stream()
            .filter(node -> ((Number) node.get("layer")).intValue() == 1)
            .findFirst()
            .orElseThrow();

    org.assertj.core.api.Assertions.assertThatNoException()
        .isThrownBy(
            () ->
                runs.validateProblemRequest(
                    userId, active.runId(), rootNodeId, 1, true, rootKcCode));
    assertThatThrownBy(
            () ->
                runs.validateProblemRequest(
                    userId,
                    active.runId(),
                    (String) lockedNode.get("id"),
                    1,
                    true,
                    (String) lockedNode.get("kcCode")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("绑定");
    assertThatThrownBy(
            () ->
                runs.validateProblemRequest(
                    userId, active.runId(), rootNodeId, 1, true, "PY43-KC-065"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("知识点");

    runs.performAction(
        userId,
        new ExpeditionRunActionRequest(
            active.runId(), rootNodeId, ExpeditionRunAction.ENTER_NODE, null));
    org.assertj.core.api.Assertions.assertThatNoException()
        .isThrownBy(
            () ->
                runs.validateProblemRequest(
                    userId, active.runId(), rootNodeId, 1, false, rootKcCode));
    assertThatThrownBy(
            () ->
                runs.validateProblemRequest(
                    userId,
                    active.runId(),
                    (String) lockedNode.get("id"),
                    1,
                    false,
                    (String) lockedNode.get("kcCode")))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void generatedProblemRequestsAreBoundedPerNodeAndBossPhase() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    Map<String, Object> root = active.nodes().getFirst();
    String rootNodeId = (String) root.get("id");
    String rootKc = (String) root.get("kcCode");

    List<Long> rootReservations =
        java.util.stream.IntStream.range(0, 3)
            .mapToObj(i -> runs.reserveProblemGeneration(userId, active.runId(), rootNodeId, 1, true, rootKc))
            .toList();
    assertThat(rootReservations).doesNotHaveDuplicates();
    assertThatThrownBy(
            () -> runs.reserveProblemGeneration(userId, active.runId(), rootNodeId, 1, true, rootKc))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("上限");

    long bossUserId = createStudent();
    ExpeditionRunDto bossRun = startRun(runs, bossUserId, 1, false);
    Map<String, Object> boss = findNode(bossRun, "boss");
    String bossNodeId = (String) boss.get("id");
    String bossKc = (String) boss.get("kcCode");
    seedReachableNode(bossUserId, bossRun, boss, "challenge", 5, 5, List.of());

    for (int phase = 1; phase <= 2; phase++) {
      ExpeditionRunDto currentBossRun = runs.findActive(bossUserId).orElseThrow();
      setBossPhase(bossUserId, currentBossRun, bossNodeId, phase);
      int expectedPhase = phase;
      List<Long> reservations =
          java.util.stream.IntStream.range(0, 3)
              .mapToObj(
                  i ->
                      runs.reserveProblemGeneration(
                          bossUserId, bossRun.runId(), bossNodeId, 1, false, bossKc))
              .toList();
      assertThat(reservations).doesNotHaveDuplicates();
      assertThatThrownBy(
              () ->
                  runs.reserveProblemGeneration(
                      bossUserId, bossRun.runId(), bossNodeId, 1, false, bossKc))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("上限");
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM expedition_problem_generation_attempt"
                      + " WHERE user_id = ? AND run_id = ?::uuid AND node_id = ?"
                      + " AND boss_phase = ?",
                  Integer.class,
                  bossUserId,
                  bossRun.runId(),
                  bossNodeId,
                  expectedPhase))
          .isEqualTo(3);
    }

    long cappedUserId = createStudent();
    ExpeditionRunDto cappedRun = startRun(runs, cappedUserId, 1, false);
    for (int i = 0; i < 36; i++) {
      jdbc.update(
          "INSERT INTO expedition_problem_generation_attempt"
              + " (user_id, run_id, node_id, boss_phase) VALUES (?, ?::uuid, ?, NULL)",
          cappedUserId,
          cappedRun.runId(),
          "legacy-" + i);
    }
    Map<String, Object> cappedRoot = cappedRun.nodes().getFirst();
    assertThatThrownBy(
            () ->
                runs.reserveProblemGeneration(
                    cappedUserId,
                    cappedRun.runId(),
                    (String) cappedRoot.get("id"),
                    1,
                    true,
                    (String) cappedRoot.get("kcCode")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("本局");
  }

  @Test
  void problemDifficultyUsesPersistedRunMomentumAndRecentVerifiedHistory() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    Map<String, Object> root = active.nodes().getFirst();
    String nodeId = (String) root.get("id");
    String kcCode = (String) root.get("kcCode");
    int mapDifficulty = ((Number) root.get("difficulty")).intValue();
    jdbc.update(
        "UPDATE expedition_run SET momentum = 6, history = '[{\"passed\":false},"
            + "{\"passed\":false}]'::jsonb WHERE user_id = ? AND run_id = ?::uuid",
        userId,
        active.runId());

    ExpeditionRunDto forgedClientSnapshot =
        new ExpeditionRunDto(
            active.phase(),
            active.focusChapter(),
            active.nodes(),
            active.layers(),
            active.currentNodeId(),
            active.hp(),
            active.maxHp(),
            0,
            0,
            999_999,
            active.relics(),
            active.isDaily(),
            active.history(),
            active.runId(),
            999_999);
    ExpeditionRunDto authoritative = runs.save(userId, forgedClientSnapshot);

    var policy = runs.resolveProblemRequest(userId, active.runId(), nodeId, 1, true, kcCode);

    assertThat(authoritative.momentum()).isEqualTo(6);
    assertThat(authoritative.score()).isZero();
    assertThat(policy.difficulty()).isEqualTo(Math.max(1, Math.min(5, mapDifficulty + 1)));
  }

  @Test
  void eventStudyBonusIsStoredByTheServerAndAppliedToItsChallenge() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    Map<String, Object> root = active.nodes().getFirst();
    String nodeId = (String) root.get("id");
    String kcCode = (String) root.get("kcCode");
    int mapDifficulty = ((Number) root.get("difficulty")).intValue();
    jdbc.update(
        "UPDATE expedition_run SET nodes = jsonb_set(nodes, '{0,kind}', '\"event\"'::jsonb)"
            + " WHERE user_id = ? AND run_id = ?::uuid",
        userId,
        active.runId());

    runs.performAction(
        userId,
        new ExpeditionRunActionRequest(
            active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.EVENT_STUDY, null));
    var policy = runs.resolveProblemRequest(userId, active.runId(), nodeId, 1, false, kcCode);

    assertThat(challenge.nodes().getFirst().get("eventDifficultyBonus")).isEqualTo(1);
    assertThat(policy.difficulty()).isEqualTo(Math.min(5, mapDifficulty + 1));
  }

  @Test
  void challengeSettlementUsesTheBoundVerifiedAttemptAndIsIdempotent() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    String kcCode = (String) active.nodes().getFirst().get("kcCode");
    runs.performAction(
        userId,
        new ExpeditionRunActionRequest(
            active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));

    String wrongNodeProblemId = "wrong-node-" + UUID.randomUUID();
    String validProblemId = "valid-problem-" + UUID.randomUUID();
    insertIssuedProblem(
        userId, active.runId(), "L1-0", wrongNodeProblemId, kcCode, "c".repeat(64), false, 0);
    insertIssuedProblem(
        userId, active.runId(), nodeId, validProblemId, kcCode, "d".repeat(64), true, 70);

    assertThatThrownBy(
            () ->
                runs.performAction(
                    userId,
                    new ExpeditionRunActionRequest(
                        active.runId(),
                        nodeId,
                        ExpeditionRunAction.SETTLE_CHALLENGE,
                        null,
                        wrongNodeProblemId)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("绑定");

    ExpeditionRunDto settled =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(),
                nodeId,
                ExpeditionRunAction.SETTLE_CHALLENGE,
                null,
                validProblemId));
    assertThat(settled.phase()).isEqualTo("map");
    assertThat(settled.score()).isEqualTo(70);
    assertThat(settled.momentum()).isEqualTo(1);
    assertThat(nodeById(settled, nodeId).get("state")).isEqualTo("cleared");

    ExpeditionRunDto repeated =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(),
                nodeId,
                ExpeditionRunAction.SETTLE_CHALLENGE,
                null,
                validProblemId));
    assertThat(repeated.momentum()).isEqualTo(1);
    assertThat(repeated.nodes()).isEqualTo(settled.nodes());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM expedition_challenge_settlement"
                    + " WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                userId,
                active.runId()))
        .isEqualTo(1);
  }

  @Test
  void focusedRunCanSettleAChallengeForItsEarlierChapterPrerequisite() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active =
        runs.start(userId, 2, false, List.of(new FrontierPickDto("PY43-KC-004", 1, 2, "先修知识点")));
    Map<String, Object> node = active.nodes().getFirst();
    String nodeId = (String) node.get("id");
    String problemId = "prerequisite-problem-" + UUID.randomUUID();
    runs.performAction(
        userId,
        new ExpeditionRunActionRequest(
            active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    insertIssuedProblem(
        userId, active.runId(), nodeId, problemId, "PY43-KC-004", "e".repeat(64), true, 70);

    ExpeditionRunDto settled =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.SETTLE_CHALLENGE, null, problemId));

    assertThat(settled.phase()).isEqualTo("map");
    assertThat(nodeById(settled, nodeId).get("state")).isEqualTo("cleared");
  }

  @Test
  void issuedQuestionFingerprintsAreScopedToTheAuthenticatedRun() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String fingerprint = "a".repeat(64);
    insertIssuedProblem(
        userId, active.runId(), "L0-0", "issued-question", "PY43-KC-001", fingerprint, true, 80);

    assertThat(runs.findIssuedQuestionFingerprints(userId, active.runId()))
        .containsExactly(fingerprint);
    assertThat(runs.findIssuedQuestionFingerprints(userId + 1, active.runId())).isEmpty();
    assertThat(runs.findIssuedQuestionFingerprints(userId, "not-a-uuid")).isEmpty();
  }

  @Test
  void excludesRecentQuestionsAcrossRunsButExpiresOlderFingerprints() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto previous = startRun(runs, userId, 1, false);
    String recentFingerprint = "c".repeat(64);
    String expiredFingerprint = "e".repeat(64);
    insertConsumedProblem(
        userId, previous.runId(), "L0-0", "recent-question", "PY43-KC-001", recentFingerprint);
    insertConsumedProblem(
        userId, previous.runId(), "L0-1", "expired-question", "PY43-KC-001", expiredFingerprint);
    jdbc.update(
        "UPDATE pregenerated_problem SET consumed_at = now() - interval '8 days'"
            + " WHERE user_id = ? AND problem_id = 'expired-question'",
        userId);

    runs.abandon(userId);
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String currentFingerprint = "f".repeat(64);
    insertConsumedProblem(
        userId, active.runId(), "L0-0", "current-question", "PY43-KC-001", currentFingerprint);

    assertThat(runs.findIssuedQuestionFingerprints(userId, active.runId()))
        .containsExactlyInAnyOrder(recentFingerprint, currentFingerprint)
        .doesNotContain(expiredFingerprint);
  }

  @Test
  void excludesRecentPracticeQuestionFingerprintsWhenThereIsNoExpeditionRun() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    String fingerprint = "b".repeat(64);
    jdbc.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, stage, kc_code, problem_id, payload, status, consumed_at,"
            + " question_fingerprint)"
            + " VALUES (?, 1, 1, 'PY43-KC-004', 'practice-question', '{}'::jsonb, 'CONSUMED',"
            + " now(), ?)",
        userId,
        fingerprint);

    assertThat(runs.findIssuedQuestionFingerprints(userId, null)).containsExactly(fingerprint);
    assertThat(runs.findIssuedQuestionFingerprints(userId + 1, null)).isEmpty();
  }

  @Test
  void bossNodeAdvancesOnlyThroughThreeVerifiedServerPhases() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    Map<String, Object> boss = findNode(active, "boss");
    seedReachableNode(userId, active, boss, "map", 5, 5, List.of());
    String bossNodeId = (String) boss.get("id");
    ExpeditionRunDto mapState = runs.findActive(userId).orElseThrow();
    ExpeditionRunDto entered =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                mapState.runId(), bossNodeId, ExpeditionRunAction.ENTER_NODE, null));
    assertThat(nodeById(entered, bossNodeId).get("bossPhase")).isEqualTo(1);

    ExpeditionRunDto state = entered;
    for (int phase = 1; phase <= 3; phase++) {
      assertThat(
              runs.validateProblemRequest(
                  userId, active.runId(), bossNodeId, 1, false, (String) boss.get("kcCode")))
          .isEqualTo(phase);
      String problemId = "issue-boss-phase-" + phase + "-" + UUID.randomUUID();
      insertIssuedProblem(
          userId,
          active.runId(),
          bossNodeId,
          problemId,
          (String) boss.get("kcCode"),
          "f".repeat(64),
          BossService.expeditionAttemptFingerprint("f".repeat(64), bossNodeId, phase),
          true,
          80);
      state =
          runs.performAction(
              userId,
              new ExpeditionRunActionRequest(
                  active.runId(),
                  bossNodeId,
                  ExpeditionRunAction.SETTLE_CHALLENGE,
                  null,
                  problemId));
      if (phase < 3) {
        assertThat(state.phase()).isEqualTo("challenge");
        assertThat(nodeById(state, bossNodeId).get("bossPhase")).isEqualTo(phase + 1);
        assertThat(
                runs.validateProblemRequest(
                    userId, active.runId(), bossNodeId, 1, false, (String) boss.get("kcCode")))
            .isEqualTo(phase + 1);
      }
    }
    assertThat(state.phase()).isEqualTo("over");
    assertThat(nodeById(state, bossNodeId).get("state")).isEqualTo("cleared");

    scoreService().record(userId, new ExpeditionScoreSubmitDto(active.runId()));
    assertThat(
            jdbc.queryForObject(
                "SELECT cleared FROM expedition_score WHERE user_id = ? AND run_id = ?::uuid",
                Boolean.class,
                userId,
                active.runId()))
        .isTrue();
  }

  @Test
  void eliteSuccessReturnsToMapWhenAllRelicsAreAlreadyOwned() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    Map<String, Object> eliteNode = findNode(active, "elite");
    List<String> allRelics =
        List.of(
            "sturdy_notes",
            "calm_mind",
            "combo_scholar",
            "second_wind",
            "focus_lens",
            "quick_thinker",
            "first_aid");
    seedReachableNode(userId, active, eliteNode, "challenge", 4, 5, allRelics);
    String nodeId = (String) eliteNode.get("id");
    String problemId = "elite-full-relics-" + UUID.randomUUID();
    insertIssuedProblem(
        userId,
        active.runId(),
        nodeId,
        problemId,
        (String) eliteNode.get("kcCode"),
        "f".repeat(64),
        true,
        85);

    ExpeditionRunDto settled =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.SETTLE_CHALLENGE, null, problemId));

    assertThat(settled.phase()).isEqualTo("map");
    assertThat(settled.relics()).containsExactlyElementsOf(allRelics);
    assertThat(nodeById(settled, nodeId).get("state")).isEqualTo("cleared");
  }

  @Test
  void failedChallengeDamageDoesNotDependOnEmotionProfile() {
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());

    long lowResilienceUserId = createStudent();
    setResilience(lowResilienceUserId, 0.2);
    ExpeditionRunDto lowRun = startRun(runs, lowResilienceUserId, 1, false);
    String lowNodeId = (String) lowRun.nodes().getFirst().get("id");
    jdbc.update(
        "UPDATE expedition_run SET momentum = 5, relics = '[\"second_wind\"]'::jsonb"
            + " WHERE user_id = ?",
        lowResilienceUserId);
    runs.performAction(
        lowResilienceUserId,
        new ExpeditionRunActionRequest(
            lowRun.runId(), lowNodeId, ExpeditionRunAction.ENTER_NODE, null));
    String lowProblemId = "low-resilience-" + UUID.randomUUID();
    insertIssuedProblem(
        lowResilienceUserId,
        lowRun.runId(),
        lowNodeId,
        lowProblemId,
        (String) lowRun.nodes().getFirst().get("kcCode"),
        "1".repeat(64),
        false,
        0);

    ExpeditionRunDto lowFailure =
        runs.performAction(
            lowResilienceUserId,
            new ExpeditionRunActionRequest(
                lowRun.runId(),
                lowNodeId,
                ExpeditionRunAction.SETTLE_CHALLENGE,
                null,
                lowProblemId));

    assertThat(lowFailure.hp()).isEqualTo(3);
    assertThat(lowFailure.momentum()).isEqualTo(2);

    long highResilienceUserId = createStudent();
    setResilience(highResilienceUserId, 0.8);
    ExpeditionRunDto highRun = startRun(runs, highResilienceUserId, 1, false);
    String highNodeId = (String) highRun.nodes().getFirst().get("id");
    jdbc.update("UPDATE expedition_run SET momentum = 5 WHERE user_id = ?", highResilienceUserId);
    runs.performAction(
        highResilienceUserId,
        new ExpeditionRunActionRequest(
            highRun.runId(), highNodeId, ExpeditionRunAction.ENTER_NODE, null));
    String highProblemId = "high-resilience-" + UUID.randomUUID();
    insertIssuedProblem(
        highResilienceUserId,
        highRun.runId(),
        highNodeId,
        highProblemId,
        (String) highRun.nodes().getFirst().get("kcCode"),
        "2".repeat(64),
        false,
        0);

    ExpeditionRunDto highFailure =
        runs.performAction(
            highResilienceUserId,
            new ExpeditionRunActionRequest(
                highRun.runId(),
                highNodeId,
                ExpeditionRunAction.SETTLE_CHALLENGE,
                null,
                highProblemId));

    assertThat(highFailure.hp()).isEqualTo(3);
    assertThat(highFailure.momentum()).isZero();
  }

  @Test
  void failedChallengeSettlementAppliesServerHpAndFirstAidOnce() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    String kcCode = (String) active.nodes().getFirst().get("kcCode");
    jdbc.update(
        "UPDATE expedition_run SET relics = '[\"first_aid\"]'::jsonb WHERE user_id = ?", userId);
    runs.performAction(
        userId,
        new ExpeditionRunActionRequest(
            active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    String problemId = "failed-problem-" + UUID.randomUUID();
    insertIssuedProblem(
        userId, active.runId(), nodeId, problemId, kcCode, "e".repeat(64), false, 0);

    ExpeditionRunDto failed =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.SETTLE_CHALLENGE, null, problemId));
    assertThat(failed.phase()).isEqualTo("challenge");
    assertThat(failed.hp()).isEqualTo(5);
    assertThat(nodeById(failed, nodeId).get("state")).isEqualTo("available");
    assertThat(failed.history().getFirst().get("passed")).isEqualTo(false);

    ExpeditionRunDto repeated =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.SETTLE_CHALLENGE, null, problemId));
    assertThat(repeated.hp()).isEqualTo(5);
  }

  @Test
  void expeditionLearningEvidenceStopsAfterFirstFailureThenFirstPass() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    insertConsumedProblem(
        userId,
        challenge.runId(),
        nodeId,
        "issued-problem",
        (String) nodeById(challenge, nodeId).get("kcCode"),
        "b".repeat(64));
    ExpeditionScoreService scores = scoreService();
    String fingerprint = "b".repeat(64);
    String attemptFingerprint =
        BossService.expeditionAttemptFingerprint(fingerprint, nodeId, null);

    var firstFailure =
        scores.recordVerifiedAttempt(
            userId,
            challenge.runId(),
            nodeId,
            null,
            "issued-problem",
            attemptFingerprint,
            false,
            0);
    ExpeditionRunDto afterFailure = runs.findActive(userId).orElseThrow();
    var repeatedFailure =
        scores.recordVerifiedAttempt(
            userId,
            challenge.runId(),
            nodeId,
            null,
            "issued-problem",
            attemptFingerprint,
            false,
            0);
    insertConsumedProblem(
        userId,
        challenge.runId(),
        nodeId,
        "issued-problem-retry",
        (String) nodeById(challenge, nodeId).get("kcCode"),
        fingerprint);
    var firstPass =
        scores.recordVerifiedAttempt(
            userId,
            challenge.runId(),
            nodeId,
            null,
            "issued-problem-retry",
            attemptFingerprint,
            true,
            100);
    var settledPass =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                challenge.runId(),
                nodeId,
                ExpeditionRunAction.SETTLE_CHALLENGE,
                null,
                "issued-problem-retry"));
    var repeatedPass =
        scores.recordVerifiedAttempt(
            userId,
            challenge.runId(),
            nodeId,
            null,
            "issued-problem-retry",
            attemptFingerprint,
            true,
            100);
    var failureAfterPass =
        scores.recordVerifiedAttempt(
            userId,
            challenge.runId(),
            nodeId,
            null,
            "issued-problem-retry",
            attemptFingerprint,
            false,
            0);

    assertThat(firstFailure)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    assertThat(repeatedFailure)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(false, false));
    assertThat(firstPass).isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    assertThat(settledPass.hp()).isEqualTo(afterFailure.hp());
    assertThat(nodeById(settledPass, nodeId).get("state")).isEqualTo("cleared");
    assertThat(repeatedPass)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(false, false));
    assertThat(failureAfterPass)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(false, false));
    assertThat(
            jdbc.queryForObject(
                "SELECT passed FROM expedition_verified_attempt WHERE user_id = ? AND run_id = ?::uuid",
                Boolean.class,
                userId,
                challenge.runId()))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT score FROM expedition_verified_attempt WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                userId,
                challenge.runId()))
        .isEqualTo(100);
  }

  @Test
  void onlyOneUnsettledProblemCanBeVerifiedPerCurrentNode() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    String kcCode = (String) nodeById(challenge, nodeId).get("kcCode");
    insertConsumedProblem(userId, challenge.runId(), nodeId, "issue-first", kcCode, "1".repeat(64));
    insertConsumedProblem(
        userId, challenge.runId(), nodeId, "issue-second", kcCode, "2".repeat(64));
    ExpeditionScoreService scores = scoreService();

    var olderIssue =
        scores.recordVerifiedAttempt(
            userId, challenge.runId(), nodeId, null, "issue-first", "a".repeat(64), true, 80);
    var currentIssue =
        scores.recordVerifiedAttempt(
            userId, challenge.runId(), nodeId, null, "issue-second", "b".repeat(64), true, 90);

    assertThat(olderIssue)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(false, false));
    assertThat(currentIssue)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM expedition_verified_attempt WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                userId,
                challenge.runId()))
        .isEqualTo(1);
  }

  @Test
  void expeditionAttemptAndTwinEvidenceRollBackTogetherWhenLearningEventFails() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    String kcCode = (String) nodeById(challenge, nodeId).get("kcCode");
    String fingerprint = "e".repeat(64);
    insertConsumedProblem(
        userId, challenge.runId(), nodeId, "issue-twin-outbox", kcCode, fingerprint);

    MasteryRepository masteryRepository = new MasteryRepository(jdbc);
    ReviewStateRepository reviewRepository = new ReviewStateRepository(jdbc);
    LearningEventListener listener =
        new LearningEventListener(
            new MasteryEvaluator(
                masteryRepository,
                new MasteryProperties(14, new BigDecimal("0.05"), new BigDecimal("0.02"), Map.of()),
                new ReviewStateService(reviewRepository, new ReviewScheduler())),
            jdbc);
    UUID attemptId = UUID.randomUUID();
    BossAttemptRequest request =
        new BossAttemptRequest("issue-twin-outbox", 1, 1, "print(1)", 10, attemptId);
    BossAttemptIdempotencyService idempotency =
        new BossAttemptIdempotencyService(
            jdbc, new ObjectMapper(), mock(ApplicationEventPublisher.class));
    var firstClaim = transactions.execute(status -> idempotency.claim(userId, attemptId, request));
    AtomicInteger publishCount = new AtomicInteger();
    ApplicationEventPublisher publisher =
        new ApplicationEventPublisher() {
          @Override
          public void publishEvent(Object event) {
            if (publishCount.incrementAndGet() == 1) {
              throw new IllegalStateException("temporary mastery database failure");
            }
            listener.onPracticePass((LearningEvent.PracticeSucceeded) event);
          }

          @Override
          public void publishEvent(ApplicationEvent event) {
            publishEvent((Object) event);
          }
        };
    ExpeditionScoreService scores = new ExpeditionScoreService(jdbc, runs, publisher);
    String idempotencyKey = "boss-attempt:" + userId + ":" + attemptId;
    LearningEvent.PracticeSucceeded event =
        new LearningEvent.PracticeSucceeded(userId, kcCode, null, "medium", null, idempotencyKey);

    assertThatThrownBy(
            () ->
                transactions.execute(
                    status ->
                        scores.recordVerifiedAttempt(
                            userId,
                            challenge.runId(),
                            nodeId, null, "issue-twin-outbox",
                            fingerprint,
                            true,
                            100,
                            event)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("temporary mastery database failure");
    transactions.executeWithoutResult(status -> idempotency.release(userId, attemptId, firstClaim));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM expedition_verified_attempt WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                userId,
                challenge.runId()))
        .isZero();

    var retryClaim = transactions.execute(status -> idempotency.claim(userId, attemptId, request));
    var retried =
        transactions.execute(
            status ->
                scores.recordVerifiedAttempt(
                    userId, challenge.runId(), nodeId, null, "issue-twin-outbox", fingerprint, true, 100, event));

    assertThat(retried).isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    BossAttemptResultDto result =
        new BossAttemptResultDto("issue-twin-outbox", true, 1, 1, null, 100, "PASS");
    transactions.executeWithoutResult(
        status -> idempotency.complete(userId, attemptId, retryClaim, result, null));
    var replay = transactions.execute(status -> idempotency.claim(userId, attemptId, request));
    assertThat(replay.replay()).isEqualTo(result);
    assertThat(publishCount).hasValue(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM kc_mastery_event WHERE user_id = ? AND idempotency_key = ?",
                Integer.class,
                userId,
                idempotencyKey))
        .isEqualTo(1);
    assertThat(masteryRepository.findAssessedKcCodes(userId)).contains(kcCode);
    assertThat(reviewRepository.find(userId, kcCode).orElseThrow().reps()).isEqualTo(1);
  }

  @Test
  void failedAttemptAppliesHpLossBeforeClientSettlementAndPassCannotUndoIt() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    String kcCode = (String) nodeById(challenge, nodeId).get("kcCode");
    String fingerprint = "c".repeat(64);
    String attemptFingerprint =
        BossService.expeditionAttemptFingerprint(fingerprint, nodeId, null);
    insertConsumedProblem(userId, challenge.runId(), nodeId, "issue-failed", kcCode, fingerprint);
    ExpeditionScoreService scores = scoreService();

    var failed =
        scores.recordVerifiedAttempt(
            userId,
            challenge.runId(),
            nodeId,
            null,
            "issue-failed",
            attemptFingerprint,
            false,
            0);

    assertThat(failed.accepted()).isTrue();
    ExpeditionRunDto afterFailure = runs.findActive(userId).orElseThrow();
    assertThat(afterFailure.hp()).isLessThan(challenge.hp());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM expedition_challenge_settlement"
                    + " WHERE user_id = ? AND run_id = ?::uuid AND problem_id = 'issue-failed'",
                Integer.class,
                userId,
                challenge.runId()))
        .isEqualTo(1);

    insertConsumedProblem(userId, challenge.runId(), nodeId, "issue-retry", kcCode, fingerprint);
    var passed =
        scores.recordVerifiedAttempt(
            userId,
            challenge.runId(),
            nodeId,
            null,
            "issue-retry",
            attemptFingerprint,
            true,
            100);
    assertThat(passed.accepted()).isTrue();
    ExpeditionRunDto settled =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                challenge.runId(),
                nodeId,
                ExpeditionRunAction.SETTLE_CHALLENGE,
                null,
                "issue-retry"));

    assertThat(settled.hp()).isEqualTo(afterFailure.hp());
    assertThat(nodeById(settled, nodeId).get("state")).isEqualTo("cleared");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM expedition_verified_attempt"
                    + " WHERE user_id = ? AND run_id = ?::uuid",
                Integer.class,
                userId,
                challenge.runId()))
        .isEqualTo(1);
  }

  @Test
  void persistedOpenProblemIsReusedAfterReloadInsteadOfIssuingAnother() throws Exception {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    String kcCode = (String) nodeById(challenge, nodeId).get("kcCode");
    BossInternalProblem issued =
        new BossInternalProblem(
            BossService.issueProblemId(null),
            1,
            1,
            1,
            kcCode,
            "Persisted challenge",
            List.of(),
            "",
            "",
            1,
            true,
            false,
            List.of(new com.pythontopia.world.boss.dto.BossTestCasePayload("", "", false)),
            "");
    EndlessPregenService pregen = new EndlessPregenService(null, jdbc, new ObjectMapper(), runs);

    BossInternalProblem first =
        pregen.persistConsumed(userId, 1, kcCode, null, null, 1, issued, challenge.runId(), nodeId);
    BossInternalProblem duplicate =
        pregen.persistConsumed(
            userId,
            1,
            kcCode,
            null,
            null,
            1,
            issued.issueAs(BossService.issueProblemId(null), kcCode),
            challenge.runId(),
            nodeId);
    var resumed = pregen.claimReady(userId, 1, 1, kcCode, 1, null, null, challenge.runId(), nodeId);

    assertThat(duplicate.problemId()).isEqualTo(first.problemId());
    assertThat(resumed).isPresent();
    assertThat(resumed.orElseThrow().problemId()).isEqualTo(first.problemId());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM pregenerated_problem WHERE user_id = ? AND expedition_run_id = ?::uuid"
                    + " AND expedition_node_id = ? AND status = 'CONSUMED'",
                Integer.class,
                userId,
                challenge.runId(),
                nodeId))
        .isEqualTo(1);
  }

  @Test
  void persistedOpenProblemIsNotReusedWhenFingerprintAlreadyAppearedEarlierInTheRun()
      throws Exception {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    String kcCode = (String) nodeById(challenge, nodeId).get("kcCode");
    BossInternalProblem duplicate =
        new BossInternalProblem(
            "issue-current",
            1,
            1,
            1,
            kcCode,
            "same question content",
            List.of(),
            "2 3",
            "5",
            1,
            true,
            false,
            List.of(new com.pythontopia.world.boss.dto.BossTestCasePayload("2 3", "5", false)),
            "print(5)");
    String fingerprint = BossService.questionFingerprint(duplicate);
    insertConsumedProblem(
        userId, challenge.runId(), "earlier-node", "issue-previous", kcCode, fingerprint);
    jdbc.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, stage, kc_code, difficulty, problem_id, payload, status,"
            + " consumed_at, expedition_run_id, expedition_node_id, question_fingerprint)"
            + " VALUES (?, 1, 1, ?, 1, ?, ?::jsonb, 'CONSUMED', now(), ?::uuid, ?, ?)",
        userId,
        kcCode,
        duplicate.problemId(),
        new ObjectMapper().writeValueAsString(duplicate),
        challenge.runId(),
        nodeId,
        fingerprint);
    EndlessPregenService pregen = new EndlessPregenService(null, jdbc, new ObjectMapper(), runs);

    var open = pregen.findOpenExpeditionProblem(userId, challenge.runId(), nodeId, null);

    assertThat(open).isEmpty();
  }

  @Test
  void claimReadyDoesNotReturnOpenProblemAtDifferentRequestedDifficulty() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    String kcCode = (String) nodeById(challenge, nodeId).get("kcCode");
    BossInternalProblem openAtWrongDifficulty =
        new BossInternalProblem(
            BossService.issueProblemId(null),
            1,
            1,
            1,
            kcCode,
            "open question",
            List.of(),
            "2 3",
            "5",
            1,
            true,
            false,
            List.of(new com.pythontopia.world.boss.dto.BossTestCasePayload("2 3", "5", false)),
            "print(5)");
    EndlessPregenService pregen = new EndlessPregenService(null, jdbc, new ObjectMapper(), runs);
    pregen.persistConsumed(
        userId, 1, kcCode, null, null, 1, openAtWrongDifficulty, challenge.runId(), nodeId);

    var reused = pregen.claimReady(userId, 1, 1, kcCode, 2, null, null, challenge.runId(), nodeId);

    assertThat(reused).isEmpty();
  }

  @Test
  void settlementRejectsAnOlderUnsettledIssueWhenANewerIssueExists() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    String kcCode = (String) nodeById(challenge, nodeId).get("kcCode");
    String olderFingerprint = "1".repeat(64);
    insertConsumedProblem(
        userId, challenge.runId(), nodeId, "issue-older", kcCode, olderFingerprint);
    insertConsumedProblem(userId, challenge.runId(), nodeId, "issue-newer", kcCode, "2".repeat(64));
    jdbc.update(
        "INSERT INTO expedition_verified_attempt"
            + " (user_id, run_id, problem_id, question_fingerprint, passed, score)"
            + " VALUES (?, ?::uuid, 'issue-older', ?, TRUE, 80)",
        userId,
        challenge.runId(),
        olderFingerprint);

    assertThatThrownBy(
            () ->
                runs.performAction(
                    userId,
                    new ExpeditionRunActionRequest(
                        challenge.runId(),
                        nodeId,
                        ExpeditionRunAction.SETTLE_CHALLENGE,
                        null,
                        "issue-older")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("绑定");
  }

  @Test
  void sameQuestionWithDifferentIssuedIdsUsesOneVerifiedLedgerEntry() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    String kcCode = (String) nodeById(challenge, nodeId).get("kcCode");
    insertConsumedProblem(
        userId, challenge.runId(), nodeId, "issued-problem-1", kcCode, "d".repeat(64));
    insertConsumedProblem(
        userId, challenge.runId(), nodeId, "issued-problem-2", kcCode, "d".repeat(64));
    ExpeditionScoreService scores = scoreService();
    String fingerprint = "d".repeat(64);

    var firstFailure =
        scores.recordVerifiedAttempt(
            userId, challenge.runId(), nodeId, null, "issued-problem-2", fingerprint, false, 0);
    var duplicateFailure =
        scores.recordVerifiedAttempt(
            userId, challenge.runId(), nodeId, null, "issued-problem-1", fingerprint, false, 0);
    insertConsumedProblem(
        userId, challenge.runId(), nodeId, "issued-problem-3", kcCode, fingerprint);
    var firstPass =
        scores.recordVerifiedAttempt(
            userId, challenge.runId(), nodeId, null, "issued-problem-3", fingerprint, true, 100);
    var duplicatePass =
        scores.recordVerifiedAttempt(
            userId, challenge.runId(), nodeId, null, "issued-problem-1", fingerprint, true, 100);

    assertThat(firstFailure)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    assertThat(duplicateFailure)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(false, false));
    assertThat(firstPass).isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(true, true));
    assertThat(duplicatePass)
        .isEqualTo(new ExpeditionScoreService.VerifiedAttemptResult(false, false));
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
  void dailyChallengeIsLimitedByTheServerAfterAbandonment() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    startRun(runs, userId, null, true);

    runs.abandon(userId);

    assertThatThrownBy(() -> startRun(runs, userId, null, true))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("今日挑战已完成");
  }

  @Test
  void dailyRunIsMarkedInItsVerifiedLeaderboardEntry() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto daily = startRun(runs, userId, null, true);
    jdbc.update(
        "INSERT INTO expedition_verified_attempt"
            + " (user_id, run_id, problem_id, question_fingerprint, passed, score)"
            + " VALUES (?, ?::uuid, 'daily-problem', ?, TRUE, 80)",
        userId,
        daily.runId(),
        "d".repeat(64));

    runs.performAction(
        userId,
        new ExpeditionRunActionRequest(daily.runId(), null, ExpeditionRunAction.FINISH, null));
    scoreService().record(userId, new ExpeditionScoreSubmitDto(daily.runId()));

    assertThat(
            jdbc.queryForObject(
                "SELECT is_daily FROM expedition_score WHERE user_id = ? AND run_id = ?::uuid",
                Boolean.class,
                userId,
                daily.runId()))
        .isTrue();
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

  private RevokedSettlementFixture createRevokedSettlementFixture() {
    long userId = createStudent();
    ObjectMapper objectMapper = new ObjectMapper();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, objectMapper);
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    String nodeId = (String) active.nodes().getFirst().get("id");
    ExpeditionRunDto challenge =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.ENTER_NODE, null));
    String kcCode = (String) nodeById(challenge, nodeId).get("kcCode");
    Integer bossPhase = "boss".equals(nodeById(challenge, nodeId).get("kind")) ? 1 : null;
    String problemId = BossService.issueProblemId(bossPhase);
    BossInternalProblem problem =
        new BossInternalProblem(
            problemId,
            1,
            1,
            2,
            kcCode,
            "revoked AI challenge",
            List.of(),
            "",
            "",
            1,
            true,
            true,
            List.of(new BossTestCasePayload("input", "output", false)),
            "print('output')");
    EndlessPregenService pregen = new EndlessPregenService(null, jdbc, objectMapper, runs);
    pregen.persistConsumed(userId, 1, kcCode, null, null, 2, problem, active.runId(), nodeId);
    BossGraphClient graph = mock(BossGraphClient.class);
    BossProblemReviewRepository reviews = mock(BossProblemReviewRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    ExpeditionScoreService scores = new ExpeditionScoreService(jdbc, runs, events);
    BossAttemptIdempotencyService idempotency = mock(BossAttemptIdempotencyService.class);
    when(idempotency.claim(eq(userId), any(UUID.class), any(BossAttemptRequest.class)))
        .thenReturn(
            new BossAttemptIdempotencyService.Claim(UUID.randomUUID(), null, "fixture"),
            new BossAttemptIdempotencyService.Claim(UUID.randomUUID(), null, "fixture"));
    when(idempotency.complete(
            eq(userId),
            any(UUID.class),
            any(BossAttemptIdempotencyService.Claim.class),
            any(BossAttemptResultDto.class),
            any()))
        .thenAnswer(invocation -> invocation.getArgument(3));
    BossService bossService =
        new BossService(
            graph,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            pregen,
            reviews,
            runs,
            scores,
            idempotency,
            events);
    return new RevokedSettlementFixture(
        userId, runs, active, nodeId, problemId, problem, graph, reviews, events, scores, bossService);
  }

  private record RevokedSettlementFixture(
      long userId,
      ExpeditionRunService runs,
      ExpeditionRunDto active,
      String nodeId,
      String problemId,
      BossInternalProblem problem,
      BossGraphClient graph,
      BossProblemReviewRepository reviews,
      ApplicationEventPublisher events,
      ExpeditionScoreService scores,
      BossService bossService) {}

  private long createStudent() {
    return jdbc.queryForObject(
        "INSERT INTO users (username, display_name, password_hash, role)"
            + " VALUES (?, 'Student', 'x', 'STUDENT') RETURNING id",
        Long.class,
        "expedition-" + UUID.randomUUID());
  }

  private void setResilience(long userId, double resilience) {
    jdbc.update(
        "INSERT INTO learner_profile (user_id, dimensions)"
            + " VALUES (?, jsonb_build_object('emotion_persistence',"
            + " jsonb_build_object('resilience', ?::double precision)))"
            + " ON CONFLICT (user_id) DO UPDATE SET dimensions = EXCLUDED.dimensions",
        userId,
        resilience);
  }

  private void insertIssuedProblem(
      long userId,
      String runId,
      String nodeId,
      String problemId,
      String kcCode,
      String fingerprint,
      boolean passed,
      int score) {
    insertIssuedProblem(
        userId, runId, nodeId, problemId, kcCode, fingerprint, fingerprint, passed, score);
  }

  private void insertConsumedProblem(
      long userId,
      String runId,
      String nodeId,
      String problemId,
      String kcCode,
      String fingerprint) {
    jdbc.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, stage, kc_code, problem_id, payload, status, consumed_at,"
            + " expedition_run_id, expedition_node_id, question_fingerprint)"
            + " VALUES (?, 1, 1, ?, ?, '{}'::jsonb, 'CONSUMED', now(), ?::uuid, ?, ?)",
        userId,
        kcCode,
        problemId,
        runId,
        nodeId,
        fingerprint);
  }

  private void insertIssuedProblem(
      long userId,
      String runId,
      String nodeId,
      String problemId,
      String kcCode,
      String questionFingerprint,
      String attemptFingerprint,
      boolean passed,
      int score) {
    jdbc.update(
        "INSERT INTO pregenerated_problem"
            + " (user_id, chapter, stage, kc_code, problem_id, payload, status, consumed_at,"
            + " expedition_run_id, expedition_node_id, question_fingerprint)"
            + " VALUES (?, 1, 1, ?, ?, '{}'::jsonb, 'CONSUMED', now(), ?::uuid, ?, ?)",
        userId,
        kcCode,
        problemId,
        runId,
        nodeId,
        questionFingerprint);
    jdbc.update(
        "INSERT INTO expedition_verified_attempt"
            + " (user_id, run_id, problem_id, question_fingerprint, passed, score)"
            + " VALUES (?, ?::uuid, ?, ?, ?, ?)",
        userId,
        runId,
        problemId,
        attemptFingerprint,
        passed,
        score);
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

  private static Map<String, Object> findNode(ExpeditionRunDto run, String kind) {
    return run.nodes().stream()
        .filter(node -> kind.equals(node.get("kind")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Generated map has no " + kind + " node"));
  }

  private RunWithNode startRunWithNode(ExpeditionRunService runs, String kind) {
    for (int attempt = 0; attempt < 32; attempt++) {
      long userId = createStudent();
      ExpeditionRunDto run = startRun(runs, userId, 1, false);
      Map<String, Object> node =
          run.nodes().stream()
              .filter(candidate -> kind.equals(candidate.get("kind")))
              .findFirst()
              .orElse(null);
      if (node != null) return new RunWithNode(userId, run, node);
    }
    throw new AssertionError("Could not generate an expedition map with a " + kind + " node");
  }

  private record RunWithNode(long userId, ExpeditionRunDto run, Map<String, Object> node) {}

  private static Map<String, Object> findRewardNode(ExpeditionRunDto run) {
    return run.nodes().stream()
        .filter(node -> "chest".equals(node.get("kind")) || "elite".equals(node.get("kind")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Generated map has no reward node"));
  }

  private static Map<String, Object> nodeById(ExpeditionRunDto run, String nodeId) {
    return run.nodes().stream()
        .filter(node -> nodeId.equals(node.get("id")))
        .findFirst()
        .orElseThrow();
  }

  private void setBossPhase(long userId, ExpeditionRunDto run, String nodeId, int bossPhase) {
    List<Map<String, Object>> nodes = run.nodes().stream().<Map<String, Object>>map(HashMap::new).toList();
    nodes.stream()
        .filter(node -> nodeId.equals(node.get("id")))
        .findFirst()
        .orElseThrow()
        .put("bossPhase", bossPhase);
    try {
      jdbc.update(
          "UPDATE expedition_run SET nodes = ?::jsonb WHERE user_id = ? AND run_id = ?::uuid",
          new ObjectMapper().writeValueAsString(nodes),
          userId,
          run.runId());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new AssertionError(e);
    }
  }

  private static void seedReachableNode(
      long userId,
      ExpeditionRunDto run,
      Map<String, Object> target,
      String phase,
      int hp,
      int maxHp,
      List<String> relics) {
    List<Map<String, Object>> nodes =
        run.nodes().stream().<Map<String, Object>>map(HashMap::new).toList();
    String targetId = (String) target.get("id");
    Map<String, Object> current =
        nodes.stream()
            .filter(node -> node.get("next") instanceof List<?> next && next.contains(targetId))
            .findFirst()
            .orElseThrow();
    current.put("state", "cleared");
    Map<String, Object> targetNode =
        nodes.stream().filter(node -> targetId.equals(node.get("id"))).findFirst().orElseThrow();
    targetNode.put("state", "reward".equals(phase) ? "cleared" : "available");
    boolean activeTarget = "reward".equals(phase) || "challenge".equals(phase);
    String currentNodeId = activeTarget ? targetId : (String) current.get("id");
    try {
      String nodesJson = new ObjectMapper().writeValueAsString(nodes);
      String relicsJson = new ObjectMapper().writeValueAsString(relics);
      jdbc.update(
          "UPDATE expedition_run SET phase = ?, hp = ?, max_hp = ?, current_node_id = ?,"
              + " nodes = ?::jsonb, relics = ?::jsonb WHERE user_id = ?",
          phase,
          hp,
          maxHp,
          currentNodeId,
          nodesJson,
          relicsJson,
          userId);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new AssertionError(e);
    }
  }

  private void markNodeCurrent(long userId, ExpeditionRunDto run, String nodeId) {
    List<Map<String, Object>> nodes =
        run.nodes().stream().<Map<String, Object>>map(HashMap::new).toList();
    nodes.stream()
        .filter(node -> nodeId.equals(node.get("id")))
        .findFirst()
        .orElseThrow()
        .put("state", "current");
    try {
      jdbc.update(
          "UPDATE expedition_run SET nodes = ?::jsonb WHERE user_id = ? AND run_id = ?::uuid",
          new ObjectMapper().writeValueAsString(nodes),
          userId,
          run.runId());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new AssertionError(e);
    }
  }
}
