package com.pythontopia.classroom.quest.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.classroom.quest.dto.ClassroomQuestDto;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ClassroomQuestRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ClassroomQuestRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Long insert(ClassroomQuestInsert insert) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO classroom_quest (
                    classroom_id, title, description, kc_codes, node_type, payload,
                    due_date, created_by, status, target_problem_id, target_resource_type
                  ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, 'ACTIVE', ?, ?)
                  """,
                  new String[] {"id"});
          ps.setLong(1, insert.classroomId());
          ps.setString(2, insert.title());
          ps.setString(3, insert.description());
          Array kcArray =
              connection.createArrayOf("text", insert.kcCodes().toArray(new String[0]));
          ps.setArray(4, kcArray);
          ps.setString(5, insert.nodeType().name());
          ps.setString(6, writeJson(insert.payload()));
          ps.setObject(7, insert.dueDate());
          ps.setLong(8, insert.createdBy());
          if (insert.targetProblemId() == null) {
            ps.setNull(9, java.sql.Types.BIGINT);
          } else {
            ps.setLong(9, insert.targetProblemId());
          }
          ps.setString(10, insert.targetResourceType());
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("classroom_quest insert returned no key");
    }
    return key.longValue();
  }

  public Optional<ClassroomQuestDto> findById(Long id) {
    return jdbcTemplate
        .query(
            """
            SELECT id, classroom_id, title, description, kc_codes,
                   node_type, payload, due_date, created_by, created_at, status,
                   target_problem_id, target_resource_type
            FROM classroom_quest
            WHERE id = ?
            """,
            (rs, rowNum) -> map(rs),
            id)
        .stream()
        .findFirst();
  }

  public List<ClassroomQuestDto> listActiveForStudent(Long userId, LocalDate today) {
    return jdbcTemplate.query(
        """
        SELECT cq.id, cq.classroom_id, cq.title, cq.description, cq.kc_codes,
               cq.node_type, cq.payload, cq.due_date, cq.created_by, cq.created_at, cq.status,
               cq.target_problem_id, cq.target_resource_type
        FROM classroom_quest cq
        JOIN classroom_members cm ON cm.classroom_id = cq.classroom_id
        WHERE cm.student_id = ? AND cm.status = 'ACTIVE'
          AND cq.status = 'ACTIVE' AND cq.due_date >= ?
        ORDER BY cq.due_date ASC, cq.id ASC
        """,
        (rs, rowNum) -> map(rs),
        userId,
        today);
  }

  public List<ClassroomQuestDto> listActiveForClassroom(Long classroomId, LocalDate today) {
    return jdbcTemplate.query(
        """
        SELECT id, classroom_id, title, description, kc_codes,
               node_type, payload, due_date, created_by, created_at, status,
               target_problem_id, target_resource_type
        FROM classroom_quest
        WHERE classroom_id = ? AND status = 'ACTIVE' AND due_date >= ?
        ORDER BY due_date ASC, id ASC
        """,
        (rs, rowNum) -> map(rs),
        classroomId,
        today);
  }

  public List<ClassroomQuestDto> listForClassroom(Long classroomId) {
    return jdbcTemplate.query(
        """
        SELECT id, classroom_id, title, description, kc_codes,
               node_type, payload, due_date, created_by, created_at, status,
               target_problem_id, target_resource_type
        FROM classroom_quest
        WHERE classroom_id = ?
        ORDER BY status ASC, due_date DESC, id DESC
        """,
        (rs, rowNum) -> map(rs),
        classroomId);
  }

  public List<ClassroomQuestDto> findByIds(List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
    return jdbcTemplate.query(
        """
        SELECT id, classroom_id, title, description, kc_codes,
               node_type, payload, due_date, created_by, created_at, status,
               target_problem_id, target_resource_type
        FROM classroom_quest
        WHERE id IN (
        """
            + placeholders
            + ")",
        (rs, rowNum) -> map(rs),
        ids.toArray());
  }

  public int archive(Long id) {
    return jdbcTemplate.update(
        """
        UPDATE classroom_quest SET status = 'ARCHIVED'
        WHERE id = ? AND status = 'ACTIVE'
        """,
        id);
  }

  private ClassroomQuestDto map(ResultSet rs) throws SQLException {
    return new ClassroomQuestDto(
        rs.getLong("id"),
        rs.getLong("classroom_id"),
        rs.getString("title"),
        rs.getString("description"),
        readStringArray(rs.getArray("kc_codes")),
        QuestNodeType.valueOf(rs.getString("node_type")),
        readJson(rs.getString("payload")),
        rs.getObject("due_date", LocalDate.class),
        rs.getLong("created_by"),
        offset(rs, "created_at"),
        rs.getString("status"),
        (Long) rs.getObject("target_problem_id"),
        rs.getString("target_resource_type"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
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

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to parse classroom_quest payload", exception);
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? objectMapper.createObjectNode() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialise classroom_quest payload", exception);
    }
  }

  public record ClassroomQuestInsert(
      Long classroomId,
      String title,
      String description,
      List<String> kcCodes,
      QuestNodeType nodeType,
      Object payload,
      LocalDate dueDate,
      Long createdBy,
      Long targetProblemId,
      String targetResourceType) {}
}
