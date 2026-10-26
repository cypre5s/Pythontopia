package com.pythontopia.world.boss.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import com.pythontopia.world.boss.dto.BossProblemReferenceDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL repository for the pack-43 review queue and global approved problem bank. */
@Repository
public class BossProblemReviewRepository {

  private static final long PACK_ID = 43L;
  private static final int MAX_SIMILARITY_CANDIDATES = 50;
  private static final String SELECT_REVIEW =
      "SELECT r.id, r.pack_id, r.source_problem_id, r.kind, r.payload::text AS payload,"
          + " r.decision, r.review_source, r.requester_id, requester.username AS requester_username,"
          + " r.requested_at, r.decider_id, decider.username AS decider_username,"
          + " r.decided_at, r.decision_reason, r.version"
          + " FROM boss_problem_review r"
          + " JOIN users requester ON requester.id = r.requester_id"
          + " LEFT JOIN users decider ON decider.id = r.decider_id";
  private static final String MATCHING_FROM =
      " FROM boss_problem_review r JOIN users requester ON requester.id = r.requester_id";
  private static final String MATCHING_WHERE =
      " WHERE r.pack_id = ?"
          + " AND (CAST(? AS text) IS NULL OR r.decision = CAST(? AS text))"
          + " AND (CAST(? AS text) IS NULL OR position(lower(CAST(? AS text)) in lower(concat_ws(' ',"
          + " r.id::text, r.source_problem_id, requester.username, r.kc_code, r.chapter::text,"
          + " r.stage::text, r.payload->>'statement'))) > 0)";

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public BossProblemReviewRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Return course-bank and reviewed statements for Graph's same-KC near-duplicate check.
   *
   * <p>shortcut: only the 50 latest AI review statements are screened; if misses rise with bank
   * size, replace this bounded projection with indexed corpus retrieval.
   */
  public List<BossProblemReferenceDto> findRecentSimilarityCandidates(String kcCode) {
    return jdbcTemplate.query(
        "WITH candidates AS ("
            + " SELECT 'course:' || p.id::text AS problem_id,"
            + " left(p.statement_markdown, 2000) AS statement, p.create_time AS created_at, 0 AS source_order"
            + " FROM course_problem p"
            + " WHERE p.pack_id = ? AND p.kc_code = ? AND p.tutor_owner_user_id IS NULL"
            + " AND nullif(btrim(p.reference_solution_code), '') IS NOT NULL"
            + " AND nullif(btrim(p.statement_markdown), '') IS NOT NULL"
            + " UNION ALL"
            + " SELECT r.source_problem_id AS problem_id,"
            + " left(r.payload->>'statement', 2000) AS statement, r.requested_at AS created_at, 1 AS source_order"
            + " FROM boss_problem_review r"
            + " WHERE r.pack_id = ? AND r.kc_code = ?"
            + " AND nullif(btrim(r.payload->>'statement'), '') IS NOT NULL"
            + ") SELECT problem_id, statement FROM candidates"
            + " ORDER BY source_order, created_at DESC NULLS LAST, problem_id LIMIT ?",
        (rs, rowNum) ->
            new BossProblemReferenceDto(
                rs.getString("problem_id"), rs.getString("statement")),
        PACK_ID,
        kcCode,
        PACK_ID,
        kcCode,
        MAX_SIMILARITY_CANDIDATES);
  }

  public BossProblemReviewRecord create(
      long requesterId, String kind, BossInternalProblem problem) {
    Long id =
        jdbcTemplate.queryForObject(
            "INSERT INTO boss_problem_review"
                + " (pack_id, source_problem_id, chapter, stage, kc_code, kind, difficulty,"
                + " payload, decision, requester_id)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', ?) RETURNING id",
            Long.class,
            PACK_ID,
            problem.problemId(),
            problem.chapter(),
            problem.stage(),
            problem.kcCode(),
            normalizeKind(kind),
            problem.difficulty(),
            serialize(problem),
            requesterId);
    if (id == null) {
      throw new IllegalStateException("problem review insert returned no id");
    }
    return findById(id).orElseThrow(() -> new IllegalStateException("created review disappeared"));
  }

  @Transactional
  public boolean approveAutomaticallyIfNotDuplicate(
      Long requesterId,
      String kind,
      int requestedChapter,
      int requestedStage,
      String requestedKcCode,
      Integer requestedDifficulty,
      BossInternalProblem problem) {
    if (!isEligibleForAutomaticApproval(
        requesterId,
        requestedChapter,
        requestedStage,
        requestedKcCode,
        requestedDifficulty,
        problem)) {
      return false;
    }
    String normalizedKind = normalizeKind(kind);
    String lockKey =
        PACK_ID
            + ":"
            + requestedChapter
            + ":"
            + requestedStage
            + ":"
            + requestedKcCode
            + ":"
            + Objects.toString(normalizedKind, "")
            + ":"
            + problem.statement();
    jdbcTemplate.queryForObject(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, lockKey);
    Boolean exists =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM boss_problem_review"
                + " WHERE pack_id = ? AND chapter = ? AND stage = ? AND kc_code = ?"
                + " AND kind IS NOT DISTINCT FROM ? AND payload->>'statement' = ?)",
            Boolean.class,
            PACK_ID,
            requestedChapter,
            requestedStage,
            requestedKcCode,
            normalizedKind,
            problem.statement());
    if (Boolean.TRUE.equals(exists)) {
      return false;
    }
    jdbcTemplate.update(
        "INSERT INTO boss_problem_review"
            + " (pack_id, source_problem_id, chapter, stage, kc_code, kind, difficulty, payload,"
            + " decision, review_source, requester_id, decided_at, decision_reason, version)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'APPROVED', 'AUTOMATED', ?, now(), ?, 2)",
        PACK_ID,
        problem.problemId(),
        problem.chapter(),
        problem.stage(),
        problem.kcCode(),
        normalizedKind,
        problem.difficulty(),
        serialize(problem),
        requesterId,
        "自动审核通过：题面结构、参考解全用例自测、KC 接地、难度与相似度检查通过");
    return true;
  }

  private static boolean isEligibleForAutomaticApproval(
      Long requesterId,
      int requestedChapter,
      int requestedStage,
      String requestedKcCode,
      Integer requestedDifficulty,
      BossInternalProblem problem) {
    return requesterId != null
        && requesterId > 0
        && problem != null
        && problem.aiGenerated()
        && problem.reviewed()
        && problem.chapter() == requestedChapter
        && problem.stage() == requestedStage
        && Objects.equals(problem.kcCode(), requestedKcCode)
        && (requestedDifficulty == null || problem.difficulty() == requestedDifficulty)
        && problem.difficulty() >= 1
        && problem.difficulty() <= 5
        && problem.problemId() != null
        && !problem.problemId().isBlank()
        && problem.statement() != null
        && !problem.statement().isBlank()
        && problem.referenceSolution() != null
        && !problem.referenceSolution().isBlank()
        && problem.totalTestCases() == problem.testCases().size()
        && problem.testCases().size() >= 3
        && problem.testCases().stream().anyMatch(testCase -> testCase.isSample())
        && problem.testCases().stream().anyMatch(testCase -> !testCase.isSample());
  }

  public List<BossProblemReviewRecord> list(int limit) {
    return jdbcTemplate.query(
        SELECT_REVIEW + " WHERE r.pack_id = ? ORDER BY r.requested_at DESC, r.id DESC LIMIT ?",
        this::mapReview,
        PACK_ID,
        limit);
  }

  public long countMatching(BossProblemReviewDecision decision, String query) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*)" + MATCHING_FROM + MATCHING_WHERE,
            Long.class,
            PACK_ID,
            decision == null ? null : decision.name(),
            decision == null ? null : decision.name(),
            query,
            query);
    return count == null ? 0 : count;
  }

  public List<BossProblemReviewRecord> listMatching(
      BossProblemReviewDecision decision,
      String query,
      int limit,
      long offset,
      BossProblemReviewSortField sortBy,
      BossProblemReviewSortDirection sortDirection) {
    return jdbcTemplate.query(
        SELECT_REVIEW
            + MATCHING_WHERE
            + orderBy(sortBy, sortDirection)
            + " LIMIT ? OFFSET ?",
        this::mapReview,
        PACK_ID,
        decision == null ? null : decision.name(),
        decision == null ? null : decision.name(),
        query,
        query,
        limit,
        offset);
  }

  private static String orderBy(
      BossProblemReviewSortField sortBy, BossProblemReviewSortDirection sortDirection) {
    String expression =
        switch (sortBy) {
          case REQUESTED_AT -> "r.requested_at";
          case STATEMENT -> "lower(coalesce(r.payload->>'statement', ''))";
          case REQUESTER_USERNAME -> "lower(requester.username)";
        };
    return " ORDER BY " + expression + " " + sortDirection.name() + ", r.id DESC";
  }

  public Optional<BossProblemReviewRecord> findById(long id) {
    return jdbcTemplate
        .query(
            SELECT_REVIEW + " WHERE r.id = ? AND r.pack_id = ?",
            this::mapReview,
            id,
            PACK_ID)
        .stream()
        .findFirst();
  }

  /**
   * Terminal decision with optimistic locking. Teachers may also confirm or reject auto-approved
   * questions; manual decisions remain immutable.
   */
  public Optional<BossProblemReviewRecord> decide(
      long id,
      int expectedVersion,
      BossProblemReviewDecision decision,
      long deciderId,
      String reason) {
    List<Long> updatedIds =
        jdbcTemplate.query(
            "UPDATE boss_problem_review"
                + " SET decision = ?, review_source = 'TEACHER', decider_id = ?,"
                + " decided_at = now(), decision_reason = ?,"
                + " version = version + 1"
                + " WHERE id = ? AND pack_id = ? AND version = ?"
                + " AND (decision = 'PENDING' OR (decision = 'APPROVED' AND review_source = 'AUTOMATED'))"
                + " RETURNING id",
            (rs, rowNum) -> rs.getLong(1),
            decision.name(),
            deciderId,
            reason,
            id,
            PACK_ID,
            expectedVersion);
    if (updatedIds.isEmpty()) {
      return Optional.empty();
    }
    return findById(updatedIds.get(0));
  }

  /**
   * Read-only global-bank selection. Match chapter/stage/KC exactly; dual-track may fall back to a
   * generic approved code problem. Prefer the requested kind, then the closest difficulty.
   */
  public Optional<ApprovedBossProblem> findApproved(
      int chapter, int stage, String kcCode, String kind, int targetDifficulty) {
    String normalizedKind = normalizeKind(kind);
    return jdbcTemplate
        .query(
            "SELECT id, version, payload::text AS payload FROM boss_problem_review"
                + " WHERE pack_id = ? AND decision = 'APPROVED'"
                + " AND chapter = ? AND stage = ? AND kc_code = ?"
                + " AND (kind IS NOT DISTINCT FROM ? OR (? = 'dual_track' AND kind IS NULL))"
                + " ORDER BY CASE WHEN kind IS NOT DISTINCT FROM ? THEN 0 ELSE 1 END,"
                + " ABS(difficulty - ?), decided_at DESC, id DESC LIMIT 1",
            (rs, rowNum) -> {
              long reviewId = rs.getLong("id");
              int version = rs.getInt("version");
              BossInternalProblem stored = deserialize(rs.getString("payload"));
              return new ApprovedBossProblem(
                  reviewId, version, withApprovedId(stored, reviewId, version));
            },
            PACK_ID,
            chapter,
            stage,
            kcCode,
            normalizedKind,
            normalizedKind,
            normalizedKind,
            targetDifficulty)
        .stream()
        .findFirst();
  }

  public boolean isApproved(BossInternalProblem problem) {
    Boolean approved =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM boss_problem_review"
                + " WHERE pack_id = ? AND decision = 'APPROVED' AND chapter = ? AND stage = ?"
                + " AND kc_code = ? AND difficulty = ?"
                + " AND (payload - 'problemId') = (CAST(? AS jsonb) - 'problemId'))",
            Boolean.class,
            PACK_ID,
            problem.chapter(),
            problem.stage(),
            problem.kcCode(),
            problem.difficulty(),
            serialize(problem));
    return Boolean.TRUE.equals(approved);
  }

  private BossProblemReviewRecord mapReview(ResultSet rs, int rowNum) throws SQLException {
    return new BossProblemReviewRecord(
        rs.getLong("id"),
        rs.getLong("pack_id"),
        rs.getString("source_problem_id"),
        rs.getString("kind"),
        deserialize(rs.getString("payload")),
        BossProblemReviewDecision.valueOf(rs.getString("decision")),
        BossProblemReviewSource.valueOf(rs.getString("review_source")),
        rs.getLong("requester_id"),
        rs.getString("requester_username"),
        rs.getObject("requested_at", OffsetDateTime.class),
        nullableLong(rs, "decider_id"),
        rs.getString("decider_username"),
        rs.getObject("decided_at", OffsetDateTime.class),
        rs.getString("decision_reason"),
        rs.getInt("version"));
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private String serialize(BossInternalProblem problem) {
    try {
      return objectMapper.writeValueAsString(problem);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("serialize reviewed BossInternalProblem failed", ex);
    }
  }

  private BossInternalProblem deserialize(String payload) {
    try {
      return objectMapper.readValue(payload, BossInternalProblem.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("deserialize reviewed BossInternalProblem failed", ex);
    }
  }

  private static String normalizeKind(String kind) {
    return kind == null || kind.isBlank() ? null : kind.trim();
  }

  private static BossInternalProblem withApprovedId(
      BossInternalProblem problem, long reviewId, int version) {
    return new BossInternalProblem(
        "approved-" + reviewId + "-v" + version,
        problem.chapter(),
        problem.stage(),
        problem.difficulty(),
        problem.kcCode(),
        problem.statement(),
        problem.hints(),
        problem.sampleInput(),
        problem.sampleOutput(),
        problem.totalTestCases(),
        problem.reviewed(),
        problem.aiGenerated(),
        problem.testCases(),
        problem.referenceSolution());
  }
}
