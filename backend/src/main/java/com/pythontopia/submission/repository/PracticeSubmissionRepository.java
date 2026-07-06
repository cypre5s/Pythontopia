package com.pythontopia.submission.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.submission.dto.JudgeResultDto;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionSource;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
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
public class PracticeSubmissionRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public PracticeSubmissionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Long insertSubmission(
      Long userId,
      Long problemId,
      Long questId,
      SubmissionSource source,
      String code) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO practice_submission (
                    user_id, problem_id, quest_id, source, language, code, status
                  ) VALUES (?, ?, ?, ?, 'PYTHON_3_11', ?, 'PENDING')
                  """,
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setLong(2, problemId);
          if (questId == null) {
            ps.setNull(3, java.sql.Types.BIGINT);
          } else {
            ps.setLong(3, questId);
          }
          ps.setString(4, source.name());
          ps.setString(5, code);
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException("practice_submission insert returned no key");
    }
    return key.longValue();
  }

  public void markStatus(
      Long submissionId, SubmissionStatus status, Integer runtimeMs, Integer memoryKb) {
    jdbcTemplate.update(
        """
        UPDATE practice_submission
        SET status = ?, runtime_ms = ?, memory_kb = ?, updated_at = now()
        WHERE id = ?
        """,
        status.name(),
        runtimeMs,
        memoryKb,
        submissionId);
  }

  public Long insertJudgeResult(
      Long submissionId,
      Verdict verdict,
      int passedCases,
      int totalCases,
      String failureReason,
      JsonNode trace) {
    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  """
                  INSERT INTO judge_result (
                    submission_id, verdict, passed_cases, total_cases, failure_reason, trace
                  ) VALUES (?, ?, ?, ?, ?, ?::jsonb)
                  """,
                  new String[] {"id"});
          ps.setLong(1, submissionId);
          ps.setString(2, verdict.name());
          ps.setInt(3, passedCases);
          ps.setInt(4, totalCases);
          ps.setString(5, failureReason);
          ps.setString(6, writeJson(trace));
          return ps;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    return key == null ? null : key.longValue();
  }

  public Optional<SubmissionDto> findSubmission(Long userId, Long submissionId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, problem_id, quest_id, source, language, code,
                   submitted_at, status, runtime_ms, memory_kb
            FROM practice_submission
            WHERE id = ? AND user_id = ?
            """,
            (rs, rowNum) -> mapSubmission(rs),
            submissionId,
            userId)
        .stream()
        .findFirst();
  }

  public Optional<String> findSubmissionKcCode(Long userId, Long submissionId) {
    return jdbcTemplate
        .query(
            """
            SELECT p.kc_code
            FROM practice_submission s
            JOIN course_problem p ON p.id = s.problem_id
            WHERE s.id = ? AND s.user_id = ?
            """,
            (rs, rowNum) -> rs.getString("kc_code"),
            submissionId,
            userId)
        .stream()
        .findFirst();
  }

  public Optional<SubmissionDto> findById(Long submissionId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, user_id, problem_id, quest_id, source, language, code,
                   submitted_at, status, runtime_ms, memory_kb
            FROM practice_submission
            WHERE id = ?
            """,
            (rs, rowNum) -> mapSubmission(rs),
            submissionId)
        .stream()
        .findFirst();
  }

  public List<SubmissionDto> list(Long userId, Long problemId, SubmissionStatus status, int limit, int offset) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, user_id, problem_id, quest_id, source, language, code,
                   submitted_at, status, runtime_ms, memory_kb
            FROM practice_submission
            WHERE user_id = ?
            """);
    List<Object> args = new ArrayList<>();
    args.add(userId);
    if (problemId != null) {
      sql.append(" AND problem_id = ?");
      args.add(problemId);
    }
    if (status != null) {
      sql.append(" AND status = ?");
      args.add(status.name());
    }
    sql.append(" ORDER BY submitted_at DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapSubmission(rs), args.toArray());
  }

  /** PathPlanner 用的最近提交记录（含 KC + 是否通过），按时间升序（最新在末尾，匹配 planner 取 [-1]）。 */
  public List<PlannerSubmission> findRecentForPlanner(Long userId, int limit) {
    List<PlannerSubmission> rows =
        jdbcTemplate.query(
            """
            SELECT s.problem_id AS problem_id, p.kc_code AS kc_code,
                   (s.status = 'PASSED') AS passed
            FROM practice_submission s
            JOIN course_problem p ON p.id = s.problem_id
            WHERE s.user_id = ?
            ORDER BY s.submitted_at DESC
            LIMIT ?
            """,
            (rs, rowNum) ->
                new PlannerSubmission(
                    rs.getLong("problem_id"), rs.getString("kc_code"), rs.getBoolean("passed")),
            userId,
            limit);
    // 查询是 DESC（最新优先），planner 的 _failed_recently 取列表 [-1] 视为最新，故反转成升序。
    java.util.Collections.reverse(rows);
    return rows;
  }

  /** 最近提交的精简投影，供 PathPlanner 判定最近练习成败。 */
  public record PlannerSubmission(Long problemId, String kcCode, boolean passed) {}

  /**
   * 同一题在某次提交之前的失败次数，用于判定重试/逆袭。{@code id < beforeSubmissionId}
   * 保证只数「先前」的失败（id 单调递增），不含当前这次。
   */
  public int countPriorFailures(Long userId, Long problemId, Long beforeSubmissionId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM practice_submission
            WHERE user_id = ? AND problem_id = ? AND id < ? AND status = 'FAILED'
            """,
            Integer.class,
            userId,
            problemId,
            beforeSubmissionId);
    return count == null ? 0 : count;
  }

  /**
   * 取「该用户提交」级别的 Postgres 事务咨询锁（{@code pg_advisory_xact_lock}）。锁随当前事务提交/回滚
   * 自动释放，故必须在 {@code @Transactional} 方法内调用。用途：让同一 userId 的并发正式提交在同一事务
   * 内串行化，消除「先 count 再 insert」的 TOCTOU 限频竞态（P0.3）。本仓库咨询锁空间仅此一处使用，
   * 直接以 userId 作为 bigint 锁键，无命名空间冲突。
   */
  public void acquireUserSubmitLock(Long userId) {
    jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, userId);
  }

  public int countSubmittedAfter(Long userId, OffsetDateTime since) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM practice_submission
            WHERE user_id = ? AND submitted_at > ?
            """,
            Integer.class,
            userId,
            since);
    return count == null ? 0 : count;
  }

  public Optional<JudgeResultDto> findJudgeResult(Long submissionId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, submission_id, verdict, passed_cases, total_cases,
                   failure_reason, trace, created_at
            FROM judge_result
            WHERE submission_id = ?
            """,
            (rs, rowNum) -> mapJudgeResult(rs),
            submissionId)
        .stream()
        .findFirst();
  }

  private SubmissionDto mapSubmission(ResultSet rs) throws SQLException {
    return new SubmissionDto(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getLong("problem_id"),
        (Long) rs.getObject("quest_id"),
        SubmissionSource.valueOf(rs.getString("source")),
        rs.getString("language"),
        rs.getString("code"),
        offset(rs, "submitted_at"),
        SubmissionStatus.valueOf(rs.getString("status")),
        (Integer) rs.getObject("runtime_ms"),
        (Integer) rs.getObject("memory_kb"));
  }

  private JudgeResultDto mapJudgeResult(ResultSet rs) throws SQLException {
    return new JudgeResultDto(
        rs.getLong("id"),
        rs.getLong("submission_id"),
        Verdict.valueOf(rs.getString("verdict")),
        rs.getInt("passed_cases"),
        rs.getInt("total_cases"),
        rs.getString("failure_reason"),
        readJson(rs.getString("trace")),
        offset(rs, "created_at"));
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }

  private String writeJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialise judge trace", exception);
    }
  }

  private JsonNode readJson(String raw) {
    try {
      return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to read judge trace", exception);
    }
  }
}
