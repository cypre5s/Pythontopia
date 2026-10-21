package com.pythontopia.admin;

import com.pythontopia.auth.security.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminStudentDetailRepository {

  private final JdbcTemplate jdbcTemplate;

  public AdminStudentDetailRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<AdminStudentDetailDto.Account> findStudent(long userId) {
    return jdbcTemplate
        .query(
            "SELECT id, username, display_name, role, status, created_at FROM users"
                + " WHERE id = ? AND role = 'STUDENT'",
            (rs, rowNum) ->
                new AdminStudentDetailDto.Account(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    Role.valueOf(rs.getString("role")),
                    rs.getString("status"),
                    rs.getObject("created_at", OffsetDateTime.class)),
            userId)
        .stream()
        .findFirst();
  }

  public List<AdminStudentDetailDto.KcMastery> findMastery(long userId) {
    return jdbcTemplate.query(
        """
        SELECT kc.kc_name, kc.chapter_order, km.mastery_score, km.confidence, km.updated_at
        FROM kc_mastery km
        LEFT JOIN course_kc kc ON kc.pack_id = 43 AND kc.kc_code = km.kc_code
        WHERE km.user_id = ?
        ORDER BY kc.chapter_order NULLS LAST, kc.id NULLS LAST, km.kc_code
        LIMIT 100
        """,
        (rs, rowNum) ->
            new AdminStudentDetailDto.KcMastery(
                rs.getString("kc_name"),
                (Integer) rs.getObject("chapter_order"),
                rs.getBigDecimal("mastery_score"),
                rs.getBigDecimal("confidence"),
                rs.getObject("updated_at", OffsetDateTime.class)),
        userId);
  }

  public List<AdminStudentDetailDto.LearningActivity> findActivity(long userId) {
    return jdbcTemplate.query(
        """
        SELECT id, event_type, source, created_at
        FROM learning_event_log
        WHERE user_id = ?
        ORDER BY created_at DESC, id DESC
        LIMIT 50
        """,
        (rs, rowNum) ->
            new AdminStudentDetailDto.LearningActivity(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getString("source"),
                rs.getObject("created_at", OffsetDateTime.class)),
        userId);
  }

  public List<AdminStudentDetailDto.Submission> findSubmissions(long userId) {
    return jdbcTemplate.query(
        """
        SELECT ps.id, cp.title AS problem_title, ps.status,
               jr.verdict AS judge_verdict, jr.passed_cases, jr.total_cases, ps.submitted_at
        FROM practice_submission ps
        LEFT JOIN course_problem cp ON cp.id = ps.problem_id
        LEFT JOIN judge_result jr ON jr.submission_id = ps.id
        WHERE ps.user_id = ?
        ORDER BY ps.submitted_at DESC, ps.id DESC
        LIMIT 20
        """,
        (rs, rowNum) -> mapSubmission(rs),
        userId);
  }

  public List<AdminStudentDetailDto.ClassroomMembership> findClassrooms(long userId) {
    return jdbcTemplate.query(
        """
        SELECT c.id, c.name, teacher.display_name AS teacher_name, c.status, cm.joined_at
        FROM classroom_members cm
        JOIN classrooms c ON c.id = cm.classroom_id
        JOIN users teacher ON teacher.id = c.teacher_id
        WHERE cm.student_id = ? AND cm.status = 'ACTIVE'
        ORDER BY c.id
        """,
        (rs, rowNum) ->
            new AdminStudentDetailDto.ClassroomMembership(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("teacher_name"),
                rs.getString("status"),
                rs.getObject("joined_at", OffsetDateTime.class)),
        userId);
  }

  private AdminStudentDetailDto.Submission mapSubmission(ResultSet rs) throws SQLException {
    return new AdminStudentDetailDto.Submission(
        rs.getLong("id"),
        rs.getString("problem_title"),
        rs.getString("status"),
        rs.getString("judge_verdict"),
        rs.getInt("passed_cases"),
        rs.getInt("total_cases"),
        rs.getObject("submitted_at", OffsetDateTime.class));
  }
}
