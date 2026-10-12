package com.pythontopia.world.boss.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.world.boss.dto.BossInternalProblem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL repository for the pack-43 review queue and global approved problem bank. */
@Repository
public class BossProblemReviewRepository {

  private static final long PACK_ID = 43L;
  private static final String SELECT_REVIEW =
      "SELECT r.id, r.pack_id, r.source_problem_id, r.kind, r.payload::text AS payload,"
          + " r.decision, r.requester_id, requester.username AS requester_username,"
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
      BossProblemReviewDecision decision, String query, int limit, long offset) {
    return jdbcTemplate.query(
        SELECT_REVIEW
            + MATCHING_WHERE
            + " ORDER BY r.requested_at DESC, r.id DESC LIMIT ? OFFSET ?",
        this::mapReview,
        PACK_ID,
        decision == null ? null : decision.name(),
        decision == null ? null : decision.name(),
        query,
        query,
        limit,
        offset);
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
   * Terminal decision with optimistic locking. Only a matching PENDING version can transition;
   * approved/rejected history is immutable.
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
                + " SET decision = ?, decider_id = ?, decided_at = now(), decision_reason = ?,"
                + " version = version + 1"
                + " WHERE id = ? AND pack_id = ? AND decision = 'PENDING' AND version = ?"
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
   * Read-only global-bank selection. Match the requested chapter/stage/KC/kind exactly and choose
   * the closest approved difficulty; no row is consumed or mutated.
   */
  public Optional<ApprovedBossProblem> findApproved(
      int chapter, int stage, String kcCode, String kind, int targetDifficulty) {
    return jdbcTemplate
        .query(
            "SELECT id, version, payload::text AS payload FROM boss_problem_review"
                + " WHERE pack_id = ? AND decision = 'APPROVED'"
                + " AND chapter = ? AND stage = ? AND kc_code = ?"
                + " AND kind IS NOT DISTINCT FROM ?"
                + " ORDER BY ABS(difficulty - ?), decided_at DESC, id DESC LIMIT 1",
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
            normalizeKind(kind),
            targetDifficulty)
        .stream()
        .findFirst();
  }

  private BossProblemReviewRecord mapReview(ResultSet rs, int rowNum) throws SQLException {
    return new BossProblemReviewRecord(
        rs.getLong("id"),
        rs.getLong("pack_id"),
        rs.getString("source_problem_id"),
        rs.getString("kind"),
        deserialize(rs.getString("payload")),
        BossProblemReviewDecision.valueOf(rs.getString("decision")),
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
