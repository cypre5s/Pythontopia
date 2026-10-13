package com.pythontopia.learner.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.learner.profile.dto.DimensionDelta;
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

  boolean hasEarlierPending(long userId, long id) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM learner_profile_projection_outbox"
                + " WHERE user_id = ? AND id < ? AND status = 'PENDING')",
            Boolean.class,
            userId,
            id);
    return Boolean.TRUE.equals(exists);
  }

  boolean markApplied(long id) {
    return jdbc.update(
            "UPDATE learner_profile_projection_outbox"
                + " SET status = 'APPLIED', attempt_count = attempt_count + 1,"
                + " error_kind = NULL, processed_at = now()"
                + " WHERE id = ? AND status = 'PENDING'",
            id)
        == 1;
  }

  int recordFailure(long id, String errorKind) {
    return jdbc.update(
        "UPDATE learner_profile_projection_outbox"
            + " SET attempt_count = attempt_count + 1, error_kind = ?,"
            + " next_attempt_at = now() + interval '10 seconds'"
            + " WHERE id = ? AND status = 'PENDING'",
        errorKind,
        id);
  }

  List<Long> findDuePendingIds(int limit) {
    return jdbc.queryForList(
        "SELECT id FROM learner_profile_projection_outbox"
            + " WHERE status = 'PENDING' AND next_attempt_at <= now()"
            + " ORDER BY id LIMIT ?",
        Long.class,
        limit);
  }

  record Entry(long id, long userId, String dimensionKey, DimensionDelta delta) {}
}
