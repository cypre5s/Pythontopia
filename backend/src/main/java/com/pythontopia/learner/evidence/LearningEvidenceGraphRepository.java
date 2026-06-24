package com.pythontopia.learner.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LearningEvidenceGraphRepository {

  private static final long PACK_ID = 43L;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public LearningEvidenceGraphRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public RawEvidenceGraph load(Long userId, String focusKc, int limit) {
    GraphBuilder builder = new GraphBuilder(userId);
    loadResources(builder, userId, focusKc, limit);
    loadChecks(builder, userId, focusKc, limit);
    loadMasteryEvents(builder, userId, focusKc, limit);
    loadLearningEvents(builder, userId, focusKc, limit);
    loadAgentRuns(builder, userId, limit);
    loadKcRelations(builder, focusKc, limit);
    return builder.build();
  }

  private void loadResources(GraphBuilder builder, Long userId, String focusKc, int limit) {
    jdbc.query(
        """
        SELECT id, kc_codes, type, title, evidence_page_ids, created_by_run_id, created_at
        FROM learning_resource
        WHERE user_id = ?
          AND (?::text IS NULL OR ?::text = ANY(kc_codes))
        ORDER BY created_at DESC
        LIMIT ?
        """,
        rs -> {
          long id = rs.getLong("id");
          String nodeId = resourceId(id);
          List<String> kcCodes = readStringArray(rs.getArray("kc_codes"));
          List<Long> pageIds = readLongArray(rs.getArray("evidence_page_ids"));
          Map<String, Object> attrs = new LinkedHashMap<>();
          attrs.put("type", rs.getString("type"));
          attrs.put("kcCodes", kcCodes);
          attrs.put("createdAt", ts(rs.getTimestamp("created_at")));
          builder.node(nodeId, "RESOURCE", rs.getString("title"), "resource", String.valueOf(id), attrs);
          builder.edge(userId(builder.userId()), nodeId, "ACTED", 0.8, Map.of("source", "learning_resource"));
          for (String kcCode : kcCodes) {
            builder.kc(kcCode);
            builder.edge(nodeId, kcId(kcCode), "COVERS_KC", 0.9, Map.of());
          }
          for (Long pageId : pageIds) {
            builder.page(pageId);
            builder.edge(nodeId, pageId(pageId), "GROUNDED_BY_PAGE", 1.0, Map.of("source", "learning_resource"));
          }
          Long runId = nullableLong(rs.getObject("created_by_run_id"));
          if (runId != null) {
            builder.node(runId(runId), "AGENT_RUN", "run " + runId, "resource_run", String.valueOf(runId), Map.of());
            builder.edge(runId(runId), nodeId, "GENERATED", 1.0, Map.of());
          }
        },
        userId,
        blankToNull(focusKc),
        blankToNull(focusKc),
        limit);
  }

  private void loadChecks(GraphBuilder builder, Long userId, String focusKc, int limit) {
    jdbc.query(
        """
        SELECT id, resource_id, kc_code, evidence_type, status, mastery_delta, remediation_type,
               reason, created_at
        FROM resource_learning_check
        WHERE user_id = ?
          AND (?::text IS NULL OR kc_code = ?::text)
        ORDER BY created_at DESC
        LIMIT ?
        """,
        rs -> {
          long id = rs.getLong("id");
          long resourceId = rs.getLong("resource_id");
          String kcCode = rs.getString("kc_code");
          String status = rs.getString("status");
          Map<String, Object> attrs = new LinkedHashMap<>();
          attrs.put("evidenceType", rs.getString("evidence_type"));
          attrs.put("status", status);
          attrs.put("masteryDelta", rs.getBigDecimal("mastery_delta"));
          attrs.put("remediationType", rs.getString("remediation_type"));
          attrs.put("reason", rs.getString("reason"));
          attrs.put("createdAt", ts(rs.getTimestamp("created_at")));
          builder.node(checkId(id), "CHECK", status + " check " + id, "resource_learning_check", String.valueOf(id), attrs);
          builder.edge(userId(builder.userId()), checkId(id), "ACTED", 0.8, Map.of("source", "resource_learning_check"));
          builder.node(resourceId(resourceId), "RESOURCE", "resource " + resourceId, "resource", String.valueOf(resourceId), Map.of());
          builder.kc(kcCode);
          builder.edge(checkId(id), resourceId(resourceId), "CHECKS_RESOURCE", 1.0, Map.of("status", status));
          builder.edge(checkId(id), kcId(kcCode), "CHECKS_KC", 1.0, Map.of("status", status));
        },
        userId,
        blankToNull(focusKc),
        blankToNull(focusKc),
        limit);
  }

  private void loadMasteryEvents(GraphBuilder builder, Long userId, String focusKc, int limit) {
    jdbc.query(
        """
        SELECT id, kc_code, source, delta, reason, created_at
        FROM kc_mastery_event
        WHERE user_id = ?
          AND (?::text IS NULL OR kc_code = ?::text)
        ORDER BY created_at DESC
        LIMIT ?
        """,
        rs -> {
          long id = rs.getLong("id");
          String kcCode = rs.getString("kc_code");
          Map<String, Object> attrs = new LinkedHashMap<>();
          attrs.put("source", rs.getString("source"));
          attrs.put("delta", rs.getBigDecimal("delta"));
          attrs.put("reason", rs.getString("reason"));
          attrs.put("createdAt", ts(rs.getTimestamp("created_at")));
          builder.node(masteryId(id), "MASTERY_EVENT", rs.getString("source") + " " + kcCode, "kc_mastery_event", String.valueOf(id), attrs);
          builder.edge(userId(builder.userId()), masteryId(id), "ACTED", 0.7, Map.of("source", "kc_mastery_event"));
          builder.kc(kcCode);
          builder.edge(masteryId(id), kcId(kcCode), "UPDATES_MASTERY", 1.0, attrs);
        },
        userId,
        blankToNull(focusKc),
        blankToNull(focusKc),
        limit);
  }

  private void loadLearningEvents(GraphBuilder builder, Long userId, String focusKc, int limit) {
    jdbc.query(
        """
        SELECT id, event_type, payload::text AS payload_text, importance, created_at
        FROM learning_event_log
        WHERE user_id = ?
          AND (
            ?::text IS NULL
            OR payload->>'kcCode' = ?::text
            OR jsonb_exists(COALESCE(payload->'kcCodes', '[]'::jsonb), ?::text)
          )
        ORDER BY created_at DESC
        LIMIT ?
        """,
        rs -> {
          long id = rs.getLong("id");
          String eventType = rs.getString("event_type");
          JsonNode payload = parse(rs.getString("payload_text"));
          Map<String, Object> attrs = new LinkedHashMap<>();
          attrs.put("eventType", eventType);
          attrs.put("importance", rs.getInt("importance"));
          attrs.put("payload", payload);
          attrs.put("createdAt", ts(rs.getTimestamp("created_at")));
          builder.node(eventId(id), "EVENT", eventType + " " + id, "learning_event_log", String.valueOf(id), attrs);
          builder.edge(userId(builder.userId()), eventId(id), "ACTED", 0.7, Map.of("source", "learning_event_log"));
          for (String kcCode : extractKcCodes(payload)) {
            builder.kc(kcCode);
            builder.edge(eventId(id), kcId(kcCode), "TOUCHES_KC", 0.9, Map.of("eventType", eventType));
          }
          Long resourceId = jsonLong(payload, "resourceId");
          if (resourceId != null) {
            builder.node(resourceId(resourceId), "RESOURCE", "resource " + resourceId, "resource", String.valueOf(resourceId), Map.of());
            builder.edge(eventId(id), resourceId(resourceId), "CONSUMED_RESOURCE", 0.9, Map.of("eventType", eventType));
          }
          Long problemId = jsonLong(payload, "problemId");
          if (problemId != null) {
            builder.node(problemId(problemId), "PROBLEM", "problem " + problemId, "problem", String.valueOf(problemId), Map.of());
            builder.edge(eventId(id), problemId(problemId), "TOUCHES_PROBLEM", 0.7, Map.of("eventType", eventType));
          }
          String failureKind = jsonText(payload, "failureKind");
          if (!failureKind.isBlank()) {
            builder.node(errorId(failureKind), "ERROR_KIND", failureKind, "failure_kind", failureKind, Map.of());
            builder.edge(eventId(id), errorId(failureKind), "HAS_ERROR_KIND", 0.8, Map.of("eventType", eventType));
          }
        },
        userId,
        blankToNull(focusKc),
        blankToNull(focusKc),
        blankToNull(focusKc),
        limit);
  }

  private void loadAgentRuns(GraphBuilder builder, Long userId, int limit) {
    jdbc.query(
        """
        SELECT id, agent_name, scenario, status, evidence_page_ids, graph_trace::text AS graph_trace,
               created_at
        FROM resource_run
        WHERE user_id = ?
        ORDER BY created_at DESC
        LIMIT ?
        """,
        rs -> {
          long id = rs.getLong("id");
          List<Long> pageIds = readLongArray(rs.getArray("evidence_page_ids"));
          Map<String, Object> attrs = new LinkedHashMap<>();
          attrs.put("agentName", rs.getString("agent_name"));
          attrs.put("scenario", rs.getString("scenario"));
          attrs.put("status", rs.getString("status"));
          attrs.put("graphTrace", parse(rs.getString("graph_trace")));
          attrs.put("createdAt", ts(rs.getTimestamp("created_at")));
          builder.node(runId(id), "AGENT_RUN", rs.getString("agent_name") + " " + id, "resource_run", String.valueOf(id), attrs);
          builder.edge(userId(builder.userId()), runId(id), "ACTED", 0.7, Map.of("source", "resource_run"));
          for (Long pageId : pageIds) {
            builder.page(pageId);
            builder.edge(runId(id), pageId(pageId), "GROUNDED_BY_PAGE", 1.0, Map.of("source", "resource_run"));
          }
        },
        userId,
        limit);
  }

  private void loadKcRelations(GraphBuilder builder, String focusKc, int limit) {
    if (focusKc == null || focusKc.isBlank()) {
      return;
    }
    jdbc.query(
        """
        SELECT subject_id, relation, object_id, source, confidence
        FROM course_kc_edge
        WHERE pack_id = ?
          AND subject_type = 'kc'
          AND object_type = 'kc'
          AND (subject_id = ? OR object_id = ?)
        ORDER BY confidence DESC, relation
        LIMIT ?
        """,
        rs -> {
          String subject = rs.getString("subject_id");
          String object = rs.getString("object_id");
          builder.kc(subject);
          builder.kc(object);
          Map<String, Object> attrs = new LinkedHashMap<>();
          attrs.put("relation", rs.getString("relation"));
          attrs.put("source", rs.getString("source"));
          attrs.put("confidence", rs.getBigDecimal("confidence"));
          builder.edge(kcId(subject), kcId(object), "KC_RELATION", 0.6, attrs);
        },
        PACK_ID,
        focusKc,
        focusKc,
        limit);
  }

  private JsonNode parse(String json) {
    if (json == null || json.isBlank()) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException ex) {
      return objectMapper.createObjectNode();
    }
  }

  private static List<String> extractKcCodes(JsonNode payload) {
    List<String> codes = new ArrayList<>();
    JsonNode single = payload == null ? null : payload.get("kcCode");
    if (single != null && single.isTextual() && !single.asText().isBlank()) {
      codes.add(single.asText());
    }
    JsonNode array = payload == null ? null : payload.get("kcCodes");
    if (array != null && array.isArray()) {
      for (JsonNode node : array) {
        if (node != null && node.isTextual() && !node.asText().isBlank()) {
          codes.add(node.asText());
        }
      }
    }
    return codes.stream().distinct().toList();
  }

  private static Long jsonLong(JsonNode payload, String key) {
    JsonNode node = payload == null ? null : payload.get(key);
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isIntegralNumber()) {
      return node.asLong();
    }
    if (node.isTextual()) {
      try {
        return Long.parseLong(node.asText());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static String jsonText(JsonNode payload, String key) {
    JsonNode node = payload == null ? null : payload.get(key);
    return node == null || node.isNull() ? "" : node.asText("");
  }

  private static Long nullableLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return null;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String ts(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant().toString();
  }

  private static List<String> readStringArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (raw instanceof String[] values) {
      return Arrays.asList(values);
    }
    if (raw instanceof Object[] values) {
      return Arrays.stream(values).map(String::valueOf).toList();
    }
    return List.of();
  }

  private static List<Long> readLongArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    if (raw instanceof Long[] values) {
      return Arrays.asList(values);
    }
    if (raw instanceof Number[] values) {
      return Arrays.stream(values).map(Number::longValue).toList();
    }
    if (raw instanceof Object[] values) {
      return Arrays.stream(values)
          .filter(Number.class::isInstance)
          .map(Number.class::cast)
          .map(Number::longValue)
          .toList();
    }
    return List.of();
  }

  static String userId(Long userId) {
    return "user:" + userId;
  }

  static String resourceId(Long id) {
    return "resource:" + id;
  }

  static String checkId(Long id) {
    return "check:" + id;
  }

  static String eventId(Long id) {
    return "event:" + id;
  }

  static String masteryId(Long id) {
    return "mastery:" + id;
  }

  static String runId(Long id) {
    return "run:" + id;
  }

  static String kcId(String code) {
    return "kc:" + code;
  }

  static String pageId(Long id) {
    return "page:" + id;
  }

  static String problemId(Long id) {
    return "problem:" + id;
  }

  static String errorId(String kind) {
    return "error:" + kind;
  }

  public record RawEvidenceGraph(List<EvidenceNodeDto> nodes, List<EvidenceEdgeDto> edges) {}

  private static final class GraphBuilder {
    private final Long userId;
    private final Map<String, EvidenceNodeDto> nodes = new LinkedHashMap<>();
    private final Map<String, EvidenceEdgeDto> edges = new LinkedHashMap<>();

    GraphBuilder(Long userId) {
      this.userId = userId;
      node(
          LearningEvidenceGraphRepository.userId(userId),
          "USER",
          "user " + userId,
          "user",
          String.valueOf(userId),
          Map.of());
    }

    Long userId() {
      return userId;
    }

    void kc(String kcCode) {
      if (kcCode != null && !kcCode.isBlank()) {
        node(kcId(kcCode), "KC", kcCode, "kc", kcCode, Map.of());
      }
    }

    void page(Long pageId) {
      if (pageId != null) {
        node(pageId(pageId), "PAGE", "page " + pageId, "page", String.valueOf(pageId), Map.of());
      }
    }

    void node(
        String id,
        String type,
        String label,
        String refType,
        String refId,
        Map<String, Object> attributes) {
      nodes.putIfAbsent(id, new EvidenceNodeDto(id, type, label, refType, refId, attributes));
    }

    void edge(
        String fromId, String toId, String relation, double weight, Map<String, Object> attributes) {
      if (fromId == null || toId == null || relation == null) {
        return;
      }
      String key = fromId + "|" + relation + "|" + toId;
      edges.putIfAbsent(key, new EvidenceEdgeDto(fromId, toId, relation, weight, attributes));
    }

    RawEvidenceGraph build() {
      return new RawEvidenceGraph(List.copyOf(nodes.values()), List.copyOf(edges.values()));
    }
  }
}
