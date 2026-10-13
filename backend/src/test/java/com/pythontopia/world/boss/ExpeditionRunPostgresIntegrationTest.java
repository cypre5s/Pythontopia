package com.pythontopia.world.boss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import com.pythontopia.world.boss.dto.ExpeditionRunAction;
import com.pythontopia.world.boss.dto.ExpeditionRunActionRequest;
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

    ExpeditionScoreService scores = new ExpeditionScoreService(jdbc);
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
        new ExpeditionRunActionRequest(
            initial.runId(), null, ExpeditionRunAction.FINISH, null));
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
                restRun.runId(), (String) restNode.get("id"), ExpeditionRunAction.ENTER_NODE, null));
    assertThat(rested.hp()).isEqualTo(4);
    assertThat(nodeById(rested, (String) restNode.get("id")).get("state")).isEqualTo("cleared");

    long reviewUserId = createStudent();
    ExpeditionRunDto reviewRun = startRun(runs, reviewUserId, 1, false);
    Map<String, Object> eventNode = findNode(reviewRun, "event");
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
    assertThat(reviewed.hp()).isEqualTo(4);
    assertThat(nodeById(reviewed, (String) eventNode.get("id")).get("state")).isEqualTo("cleared");

    for (ExpeditionRunAction choice :
        List.of(ExpeditionRunAction.EVENT_STUDY, ExpeditionRunAction.EVENT_STEADY)) {
      long userId = createStudent();
      ExpeditionRunDto run = startRun(runs, userId, 1, false);
      Map<String, Object> node = findNode(run, "event");
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
              new ExpeditionRunActionRequest(
                  run.runId(), (String) node.get("id"), choice, null));
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
                beforeSettlement.runId(), chosenId, ExpeditionRunAction.SETTLE_CHALLENGE, null,
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
                map.runId(),
                (String) eventNode.get("id"),
                ExpeditionRunAction.ENTER_NODE,
                null));
    markNodeCurrent(eventUserId, event, event.currentNodeId());

    ExpeditionRunDto resumedEvent = runs.findActive(eventUserId).orElseThrow();
    ExpeditionRunDto reenteredEvent =
        runs.performAction(
            eventUserId,
            new ExpeditionRunActionRequest(
                eventRun.runId(),
                event.currentNodeId(),
                ExpeditionRunAction.ENTER_NODE,
                null));
    assertThat(reenteredEvent.phase()).isEqualTo("event");
    assertThat(reenteredEvent.nodes()).isEqualTo(resumedEvent.nodes());
    assertThat(nodeById(reenteredEvent, event.currentNodeId()).get("state")).isEqualTo("current");
  }

  @Test
  void rewardActionOnlyClaimsKnownUnownedRelicsAndAppliesSturdyNotes() {
    long userId = createStudent();
    ExpeditionRunService runs = new ExpeditionRunService(jdbc, new ObjectMapper());
    ExpeditionRunDto active = startRun(runs, userId, 1, false);
    Map<String, Object> chestNode = findNode(active, "chest");
    List<String> sixRelics =
        List.of("calm_mind", "combo_scholar", "second_wind", "focus_lens", "quick_thinker", "first_aid");
    seedReachableNode(userId, active, chestNode, "reward", 2, 5, sixRelics);

    assertThatThrownBy(
            () ->
                runs.performAction(
                    userId,
                    new ExpeditionRunActionRequest(
                        active.runId(), (String) chestNode.get("id"), ExpeditionRunAction.CLAIM_RELIC,
                        "not-a-relic")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("遗物");

    ExpeditionRunDto claimed =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), (String) chestNode.get("id"), ExpeditionRunAction.CLAIM_RELIC,
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
    Map<String, Object> chestNode = findNode(started, "chest");
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
                "sturdy_notes", "calm_mind", "combo_scholar", "second_wind", "focus_lens",
                "quick_thinker", "first_aid")
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
                active.runId(), (String) active.nodes().getFirst().get("id"),
                ExpeditionRunAction.ENTER_NODE, null));
    List<Map<String, Object>> spoofedNodes =
        changed.nodes().stream().<Map<String, Object>>map(HashMap::new).toList();
    spoofedNodes.getFirst().put("state", "cleared");
    ExpeditionRunDto spoofed =
        new ExpeditionRunDto(
            "map", changed.focusChapter(), spoofedNodes, changed.layers(), null, 1, 5, 12, 12,
            999_999, List.of("sturdy_notes"), changed.isDaily(), List.of(), changed.runId());

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
            () -> runs.validateProblemRequest(userId, active.runId(), rootNodeId, 1, true, rootKcCode));
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
        userId,
        active.runId(),
        "L1-0",
        wrongNodeProblemId,
        kcCode,
        "c".repeat(64),
        false,
        0);
    insertIssuedProblem(
        userId,
        active.runId(),
        nodeId,
        validProblemId,
        kcCode,
        "d".repeat(64),
        true,
        70);

    assertThatThrownBy(
            () ->
                runs.performAction(
                    userId,
                    new ExpeditionRunActionRequest(
                        active.runId(), nodeId, ExpeditionRunAction.SETTLE_CHALLENGE,
                        null, wrongNodeProblemId)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("绑定");

    ExpeditionRunDto settled =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.SETTLE_CHALLENGE, null, validProblemId));
    assertThat(settled.phase()).isEqualTo("map");
    assertThat(settled.score()).isEqualTo(70);
    assertThat(settled.momentum()).isEqualTo(1);
    assertThat(nodeById(settled, nodeId).get("state")).isEqualTo("cleared");

    ExpeditionRunDto repeated =
        runs.performAction(
            userId,
            new ExpeditionRunActionRequest(
                active.runId(), nodeId, ExpeditionRunAction.SETTLE_CHALLENGE, null, validProblemId));
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
                  userId,
                  active.runId(),
                  bossNodeId,
                  1,
                  false,
                  (String) boss.get("kcCode")))
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
                    userId,
                    active.runId(),
                    bossNodeId,
                    1,
                    false,
                    (String) boss.get("kcCode")))
            .isEqualTo(phase + 1);
      }
    }
    assertThat(state.phase()).isEqualTo("over");
    assertThat(nodeById(state, bossNodeId).get("state")).isEqualTo("cleared");

    new ExpeditionScoreService(jdbc)
        .record(userId, new ExpeditionScoreSubmitDto(active.runId()));
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
  void failedChallengeUsesResilienceAndSecondWindFromServerState() {
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

    assertThat(lowFailure.hp()).isEqualTo(4);
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
        "UPDATE expedition_run SET relics = '[\"first_aid\"]'::jsonb WHERE user_id = ?",
        userId);
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
        new ExpeditionRunActionRequest(
            daily.runId(), null, ExpeditionRunAction.FINISH, null));
    new ExpeditionScoreService(jdbc).record(userId, new ExpeditionScoreSubmitDto(daily.runId()));

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

  private static Map<String, Object> nodeById(ExpeditionRunDto run, String nodeId) {
    return run.nodes().stream()
        .filter(node -> nodeId.equals(node.get("id")))
        .findFirst()
        .orElseThrow();
  }

  private static void seedReachableNode(
      long userId,
      ExpeditionRunDto run,
      Map<String, Object> target,
      String phase,
      int hp,
      int maxHp,
      List<String> relics) {
    List<Map<String, Object>> nodes = run.nodes().stream().<Map<String, Object>>map(HashMap::new).toList();
    String targetId = (String) target.get("id");
    Map<String, Object> current =
        nodes.stream()
            .filter(
                node ->
                    node.get("next") instanceof List<?> next && next.contains(targetId))
            .findFirst()
            .orElseThrow();
    current.put("state", "cleared");
    Map<String, Object> targetNode =
        nodes.stream()
        .filter(node -> targetId.equals(node.get("id")))
        .findFirst()
        .orElseThrow();
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
    List<Map<String, Object>> nodes = run.nodes().stream().<Map<String, Object>>map(HashMap::new).toList();
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
