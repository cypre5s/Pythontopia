package com.pythontopia.learner.quest.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.quest.dto.QuestDto;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import com.pythontopia.learner.quest.dto.QuestSource;
import com.pythontopia.learner.quest.dto.QuestStatus;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class QuestRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public QuestRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Long insert(QuestInsert request) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO quest (
                    user_id, source, source_ref_id, title, description, kc_code,
                    node_type, payload, due_date, priority, status,
                    required_evidence_type, required_mastery_delta,
                    target_problem_ids, target_resource_id,
                    deep_link_route, deep_link_params
                  ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 'OPEN',
                            ?, ?, ?, ?, ?, ?::jsonb)
                  """,
                  new String[] {"id"});
          ps.setLong(1, request.userId());
          ps.setString(2, request.source().name());
          if (request.sourceRefId() == null) {
            ps.setNull(3, java.sql.Types.BIGINT);
          } else {
            ps.setLong(3, request.sourceRefId());
          }
          ps.setString(4, request.title());
          ps.setString(5, request.description());
          ps.setString(6, request.kcCode());
          ps.setString(7, request.nodeType().name());
          ps.setString(8, writeJson(request.payload()));
          ps.setObject(9, request.dueDate());
          ps.setInt(10, request.priority());
          ps.setString(11, request.requiredEvidenceType());
          if (request.requiredMasteryDelta() == null) {
            ps.setNull(12, java.sql.Types.NUMERIC);
          } else {
            ps.setBigDecimal(12, request.requiredMasteryDelta());
          }
          List<Long> targetProblemIds =
              request.targetProblemIds() == null ? List.of() : request.targetProblemIds();
          Array targetProblemArray =
              connection.createArrayOf("BIGINT", targetProblemIds.toArray(Long[]::new));
          ps.setArray(13, targetProblemArray);
          if (request.targetResourceId() == null) {
            ps.setNull(14, java.sql.Types.BIGINT);
          } else {
            ps.setLong(14, request.targetResourceId());
          }
          ps.setString(15, request.deepLinkRoute());
          ps.setString(16, writeJson(request.deepLinkParams()));
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("quest insert returned no key");
    }
    return key.longValue();
  }

  public Optional<QuestDto> findById(Long userId, Long questId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, source, source_ref_id, title, description, kc_code,
                   node_type, payload, due_date, priority, status,
                   required_evidence_type, required_mastery_delta,
                   target_problem_ids, target_resource_id,
                   deep_link_route, deep_link_params,
                   created_at, updated_at
            FROM quest
            WHERE id = ?
            """,
            (rs, rowNum) -> map(rs),
            questId)
        .stream()
        .findFirst();
  }

  public Optional<QuestDto> findOwned(Long userId, Long questId) {
    return findById(userId, questId).filter(quest -> quest.userId().equals(userId));
  }

  public List<QuestDto> findTodayQuests(Long userId, LocalDate today) {
    return jdbcTemplate.query(
        """
        SELECT id, user_id, source, source_ref_id, title, description, kc_code,
               node_type, payload, due_date, priority, status,
               required_evidence_type, required_mastery_delta,
               target_problem_ids, target_resource_id,
               deep_link_route, deep_link_params,
               created_at, updated_at
        FROM quest
        WHERE user_id = ? AND status IN ('OPEN', 'IN_PROGRESS') AND due_date >= ?
        ORDER BY priority DESC, due_date ASC, id ASC
        """,
        (rs, rowNum) -> map(rs),
        userId,
        today);
  }

  public List<QuestDto> list(Long userId, QuestStatus status, QuestSource source, int limit, int offset) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, user_id, source, source_ref_id, title, description, kc_code,
                   node_type, payload, due_date, priority, status,
                   required_evidence_type, required_mastery_delta,
                   target_problem_ids, target_resource_id,
                   deep_link_route, deep_link_params,
                   created_at, updated_at
            FROM quest
            WHERE user_id = ?
            """);
    List<Object> args = new ArrayList<>();
    args.add(userId);
    if (status != null) {
      sql.append(" AND status = ?");
      args.add(status.name());
    }
    if (source != null) {
      sql.append(" AND source = ?");
      args.add(source.name());
    }
    sql.append(" ORDER BY due_date ASC, priority DESC, id ASC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> map(rs), args.toArray());
  }

  public int updateStatus(Long userId, Long questId, QuestStatus newStatus) {
    return jdbcTemplate.update(
        """
        UPDATE quest
        SET status = ?, updated_at = now()
        WHERE id = ? AND user_id = ?
        """,
        newStatus.name(),
        questId,
        userId);
  }

  public int expireOverdue(LocalDate today) {
    return jdbcTemplate.update(
        """
        UPDATE quest
        SET status = 'EXPIRED', updated_at = now()
        WHERE status IN ('OPEN', 'IN_PROGRESS') AND due_date < ?
        """,
        today);
  }

  public void recordProgress(Long questId, String eventType, String note) {
    jdbcTemplate.update(
        """
        INSERT INTO quest_progress (quest_id, event_type, note)
        VALUES (?, ?, ?)
        """,
        questId,
        eventType,
        note);
  }

  public int archiveQuestsForPath(Long userId, Long pathId, QuestStatus terminalStatus) {
    return jdbcTemplate.update(
        """
        UPDATE quest
        SET status = ?, updated_at = now()
        WHERE user_id = ? AND source = 'PATH_PLANNER' AND source_ref_id IN (
          SELECT id FROM learning_path_node WHERE path_id = ?
        )
        AND status IN ('OPEN', 'IN_PROGRESS')
        """,
        terminalStatus.name(),
        userId,
        pathId);
  }

  public int archiveSystemDefaults(Long userId) {
    return jdbcTemplate.update(
        """
        UPDATE quest
        SET status = 'SKIPPED', updated_at = now()
        WHERE user_id = ? AND source = 'SYSTEM_DEFAULT' AND status IN ('OPEN', 'IN_PROGRESS')
        """,
        userId);
  }

  public boolean hasAnyQuest(Long userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quest WHERE user_id = ?", Integer.class, userId);
    return count != null && count > 0;
  }

  private QuestDto map(ResultSet rs) throws SQLException {
    return new QuestDto(
        rs.getLong("id"),
        rs.getLong("user_id"),
        QuestSource.valueOf(rs.getString("source")),
        (Long) rs.getObject("source_ref_id"),
        rs.getString("title"),
        rs.getString("description"),
        rs.getString("kc_code"),
        QuestNodeType.valueOf(rs.getString("node_type")),
        readJson(rs.getString("payload")),
        rs.getObject("due_date", LocalDate.class),
        rs.getInt("priority"),
        QuestStatus.valueOf(rs.getString("status")),
        rs.getString("required_evidence_type"),
        rs.getBigDecimal("required_mastery_delta"),
        readLongArray(rs.getArray("target_problem_ids")),
        (Long) rs.getObject("target_resource_id"),
        rs.getString("deep_link_route"),
        readJson(rs.getString("deep_link_params")),
        offset(rs, "created_at"),
        offset(rs, "updated_at"));
  }

  private List<Long> readLongArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (raw instanceof Long[] values) {
      return Arrays.asList(values);
    }
    if (raw instanceof Object[] values) {
      return Arrays.stream(values)
          .filter(java.util.Objects::nonNull)
          .map(value -> ((Number) value).longValue())
          .toList();
    }
    return List.of();
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? objectMapper.createObjectNode() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialise quest payload", exception);
    }
  }

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to parse quest payload", exception);
    }
  }

  public record QuestInsert(
      Long userId,
      QuestSource source,
      Long sourceRefId,
      String title,
      String description,
      String kcCode,
      QuestNodeType nodeType,
      Object payload,
      LocalDate dueDate,
      int priority,
      String requiredEvidenceType,
      BigDecimal requiredMasteryDelta,
      List<Long> targetProblemIds,
      Long targetResourceId,
      String deepLinkRoute,
      Object deepLinkParams) {

    public QuestInsert(
        Long userId,
        QuestSource source,
        Long sourceRefId,
        String title,
        String description,
        String kcCode,
        QuestNodeType nodeType,
        Object payload,
        LocalDate dueDate,
        int priority) {
      this(
          userId,
          source,
          sourceRefId,
          title,
          description,
          kcCode,
          nodeType,
          payload,
          dueDate,
          priority,
          null,
          null,
          List.of(),
          null,
          null,
          null);
    }
  }
}
