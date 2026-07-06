package com.pythontopia.learner.path.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.path.dto.LearningPathDto;
import com.pythontopia.learner.path.dto.LearningPathNodeDto;
import com.pythontopia.learner.path.dto.LearningPathStatus;
import com.pythontopia.learner.path.dto.PathNodeType;
import com.pythontopia.learner.path.dto.SuggestedResourceType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class LearningPathRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public LearningPathRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public List<Long> archiveActivePaths(Long userId, Long packId) {
    List<Long> ids =
        jdbcTemplate.query(
            "SELECT id FROM learning_path WHERE user_id = ? AND pack_id = ? AND status = 'ACTIVE'",
            (rs, rowNum) -> rs.getLong(1),
            userId,
            packId);
    if (!ids.isEmpty()) {
      jdbcTemplate.update(
          "UPDATE learning_path SET status = 'ARCHIVED' WHERE user_id = ? AND pack_id = ? AND status = 'ACTIVE'",
          userId,
          packId);
    }
    return ids;
  }

  public int nextVersion(Long userId, Long packId) {
    Integer current =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version), 0) FROM learning_path WHERE user_id = ? AND pack_id = ?",
            Integer.class,
            userId,
            packId);
    return (current == null ? 0 : current) + 1;
  }

  public Long insertPath(
      Long userId,
      Long packId,
      int version,
      String goalSummary,
      OffsetDateTime nextReviewAt,
      Long sourceRunId) {
    return insertPathInternal(
        userId, packId, version, "ACTIVE", goalSummary, nextReviewAt, sourceRunId);
  }

  public Long insertDraftPath(
      Long userId,
      Long packId,
      int version,
      String goalSummary,
      OffsetDateTime nextReviewAt,
      Long sourceRunId) {
    return insertPathInternal(
        userId, packId, version, "DRAFT", goalSummary, nextReviewAt, sourceRunId);
  }

  public int updateStatus(Long pathId, LearningPathStatus status) {
    return jdbcTemplate.update(
        "UPDATE learning_path SET status = ? WHERE id = ?",
        status.name(),
        pathId);
  }

  private Long insertPathInternal(
      Long userId,
      Long packId,
      int version,
      String status,
      String goalSummary,
      OffsetDateTime nextReviewAt,
      Long sourceRunId) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO learning_path (
                    user_id, pack_id, version, status, goal_summary,
                    next_review_at, source_run_id
                  ) VALUES (?, ?, ?, ?, ?, ?, ?)
                  """,
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setLong(2, packId);
          ps.setInt(3, version);
          ps.setString(4, status);
          ps.setString(5, goalSummary);
          if (nextReviewAt == null) {
            ps.setNull(6, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
          } else {
            ps.setObject(6, nextReviewAt);
          }
          if (sourceRunId == null) {
            ps.setNull(7, java.sql.Types.BIGINT);
          } else {
            ps.setLong(7, sourceRunId);
          }
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("learning_path insert returned no key");
    }
    return key.longValue();
  }

  public Long insertNode(Long pathId, LearningPathNodeDraft node) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO learning_path_node (
                    path_id, day_index, order_in_day, kc_code, node_type,
                    reason_summary, evidence_summary, suggested_resource_type,
                    suggested_problem_id, payload
                  ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                  """,
                  new String[] {"id"});
          ps.setLong(1, pathId);
          ps.setInt(2, node.dayIndex());
          ps.setInt(3, node.orderInDay());
          ps.setString(4, node.kcCode());
          ps.setString(5, node.nodeType().name());
          ps.setString(6, node.reasonSummary());
          ps.setString(7, node.evidenceSummary());
          if (node.suggestedResourceType() == null) {
            ps.setNull(8, java.sql.Types.VARCHAR);
          } else {
            ps.setString(8, node.suggestedResourceType().name());
          }
          if (node.suggestedProblemId() == null) {
            ps.setNull(9, java.sql.Types.BIGINT);
          } else {
            ps.setLong(9, node.suggestedProblemId());
          }
          ps.setString(10, writeJson(node.sourceClassroomQuestId()));
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("learning_path_node insert returned no key");
    }
    return key.longValue();
  }

  public Optional<LearningPathDto> findActive(Long userId, Long packId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, pack_id, version, status, goal_summary,
                   generated_at, next_review_at, source_run_id
            FROM learning_path
            WHERE user_id = ? AND pack_id = ? AND status = 'ACTIVE'
            ORDER BY version DESC LIMIT 1
            """,
            (rs, rowNum) -> mapPath(rs),
            userId,
            packId)
        .stream()
        .findFirst()
        .map(this::attachNodes);
  }

  public Optional<LearningPathDto> findById(Long userId, Long pathId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, pack_id, version, status, goal_summary,
                   generated_at, next_review_at, source_run_id
            FROM learning_path
            WHERE id = ? AND user_id = ?
            """,
            (rs, rowNum) -> mapPath(rs),
            pathId,
            userId)
        .stream()
        .findFirst()
        .map(this::attachNodes);
  }

  public List<LearningPathDto> listArchived(Long userId, Long packId, int limit) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, pack_id, version, status, goal_summary,
                   generated_at, next_review_at, source_run_id
            FROM learning_path
            WHERE user_id = ? AND pack_id = ? AND status = 'ARCHIVED'
            ORDER BY version DESC LIMIT ?
            """,
            (rs, rowNum) -> mapPath(rs),
            userId,
            packId,
            limit)
        .stream()
        .map(this::attachNodes)
        .toList();
  }

  public List<LearningPathNodeDto> findNodes(Long pathId) {
    return jdbcTemplate.query(
        """
        SELECT id, path_id, day_index, order_in_day, kc_code, node_type,
               reason_summary, evidence_summary, suggested_resource_type,
               suggested_problem_id, payload
        FROM learning_path_node
        WHERE path_id = ?
        ORDER BY day_index, order_in_day
        """,
        (rs, rowNum) -> mapNode(rs),
        pathId);
  }

  private LearningPathDto attachNodes(LearningPathDto path) {
    List<LearningPathNodeDto> nodes = findNodes(path.id());
    return new LearningPathDto(
        path.id(),
        path.userId(),
        path.packId(),
        path.version(),
        path.status(),
        path.goalSummary(),
        path.generatedAt(),
        path.nextReviewAt(),
        path.sourceRunId(),
        nodes);
  }

  private LearningPathDto mapPath(ResultSet rs) throws SQLException {
    return new LearningPathDto(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getLong("pack_id"),
        rs.getInt("version"),
        LearningPathStatus.valueOf(rs.getString("status")),
        rs.getString("goal_summary"),
        offset(rs, "generated_at"),
        offset(rs, "next_review_at"),
        (Long) rs.getObject("source_run_id"),
        List.of());
  }

  private LearningPathNodeDto mapNode(ResultSet rs) throws SQLException {
    String suggestedResource = rs.getString("suggested_resource_type");
    String payloadRaw = rs.getString("payload");
    Long classroomQuestId = readClassroomQuestId(payloadRaw);
    return new LearningPathNodeDto(
        rs.getLong("id"),
        rs.getLong("path_id"),
        rs.getInt("day_index"),
        rs.getInt("order_in_day"),
        rs.getString("kc_code"),
        PathNodeType.valueOf(rs.getString("node_type")),
        rs.getString("reason_summary"),
        rs.getString("evidence_summary"),
        suggestedResource == null ? null : SuggestedResourceType.valueOf(suggestedResource),
        (Long) rs.getObject("suggested_problem_id"),
        classroomQuestId);
  }

  private Long readClassroomQuestId(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      var node = objectMapper.readTree(json);
      if (node.hasNonNull("source_classroom_quest_id")) {
        return node.get("source_classroom_quest_id").asLong();
      }
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to read learning_path_node payload", exception);
    }
    return null;
  }

  private String writeJson(Long sourceClassroomQuestId) {
    try {
      var node = objectMapper.createObjectNode();
      if (sourceClassroomQuestId != null) {
        node.put("source_classroom_quest_id", sourceClassroomQuestId);
      }
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialise path node payload", exception);
    }
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }

  public record LearningPathNodeDraft(
      int dayIndex,
      int orderInDay,
      String kcCode,
      PathNodeType nodeType,
      String reasonSummary,
      String evidenceSummary,
      SuggestedResourceType suggestedResourceType,
      Long suggestedProblemId,
      Long sourceClassroomQuestId) {}

  public List<LearningPathNodeDraft> emptyDrafts() {
    return new ArrayList<>();
  }
}
