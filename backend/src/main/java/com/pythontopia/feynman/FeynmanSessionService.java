package com.pythontopia.feynman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.feynman.dto.FeynmanDtos.SessionResponse;
import com.pythontopia.feynman.dto.FeynmanDtos.TurnResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeynmanSessionService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final MisconceptionExtractor extractor;
  private final FeynmanGraphClient graphClient;

  public FeynmanSessionService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      MisconceptionExtractor extractor,
      FeynmanGraphClient graphClient) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.extractor = extractor;
    this.graphClient = graphClient;
  }

  @Transactional
  public SessionResponse start(long userId, String kcCode) {
    List<Misconception> misconceptions = extractor.extract(userId, kcCode);
    String state = toJson(misconceptions);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO feynman_session (user_id, kc_code, tutee_state, status) "
                + "VALUES (?, ?, ?::jsonb, 'OPEN') RETURNING id",
            Long.class,
            userId,
            kcCode,
            state);
    return new SessionResponse(id == null ? 0L : id, userId, kcCode, "OPEN", readJson(state));
  }

  @Transactional
  public TurnResponse teach(long sessionId, String explanation) {
    SessionData session = loadSession(sessionId);
    List<Misconception> misconceptions = parseMisconceptions(session.tuteeState());
    FeynmanGraphClient.FeynmanOutput output =
        graphClient
            .step(session.kcCode(), misconceptions, explanation)
            .orElseGet(() -> fallbackStep(misconceptions, explanation));
    String nextState = toJson(output.updatedMisconceptions());
    Long turnId =
        jdbc.queryForObject(
            "INSERT INTO feynman_turn (session_id, role, content, taught_fix) "
                + "VALUES (?, 'TUTEE', ?, ?) RETURNING id",
            Long.class,
            sessionId,
            output.replyMd(),
            output.producedCode());
    jdbc.update(
        "UPDATE feynman_session SET tutee_state = ?::jsonb, status = ? WHERE id = ?",
        nextState,
        allResolved(output.updatedMisconceptions()) ? "TAUGHT" : "OPEN",
        sessionId);
    return new TurnResponse(
        turnId == null ? 0L : turnId, "TUTEE", output.replyMd(), output.producedCode());
  }

  SessionData loadSession(long sessionId) {
    return jdbc.queryForObject(
        "SELECT id, user_id, kc_code, status, tutee_state::text AS state FROM feynman_session WHERE id = ?",
        (rs, rowNum) ->
            new SessionData(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("kc_code"),
                rs.getString("status"),
                rs.getString("state")),
        sessionId);
  }

  private FeynmanGraphClient.FeynmanOutput fallbackStep(
      List<Misconception> misconceptions, String explanation) {
    boolean correct = explanation != null && explanation.length() >= 12 && !looksLikeAnswerDump(explanation);
    List<Misconception> updated =
        misconceptions.stream()
            .map(m -> correct ? new Misconception(m.tag(), m.evidenceEventIds(), true) : m)
            .toList();
    String reply =
        correct
            ? "我懂了：要先说清原因，再改代码。我试着按你的解释修正。"
            : "我还没真正懂，能不能别直接给答案，而是讲讲为什么这样改？";
    String code = correct ? "print('ok')" : null;
    return new FeynmanGraphClient.FeynmanOutput(reply, updated, code);
  }

  private static boolean looksLikeAnswerDump(String explanation) {
    String text = explanation == null ? "" : explanation.toLowerCase();
    return text.contains("answer_key") || text.contains("```python");
  }

  private static boolean allResolved(List<Misconception> misconceptions) {
    return misconceptions != null && misconceptions.stream().allMatch(Misconception::resolved);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      return "[]";
    }
  }

  private JsonNode readJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      return objectMapper.createArrayNode();
    }
  }

  private List<Misconception> parseMisconceptions(String json) {
    try {
      return objectMapper.readerForListOf(Misconception.class).readValue(json);
    } catch (java.io.IOException ex) {
      return List.of();
    }
  }

  record SessionData(long id, long userId, String kcCode, String status, String tuteeState) {}
}
