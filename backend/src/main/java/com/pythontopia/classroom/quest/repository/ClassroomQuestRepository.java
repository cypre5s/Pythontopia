package com.pythontopia.classroom.quest.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.classroom.quest.dto.ClassroomQuestDto;
import com.pythontopia.learner.quest.dto.QuestNodeType;
import com.pythontopia.learner.quest.repository.QuestRepository.QuestInsert;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ClassroomQuestRepository {

  private static final int MAX_QUESTS_PER_INSERT = 1000;
  private static final String STUDENT_QUEST_VALUE_ROW =
      "(?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?::jsonb)";

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
               cq.target_problem_id, cq.target_resource_type,
               q.id AS student_quest_id,
               q.status AS student_quest_status,
               q.required_evidence_type AS student_required_evidence_type,
               q.target_problem_ids AS student_target_problem_ids,
               q.deep_link_route AS student_deep_link_route,
               q.deep_link_params AS student_deep_link_params
        FROM classroom_quest cq
        JOIN classroom_members cm ON cm.classroom_id = cq.classroom_id
        LEFT JOIN quest q
          ON q.user_id = cm.student_id
         AND q.source = 'CLASSROOM'
         AND q.source_ref_id = cq.id
        WHERE cm.student_id = ? AND cm.status = 'ACTIVE'
          AND cq.status = 'ACTIVE' AND cq.due_date >= ?
        ORDER BY cq.due_date ASC, cq.id ASC
        """,
        mapWithStudentQuest(),
        userId,
        today);
  }

  private RowMapper<ClassroomQuestDto> mapWithStudentQuest() {
    return (rs, rowNum) -> {
      ClassroomQuestDto base = map(rs);
      Long studentQuestId = (Long) rs.getObject("student_quest_id");
      String studentStatus = rs.getString("student_quest_status");
      String requiredType = rs.getString("student_required_evidence_type");
      List<Long> studentTargetProblemIds =
          readLongArray(rs.getArray("student_target_problem_ids"));
      String deepLinkRoute = rs.getString("student_deep_link_route");
      JsonNode deepLinkParams = readJson(rs.getString("student_deep_link_params"));
      return new ClassroomQuestDto(
          base.id(),
          base.classroomId(),
          base.title(),
          base.description(),
          base.kcCodes(),
          base.nodeType(),
          base.payload(),
          base.dueDate(),
          base.createdBy(),
          base.createdAt(),
          base.status(),
          base.targetProblemId(),
          base.targetResourceType(),
          studentQuestId,
          studentStatus,
          requiredType,
          studentTargetProblemIds,
          deepLinkRoute,
          deepLinkParams);
    };
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
          .filter(Objects::nonNull)
          .map(value -> ((Number) value).longValue())
          .toList();
    }
    return List.of();
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

  /**
   * 归档班级任务时同步撤回已分发的学生任务副本。
   *
   * <p>两条读路口径本来是分叉的：{@code /api/classroom/quests/mine} 过滤 {@code cq.status='ACTIVE'}
   * 会让归档任务消失，而 {@code /api/quests/today}（learner 包，只看 {@code quest.status + due_date}）
   * 仍会把它列成今日任务并允许完成、触发 mastery。这里把还没做完的副本置为 {@code SKIPPED}，
   * 已完成（COMPLETED）的历史不动，保留复盘证据。
   *
   * <p>SQL 写在 classroom 包内（learner 的 QuestRepository 不归本模块改）。
   */
  public int skipDistributedQuests(Long classroomQuestId) {
    return jdbcTemplate.update(
        """
        UPDATE quest
        SET status = 'SKIPPED', updated_at = now()
        WHERE source = 'CLASSROOM'
          AND source_ref_id = ?
          AND status IN ('OPEN', 'IN_PROGRESS')
        """,
        classroomQuestId);
  }

  /** Inserts student quest copies in bounded SQL statements and returns the number created. */
  public int insertStudentQuestsIfAbsent(List<QuestInsert> inserts) {
    int created = 0;
    for (int start = 0; start < inserts.size(); start += MAX_QUESTS_PER_INSERT) {
      List<QuestInsert> batch =
          inserts.subList(start, Math.min(start + MAX_QUESTS_PER_INSERT, inserts.size()));
      String values =
          String.join(", ", Collections.nCopies(batch.size(), STUDENT_QUEST_VALUE_ROW));
      List<Long> ids =
          jdbcTemplate.query(
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
                        ) VALUES %s
                        ON CONFLICT DO NOTHING
                        RETURNING id
                        """.formatted(values));
                int index = 1;
                for (QuestInsert insert : batch) {
                  ps.setLong(index++, insert.userId());
                  ps.setString(index++, insert.source().name());
                  if (insert.sourceRefId() == null) {
                    ps.setNull(index++, java.sql.Types.BIGINT);
                  } else {
                    ps.setLong(index++, insert.sourceRefId());
                  }
                  ps.setString(index++, insert.title());
                  ps.setString(index++, insert.description());
                  ps.setString(index++, insert.kcCode());
                  ps.setString(index++, insert.nodeType().name());
                  ps.setString(index++, writeJson(insert.payload()));
                  ps.setObject(index++, insert.dueDate());
                  ps.setInt(index++, insert.priority());
                  ps.setString(index++, insert.requiredEvidenceType());
                  if (insert.requiredMasteryDelta() == null) {
                    ps.setNull(index++, java.sql.Types.NUMERIC);
                  } else {
                    ps.setBigDecimal(index++, insert.requiredMasteryDelta());
                  }
                  List<Long> targetProblemIds =
                      insert.targetProblemIds() == null ? List.of() : insert.targetProblemIds();
                  ps.setArray(
                      index++,
                      connection.createArrayOf("BIGINT", targetProblemIds.toArray(Long[]::new)));
                  if (insert.targetResourceId() == null) {
                    ps.setNull(index++, java.sql.Types.BIGINT);
                  } else {
                    ps.setLong(index++, insert.targetResourceId());
                  }
                  ps.setString(index++, insert.deepLinkRoute());
                  ps.setString(index++, writeJson(insert.deepLinkParams()));
                }
                return ps;
              },
              (rs, rowNum) -> rs.getLong("id"));
      created += ids.size();
    }
    return created;
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
        rs.getString("target_resource_type"),
        null,
        null,
        null,
        List.of(),
        null,
        null);
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
