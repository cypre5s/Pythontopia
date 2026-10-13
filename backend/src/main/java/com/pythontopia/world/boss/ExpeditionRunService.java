package com.pythontopia.world.boss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.boss.dto.ExpeditionRunAction;
import com.pythontopia.world.boss.dto.ExpeditionRunActionRequest;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import com.pythontopia.world.boss.dto.FrontierPickDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 无尽远征 run 跨会话快照持久化。
 *
 * <p>每个用户至多一条活跃/已结算 run。run id 与地图配置由服务端签发，活跃期间不可由快照改写；新局只能在结算或显式弃局后创建。
 *
 * <ul>
 *   <li>{@link #findActive}：GET 活跃 run（无则 empty → controller 返 204）；
 *   <li>{@link #save}：PUT 校验地图并返回服务端活动 run；
 *   <li>{@link #abandon}：DELETE 结束 / 弃局。
 * </ul>
 *
 * <p>地图拓扑与题目配置在开局后不可由快照改写；PUT 只校验地图并返回服务端状态。节点、体力和遗物由动作端点更新。
 * 掌握度 / 判题结果由 learner_mastery 等权威表负责，成绩由 expedition_verified_attempt 汇总。
 */
@Service
public class ExpeditionRunService {

  private static final Logger log = LoggerFactory.getLogger(ExpeditionRunService.class);
  private static final int MAX_SNAPSHOT_BYTES = 64 * 1024;
  private static final ZoneId DAILY_CHALLENGE_ZONE = ZoneId.of("Asia/Shanghai");
  private static final Set<String> PERSISTABLE_PHASES =
      Set.of("map", "event", "challenge", "reward");
  private static final Set<String> NODE_STATES =
      Set.of("locked", "available", "current", "cleared", "bypassed");
  private static final Set<String> CHALLENGE_NODE_KINDS =
      Set.of("battle", "bug", "elite", "boss");
  private static final Set<String> RELIC_IDS =
      Set.of(
          "sturdy_notes",
          "calm_mind",
          "combo_scholar",
          "second_wind",
          "focus_lens",
          "quick_thinker",
          "first_aid");
  private static final Set<String> IMMUTABLE_NODE_FIELDS =
      Set.of("id", "layer", "col", "kind", "kcCode", "challengeKind", "difficulty", "next");
  private static final Set<String> ALLOWED_NODE_FIELDS =
      Set.of(
          "id", "layer", "col", "kind", "kcCode", "challengeKind", "difficulty", "next",
          "state", "bossPhase");
  private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP =
      new TypeReference<>() {};
  private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {};

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ExpeditionRunService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  /** 取当前用户的活跃 run；无则 empty。 */
  public Optional<ExpeditionRunDto> findActive(Long userId) {
    if (userId == null) {
      return Optional.empty();
    }
    List<ExpeditionRunDto> rows =
        jdbcTemplate.query(
            "SELECT run_id::text AS run_id,"
                + " CASE WHEN phase IN ('event', 'challenge', 'reward', 'over') THEN phase ELSE 'map' END AS phase,"
                + " focus_chapter, nodes::text AS nodes, layers, current_node_id,"
                + " hp, max_hp, momentum, best_momentum,"
                + " (SELECT COALESCE(SUM(a.score), 0) FROM expedition_verified_attempt a"
                + " WHERE a.user_id = expedition_run.user_id AND a.run_id = expedition_run.run_id)"
                + " AS verified_score,"
                + " (SELECT COUNT(*) FROM expedition_verified_attempt a"
                + " WHERE a.user_id = expedition_run.user_id AND a.run_id = expedition_run.run_id"
                + " AND a.passed) AS verified_depth, relics::text AS relics,"
                + " is_daily, history::text AS history"
                + " FROM expedition_run WHERE user_id = ? AND status = 'ACTIVE'",
            (rs, i) ->
                new ExpeditionRunDto(
                    rs.getString("phase"),
                    (Integer) rs.getObject("focus_chapter"),
                    listOfMap(rs.getString("nodes")),
                    rs.getInt("layers"),
                    rs.getString("current_node_id"),
                    rs.getInt("hp"),
                    rs.getInt("max_hp"),
                    rs.getInt("momentum"),
                    rs.getInt("best_momentum"),
                    rs.getInt("verified_score"),
                    listOfString(rs.getString("relics")),
                    rs.getBoolean("is_daily"),
                    listOfMap(rs.getString("history")),
                    rs.getString("run_id"),
                    rs.getInt("verified_depth")),
            userId);
    return rows.stream().findFirst();
  }

  /** 校验兼容快照的身份和地图，然后返回服务端状态；请求中的游戏进度不会写入数据库。 */
  @Transactional
  public ExpeditionRunDto save(Long userId, ExpeditionRunDto dto) {
    if (userId == null) {
      return dto;
    }
    validateSnapshotSize(dto);
    validatePersistablePhase(dto.phase());
    lockUserRun(userId);
    List<ExistingRun> existing = lockRunRow(userId);
    if (existing.isEmpty()) {
      if (dto.runId() == null) throw startRequired();
      throw staleRun();
    }
    ExistingRun current = existing.getFirst();
    if (!"ACTIVE".equals(current.status())) {
      if (dto.runId() == null) throw startRequired();
      throw staleRun();
    }
    if (!current.runId().equals(normalizeRunId(dto.runId()))) {
      throw staleRun();
    }
    if (!Objects.equals(current.focusChapter(), dto.focusChapter())) {
      throw chapterChanged();
    }
    validateMapSnapshot(current, dto);
    return findActive(userId).orElseThrow(this::staleRun);
  }

  /** Apply one typed gameplay command while holding the user's active run row lock. */
  @Transactional
  public ExpeditionRunDto performAction(Long userId, ExpeditionRunActionRequest request) {
    if (userId == null || request == null || request.action() == null) {
      throw invalidAction("远征操作参数无效");
    }
    if (request.action() != ExpeditionRunAction.SETTLE_CHALLENGE && request.problemId() != null) {
      throw invalidAction("该远征操作不能携带题目 id");
    }
    ExpeditionRunDto run = lockActiveSnapshot(userId, request.runId());
    return switch (request.action()) {
      case ENTER_NODE -> enterNode(userId, run, request);
      case EVENT_REVIEW -> reviewEvent(userId, run, request);
      case EVENT_STUDY, EVENT_STEADY -> chooseEventChallenge(userId, run, request);
      case CLAIM_RELIC -> claimRelic(userId, run, request);
      case SETTLE_CHALLENGE -> settleChallenge(userId, run, request);
      case FINISH -> finishRun(userId, run, request);
    };
  }

  /** Start a run with a server-generated map and reset all client-supplied gameplay state. */
  @Transactional
  public ExpeditionRunDto start(
      Long userId, Integer focusChapter, boolean isDaily, List<FrontierPickDto> picks) {
    LocalDate challengeDate = LocalDate.now(DAILY_CHALLENGE_ZONE);
    if (isDaily && userId != null) {
      lockUserRun(userId);
      boolean claimed =
          Boolean.TRUE.equals(
              jdbcTemplate.queryForObject(
                  "SELECT EXISTS(SELECT 1 FROM expedition_daily_claim"
                      + " WHERE user_id = ? AND challenge_date = ?)",
                  Boolean.class,
                  userId,
                  challengeDate));
      if (claimed) throw dailyChallengeAlreadyClaimed();
    }
    long seed =
        isDaily
            ? challengeDate.toEpochDay()
            : ThreadLocalRandom.current().nextLong();
    ExpeditionMapGenerator.MapData map = ExpeditionMapGenerator.generate(picks, seed);
    ExpeditionRunDto initial =
        new ExpeditionRunDto(
            "map",
            focusChapter,
            map.nodes(),
            map.layers(),
            null,
            5,
            5,
            0,
            0,
            0,
            List.of(),
            isDaily,
            List.of(),
            null,
            0);
    ExpeditionRunDto started = persistSnapshot(userId, initial, true);
    if (isDaily && userId != null) {
      int claimed =
          jdbcTemplate.update(
              "INSERT INTO expedition_daily_claim (user_id, challenge_date) VALUES (?, ?)"
                  + " ON CONFLICT (user_id, challenge_date) DO NOTHING",
              userId,
              challengeDate);
      if (claimed != 1) throw dailyChallengeAlreadyClaimed();
    }
    return started;
  }

  private ExpeditionRunDto persistSnapshot(Long userId, ExpeditionRunDto dto, boolean allowNewRun) {
    if (userId == null) {
      return dto;
    }
    validateSnapshotSize(dto);
    validatePersistablePhase(dto.phase());
    lockUserRun(userId);
    List<ExistingRun> existing =
        jdbcTemplate.query(
            "SELECT run_id::text AS run_id, status, focus_chapter, nodes::text AS nodes, layers, is_daily"
                + " FROM expedition_run"
                + " WHERE user_id = ? FOR UPDATE",
            (rs, rowNum) ->
                new ExistingRun(
                    rs.getString("run_id"),
                    rs.getString("status"),
                    (Integer) rs.getObject("focus_chapter"),
                    listOfMap(rs.getString("nodes")),
                    rs.getInt("layers"),
                    rs.getBoolean("is_daily")),
            userId);

    String requestedRunId = dto.runId() == null ? null : UUID.fromString(dto.runId()).toString();
    String runId;
    if (existing.isEmpty()) {
      if (requestedRunId != null || !allowNewRun) {
        if (requestedRunId == null && !allowNewRun) throw startRequired();
        throw staleRun();
      }
      runId = UUID.randomUUID().toString();
    } else {
      ExistingRun current = existing.getFirst();
      if ("ACTIVE".equals(current.status())) {
        if (!current.runId().equals(requestedRunId)) {
          throw staleRun();
        }
        if (!Objects.equals(current.focusChapter(), dto.focusChapter())) {
          throw chapterChanged();
        }
        validateMapSnapshot(current, dto);
        runId = current.runId();
      } else {
        if (requestedRunId != null || !allowNewRun) {
          if (requestedRunId == null && !allowNewRun) throw startRequired();
          throw staleRun();
        }
        runId = UUID.randomUUID().toString();
      }
    }
    jdbcTemplate.update(
        "INSERT INTO expedition_run"
            + " (user_id, run_id, phase, focus_chapter, hp, max_hp, momentum, best_momentum, score,"
            + " layers, current_node_id, is_daily, nodes, relics, history, status, updated_at)"
            + " VALUES (?, ?::uuid, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, 'ACTIVE', now())"
            + " ON CONFLICT (user_id) DO UPDATE SET"
            + " run_id = EXCLUDED.run_id, phase = EXCLUDED.phase,"
            + " focus_chapter = EXCLUDED.focus_chapter, hp = EXCLUDED.hp,"
            + " max_hp = EXCLUDED.max_hp, momentum = EXCLUDED.momentum,"
            + " best_momentum = EXCLUDED.best_momentum,"
            + " score = COALESCE((SELECT SUM(a.score) FROM expedition_verified_attempt a"
            + " WHERE a.user_id = EXCLUDED.user_id AND a.run_id = EXCLUDED.run_id), 0),"
            + " layers = EXCLUDED.layers, current_node_id = EXCLUDED.current_node_id,"
            + " is_daily = EXCLUDED.is_daily, nodes = EXCLUDED.nodes, relics = EXCLUDED.relics,"
            + " history = EXCLUDED.history, status = 'ACTIVE', updated_at = now()",
        userId,
        runId,
        dto.phase(),
        dto.focusChapter(),
        dto.hp(),
        dto.maxHp(),
        dto.momentum(),
        dto.bestMomentum(),
        dto.layers(),
        dto.currentNodeId(),
        dto.isDaily(),
        serialize(dto.nodes()),
        serialize(dto.relics()),
        serialize(dto.history()));
    VerifiedProgress verifiedProgress =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(score), 0) AS score,"
                + " COUNT(*) FILTER (WHERE passed) AS verified_depth FROM expedition_verified_attempt"
                + " WHERE user_id = ? AND run_id = ?::uuid",
            (rs, rowNum) -> new VerifiedProgress(rs.getInt("score"), rs.getInt("verified_depth")),
            userId,
            runId);
    return withRunId(
        dto, runId, verifiedProgress == null ? new VerifiedProgress(0, 0) : verifiedProgress);
  }

  private List<ExistingRun> lockRunRow(Long userId) {
    return jdbcTemplate.query(
        "SELECT run_id::text AS run_id, status, focus_chapter, nodes::text AS nodes, layers, is_daily"
            + " FROM expedition_run WHERE user_id = ? FOR UPDATE",
        (rs, rowNum) ->
            new ExistingRun(
                rs.getString("run_id"),
                rs.getString("status"),
                (Integer) rs.getObject("focus_chapter"),
                listOfMap(rs.getString("nodes")),
                rs.getInt("layers"),
                rs.getBoolean("is_daily")),
        userId);
  }

  private ExpeditionRunDto lockActiveSnapshot(Long userId, String requestedRunId) {
    String normalizedRunId = normalizeRunId(requestedRunId);
    List<ExistingRun> rows = lockRunRow(userId);
    if (rows.isEmpty()) throw staleRun();
    ExistingRun current = rows.getFirst();
    if (!"ACTIVE".equals(current.status()) || !current.runId().equals(normalizedRunId)) {
      throw staleRun();
    }
    ExpeditionRunDto run = findActive(userId).orElseThrow(this::staleRun);
    validateMapSnapshot(current, run);
    validateRelics(run.relics());
    return run;
  }

  private ExpeditionRunDto enterNode(
      Long userId, ExpeditionRunDto run, ExpeditionRunActionRequest request) {
    requireNoRelic(request);
    Map<String, Object> node = findNode(run, request.nodeId());
    if (("challenge".equals(run.phase()) || "event".equals(run.phase()))
        && request.nodeId().equals(run.currentNodeId())
        && ("available".equals(node.get("state")) || "current".equals(node.get("state")))
        && ("event".equals(node.get("kind"))
            || CHALLENGE_NODE_KINDS.contains(stringValue(node.get("kind"))))) {
      // Resume path: the Web client may normalize transient phases to its map view. Re-entering
      // only the server's current node returns its canonical challenge/event state unchanged.
      return run;
    }
    requirePhase(run, "map");
    requireReachable(run, node);
    String kind = stringValue(node.get("kind"));
    List<Map<String, Object>> nodes = copyNodes(run.nodes());
    String currentNodeId = request.nodeId();
    Map<String, Object> changedNode = findNode(nodes, currentNodeId);
    String phase;
    int hp = run.hp();
    int maxHp = run.maxHp();
    if ("rest".equals(kind)) {
      hp = heal(hp, maxHp, 1 + (run.relics().contains("focus_lens") ? 1 : 0));
      hp = clearNode(userId, run.runId(), nodes, currentNodeId, run.layers(), hp, maxHp);
      phase = "map";
    } else if ("chest".equals(kind)) {
      hp = clearNode(userId, run.runId(), nodes, currentNodeId, run.layers(), hp, maxHp);
      phase = run.relics().size() >= RELIC_IDS.size() ? "map" : "reward";
    } else if ("event".equals(kind)) {
      phase = "event";
    } else if (CHALLENGE_NODE_KINDS.contains(kind)) {
      if ("boss".equals(kind)) changedNode.put("bossPhase", 1);
      phase = "challenge";
    } else {
      throw invalidAction("该节点类型不可进入");
    }
    return persistActionState(userId, run, phase, currentNodeId, hp, maxHp, nodes, run.relics());
  }

  private ExpeditionRunDto reviewEvent(
      Long userId, ExpeditionRunDto run, ExpeditionRunActionRequest request) {
    requireEventNode(run, request);
    List<Map<String, Object>> nodes = copyNodes(run.nodes());
    int hp = heal(run.hp(), run.maxHp(), 1 + (run.relics().contains("focus_lens") ? 1 : 0));
    hp = clearNode(userId, run.runId(), nodes, run.currentNodeId(), run.layers(), hp, run.maxHp());
    return persistActionState(
        userId, run, "map", run.currentNodeId(), hp, run.maxHp(), nodes, run.relics());
  }

  private ExpeditionRunDto chooseEventChallenge(
      Long userId, ExpeditionRunDto run, ExpeditionRunActionRequest request) {
    requireEventNode(run, request);
    return persistActionState(
        userId,
        run,
        "challenge",
        run.currentNodeId(),
        run.hp(),
        run.maxHp(),
        copyNodes(run.nodes()),
        run.relics());
  }

  private ExpeditionRunDto claimRelic(
      Long userId, ExpeditionRunDto run, ExpeditionRunActionRequest request) {
    requirePhase(run, "reward");
    Map<String, Object> rewardNode = findNode(run, request.nodeId());
    if (!request.nodeId().equals(run.currentNodeId())
        || !"cleared".equals(rewardNode.get("state"))
        || !("chest".equals(rewardNode.get("kind")) || "elite".equals(rewardNode.get("kind")))
        || request.relicId() == null
        || !RELIC_IDS.contains(request.relicId())
        || run.relics().contains(request.relicId())) {
      throw invalidAction("奖励节点或遗物选择无效");
    }
    if (run.relics().size() >= RELIC_IDS.size()) {
      throw invalidAction("遗物数量已达上限");
    }
    List<String> relics = new ArrayList<>(run.relics());
    relics.add(request.relicId());
    int maxHp = run.maxHp();
    int hp = run.hp();
    if ("sturdy_notes".equals(request.relicId())) {
      maxHp = Math.min(6, maxHp + 1);
      hp = Math.min(maxHp, hp + 1);
    }
    return persistActionState(
        userId,
        run,
        "map",
        run.currentNodeId(),
        hp,
        maxHp,
        copyNodes(run.nodes()),
        relics);
  }

  private ExpeditionRunDto finishRun(
      Long userId, ExpeditionRunDto run, ExpeditionRunActionRequest request) {
    if (request.nodeId() != null
        || request.relicId() != null
        || request.problemId() != null
        || "over".equals(run.phase())) {
      throw invalidAction("当前状态不能结束远征");
    }
    return persistActionState(
        userId,
        run,
        "over",
        run.currentNodeId(),
        run.hp(),
        run.maxHp(),
        copyNodes(run.nodes()),
        run.relics());
  }

  private ExpeditionRunDto settleChallenge(
      Long userId, ExpeditionRunDto run, ExpeditionRunActionRequest request) {
    if (request.relicId() != null || request.nodeId() == null || request.problemId() == null) {
      throw invalidAction("判题结算参数无效");
    }
    Map<String, Object> node = findNode(run, request.nodeId());
    ChallengeEvidence evidence = findChallengeEvidence(userId, run, request, node);
    Optional<ChallengeSettlement> prior =
        findSettlement(userId, run.runId(), evidence.questionFingerprint());
    if (prior.isPresent()) {
      ChallengeSettlement settlement = prior.get();
      if (!settlement.nodeId().equals(request.nodeId())) {
        throw problemBindingMismatch();
      }
      if (settlement.passed() || !evidence.passed()) {
        return findActive(userId).orElseThrow(this::staleRun);
      }
      // A failed verdict can later be upgraded to a pass for the same fingerprint. Keep the
      // original HP result, while allowing that verified pass to clear the current node.
      if (!"challenge".equals(run.phase()) || !request.nodeId().equals(run.currentNodeId())) {
        throw problemBindingMismatch();
      }
      return applyChallengeOutcome(userId, run, node, request.problemId(), evidence, false, true);
    }
    validateIssuedBossPhase(node, evidence, request.problemId());
    if (!"challenge".equals(run.phase()) || !request.nodeId().equals(run.currentNodeId())) {
      throw problemBindingMismatch();
    }
    if (!CHALLENGE_NODE_KINDS.contains(stringValue(node.get("kind")))
        && !"event".equals(node.get("kind"))) {
      throw problemBindingMismatch();
    }
    return applyChallengeOutcome(userId, run, node, request.problemId(), evidence, true, false);
  }

  private ExpeditionRunDto applyChallengeOutcome(
      Long userId,
      ExpeditionRunDto run,
      Map<String, Object> currentNode,
      String problemId,
      ChallengeEvidence evidence,
      boolean createSettlement,
      boolean upgradeFailure) {
    List<Map<String, Object>> nodes = copyNodes(run.nodes());
    Map<String, Object> node = findNode(nodes, run.currentNodeId());
    List<Map<String, Object>> history = copyNodes(run.history());
    int hp = run.hp();
    int momentum;
    int bestMomentum = run.bestMomentum();
    String phase;
    boolean firstAidUsed = false;
    int settledScore = evidence.passed() ? evidence.score() : 0;

    if (evidence.passed()) {
      momentum = Math.min(12, run.momentum() + 1);
      bestMomentum = Math.max(bestMomentum, momentum);
      if ("boss".equals(node.get("kind"))) {
        int bossPhase = node.get("bossPhase") == null ? 1 : integerValue(node.get("bossPhase"));
        if (bossPhase < 1 || bossPhase > 3) throw mapChanged();
        if (bossPhase < 3) {
          node.put("bossPhase", bossPhase + 1);
          phase = "challenge";
        } else {
          hp = clearNode(
              userId, run.runId(), nodes, run.currentNodeId(), run.layers(), hp, run.maxHp());
          phase = "over";
        }
      } else {
        hp = clearNode(
            userId, run.runId(), nodes, run.currentNodeId(), run.layers(), hp, run.maxHp());
        phase =
            "elite".equals(node.get("kind")) && run.relics().size() < RELIC_IDS.size()
                ? "reward"
                : "map";
      }
    } else {
      momentum = run.relics().contains("second_wind") ? Math.floorDiv(run.momentum(), 2) : 0;
      firstAidUsed = run.relics().contains("first_aid") && !hasUsedFirstAid(userId, run.runId());
      int damage = challengeDamage(userId);
      if (run.relics().contains("calm_mind")) damage = Math.max(1, damage - 1);
      if (firstAidUsed) damage = 0;
      hp = Math.max(0, hp - damage);
      phase = hp == 0 ? "over" : "challenge";
    }

    Map<String, Object> historyItem = new LinkedHashMap<>();
    historyItem.put("nodeId", run.currentNodeId());
    historyItem.put("kcCode", node.get("kcCode"));
    historyItem.put("kind", node.get("kind"));
    historyItem.put("problemId", problemId);
    historyItem.put("passed", evidence.passed());
    historyItem.put("score", settledScore);
    historyItem.put("firstAidUsed", firstAidUsed);
    historyItem.put("at", OffsetDateTime.now().toString());
    history.addFirst(historyItem);

    if (createSettlement) {
      jdbcTemplate.update(
          "INSERT INTO expedition_challenge_settlement"
              + " (user_id, run_id, question_fingerprint, problem_id, node_id, passed, score, first_aid_used)"
              + " VALUES (?, ?::uuid, ?, ?, ?, ?, ?, ?)",
          userId,
          run.runId(),
          evidence.questionFingerprint(),
          problemId,
          run.currentNodeId(),
          evidence.passed(),
          settledScore,
          firstAidUsed);
    } else if (upgradeFailure) {
      jdbcTemplate.update(
          "UPDATE expedition_challenge_settlement SET passed = TRUE, score = ?, problem_id = ?"
              + " WHERE user_id = ? AND run_id = ?::uuid AND question_fingerprint = ? AND passed = FALSE",
          settledScore,
          problemId,
          userId,
          run.runId(),
          evidence.questionFingerprint());
    }

    int updated =
        jdbcTemplate.update(
            "UPDATE expedition_run SET phase = ?, hp = ?, momentum = ?, best_momentum = ?,"
                + " nodes = ?::jsonb, history = ?::jsonb, updated_at = now()"
                + " WHERE user_id = ? AND run_id = ?::uuid AND status = 'ACTIVE'",
            phase,
            hp,
            momentum,
            bestMomentum,
            serialize(nodes),
            serialize(history),
            userId,
            run.runId());
    if (updated != 1) throw staleRun();
    return findActive(userId).orElseThrow(this::staleRun);
  }

  private ChallengeEvidence findChallengeEvidence(
      Long userId,
      ExpeditionRunDto run,
      ExpeditionRunActionRequest request,
      Map<String, Object> node) {
    List<IssuedProblem> issuedProblems =
        jdbcTemplate.query(
            "SELECT p.expedition_node_id, p.kc_code, p.chapter, p.problem_id, p.question_fingerprint"
                + " FROM pregenerated_problem p"
                + " WHERE p.user_id = ? AND p.expedition_run_id = ?::uuid"
                + " AND p.expedition_node_id = ? AND p.problem_id = ? AND p.status = 'CONSUMED'"
                + " LIMIT 1",
            (rs, rowNum) ->
                new IssuedProblem(
                    rs.getString("expedition_node_id"),
                    rs.getString("kc_code"),
                    rs.getInt("chapter"),
                    rs.getString("problem_id"),
                    rs.getString("question_fingerprint")),
            userId,
            run.runId(),
            request.nodeId(),
            request.problemId());
    if (issuedProblems.isEmpty()) throw problemBindingMismatch();
    IssuedProblem issued = issuedProblems.getFirst();
    Integer bossPhase = BossService.bossPhaseFromProblemId(issued.problemId());
    if (!request.nodeId().equals(issued.nodeId())
        || !Objects.equals(node.get("kcCode"), issued.kcCode())
        || issued.questionFingerprint() == null
        || (run.focusChapter() != null && issued.chapter() != run.focusChapter())) {
      throw problemBindingMismatch();
    }
    String attemptFingerprint =
        BossService.expeditionAttemptFingerprint(
            issued.questionFingerprint(), issued.nodeId(), bossPhase);
    List<ChallengeEvidence> attempts =
        jdbcTemplate.query(
            "SELECT question_fingerprint, passed, score FROM expedition_verified_attempt"
                + " WHERE user_id = ? AND run_id = ?::uuid"
                + " AND (problem_id = ? OR question_fingerprint = ?)"
                + " ORDER BY passed DESC, score DESC LIMIT 1",
            (rs, rowNum) ->
                new ChallengeEvidence(
                    issued.nodeId(),
                    issued.kcCode(),
                    issued.chapter(),
                    rs.getString("question_fingerprint"),
                    bossPhase,
                    rs.getBoolean("passed"),
                    rs.getInt("score")),
            userId,
            run.runId(),
            request.problemId(),
            attemptFingerprint);
    if (attempts.isEmpty()) throw problemBindingMismatch();
    return attempts.getFirst();
  }

  private void validateIssuedBossPhase(
      Map<String, Object> node, ChallengeEvidence evidence, String problemId) {
    Integer currentPhase = null;
    if ("boss".equals(node.get("kind"))) {
      currentPhase = node.get("bossPhase") == null ? 1 : integerValue(node.get("bossPhase"));
      if (currentPhase < 1 || currentPhase > 3) throw mapChanged();
    }
    Integer issuedPhase = evidence.bossPhase();
    if (currentPhase != null
        && currentPhase == 1
        && issuedPhase == null
        && BossService.isLegacyIssueProblemId(problemId)) {
      issuedPhase = 1;
    }
    if (!Objects.equals(currentPhase, issuedPhase)) throw problemBindingMismatch();
  }

  private Optional<ChallengeSettlement> findSettlement(
      Long userId, String runId, String questionFingerprint) {
    return jdbcTemplate
        .query(
            "SELECT node_id, passed FROM expedition_challenge_settlement"
                + " WHERE user_id = ? AND run_id = ?::uuid AND question_fingerprint = ?",
            (rs, rowNum) -> new ChallengeSettlement(rs.getString("node_id"), rs.getBoolean("passed")),
            userId,
            runId,
            questionFingerprint)
        .stream()
        .findFirst();
  }

  private boolean hasUsedFirstAid(Long userId, String runId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM expedition_challenge_settlement"
                + " WHERE user_id = ? AND run_id = ?::uuid AND first_aid_used)",
            Boolean.class,
            userId,
            runId));
  }

  private int challengeDamage(Long userId) {
    List<Double> resilience =
        jdbcTemplate.query(
            "SELECT CASE WHEN dimensions #>> '{emotion_persistence,resilience}' ~ '^[0-9]+([.][0-9]+)?$'"
                + " THEN (dimensions #>> '{emotion_persistence,resilience}')::double precision END"
                + " FROM learner_profile WHERE user_id = ?",
            (rs, rowNum) -> {
              double value = rs.getDouble(1);
              return rs.wasNull() ? null : value;
            },
            userId);
    return resilience.stream().filter(Objects::nonNull).findFirst().orElse(0.5) < 0.4 ? 1 : 2;
  }

  private BusinessException problemBindingMismatch() {
    return new BusinessException(
        HttpStatus.CONFLICT,
        "EXPEDITION_PROBLEM_BINDING_MISMATCH",
        "题目与当前远征节点绑定不匹配，无法结算");
  }

  private ExpeditionRunDto persistActionState(
      Long userId,
      ExpeditionRunDto run,
      String phase,
      String currentNodeId,
      int hp,
      int maxHp,
      List<Map<String, Object>> nodes,
      List<String> relics) {
    int updated =
        jdbcTemplate.update(
            "UPDATE expedition_run SET phase = ?, current_node_id = ?, hp = ?, max_hp = ?,"
                + " nodes = ?::jsonb, relics = ?::jsonb, updated_at = now()"
                + " WHERE user_id = ? AND run_id = ?::uuid AND status = 'ACTIVE'",
            phase,
            currentNodeId,
            hp,
            maxHp,
            serialize(nodes),
            serialize(relics),
            userId,
            run.runId());
    if (updated != 1) throw staleRun();
    return findActive(userId).orElseThrow(this::staleRun);
  }

  private void requireReachable(ExpeditionRunDto run, Map<String, Object> node) {
    if (!"map".equals(run.phase()) || !"available".equals(node.get("state"))) {
      throw invalidAction("该节点当前不可达");
    }
    if (run.currentNodeId() == null) {
      if (integerValue(node.get("layer")) != 0) throw invalidAction("该节点当前不可达");
      return;
    }
    Map<String, Object> current = findNode(run, run.currentNodeId());
    Object next = current.get("next");
    if (!"cleared".equals(current.get("state"))
        || !(next instanceof List<?> reachable)
        || !reachable.contains(node.get("id"))) {
      throw invalidAction("该节点当前不可达");
    }
  }

  private void requireEventNode(ExpeditionRunDto run, ExpeditionRunActionRequest request) {
    requirePhase(run, "event");
    if (request.relicId() != null
        || request.nodeId() == null
        || !request.nodeId().equals(run.currentNodeId())) {
      throw invalidAction("事件节点与当前远征阶段不匹配");
    }
    Map<String, Object> node = findNode(run, run.currentNodeId());
    if (!"event".equals(node.get("kind"))) throw invalidAction("当前节点不是事件节点");
  }

  private void requirePhase(ExpeditionRunDto run, String phase) {
    if (!phase.equals(run.phase())) throw invalidAction("当前远征阶段不支持该操作");
  }

  private void requireNoRelic(ExpeditionRunActionRequest request) {
    if (request.relicId() != null) throw invalidAction("该操作不能携带遗物 id");
  }

  private Map<String, Object> findNode(ExpeditionRunDto run, String nodeId) {
    if (nodeId == null) throw invalidAction("节点 id 必填");
    Map<String, Object> found = null;
    for (Map<String, Object> node : run.nodes()) {
      if (nodeId.equals(node.get("id"))) {
        if (found != null) throw mapChanged();
        found = node;
      }
    }
    if (found == null) throw invalidAction("节点不属于当前地图");
    return found;
  }

  private int clearNode(
      Long userId,
      String runId,
      List<Map<String, Object>> nodes,
      String nodeId,
      int layers,
      int hp,
      int maxHp) {
    Map<String, Object> node = findNode(nodes, nodeId);
    int layer = integerValue(node.get("layer"));
    node.put("state", "cleared");
    for (Map<String, Object> sibling : nodes) {
      if (integerValue(sibling.get("layer")) == layer
          && !nodeId.equals(sibling.get("id"))
          && "available".equals(sibling.get("state"))) {
        sibling.put("state", "bypassed");
        cancelBypassedPrefetch(userId, runId, stringValue(sibling.get("id")));
      }
    }
    Object next = node.get("next");
    if (!(next instanceof List<?> nextIds)) throw mapChanged();
    for (Object nextId : nextIds) {
      if (!(nextId instanceof String targetId)) throw mapChanged();
      Map<String, Object> target = findNode(nodes, targetId);
      if ("locked".equals(target.get("state"))) target.put("state", "available");
    }
    int halfway = Math.floorDiv(layers - 1, 2);
    boolean firstClearedOnHalfway =
        layer == halfway
            && nodes.stream()
                .noneMatch(
                    candidate ->
                        integerValue(candidate.get("layer")) == halfway
                            && !nodeId.equals(candidate.get("id"))
                            && "cleared".equals(candidate.get("state")));
    return firstClearedOnHalfway && hp < maxHp ? Math.min(maxHp, hp + 1) : hp;
  }

  private void cancelBypassedPrefetch(Long userId, String runId, String nodeId) {
    if (userId == null || runId == null) return;
    jdbcTemplate.update(
        "UPDATE pregenerated_problem"
            + " SET status = 'FAILED', payload = NULL, problem_id = NULL,"
            + " error_message = '远征分支节点已跳过', updated_at = now()"
            + " WHERE user_id = ? AND expedition_run_id = ?::uuid AND expedition_node_id = ?"
            + " AND status IN ('PENDING', 'READY')",
        userId,
        runId,
        nodeId);
  }

  private Map<String, Object> findNode(List<Map<String, Object>> nodes, String nodeId) {
    Map<String, Object> found = null;
    for (Map<String, Object> node : nodes) {
      if (nodeId.equals(node.get("id"))) {
        if (found != null) throw mapChanged();
        found = node;
      }
    }
    if (found == null) throw mapChanged();
    return found;
  }

  private List<Map<String, Object>> copyNodes(List<Map<String, Object>> nodes) {
    List<Map<String, Object>> copy = new ArrayList<>(nodes.size());
    for (Map<String, Object> node : nodes) copy.add(new LinkedHashMap<>(node));
    return copy;
  }

  private int heal(int hp, int maxHp, int amount) {
    return Math.min(maxHp, hp + amount);
  }

  private int integerValue(Object value) {
    if (value instanceof Number number) return number.intValue();
    throw mapChanged();
  }

  private String stringValue(Object value) {
    if (value instanceof String string) return string;
    throw mapChanged();
  }

  private void validateRelics(List<String> relics) {
    Set<String> unique = new HashSet<>();
    if (relics.size() > RELIC_IDS.size()) throw invalidAction("远征遗物状态无效");
    for (String relicId : relics) {
      if (relicId == null || !RELIC_IDS.contains(relicId) || !unique.add(relicId)) {
        throw invalidAction("远征遗物状态无效");
      }
    }
  }

  private String normalizeRunId(String runId) {
    try {
      return UUID.fromString(runId).toString();
    } catch (RuntimeException exception) {
      throw invalidAction("远征 runId 无效");
    }
  }

  /** Validate a problem request against the user's active run and its immutable focus chapter. */
  public void validateProblemRequest(Long userId, String runId, int chapter) {
    if (runId == null) {
      return;
    }
    List<Integer> focusChapters =
        jdbcTemplate.query(
            "SELECT focus_chapter FROM expedition_run WHERE user_id = ? AND run_id = ?::uuid"
                + " AND status = 'ACTIVE'",
            (rs, rowNum) -> (Integer) rs.getObject("focus_chapter"),
            userId,
            runId);
    if (userId == null || focusChapters.isEmpty()) {
      throw staleRun();
    }
    Integer focusChapter = focusChapters.getFirst();
    if (focusChapter == null) {
      return;
    }
    if (focusChapter != chapter) {
      throw chapterChanged();
    }
  }

  /** Validate an expedition problem against its server-owned node and current map phase. */
  public Integer validateProblemRequest(
      Long userId,
      String runId,
      String nodeId,
      int chapter,
      boolean allowPrefetch,
      String kcCode) {
    if (runId == null) {
      if (nodeId != null) throw problemBindingMismatch();
      return null;
    }
    if (userId == null || nodeId == null) throw problemBindingMismatch();
    List<ProblemRunState> rows =
        jdbcTemplate.query(
            "SELECT phase, focus_chapter, nodes::text AS nodes, layers, current_node_id"
                + " FROM expedition_run WHERE user_id = ? AND run_id = ?::uuid AND status = 'ACTIVE'",
            (rs, rowNum) ->
                new ProblemRunState(
                    rs.getString("phase"),
                    (Integer) rs.getObject("focus_chapter"),
                    listOfMap(rs.getString("nodes")),
                    rs.getInt("layers"),
                    rs.getString("current_node_id")),
            userId,
            runId);
    if (rows.isEmpty()) throw staleRun();
    ProblemRunState state = rows.getFirst();
    if (state.focusChapter() != null && state.focusChapter() != chapter) throw chapterChanged();
    Map<String, Object> node = findNode(state.nodes(), nodeId);
    if (!Objects.equals(node.get("kcCode"), kcCode)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EXPEDITION_PROBLEM_NODE_MISMATCH",
          "题目知识点与绑定的远征节点不一致");
    }
    Integer bossPhase = null;
    if ("boss".equals(node.get("kind"))) {
      bossPhase = node.get("bossPhase") == null ? 1 : integerValue(node.get("bossPhase"));
      if (bossPhase < 1 || bossPhase > 3) throw mapChanged();
    }
    if ("map".equals(state.phase()) && allowPrefetch) {
      if (!"available".equals(node.get("state"))) throw problemBindingMismatch();
      if (state.currentNodeId() == null) {
        if (integerValue(node.get("layer")) != 0) throw problemBindingMismatch();
        return bossPhase;
      }
      Map<String, Object> current = findNode(state.nodes(), state.currentNodeId());
      Object next = current.get("next");
      if (!"cleared".equals(current.get("state"))
          || !(next instanceof List<?> reachable)
          || !reachable.contains(nodeId)) {
        throw problemBindingMismatch();
      }
      return bossPhase;
    }
    if ("challenge".equals(state.phase())
        && nodeId.equals(state.currentNodeId())
        && ("available".equals(node.get("state")) || "current".equals(node.get("state")))) {
      return bossPhase;
    }
    throw problemBindingMismatch();
  }

  public boolean isActiveRun(Long userId, String runId) {
    return userId != null
        && runId != null
        && Boolean.TRUE.equals(
            jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM expedition_run WHERE user_id = ? AND run_id = ?::uuid AND status = 'ACTIVE')",
                Boolean.class,
                userId,
                runId));
  }

  /** 结束 / 弃局：删除当前用户的活跃 run。 */
  @Transactional
  public void abandon(Long userId) {
    if (userId == null) {
      return;
    }
    List<String> runIds =
        jdbcTemplate.query(
            "SELECT run_id::text FROM expedition_run WHERE user_id = ? FOR UPDATE",
            (rs, rowNum) -> rs.getString(1),
            userId);
    for (String runId : runIds) {
      jdbcTemplate.update(
          "UPDATE pregenerated_problem SET status = 'FAILED', payload = NULL, problem_id = NULL,"
              + " error_message = '远征已放弃，预加载题目已作废', updated_at = now()"
              + " WHERE user_id = ? AND expedition_run_id = ?::uuid"
              + " AND status IN ('PENDING', 'READY')",
          userId,
          runId);
    }
    jdbcTemplate.update("DELETE FROM expedition_run WHERE user_id = ?", userId);
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? List.of() : value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("serialize expedition_run payload failed", e);
    }
  }

  private void validateSnapshotSize(ExpeditionRunDto dto) {
    try {
      if (objectMapper.writeValueAsBytes(dto).length > MAX_SNAPSHOT_BYTES) {
        throw new BusinessException(
            HttpStatus.PAYLOAD_TOO_LARGE, "EXPEDITION_SNAPSHOT_TOO_LARGE", "远征存档快照过大");
      }
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("invalid expedition run snapshot", e);
    }
  }

  private void validatePersistablePhase(String phase) {
    if (!PERSISTABLE_PHASES.contains(phase)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "EXPEDITION_PHASE_INVALID", "远征阶段无效");
    }
  }

  private void validateMapSnapshot(ExistingRun current, ExpeditionRunDto requested) {
    if (current.layers() != requested.layers()
        || current.isDaily() != requested.isDaily()
        || current.nodes().size() != requested.nodes().size()) {
      throw mapChanged();
    }
    Map<String, Map<String, Object>> requestedById = new HashMap<>();
    for (Map<String, Object> node : requested.nodes()) {
      Object id = node.get("id");
      if (!(id instanceof String nodeId)
          || !(node.get("state") instanceof String state)
          || !NODE_STATES.contains(state)
          || !ALLOWED_NODE_FIELDS.containsAll(node.keySet())
          || (node.containsKey("bossPhase")
              && (!(node.get("bossPhase") instanceof Number bossPhase)
                  || bossPhase.intValue() < 1
                  || bossPhase.intValue() > 3
                  || !"boss".equals(node.get("kind"))))
          || requestedById.put(nodeId, node) != null) {
        throw mapChanged();
      }
    }
    if (requested.currentNodeId() != null
        && !requestedById.containsKey(requested.currentNodeId())) {
      throw mapChanged();
    }
    for (Map<String, Object> existingNode : current.nodes()) {
      Object id = existingNode.get("id");
      Map<String, Object> requestedNode =
          id instanceof String nodeId ? requestedById.get(nodeId) : null;
      if (requestedNode == null
          || IMMUTABLE_NODE_FIELDS.stream()
              .anyMatch(
                  field -> !Objects.equals(existingNode.get(field), requestedNode.get(field)))) {
        throw mapChanged();
      }
    }
  }

  private ExpeditionRunDto withRunId(
      ExpeditionRunDto run, String runId, VerifiedProgress progress) {
    return new ExpeditionRunDto(
        run.phase(),
        run.focusChapter(),
        run.nodes(),
        run.layers(),
        run.currentNodeId(),
        run.hp(),
        run.maxHp(),
        run.momentum(),
        run.bestMomentum(),
        progress.score(),
        run.relics(),
        run.isDaily(),
        run.history(),
        runId,
        progress.depth());
  }

  private BusinessException staleRun() {
    return new BusinessException(HttpStatus.CONFLICT, "EXPEDITION_RUN_CHANGED", "远征存档已变化，请刷新后重试");
  }

  private void lockUserRun(Long userId) {
    jdbcTemplate.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtext('pythontopia.expedition.run'), hashtext(?))",
        Object.class,
        userId.toString());
  }

  private BusinessException dailyChallengeAlreadyClaimed() {
    return new BusinessException(
        HttpStatus.CONFLICT, "EXPEDITION_DAILY_ALREADY_CLAIMED", "今日挑战已完成，请明天再来");
  }

  private BusinessException startRequired() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "EXPEDITION_RUN_START_REQUIRED", "新局只能通过服务端开局");
  }

  private BusinessException mapChanged() {
    return new BusinessException(
        HttpStatus.CONFLICT, "EXPEDITION_MAP_CHANGED", "远征地图配置不可修改，请重新加载存档");
  }

  private BusinessException chapterChanged() {
    return new BusinessException(
        HttpStatus.CONFLICT, "EXPEDITION_CHAPTER_CHANGED", "远征章节已绑定，不能在本局中切换章节");
  }

  private BusinessException invalidAction(String message) {
    return new BusinessException(HttpStatus.CONFLICT, "EXPEDITION_ACTION_INVALID", message);
  }

  record ExistingRun(
      String runId,
      String status,
      Integer focusChapter,
      List<Map<String, Object>> nodes,
      int layers,
      boolean isDaily) {
    ExistingRun(String runId, String status) {
      this(runId, status, null, List.of(), 0, false);
    }

    ExistingRun(String runId, String status, Integer focusChapter) {
      this(runId, status, focusChapter, List.of(), 0, false);
    }
  }

  private record VerifiedProgress(int score, int depth) {}

  private record ChallengeEvidence(
      String nodeId,
      String kcCode,
      int chapter,
      String questionFingerprint,
      Integer bossPhase,
      boolean passed,
      int score) {}

  private record IssuedProblem(
      String nodeId, String kcCode, int chapter, String problemId, String questionFingerprint) {}

  private record ChallengeSettlement(String nodeId, boolean passed) {}

  private record ProblemRunState(
      String phase,
      Integer focusChapter,
      List<Map<String, Object>> nodes,
      int layers,
      String currentNodeId) {}

  private List<Map<String, Object>> listOfMap(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, LIST_OF_MAP);
    } catch (JsonProcessingException e) {
      log.warn("deserialize expedition_run list-of-map failed: {}", e.getMessage());
      return List.of();
    }
  }

  private List<String> listOfString(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, LIST_OF_STRING);
    } catch (JsonProcessingException e) {
      log.warn("deserialize expedition_run list-of-string failed: {}", e.getMessage());
      return List.of();
    }
  }
}
