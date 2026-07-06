package com.pythontopia.workshop.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.workshop.dto.ContentFormat;
import com.pythontopia.workshop.dto.LearningResourceDto;
import com.pythontopia.workshop.dto.ResourceFeedbackStatus;
import com.pythontopia.workshop.dto.ResourceListItemDto;
import com.pythontopia.workshop.dto.ResourceStatusFlag;
import com.pythontopia.workshop.dto.ResourceType;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public class LearningResourceRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public LearningResourceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Long insert(
      Long userId,
      Long packId,
      List<String> kcCodes,
      Long chapterId,
      ResourceType type,
      String title,
      ContentFormat contentFormat,
      JsonNode content,
      List<Long> evidencePageIds,
      String summary,
      ResourceStatusFlag safetyStatus,
      ResourceStatusFlag groundingStatus,
      Long createdByRunId) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO learning_resource (
                    user_id, pack_id, kc_codes, chapter_id, type, title,
                    content_format, content, evidence_page_ids, summary,
                    safety_status, grounding_status, created_by_run_id
                  ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                  """,
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setLong(2, packId);
          Array kcArray = connection.createArrayOf("TEXT", kcCodes.toArray());
          ps.setArray(3, kcArray);
          if (chapterId == null) {
            ps.setNull(4, java.sql.Types.BIGINT);
          } else {
            ps.setLong(4, chapterId);
          }
          ps.setString(5, type.name());
          ps.setString(6, title);
          ps.setString(7, contentFormat.name());
          ps.setString(8, writeJson(content));
          Array pageIds =
              connection.createArrayOf("BIGINT", evidencePageIds.toArray(Long[]::new));
          ps.setArray(9, pageIds);
          ps.setString(10, summary);
          ps.setString(11, safetyStatus.name());
          ps.setString(12, groundingStatus.name());
          if (createdByRunId == null) {
            ps.setNull(13, java.sql.Types.BIGINT);
          } else {
            ps.setLong(13, createdByRunId);
          }
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("learning_resource insert returned no key");
    }
    return key.longValue();
  }

  public Optional<LearningResourceDto> findById(Long userId, Long resourceId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, pack_id, kc_codes, chapter_id, type, title,
                   content_format, content, evidence_page_ids, summary,
                   safety_status, grounding_status, feedback_status, feedback_note,
                   created_at
            FROM learning_resource
            WHERE id = ? AND user_id = ?
            """,
            this::map,
            resourceId,
            userId)
        .stream()
        .findFirst();
  }

  public List<ResourceListItemDto> list(
      Long userId, ResourceType type, String kcCode, int limit, int offset) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, type, title, summary, kc_codes, grounding_status, feedback_status, created_at
            FROM learning_resource
            WHERE user_id = ?
            """);
    List<Object> args = new ArrayList<>();
    args.add(userId);
    if (type != null) {
      sql.append(" AND type = ?");
      args.add(type.name());
    }
    if (kcCode != null && !kcCode.isBlank()) {
      sql.append(" AND ? = ANY(kc_codes)");
      args.add(kcCode);
    }
    sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbcTemplate.query(sql.toString(), this::mapListItem, args.toArray());
  }

  public int countCreatedAfter(Long userId, OffsetDateTime since) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM learning_resource
            WHERE user_id = ? AND created_at > ?
            """,
            Integer.class,
            userId,
            since);
    return count == null ? 0 : count;
  }

  public BigDecimal sumMasteryRewardsToday(Long userId, String kcCode, OffsetDateTime startOfDay) {
    BigDecimal sum =
        jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(delta), 0)
            FROM kc_mastery_event
            WHERE user_id = ? AND kc_code = ? AND created_at > ?
              AND source = 'MICRO_CHECK_PASS' AND delta > 0
            """,
            BigDecimal.class,
            userId,
            kcCode,
            startOfDay);
    return sum == null ? BigDecimal.ZERO : sum;
  }

  public void updateFeedback(
      Long userId, Long resourceId, ResourceFeedbackStatus status, String note) {
    jdbcTemplate.update(
        """
        UPDATE learning_resource
        SET feedback_status = ?, feedback_note = ?
        WHERE id = ? AND user_id = ?
        """,
        status.name(),
        note,
        resourceId,
        userId);
  }

  private LearningResourceDto map(ResultSet rs, int rowNum) throws SQLException {
    JsonNode content = readJson(rs.getString("content"));
    List<String> kcCodes = readStringArray(rs.getArray("kc_codes"));
    List<Long> pageIds = readLongArray(rs.getArray("evidence_page_ids"));
    return new LearningResourceDto(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getLong("pack_id"),
        kcCodes,
        (Long) rs.getObject("chapter_id"),
        ResourceType.valueOf(rs.getString("type")),
        rs.getString("title"),
        ContentFormat.valueOf(rs.getString("content_format")),
        content,
        pageIds,
        rs.getString("summary"),
        ResourceStatusFlag.valueOf(rs.getString("safety_status")),
        ResourceStatusFlag.valueOf(rs.getString("grounding_status")),
        ResourceFeedbackStatus.valueOf(rs.getString("feedback_status")),
        rs.getString("feedback_note"),
        offset(rs, "created_at"));
  }

  private ResourceListItemDto mapListItem(ResultSet rs, int rowNum) throws SQLException {
    return new ResourceListItemDto(
        rs.getLong("id"),
        ResourceType.valueOf(rs.getString("type")),
        rs.getString("title"),
        rs.getString("summary"),
        readStringArray(rs.getArray("kc_codes")),
        ResourceStatusFlag.valueOf(rs.getString("grounding_status")),
        ResourceFeedbackStatus.valueOf(rs.getString("feedback_status")),
        offset(rs, "created_at"));
  }

  private List<String> readStringArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (raw instanceof Object[] values) {
      return Arrays.stream(values).map(String::valueOf).toList();
    }
    return List.of();
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
      return Arrays.stream(values).map(value -> ((Number) value).longValue()).toList();
    }
    return List.of();
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }

  private String writeJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize learning_resource content", exception);
    }
  }

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to read learning_resource content", exception);
    }
  }
}
