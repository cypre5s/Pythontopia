package com.pythontopia.world.boss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import com.pythontopia.world.boss.dto.FrontierPickDto;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 *   <li>{@link #save}：PUT upsert 活跃 run（结构化标量列 + nodes/relics/history JSONB）；
 *   <li>{@link #abandon}：DELETE 结束 / 弃局。
 * </ul>
 *
 * <p>地图拓扑与题目配置在开局后不可由快照改写；节点进度、HP、遗物和历史仍由客户端提供，不能用于正式奖励或排行榜。 shortcut: per-action state is
 * client-authored; move it to a server action ledger before granting rewards or trusting
 * completion. 掌握度 / 判题结果由 learner_mastery 等权威表负责，成绩由 expedition_verified_attempt 汇总。
 */
@Service
public class ExpeditionRunService {

  private static final Logger log = LoggerFactory.getLogger(ExpeditionRunService.class);
  private static final int MAX_SNAPSHOT_BYTES = 64 * 1024;
  private static final Set<String> PERSISTABLE_PHASES =
      Set.of("map", "event", "challenge", "reward");
  private static final Set<String> NODE_STATES =
      Set.of("locked", "available", "current", "cleared", "bypassed");
  private static final Set<String> IMMUTABLE_NODE_FIELDS =
      Set.of("id", "layer", "col", "kind", "kcCode", "challengeKind", "difficulty", "next");
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
                + " CASE WHEN phase IN ('event', 'challenge', 'reward') THEN phase ELSE 'map' END AS phase,"
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

  /** 保存当前 run；身份、章节和开局地图不可改写，快照中的分数不作为事实来源。 */
  @Transactional
  public ExpeditionRunDto save(Long userId, ExpeditionRunDto dto) {
    return persistSnapshot(userId, dto, false);
  }

  /** Start a run with a server-generated map and reset all client-supplied gameplay state. */
  @Transactional
  public ExpeditionRunDto start(
      Long userId, Integer focusChapter, boolean isDaily, List<FrontierPickDto> picks) {
    long seed =
        isDaily
            ? LocalDate.now(ZoneOffset.UTC).toEpochDay()
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
    return persistSnapshot(userId, initial, true);
  }

  private ExpeditionRunDto persistSnapshot(Long userId, ExpeditionRunDto dto, boolean allowNewRun) {
    if (userId == null) {
      return dto;
    }
    validateSnapshotSize(dto);
    validatePersistablePhase(dto.phase());
    jdbcTemplate.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtext('pythontopia.expedition.run'), hashtext(?))",
        Object.class,
        userId.toString());
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
    Map<String, Map<String, Object>> requestedById = new java.util.HashMap<>();
    for (Map<String, Object> node : requested.nodes()) {
      Object id = node.get("id");
      if (!(id instanceof String nodeId)
          || !(node.get("state") instanceof String state)
          || !NODE_STATES.contains(state)
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
          || !existingNode.keySet().equals(requestedNode.keySet())
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
