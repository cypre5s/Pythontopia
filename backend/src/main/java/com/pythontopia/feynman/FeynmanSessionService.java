package com.pythontopia.feynman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.feynman.dto.FeynmanDtos.BugHuntResponse;
import com.pythontopia.feynman.dto.FeynmanDtos.SessionResponse;
import com.pythontopia.feynman.dto.FeynmanDtos.TurnResponse;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import com.pythontopia.submission.repository.PublicProblemRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FeynmanSessionService {

  private static final int HISTORY_TURN_LIMIT = 12;
  private static final int HISTORY_CHAR_BUDGET = 6000;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final MisconceptionExtractor extractor;
  private final FeynmanGraphClient graphClient;
  private final PublicProblemRepository problemRepository;
  private final ProfileRepository profileRepository;
  private final FeynmanBugBankRepository bugBank;
  private final TransactionTemplate transaction;

  public FeynmanSessionService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      MisconceptionExtractor extractor,
      FeynmanGraphClient graphClient,
      PublicProblemRepository problemRepository,
      ProfileRepository profileRepository,
      FeynmanBugBankRepository bugBank,
      PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.extractor = extractor;
    this.graphClient = graphClient;
    this.problemRepository = problemRepository;
    this.profileRepository = profileRepository;
    this.bugBank = bugBank;
    this.transaction = new TransactionTemplate(transactionManager);
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

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public TurnResponse teach(long userId, long sessionId, String explanation) {
    SessionData session = loadSession(userId, sessionId);
    List<Misconception> misconceptions = parseMisconceptions(session.tuteeState());
    // 必须先读取再插入本轮，否则本轮解释会在 history 和 student_teaching 中重复。
    List<FeynmanGraphClient.ConversationTurn> history = recentHistory(sessionId);

    // 防作弊红线：贴 answer_key/完整代码不算「教会」，学弟拒收且误解不解除。
    if (FeynmanAntiCheat.looksLikeAnswerDump(explanation)) {
      return transaction.execute(
          status -> {
            insertStudentTurn(sessionId, explanation);
            return refuseAnswerDump(sessionId, misconceptions);
          });
    }

    // AGENTS 红线：Agent 不可达必须 fail fast。绝不能靠「解释长度」这类本地规则伪造
    // 「教会 + 学弟产出代码」，那会把误解全部置 resolved、写假 taught_fix，
    // 再由真判题判 WA 反过来把 AI 故障记成学生的 teach_back_failed。
    // 与同类的 bugHunt（FEYNMAN_BUG_EXPOSURE_UNAVAILABLE）保持同一策略。
    FeynmanGraphClient.FeynmanOutput output =
        graphClient
            .step(
                session.kcCode(),
                misconceptions.isEmpty() ? kcTopic(session.kcCode()) : null,
                learnerContext(userId),
                misconceptions,
                explanation,
                history)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_GATEWAY,
                        "FEYNMAN_TUTEE_UNAVAILABLE",
                        "AI 学弟暂时不可用，本次教学未判定成败，请稍后重试"));
    String nextState = toState(output.updatedMisconceptions());
    boolean taught = allResolved(output.updatedMisconceptions());
    return transaction.execute(
        status -> {
          insertStudentTurn(sessionId, explanation);
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
              taught ? "TAUGHT" : "OPEN",
              sessionId);
          return new TurnResponse(
              turnId == null ? 0L : turnId,
              "TUTEE",
              output.replyMd(),
              output.producedCode(),
              null);
        });
  }

  private void insertStudentTurn(long sessionId, String explanation) {
    jdbc.update(
        "INSERT INTO feynman_turn (session_id, role, content, taught_fix) VALUES (?, 'STUDENT', ?, NULL)",
        sessionId,
        explanation == null ? "" : explanation);
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
  public BugHuntResponse bugHunt(long userId, long sessionId) {
    SessionData session = loadSession(userId, sessionId);
    List<Misconception> misconceptions = parseMisconceptions(session.tuteeState());
    FeynmanGraphClient.LearnerContext learnerContext = learnerContext(userId);
    String difficulty = difficultyFor(learnerContext.level());
    if (misconceptions.isEmpty()) {
      Optional<FeynmanBugBankRepository.BugSample> shared =
          bugBank.findOne(session.kcCode(), difficulty);
      if (shared.isPresent()) {
        FeynmanBugBankRepository.BugSample sample = shared.get();
        return new BugHuntResponse(
            "generic_beginner_bug",
            sample.misconceptionTag(),
            sample.buggyCode(),
            sample.bugLineIndices(),
            sample.hintMd());
      }
    }
    // 主题：课程 KC 取中文名让 LLM 生成贴该 KC 的 bug；课件外概念 kcCode 本身即概念词。
    Optional<String> courseTopic = courseKcTopic(session.kcCode());
    String topic = courseTopic.orElse(session.kcCode());
    FeynmanGraphClient.BugExposure exposure =
        graphClient
            .bugExposure(session.kcCode(), topic, learnerContext, misconceptions)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_GATEWAY,
                        "FEYNMAN_BUG_EXPOSURE_UNAVAILABLE",
                        "代码找茬服务暂不可用，稍后再试"));
    if (courseTopic.isPresent() && shareableGenericSample(misconceptions, topic, exposure)) {
      bugBank.insertIfAbsent(session.kcCode(), topic, exposure);
    }
    return new BugHuntResponse(
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
    return courseKcTopic(kcCode).orElse(kcCode);
  }

  private Optional<String> courseKcTopic(String kcCode) {
    if (kcCode == null || kcCode.isBlank()) {
      return Optional.empty();
    }
    try {
      return jdbc
          .query(
              "SELECT kc_name FROM course_kc WHERE pack_id = 43 AND kc_code = ? LIMIT 1",
              (rs, rowNum) -> rs.getString("kc_name"),
              kcCode)
          .stream()
          .filter(name -> name != null && !name.isBlank())
          .findFirst();
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  private FeynmanGraphClient.LearnerContext learnerContext(long userId) {
    try {
      JsonNode dimensions = profileRepository.findDimensions(userId).orElse(null);
      int selfRatedLevel =
          dimensions == null
              ? 0
              : dimensions.path("knowledge_base").path("python_self_rated_level").asInt(0);
      String level =
          selfRatedLevel >= 4 ? "advanced" : selfRatedLevel == 3 ? "intermediate" : "beginner";
      String responseStyle = "examples";
      JsonNode mediaPriority =
          dimensions == null ? null : dimensions.path("learning_preference").path("media_priority");
      if (mediaPriority != null && mediaPriority.isArray()) {
        for (JsonNode medium : mediaPriority) {
          if ("code".equals(medium.asText())) {
            responseStyle = "practice";
            break;
          }
          if ("text".equals(medium.asText()) || "mindmap".equals(medium.asText())) {
            responseStyle = "step_by_step";
            break;
          }
          if ("video".equals(medium.asText())) {
            break;
          }
        }
      }
      return new FeynmanGraphClient.LearnerContext(level, responseStyle);
    } catch (RuntimeException exception) {
      return new FeynmanGraphClient.LearnerContext("beginner", "examples");
    }
  }

  private static String difficultyFor(String level) {
    return switch (level) {
      case "intermediate" -> "medium";
      case "advanced" -> "hard";
      default -> "easy";
    };
  }

  private static boolean shareableGenericSample(
      List<Misconception> misconceptions, String topic, FeynmanGraphClient.BugExposure sample) {
    if (!misconceptions.isEmpty()
        || topic == null
        || topic.isBlank()
        || topic.length() > 160
        || !"generic_beginner_bug".equals(sample.source())
        || !"topic_specific_bug".equals(sample.misconceptionTag())
        || !sample.generatedByAi()
        || sample.buggyCode() == null
        || sample.fixedCode() == null
        || sample.buggyCode().length() > 4000
        || sample.fixedCode().length() > 4000
        || sample.hintMd() == null
        || sample.hintMd().isBlank()
        || sample.hintMd().length() > 500
        || !List.of("easy", "medium", "hard").contains(sample.difficulty())) {
      return false;
    }
    String[] buggyLines = sample.buggyCode().split("\\n", -1);
    String[] fixedLines = sample.fixedCode().split("\\n", -1);
    if (buggyLines.length < 6 || buggyLines.length > 14 || buggyLines.length != fixedLines.length) {
      return false;
    }
    List<Integer> diff = new ArrayList<>();
    for (int index = 0; index < buggyLines.length; index++) {
      if (!buggyLines[index].equals(fixedLines[index])) {
        diff.add(index);
      }
    }
    return !diff.isEmpty() && diff.equals(sample.bugLineIndices());
  }

  record SessionData(
      long id, long userId, String kcCode, Long problemId, String status, String tuteeState) {}
}
