package com.pythontopia.classroom.repository;

import com.pythontopia.classroom.domain.Classroom;
import com.pythontopia.classroom.dto.ClassroomMemberDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ClassroomRepository {

  private final JdbcTemplate jdbcTemplate;

  public ClassroomRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Classroom create(String name, String description, Long teacherId, String joinCode) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          var statement =
              connection.prepareStatement(
                  """
                  INSERT INTO classrooms (name, description, teacher_id, pack_id, join_code)
                  VALUES (?, ?, ?, 43, ?)
                  """,
                  new String[] {"id"});
          statement.setString(1, name);
          statement.setString(2, description == null ? "" : description);
          statement.setLong(3, teacherId);
          statement.setString(4, joinCode);
          return statement;
        },
        keyHolder);
    return findById(keyHolder.getKey().longValue()).orElseThrow();
  }

  public Optional<Classroom> findById(Long id) {
    return jdbcTemplate
        .query(
            """
            SELECT id, name, description, teacher_id, pack_id, join_code, status
            FROM classrooms
            WHERE id = ?
            """,
            this::map,
            id)
        .stream()
        .findFirst();
  }

  public Optional<Classroom> findByJoinCode(String joinCode) {
    return jdbcTemplate
        .query(
            """
            SELECT id, name, description, teacher_id, pack_id, join_code, status
            FROM classrooms
            WHERE join_code = ?
            """,
            this::map,
            joinCode)
        .stream()
        .findFirst();
  }

  public List<Classroom> findByTeacher(Long teacherId) {
    return jdbcTemplate.query(
        """
        SELECT id, name, description, teacher_id, pack_id, join_code, status
        FROM classrooms
        WHERE teacher_id = ?
        ORDER BY id
        """,
        this::map,
        teacherId);
  }

  public List<Classroom> findByStudent(Long studentId) {
    return jdbcTemplate.query(
        """
        SELECT c.id, c.name, c.description, c.teacher_id, c.pack_id, c.join_code, c.status
        FROM classrooms c
        JOIN classroom_members m ON m.classroom_id = c.id
        WHERE m.student_id = ? AND m.status = 'ACTIVE'
        ORDER BY c.id
        """,
        this::map,
        studentId);
  }

  /**
   * 加入班级（幂等）：{@code classroom_members} 上有 {@code UNIQUE (classroom_id, student_id)}，
   * 而 {@link #removeMember} 是软删（{@code status='REMOVED'}）。裸 INSERT 会让「被移出的学生」
   * 永远撞唯一键、再也回不来，因此这里改为 upsert 复活成员行。
   */
  public void addMember(Long classroomId, Long studentId) {
    jdbcTemplate.update(
        """
        INSERT INTO classroom_members (classroom_id, student_id, status)
        VALUES (?, ?, 'ACTIVE')
        ON CONFLICT (classroom_id, student_id) DO UPDATE
          SET status = 'ACTIVE', joined_at = now()
        """,
        classroomId,
        studentId);
  }

  /** 成员当前状态（ACTIVE / REMOVED）；从未加入过则为 empty。 */
  public Optional<String> findMemberStatus(Long classroomId, Long studentId) {
    return jdbcTemplate
        .query(
            """
            SELECT status FROM classroom_members
            WHERE classroom_id = ? AND student_id = ?
            """,
            (rs, rowNum) -> rs.getString("status"),
            classroomId,
            studentId)
        .stream()
        .findFirst();
  }

  public List<ClassroomMemberDto> findMembers(Long classroomId) {
    return jdbcTemplate.query(
        """
        SELECT u.id AS student_id, u.username, u.display_name, m.status
        FROM classroom_members m
        JOIN users u ON u.id = m.student_id
        WHERE m.classroom_id = ? AND m.status = 'ACTIVE'
        ORDER BY u.id
        """,
        (rs, rowNum) ->
            new ClassroomMemberDto(
                rs.getLong("student_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("status")),
        classroomId);
  }

  public int removeMember(Long classroomId, Long studentId) {
    return jdbcTemplate.update(
        """
        UPDATE classroom_members
        SET status = 'REMOVED'
        WHERE classroom_id = ? AND student_id = ?
        """,
        classroomId,
        studentId);
  }

  public int updateBasics(Long classroomId, String name, String description) {
    return jdbcTemplate.update(
        """
        UPDATE classrooms
        SET name = ?, description = ?
        WHERE id = ?
        """,
        name,
        description == null ? "" : description,
        classroomId);
  }

  public int updateJoinCode(Long classroomId, String joinCode) {
    return jdbcTemplate.update(
        """
        UPDATE classrooms SET join_code = ? WHERE id = ?
        """,
        joinCode,
        classroomId);
  }

  public List<ClassroomMemberDto> findMembersByStatus(Long classroomId, String status) {
    return jdbcTemplate.query(
        """
        SELECT u.id AS student_id, u.username, u.display_name, m.status
        FROM classroom_members m
        JOIN users u ON u.id = m.student_id
        WHERE m.classroom_id = ? AND m.status = ?
        ORDER BY u.id
        """,
        (rs, rowNum) ->
            new ClassroomMemberDto(
                rs.getLong("student_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("status")),
        classroomId,
        status);
  }

  private Classroom map(ResultSet rs, int rowNum) throws SQLException {
    return new Classroom(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getLong("teacher_id"),
        rs.getLong("pack_id"),
        rs.getString("join_code"),
        rs.getString("status"));
  }
}
