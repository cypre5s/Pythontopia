package com.pythontopia.feynman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.feynman.dto.FeynmanDtos.SessionResponse;
import com.pythontopia.feynman.dto.FeynmanDtos.TurnResponse;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeynmanSessionService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final MisconceptionExtractor extractor;
  private final FeynmanGraphClient graphClient;
  private final PublicProblemRepository problemRepository;

  public FeynmanSessionService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      MisconceptionExtractor extractor,
      FeynmanGraphClient graphClient,
      PublicProblemRepository problemRepository) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.extractor = extractor;
    this.graphClient = graphClient;
    this.problemRepository = problemRepository;
  }

  @Transactional
  public SessionResponse start(long userId, String kcCode) {
    List<Misconception> misconceptions = extractor.extract(userId, kcCode);
    String state = toState(misconceptions);
    PublicProblemRepository.RepresentativeProblem representative =
        problemRepository
            .findRepresentativeProblem(43L, kcCode)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "FEYNMAN_PROBLEM_NOT_FOUND",
                        "该知识点暂无可用于费曼判题的代表题"));
    Long id =
        jdbc.queryForObject(
            "INSERT INTO feynman_session (user_id, kc_code, problem_id, tutee_state, status) "
                + "VALUES (?, ?, ?, ?::jsonb, 'OPEN') RETURNING id",
            Long.class,
            userId,
            kcCode,
            representative.id(),
            state);
    return new SessionResponse(id == null ? 0L : id, userId, kcCode, "OPEN", readJson(state));
  }

  @Transactional
  public TurnResponse teach(long userId, long sessionId, String explanation) {
    SessionData session = loadSession(userId, sessionId);
    List<Misconception> misconceptions = parseMisconceptions(session.tuteeState());

    // 学生本回合的教学先落库为 STUDENT 轮，支撑「误解→你的教学→判题→证据」玻璃盒可溯源。
    jdbc.update(
        "INSERT INTO feynman_turn (session_id, role, content, taught_fix) VALUES (?, 'STUDENT', ?, NULL)",
        sessionId,
        explanation == null ? "" : explanation);

    // 防作弊红线：贴 answer_key/完整代码不算「教会」，学弟拒收且误解不解除。
    if (FeynmanAntiCheat.looksLikeAnswerDump(explanation)) {
      return refuseAnswerDump(sessionId, misconceptions);
    }

    FeynmanGraphClient.FeynmanOutput output =
        graphClient
            .step(session.kcCode(), misconceptions, explanation)
            .orElseGet(() -> fallbackStep(misconceptions, explanation));
    String nextState = toState(output.updatedMisconceptions());
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

  private TurnResponse refuseAnswerDump(long sessionId, List<Misconception> misconceptions) {
    String reply = "请不要直接把完整代码或答案贴给我——讲讲「为什么」这样改，用反例点出我原来的错误规则。";
    Long turnId =
        jdbc.queryForObject(
            "INSERT INTO feynman_turn (session_id, role, content, taught_fix) "
                + "VALUES (?, 'TUTEE', ?, NULL) RETURNING id",
            Long.class,
            sessionId,
            reply);
    // 误解保持不变（不解除），状态保持 OPEN。
    jdbc.update(
        "UPDATE feynman_session SET tutee_state = ?::jsonb, status = 'OPEN' WHERE id = ?",
        toState(misconceptions),
        sessionId);
    return new TurnResponse(turnId == null ? 0L : turnId, "TUTEE", reply, null);
  }

  SessionData loadSession(long userId, long sessionId) {
    try {
      return jdbc.queryForObject(
          "SELECT id, user_id, kc_code, problem_id, status, tutee_state::text AS state "
              + "FROM feynman_session WHERE id = ? AND user_id = ?",
          (rs, rowNum) ->
              new SessionData(
                  rs.getLong("id"),
                  rs.getLong("user_id"),
                  rs.getString("kc_code"),
                  rs.getObject("problem_id", Long.class),
                  rs.getString("status"),
                  rs.getString("state")),
          sessionId,
          userId);
    } catch (EmptyResultDataAccessException exception) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "FEYNMAN_SESSION_NOT_FOUND", "Feynman session not found");
    }
  }

  private FeynmanGraphClient.FeynmanOutput fallbackStep(
      List<Misconception> misconceptions, String explanation) {
    // 仅离线兜底：要求解释足够具体（且已通过防作弊），否则视为「没讲清原因」。
    boolean correct = explanation != null && explanation.length() >= 12;
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

  private static boolean allResolved(List<Misconception> misconceptions) {
    return misconceptions != null && misconceptions.stream().allMatch(Misconception::resolved);
  }

  /** 持久化形态对齐迁移注释 {@code {misconceptions:[...]}} 与前端 FeynmanTuteeStateDto。 */
  private String toState(List<Misconception> misconceptions) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("misconceptions", misconceptions == null ? List.of() : misconceptions);
    return toJson(state);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      return "{\"misconceptions\":[]}";
    }
  }

  private JsonNode readJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      return objectMapper.createObjectNode().set("misconceptions", objectMapper.createArrayNode());
    }
  }

  private List<Misconception> parseMisconceptions(String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode arr = root.isArray() ? root : root.path("misconceptions");
      if (!arr.isArray()) {
        return List.of();
      }
      return objectMapper.convertValue(
          arr, objectMapper.getTypeFactory().constructCollectionType(List.class, Misconception.class));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      return List.of();
    }
  }

  record SessionData(long id, long userId, String kcCode, Long problemId, String status, String tuteeState) {}
}
