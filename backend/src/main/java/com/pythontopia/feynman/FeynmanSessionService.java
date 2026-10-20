package com.pythontopia.feynman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.feynman.dto.FeynmanDtos.SessionResponse;
import com.pythontopia.feynman.dto.FeynmanDtos.TurnResponse;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.util.ArrayList;
import java.util.Collections;
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

  private static final int HISTORY_TURN_LIMIT = 12;
  private static final int HISTORY_CHAR_BUDGET = 6000;

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
  public SessionResponse start(long userId, String rawKcCode) {
    // kc_code 列为 VARCHAR(40)：课件外自由概念可能超长，截断到 40 防写库失败（不影响生成主题，
    // 主题另由 kcTopic 取人类可读串）。
    String kcCode = rawKcCode != null && rawKcCode.length() > 40 ? rawKcCode.substring(0, 40) : rawKcCode;
    List<Misconception> misconceptions = extractor.extract(userId, kcCode);
    String state = toState(misconceptions);
    // 课程模式：该 KC 在 pack43 有代表题 → 绑定代表题，支持「教学→产代码→真判题→掌握度回流」。
    // 概念模式：课件外概念 / 无代表题的 KC → problem_id 留空，仅走「代码找茬 + 学弟对话」（通识，不计掌握度）。
    Long problemId =
        problemRepository
            .findRepresentativeProblem(43L, kcCode)
            .map(PublicProblemRepository.RepresentativeProblem::id)
            .orElse(null);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO feynman_session (user_id, kc_code, problem_id, tutee_state, status) "
                + "VALUES (?, ?, ?, ?::jsonb, 'OPEN') RETURNING id",
            Long.class,
            userId,
            kcCode,
            problemId,
            state);
    return new SessionResponse(id == null ? 0L : id, userId, kcCode, "OPEN", readJson(state));
  }

  @Transactional
  public TurnResponse teach(long userId, long sessionId, String explanation) {
    SessionData session = loadSession(userId, sessionId);
    List<Misconception> misconceptions = parseMisconceptions(session.tuteeState());
    // 必须先读取再插入本轮，否则本轮解释会在 history 和 student_teaching 中重复。
    List<FeynmanGraphClient.ConversationTurn> history = recentHistory(sessionId);

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
            .step(session.kcCode(), misconceptions, explanation, history)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_GATEWAY,
                        "FEYNMAN_TUTEE_UNAVAILABLE",
                        "AI 学弟暂时不可用，本次教学未判定成败，请稍后重试"));
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
        turnId == null ? 0L : turnId,
        "TUTEE",
        output.replyMd(),
        output.producedCode(),
        null);
  }

  private List<FeynmanGraphClient.ConversationTurn> recentHistory(long sessionId) {
    List<FeynmanGraphClient.ConversationTurn> newestFirst =
        jdbc.query(
            "SELECT role, content FROM feynman_turn WHERE session_id = ? "
                + "ORDER BY created_at DESC, id DESC LIMIT ?",
            (rs, rowNum) -> {
              String role =
                  switch (rs.getString("role")) {
                    case "STUDENT" -> "user";
                    case "TUTEE" -> "assistant";
                    default -> throw new IllegalStateException("Unknown Feynman turn role");
                  };
              String content = rs.getString("content");
              return new FeynmanGraphClient.ConversationTurn(
                  role, content == null ? "" : content);
            },
            sessionId,
            HISTORY_TURN_LIMIT);
    List<FeynmanGraphClient.ConversationTurn> chronological =
        new ArrayList<>(newestFirst);
    Collections.reverse(chronological);

    int totalCharacters =
        chronological.stream().mapToInt(turn -> turn.content().length()).sum();
    int firstKept = 0;
    while (totalCharacters > HISTORY_CHAR_BUDGET && firstKept < chronological.size()) {
      totalCharacters -= chronological.get(firstKept).content().length();
      firstKept++;
    }
    return List.copyOf(chronological.subList(firstKept, chronological.size()));
  }

  /** 代码找茬：基于该会话的真实误解，让学弟写出含 bug 的代码 + 真实 bug 行号供学生找茬。 */
  @Transactional(readOnly = true)
  public com.pythontopia.feynman.dto.FeynmanDtos.BugHuntResponse bugHunt(
      long userId, long sessionId) {
    SessionData session = loadSession(userId, sessionId);
    List<Misconception> misconceptions = parseMisconceptions(session.tuteeState());
    // 主题：课程 KC 取中文名让 LLM 生成贴该 KC 的 bug；课件外概念 kcCode 本身即概念词。
    String topic = kcTopic(session.kcCode());
    FeynmanGraphClient.BugExposure exposure =
        graphClient
            .bugExposure(session.kcCode(), topic, misconceptions)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_GATEWAY,
                        "FEYNMAN_BUG_EXPOSURE_UNAVAILABLE",
                        "代码找茬服务暂不可用，稍后再试"));
    return new com.pythontopia.feynman.dto.FeynmanDtos.BugHuntResponse(
        exposure.source(),
        exposure.misconceptionTag(),
        exposure.buggyCode(),
        exposure.bugLineIndices(),
        exposure.hintMd());
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
    return new TurnResponse(turnId == null ? 0L : turnId, "TUTEE", reply, null, null);
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

  private static boolean allResolved(List<Misconception> misconceptions) {
    return misconceptions != null
        && !misconceptions.isEmpty()
        && misconceptions.stream().allMatch(Misconception::resolved);
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

  boolean hasMisconceptions(String tuteeState) {
    return !parseMisconceptions(tuteeState).isEmpty();
  }

  /**
   * 该 KC 的人类可读主题：课程 KC 取 {@code course_kc.kc_name}（让 LLM 生成贴该 KC 的 bug）；
   * 课件外概念在 kc_code 列本就是概念词，直接用作主题。查不到一律回退 kcCode，绝不阻塞。
   */
  private String kcTopic(String kcCode) {
    if (kcCode == null || kcCode.isBlank()) {
      return kcCode;
    }
    try {
      String name =
          jdbc
              .query(
                  "SELECT kc_name FROM course_kc WHERE pack_id = 43 AND kc_code = ? LIMIT 1",
                  (rs, rowNum) -> rs.getString("kc_name"),
                  kcCode)
              .stream()
              .findFirst()
              .orElse(null);
      return name == null || name.isBlank() ? kcCode : name;
    } catch (RuntimeException ex) {
      return kcCode;
    }
  }

  record SessionData(long id, long userId, String kcCode, Long problemId, String status, String tuteeState) {}
}
