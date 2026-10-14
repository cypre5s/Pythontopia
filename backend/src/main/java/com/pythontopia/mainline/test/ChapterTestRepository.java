package com.pythontopia.mainline.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.mainline.domain.ChapterAssessmentKind;
import com.pythontopia.mainline.domain.ChapterTestRecord;
import com.pythontopia.mainline.domain.ChapterTestStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** chapter_test 读写仓库（后端权威，questions 含答案，绝不整体下发前端）。 */
@Repository
public class ChapterTestRepository {

  private static final String SELECT_COLUMNS =
      "id, user_id, chapter, assessment_kind, status, attempt_no, questions::text AS questions,"
          + " profile_snapshot::text AS profile_snapshot, generator_meta::text AS generator_meta,"
          + " time_limit_sec, started_at, deadline_at, submitted_at, score, detail::text AS detail,"
          + " integrity::text AS integrity, created_at";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public ChapterTestRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public long insertPending(long userId, int chapter, int attemptNo, int timeLimitSec) {
    return insertPending(
        userId, chapter, ChapterAssessmentKind.FINAL, attemptNo, timeLimitSec);
  }

  public long insertPending(
      long userId,
      int chapter,
      ChapterAssessmentKind assessmentKind,
      int attemptNo,
      int timeLimitSec) {
    Long id =
        jdbcTemplate.queryForObject(
            "INSERT INTO chapter_test"
                + " (user_id, chapter, assessment_kind, status, attempt_no, time_limit_sec)"
                + " VALUES (?, ?, ?, 'PENDING', ?, ?) RETURNING id",
            Long.class,
            userId,
            chapter,
            assessmentKind.name(),
            attemptNo,
            timeLimitSec);
    if (id == null) {
      throw new IllegalStateException("chapter_test insert returned no id");
    }
    return id;
  }

  public Optional<ChapterTestRecord> findById(long testId) {
    return jdbcTemplate
        .query("SELECT " + SELECT_COLUMNS + " FROM chapter_test WHERE id = ?", this::map, testId)
        .stream()
        .findFirst();
  }

  public Optional<ChapterTestRecord> findOwned(long userId, long testId) {
    return jdbcTemplate
        .query(
            "SELECT " + SELECT_COLUMNS + " FROM chapter_test WHERE id = ? AND user_id = ?",
            this::map,
            testId,
            userId)
        .stream()
        .findFirst();
  }

  public Optional<ChapterTestRecord> findOwnedForUpdate(long userId, long testId) {
    return jdbcTemplate
        .query(
            "SELECT " + SELECT_COLUMNS + " FROM chapter_test WHERE id = ? AND user_id = ? FOR UPDATE",
            this::map,
            testId,
            userId)
        .stream()
        .findFirst();
  }

  /** 该学生该章最近一条章测（任意状态）。 */
  public Optional<ChapterTestRecord> findLatest(long userId, int chapter) {
    return findLatest(userId, chapter, ChapterAssessmentKind.FINAL);
  }

  public Optional<ChapterTestRecord> findLatest(
      long userId, int chapter, ChapterAssessmentKind assessmentKind) {
    return jdbcTemplate
        .query(
            "SELECT "
                + SELECT_COLUMNS
                + " FROM chapter_test WHERE user_id = ? AND chapter = ? AND assessment_kind = ?"
                + " ORDER BY created_at DESC, id DESC LIMIT 1",
            this::map,
            userId,
            chapter,
            assessmentKind.name())
        .stream()
        .findFirst();
  }

  /**
   * 一次取齐该学生「每章最新章测状态」（overview 单查询，避免 N+1，§11.4）。
   * 用 DISTINCT ON (chapter) 按 created_at DESC 取每章最近一行的状态。
   */
  public java.util.Map<Integer, String> findLatestStatusByChapter(long userId) {
    java.util.Map<Integer, String> out = new java.util.HashMap<>();
    jdbcTemplate
        .query(
            "SELECT DISTINCT ON (chapter) chapter, status FROM chapter_test"
                + " WHERE user_id = ? AND assessment_kind = 'FINAL'"
                + " ORDER BY chapter, created_at DESC, id DESC",
            (rs, rowNum) -> java.util.Map.entry(rs.getInt("chapter"), rs.getString("status")),
            userId)
        .forEach(entry -> out.put(entry.getKey(), entry.getValue()));
    return out;
  }

  /** 该章历史最高分（仅 SUBMITTED 计入；用于星级公式 §5）。 */
  public Optional<Integer> findBestScore(long userId, int chapter) {
    Integer best =
        jdbcTemplate.queryForObject(
            "SELECT MAX(score) FROM chapter_test"
                + " WHERE user_id = ? AND chapter = ? AND assessment_kind = 'FINAL'"
                + " AND status = 'SUBMITTED' AND score IS NOT NULL",
            Integer.class,
            userId,
            chapter);
    return Optional.ofNullable(best);
  }

  public int nextAttemptNo(long userId, int chapter) {
    return nextAttemptNo(userId, chapter, ChapterAssessmentKind.FINAL);
  }

  public int nextAttemptNo(
      long userId, int chapter, ChapterAssessmentKind assessmentKind) {
    Integer max =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(attempt_no), 0) FROM chapter_test"
                + " WHERE user_id = ? AND chapter = ? AND assessment_kind = ?",
            Integer.class,
            userId,
            chapter,
            assessmentKind.name());
    return (max == null ? 0 : max) + 1;
  }

  public void markStatus(long testId, ChapterTestStatus status) {
    jdbcTemplate.update("UPDATE chapter_test SET status = ? WHERE id = ?", status.name(), testId);
  }

  /** graph 题面就绪：写 questions + generator_meta(+profile_snapshot)，状态置 READY。 */
  public void markReady(long testId, JsonNode questions, JsonNode generatorMeta, JsonNode profile) {
    jdbcTemplate.update(
        "UPDATE chapter_test SET status = 'READY', questions = ?::jsonb,"
            + " generator_meta = ?::jsonb, profile_snapshot = ?::jsonb WHERE id = ?",
        writeJson(questions),
        writeJson(generatorMeta),
        writeJson(profile),
        testId);
  }

  /** 开考：置 IN_PROGRESS + started_at/deadline_at（仅当当前 READY，乐观并发保护）。 */
  public boolean startExam(long testId, OffsetDateTime startedAt, OffsetDateTime deadlineAt) {
    int updated =
        jdbcTemplate.update(
            "UPDATE chapter_test SET status = 'IN_PROGRESS', started_at = ?, deadline_at = ?"
                + " WHERE id = ? AND status = 'READY'",
            startedAt,
            deadlineAt,
            testId);
    return updated == 1;
  }

  /** 提交判分：score + detail + integrity，状态置 SUBMITTED（仅当当前 IN_PROGRESS）。 */
  public boolean markSubmitted(
      long testId, int score, JsonNode detail, JsonNode integrity, OffsetDateTime submittedAt) {
    int updated =
        jdbcTemplate.update(
            "UPDATE chapter_test SET status = 'SUBMITTED', score = ?, detail = ?::jsonb,"
                + " integrity = ?::jsonb, submitted_at = ? WHERE id = ? AND status = 'IN_PROGRESS'",
            score,
            writeJson(detail),
            writeJson(integrity),
            submittedAt,
            testId);
    return updated == 1;
  }

  /** 超时拒收：状态置 EXPIRED + 记 integrity.overtime（§4.2）。 */
  public void markExpiredOvertime(long testId, JsonNode integrity) {
    jdbcTemplate.update(
        "UPDATE chapter_test SET status = 'EXPIRED', integrity = ?::jsonb WHERE id = ?",
        writeJson(integrity),
        testId);
  }

  private ChapterTestRecord map(ResultSet rs, int rowNum) throws SQLException {
    return new ChapterTestRecord(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getInt("chapter"),
        ChapterAssessmentKind.valueOf(rs.getString("assessment_kind")),
        ChapterTestStatus.valueOf(rs.getString("status")),
        rs.getInt("attempt_no"),
        readJson(rs.getString("questions")),
        readJson(rs.getString("profile_snapshot")),
        readJson(rs.getString("generator_meta")),
        rs.getInt("time_limit_sec"),
        offset(rs, "started_at"),
        offset(rs, "deadline_at"),
        offset(rs, "submitted_at"),
        (Integer) rs.getObject("score"),
        readJson(rs.getString("detail")),
        readJson(rs.getString("integrity")),
        offset(rs, "created_at"));
  }

  private JsonNode readJson(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return objectMapper.readTree(raw);
    } catch (JsonProcessingException ex) {
      return null;
    }
  }

  private String writeJson(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("serialize chapter_test json failed", ex);
    }
  }

  private OffsetDateTime offset(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
  }
}
