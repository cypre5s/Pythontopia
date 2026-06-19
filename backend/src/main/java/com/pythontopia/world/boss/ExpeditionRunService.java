package com.pythontopia.world.boss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 无尽远征 run 跨会话持久化（后端权威）。
 *
 * <p>每个用户至多一条「活跃 run」（{@code expedition_run.user_id} 主键 → UPSERT 覆盖、DELETE 弃局）：
 *
 * <ul>
 *   <li>{@link #findActive}：GET 活跃 run（无则 empty → controller 返 204）；
 *   <li>{@link #save}：PUT upsert 活跃 run（结构化标量列 + nodes/relics/history JSONB）；
 *   <li>{@link #abandon}：DELETE 结束 / 弃局。
 * </ul>
 *
 * <p>nodes/relics/history 以 JSONB 整体存取（{@link ObjectMapper} 序列化），后端不解释其内部结构。
 * 掌握度 / 判题结果由 learner_mastery 等权威表负责，本表只存「一局 run 的过程进度」。
 */
@Service
public class ExpeditionRunService {

  private static final Logger log = LoggerFactory.getLogger(ExpeditionRunService.class);
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
            "SELECT phase, focus_chapter, nodes::text AS nodes, layers, current_node_id,"
                + " hp, max_hp, momentum, best_momentum, score, relics::text AS relics,"
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
                    rs.getInt("score"),
                    listOfString(rs.getString("relics")),
                    rs.getBoolean("is_daily"),
                    listOfMap(rs.getString("history"))),
            userId);
    return rows.stream().findFirst();
  }

  /** UPSERT 当前用户的活跃 run（单活跃：user_id 主键冲突即覆盖）。 */
  public ExpeditionRunDto save(Long userId, ExpeditionRunDto dto) {
    if (userId == null) {
      return dto;
    }
    jdbcTemplate.update(
        "INSERT INTO expedition_run"
            + " (user_id, phase, focus_chapter, hp, max_hp, momentum, best_momentum, score,"
            + " layers, current_node_id, is_daily, nodes, relics, history, status, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, 'ACTIVE', now())"
            + " ON CONFLICT (user_id) DO UPDATE SET"
            + " phase = EXCLUDED.phase, focus_chapter = EXCLUDED.focus_chapter, hp = EXCLUDED.hp,"
            + " max_hp = EXCLUDED.max_hp, momentum = EXCLUDED.momentum,"
            + " best_momentum = EXCLUDED.best_momentum, score = EXCLUDED.score,"
            + " layers = EXCLUDED.layers, current_node_id = EXCLUDED.current_node_id,"
            + " is_daily = EXCLUDED.is_daily, nodes = EXCLUDED.nodes, relics = EXCLUDED.relics,"
            + " history = EXCLUDED.history, status = 'ACTIVE', updated_at = now()",
        userId,
        dto.phase(),
        dto.focusChapter(),
        dto.hp(),
        dto.maxHp(),
        dto.momentum(),
        dto.bestMomentum(),
        dto.score(),
        dto.layers(),
        dto.currentNodeId(),
        dto.isDaily(),
        serialize(dto.nodes()),
        serialize(dto.relics()),
        serialize(dto.history()));
    return dto;
  }

  /** 结束 / 弃局：删除当前用户的活跃 run。 */
  public void abandon(Long userId) {
    if (userId == null) {
      return;
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
