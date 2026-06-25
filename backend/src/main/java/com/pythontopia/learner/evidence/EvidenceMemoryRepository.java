package com.pythontopia.learner.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EvidenceMemoryRepository {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public EvidenceMemoryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public Long insert(EvidenceMemorySnapshotDto snapshot) {
    try {
      String json = objectMapper.writeValueAsString(snapshot);
      return jdbc.queryForObject(
          """
          INSERT INTO learner_evidence_memory_snapshot (
            user_id, focus_kc, direct_signal_count, propagated_signal_count,
            retained_evidence_count, graph_memory_score, naive_memory_score, snapshot_json
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
          RETURNING id
          """,
          Long.class,
          snapshot.userId(),
          blankToNull(snapshot.focusKc()),
          snapshot.directSignalCount(),
          snapshot.propagatedSignalCount(),
          snapshot.retainedEvidenceCount(),
          BigDecimal.valueOf(snapshot.graphMemoryScore()),
          BigDecimal.valueOf(snapshot.naiveMemoryScore()),
          json);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize evidence memory snapshot", ex);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
