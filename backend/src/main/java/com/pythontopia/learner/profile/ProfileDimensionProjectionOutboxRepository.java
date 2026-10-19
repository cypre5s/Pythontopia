package com.pythontopia.learner.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.ProfileDimensionProjectionOutboxDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ProfileDimensionProjectionOutboxRepository {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  ProfileDimensionProjectionOutboxRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  long enqueue(ProfileDimensionProjection projection) {
    // Serialize event insertion for one learner. The transaction lock is held through commit,
    // keeping generated outbox IDs in the same order as that learner's committed updates.
    jdbc.queryForObject(
        "SELECT pg_advisory_xact_lock(?::bigint)", Object.class, projection.userId());
    String deltaJson;
    try {
      deltaJson = objectMapper.writeValueAsString(projection.delta());
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("serialize profile projection failed", ex);
    }
    Long id =
        jdbc.queryForObject(
            "INSERT INTO learner_profile_projection_outbox"
                + " (user_id, idempotency_key, dimension_key, delta)"
                + " VALUES (?, ?, ?, ?::jsonb)"
                + " ON CONFLICT (idempotency_key) DO UPDATE"
                + " SET idempotency_key = EXCLUDED.idempotency_key"
                + " RETURNING id",
            Long.class,
            projection.userId(),
            projection.idempotencyKey(),
            projection.dimensionKey(),
            deltaJson);
    return id == null ? 0L : id;
  }

  Optional<Entry> findPendingForUpdate(long id) {
    return jdbc
        .query(
            "SELECT id, user_id, dimension_key, delta::text AS delta_json"
                + " FROM learner_profile_projection_outbox"
                + " WHERE id = ? AND status = 'PENDING' AND next_attempt_at <= now() FOR UPDATE",
            (rs, rowNum) -> {
              try {
                return new Entry(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("dimension_key"),
                    objectMapper.readValue(rs.getString("delta_json"), DimensionDelta.class));
              } catch (JsonProcessingException ex) {
                throw new IllegalStateException("deserialize profile projection failed", ex);
              }
            },
            id)
        .stream()
        .findFirst();
  }

  boolean hasEarlierUnresolved(long userId, long id) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM learner_profile_projection_outbox"
                + " WHERE user_id = ? AND id < ? AND status IN ('PENDING', 'FAILED'))",
            Boolean.class,
            userId,
            id);
    return Boolean.TRUE.equals(exists);
  }

  boolean markApplied(long id) {
    return jdbc.update(
            "UPDATE learner_profile_projection_outbox"
                + " SET status = 'APPLIED', attempt_count = attempt_count + 1,"
                + " error_kind = NULL, failed_at = NULL, processed_at = now()"
                + " WHERE id = ? AND status = 'PENDING'",
            id)
        == 1;
  }

  Failure recordFailure(
      long id, String errorKind, int maxAttempts, int baseBackoffSeconds, int maxBackoffSeconds) {
    String safeErrorKind =
        errorKind == null ? "UnknownException" : errorKind.substring(0, Math.min(128, errorKind.length()));
    return jdbc
        .query(
            "UPDATE learner_profile_projection_outbox"
                + " SET attempt_count = attempt_count + 1,"
                + " status = CASE WHEN attempt_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,"
                + " error_kind = ?,"
                + " failed_at = CASE WHEN attempt_count + 1 >= ? THEN now() ELSE NULL END,"
                + " next_attempt_at = CASE WHEN attempt_count + 1 >= ? THEN now()"
                + " ELSE now() + LEAST(?::double precision * power(2, attempt_count),"
                + " ?::double precision) * interval '1 second' END"
                + " WHERE id = ? AND status = 'PENDING'"
                + " RETURNING attempt_count, status",
            (rs, rowNum) -> new Failure(rs.getInt("attempt_count"), rs.getString("status")),
            maxAttempts,
            safeErrorKind,
            maxAttempts,
            maxAttempts,
            baseBackoffSeconds,
            maxBackoffSeconds,
            id)
        .stream()
        .findFirst()
        .orElse(null);
  }

  boolean repairFailed(long id, long adminUserId, String dimensionKey, DimensionDelta delta) {
    String deltaJson;
    try {
      deltaJson = objectMapper.writeValueAsString(delta);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("serialize repaired profile projection failed", ex);
    }
    return jdbc.update(
            "UPDATE learner_profile_projection_outbox"
                + " SET dimension_key = ?, delta = ?::jsonb, status = 'PENDING',"
                + " attempt_count = 0, error_kind = NULL, failed_at = NULL,"
                + " next_attempt_at = now(), processed_at = NULL,"
                + " manual_replay_count = manual_replay_count + 1,"
                + " last_replayed_by = ?, last_replayed_at = now()"
                + " WHERE id = ? AND status = 'FAILED'",
            dimensionKey,
            deltaJson,
            adminUserId,
            id)
        == 1;
  }

  long countFailed() {
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM learner_profile_projection_outbox WHERE status = 'FAILED'",
            Long.class);
    return count == null ? 0L : count;
  }

  List<ProfileDimensionProjectionOutboxDto> findFailed(int page, int size) {
    int offset = Math.multiplyExact(page, size);
    return jdbc.query(
        "SELECT id, user_id, dimension_key, status, attempt_count, error_kind,"
            + " manual_replay_count, last_replayed_by, last_replayed_at, created_at,"
            + " failed_at, next_attempt_at, processed_at"
            + " FROM learner_profile_projection_outbox WHERE status = 'FAILED'"
            + " ORDER BY failed_at, id LIMIT ? OFFSET ?",
        (rs, rowNum) -> mapAdminEntry(rs),
        size,
        offset);
  }

  Optional<ProfileDimensionProjectionOutboxDto> findForAdmin(long id) {
    return jdbc
        .query(
            "SELECT id, user_id, dimension_key, status, attempt_count, error_kind,"
                + " manual_replay_count, last_replayed_by, last_replayed_at, created_at,"
                + " failed_at, next_attempt_at, processed_at"
                + " FROM learner_profile_projection_outbox WHERE id = ?",
            (rs, rowNum) -> mapAdminEntry(rs),
            id)
        .stream()
        .findFirst();
  }

  private static ProfileDimensionProjectionOutboxDto mapAdminEntry(
      java.sql.ResultSet rs) throws java.sql.SQLException {
    return new ProfileDimensionProjectionOutboxDto(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("dimension_key"),
        rs.getString("status"),
        rs.getInt("attempt_count"),
        rs.getString("error_kind"),
        rs.getInt("manual_replay_count"),
        rs.getObject("last_replayed_by", Long.class),
        rs.getObject("last_replayed_at", OffsetDateTime.class),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("failed_at", OffsetDateTime.class),
        rs.getObject("next_attempt_at", OffsetDateTime.class),
        rs.getObject("processed_at", OffsetDateTime.class));
  }

  List<Long> findDuePendingIds(int limit) {
    return jdbc.queryForList(
        "SELECT candidate.id FROM learner_profile_projection_outbox candidate"
            + " WHERE candidate.status = 'PENDING' AND candidate.next_attempt_at <= now()"
            + " AND NOT EXISTS (SELECT 1 FROM learner_profile_projection_outbox earlier"
            + " WHERE earlier.user_id = candidate.user_id AND earlier.id < candidate.id"
            + " AND earlier.status IN ('PENDING', 'FAILED'))"
            + " ORDER BY candidate.id LIMIT ?",
        Long.class,
        limit);
  }

  record Entry(long id, long userId, String dimensionKey, DimensionDelta delta) {}

  record Failure(int attemptCount, String status) {}
}
