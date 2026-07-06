package com.pythontopia.feynman;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 从真实学习事件抽取该生在某 KC 上的误解集合；无真实错误史时返回空，不合成。 */
@Service
public class MisconceptionExtractor {

  private final JdbcTemplate jdbc;

  public MisconceptionExtractor(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Misconception> extract(long userId, String kcCode) {
    List<Map.Entry<String, Long>> rows =
        jdbc.query(
            "SELECT id, COALESCE(NULLIF(payload->>'failureKind', ''), 'unknown_failure') AS tag "
                + "FROM learning_event_log "
                + "WHERE user_id = ? AND event_type = 'practice_failed' "
                + "AND (payload->>'kcCode' = ? OR EXISTS ("
                + "SELECT 1 FROM jsonb_array_elements_text(payload->'kcCodes') AS kc(code) "
                + "WHERE kc.code = ?)) "
                + "ORDER BY created_at DESC LIMIT 20",
            (rs, rowNum) -> Map.entry(rs.getString("tag"), rs.getLong("id")),
            userId,
            kcCode,
            kcCode);
    Map<String, List<Long>> grouped = new TreeMap<>();
    for (Map.Entry<String, Long> row : rows) {
      grouped.computeIfAbsent(row.getKey(), ignored -> new ArrayList<>()).add(row.getValue());
    }
    return grouped.entrySet().stream()
        .map(entry -> new Misconception(entry.getKey(), entry.getValue(), false))
        .toList();
  }
}
