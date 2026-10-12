package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.submission.dto.PublicProblemDto;
import com.pythontopia.submission.dto.SubmissionDetailDto;
import com.pythontopia.submission.dto.SubmissionDto;
import com.pythontopia.submission.dto.SubmissionStatus;
import com.pythontopia.submission.dto.Verdict;
import com.pythontopia.submission.repository.PublicProblemRepository;
import com.pythontopia.submission.service.SubmissionService;
import com.pythontopia.tutor.dto.TutorWorkflowCardDto;
import com.pythontopia.tutor.dto.TutorWorkflowAgentStateDto;
import com.pythontopia.tutor.dto.TutorWorkflowFeedbackRequest;
import com.pythontopia.tutor.dto.TutorWorkflowRunRequest;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 导学工作流编排：会话管理 + 证据组装（题面 / 判题 / 画像）+ graph SSE 转发 + 投影落库。
 *
 * <p>与 Alethicode 源版的「graph 回调 Java 内部工具 API 组装 evidence_pack」不同，按本仓
 * 既有模式在转发请求体里直接下传 problem / diagnosis / profile_brief（graph 零回调），
 * 课件 RAG 证据由 graph 自取。阶段合法性由 graph 状态机权威判定（非法迁移 SSE error）。
 */
@Service
public class TutorWorkflowService {

  private static final Logger log = LoggerFactory.getLogger(TutorWorkflowService.class);

  /** 唯一课程知识底座 Alethicode packId=43（AGENTS 硬约束）。 */
  private static final long COURSE_PACK_ID = 43L;

  /** 连错统计回看窗口（最近 N 条同题提交）。 */
  private static final int CONSECUTIVE_ERROR_WINDOW = 10;

  private static final SecureRandom RANDOM = new SecureRandom();

  private final TutorWorkflowRepository repository;
  private final TutorWorkflowGraphClient graphClient;
  private final TutorService tutorService;
  private final PublicProblemRepository problemRepository;
  private final SubmissionService submissionService;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public TutorWorkflowService(
      TutorWorkflowRepository repository,
      TutorWorkflowGraphClient graphClient,
      TutorService tutorService,
      PublicProblemRepository problemRepository,
      SubmissionService submissionService,
      ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.graphClient = graphClient;
    this.tutorService = tutorService;
    this.problemRepository = problemRepository;
    this.submissionService = submissionService;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
  }

  /** 取（或创建）该生该题的活跃会话快照 + 卡片时间线（前端恢复用）。 */
  public Map<String, Object> activeSession(Long userId, Long problemId) {
    requireProblem(problemId);
    TutorWorkflowSessionDto session = findOrCreate(userId, problemId);
    List<TutorWorkflowCardDto> cards = repository.findCards(session.id());
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("sessionKey", session.sessionKey());
    snapshot.put("problemId", session.problemId());
    snapshot.put("phase", session.phase());
    snapshot.put("seq", session.seq());
    snapshot.put("cards", cards);
    return snapshot;
  }

  /** 执行一次工作流事件（SSE）：组装证据 → 转发 graph → result 投影落库。 */
  public SseEmitter run(Long userId, TutorWorkflowRunRequest request) {
    PublicProblemDto problem = requireProblem(request.problemId());
    TutorWorkflowSessionDto session = findOrCreate(userId, request.problemId());

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("session_id", session.sessionKey());
    body.put("user_id", userId);
    body.put("event", request.event());
    body.put("problem", problemContext(problem));
    Map<String, Object> eventData = new HashMap<>(request.eventDataOrEmpty());
    Map<String, Object> diagnosis = resolveDiagnosis(userId, request, eventData);
    if (diagnosis != null) {
      body.put("diagnosis", diagnosis);
    }
    body.put("event_data", eventData);
    body.put("profile_brief", tutorService.resolveProfileBrief(userId, null));
    body.put("coach_mode", true);
    repository.findAgentState(session.id()).ifPresent(state -> restoreAgentState(body, state));
    // 投影回放：graph 进程内已有会话时忽略；重启后据此恢复阶段（持久化为权威）。
    body.put("restore_phase", session.phase());
    body.put("restore_seq", session.seq());

    SseEmitter emitter = new SseEmitter(180_000L);
    graphClient.runStream(
        userId,
        body,
        emitter,
        resultJson -> {
          try {
            persistProjection(session, request, resultJson);
          } catch (Exception e) {
            log.warn("Failed to persist tutor workflow projection", e);
          }
        });
    return emitter;
  }

  /** 记录学生对导学动作/卡片的显式反馈；失败不应由前端阻塞导学主链路。 */
  public void recordFeedback(
      Long userId, String sessionKey, TutorWorkflowFeedbackRequest request) {
    TutorWorkflowSessionDto session =
        repository
            .findBySessionKey(userId, sessionKey)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "WORKFLOW_SESSION_NOT_FOUND", "导学会话不存在"));
    PublicProblemDto problem = requireProblem(session.problemId());
    String targetKey = feedbackTargetKey(request);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("event_type", request.eventType());
    if (request.actionKey() != null && !request.actionKey().isBlank()) {
      payload.put("action_key", request.actionKey());
    }
    if (request.cardKey() != null && !request.cardKey().isBlank()) {
      payload.put("card_key", request.cardKey());
    }
    payload.put("payload", request.payloadOrEmpty());
    try {
      repository.insertActionEvent(
          session.id(),
          session.seq(),
          request.eventType(),
          targetKey,
          objectMapper.writeValueAsString(payload));
    } catch (Exception e) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "WORKFLOW_FEEDBACK_INVALID", "导学反馈写入失败");
    }

    if ("ACTION_CLICKED".equals(request.eventType())) {
      eventPublisher.publishEvent(
          new LearningEvent.GuideActionClicked(
              userId, session.problemId(), problem.kcCode(), targetKey));
    } else if ("CHECKPOINT_COMPLETED".equals(request.eventType())) {
      eventPublisher.publishEvent(
          new LearningEvent.GuideCheckpointCompleted(
              userId, session.problemId(), problem.kcCode(), targetKey));
    }
  }

  private TutorWorkflowSessionDto findOrCreate(Long userId, Long problemId) {
    return repository
        .findActive(userId, problemId)
        .orElseGet(
            () ->
                repository.insertSession(
                    userId, problemId, sessionKey(userId, problemId)));
  }

  private static String sessionKey(Long userId, Long problemId) {
    return "wf-" + userId + "-" + problemId + "-" + Integer.toHexString(RANDOM.nextInt());
  }

  private String feedbackTargetKey(TutorWorkflowFeedbackRequest request) {
    return switch (request.eventType()) {
      case "ACTION_CLICKED" -> requireFeedbackKey(request.actionKey(), "ACTION_KEY_REQUIRED");
      case "CHECKPOINT_COMPLETED" -> requireFeedbackKey(request.cardKey(), "CARD_KEY_REQUIRED");
      case "CARD_HELPFUL", "CARD_NOT_HELPFUL" -> {
        String cardKey = request.cardKey();
        if (cardKey != null && !cardKey.isBlank()) {
          yield cardKey;
        }
        yield requireFeedbackKey(request.actionKey(), "FEEDBACK_TARGET_REQUIRED");
      }
      default ->
          throw new BusinessException(
              HttpStatus.BAD_REQUEST, "UNKNOWN_WORKFLOW_FEEDBACK", "未知的导学反馈事件");
    };
  }

  private String requireFeedbackKey(String value, String code) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, code, "导学反馈缺少目标");
    }
    return value;
  }

  private PublicProblemDto requireProblem(Long problemId) {
    return problemRepository
        .findPublic(COURSE_PACK_ID, problemId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在"));
  }

  private Map<String, Object> problemContext(PublicProblemDto problem) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("problem_id", problem.id());
    context.put("title", nullSafe(problem.title()));
    context.put("statement", nullSafe(problem.statementMarkdown()));
    context.put("input_description", nullSafe(problem.inputDescription()));
    context.put("output_description", nullSafe(problem.outputDescription()));
    context.put("samples", problem.samples() == null ? List.of() : problem.samples());
    context.put(
        "kc_codes", problem.kcCode() == null || problem.kcCode().isBlank()
            ? List.of()
            : List.of(problem.kcCode()));
    context.put("difficulty", nullSafe(problem.difficulty()));
    return context;
  }

  /**
   * ERROR_FEEDBACK / AC_REVIEW 取判题证据：eventData.submission_id 必填，归属 / 题目 / 判定
   * 一致性校验后投影（AC_REVIEW 必须 AC、ERROR_FEEDBACK 必须非 AC）。其余事件返回 null。
   */
  private Map<String, Object> resolveDiagnosis(
      Long userId, TutorWorkflowRunRequest request, Map<String, Object> eventData) {
    boolean needsDiagnosis =
        "ERROR_FEEDBACK".equals(request.event()) || "AC_REVIEW".equals(request.event());
    if (!needsDiagnosis) {
      return null;
    }
    Object raw = eventData.get("submission_id");
    long submissionId;
    try {
      submissionId = Long.parseLong(String.valueOf(raw));
    } catch (NumberFormatException e) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "SUBMISSION_ID_REQUIRED", request.event() + " 需要 submission_id");
    }
    // findOne 内置归属校验（非本人提交 404）。
    SubmissionDetailDto detail = submissionService.findOne(userId, submissionId);
    if (!request.problemId().equals(detail.submission().problemId())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "SUBMISSION_MISMATCH", "提交与当前题目不匹配");
    }
    Verdict verdict = detail.judgeResult() == null ? null : detail.judgeResult().verdict();
    boolean accepted = verdict == Verdict.AC;
    if ("AC_REVIEW".equals(request.event()) && !accepted) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "SUBMISSION_NOT_ACCEPTED", "该提交未通过，无法复盘");
    }
    if ("ERROR_FEEDBACK".equals(request.event()) && accepted) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "SUBMISSION_ACCEPTED", "该提交已通过，请改用 AC 复盘");
    }

    Map<String, Object> diagnosis = new LinkedHashMap<>();
    diagnosis.put("submission_id", submissionId);
    diagnosis.put("verdict", verdict == null ? "" : verdict.name());
    diagnosis.put("code", nullSafe(detail.submission().code()));
    diagnosis.put(
        "failure_reason",
        detail.judgeResult() == null ? "" : nullSafe(detail.judgeResult().failureReason()));
    diagnosis.put(
        "passed_cases", detail.judgeResult() == null ? 0 : detail.judgeResult().passedCases());
    diagnosis.put(
        "total_cases", detail.judgeResult() == null ? 0 : detail.judgeResult().totalCases());
    diagnosis.put("consecutive_errors", countConsecutiveErrors(userId, request.problemId()));
    return diagnosis;
  }

  /** 最近提交里该题的连续失败次数（从最新往回数到首个非失败为止），驱动挫败度分级。 */
  int countConsecutiveErrors(Long userId, Long problemId) {
    try {
      List<SubmissionDto> recent =
          submissionService.list(userId, problemId, null, CONSECUTIVE_ERROR_WINDOW, 0);
      int count = 0;
      for (SubmissionDto submission : recent) {
        if (submission.status() == SubmissionStatus.FAILED) {
          count += 1;
        } else {
          break;
        }
      }
      return count;
    } catch (RuntimeException ex) {
      log.warn("countConsecutiveErrors degraded: {}", ex.getMessage());
      return 0;
    }
  }

  /** result 投影落库：更新会话 phase/seq + 插入卡片（含 ai_reply 的 user_message 回放）。 */
  private void persistProjection(
      TutorWorkflowSessionDto session, TutorWorkflowRunRequest request, String resultJson)
      throws Exception {
    JsonNode root = objectMapper.readTree(resultJson);
    // AG-UI RUN_FINISHED 形状是 {"result": {...}}；历史名 result 是裸体。两种都接。
    JsonNode result = root.has("result") && root.path("result").isObject() ? root.path("result") : root;
    String phase = result.path("phase").asText(session.phase());
    int seq = result.path("seq").asInt(session.seq());
    String event = result.path("event").asText(request.event());
    repository.updateProgress(session.id(), phase, seq);
    persistAgentProjection(session.id(), seq, event, result);

    JsonNode card = result.path("card");
    String cardKey = result.path("card_key").asText(null);
    if (card.isObject() && cardKey != null && !cardKey.isBlank()) {
      JsonNode payload = card;
      if ("ai_reply".equals(cardKey)) {
        Object message = request.eventDataOrEmpty().get("message");
        com.fasterxml.jackson.databind.node.ObjectNode enriched = card.deepCopy();
        enriched.put("user_message", message == null ? "" : String.valueOf(message));
        payload = enriched;
      }
      repository.insertCard(
          session.id(), seq, event, cardKey, objectMapper.writeValueAsString(payload));
      insertActionEvent(session.id(), seq, event, cardKey, result);
    }
  }

  private void restoreAgentState(
      Map<String, Object> body, TutorWorkflowAgentStateDto agentState) {
    try {
      Map<String, Object> state =
          objectMapper.readValue(
              agentState.stateJson(), new TypeReference<Map<String, Object>>() {});
      if (!state.isEmpty()) {
        body.put("student_state", state);
      }
    } catch (Exception e) {
      log.warn("Failed to restore tutor workflow agent state", e);
    }
  }

  private void persistAgentProjection(Long sessionId, int seq, String event, JsonNode result)
      throws Exception {
    JsonNode studentState = result.path("student_state");
    JsonNode coachDecision = result.path("coach_decision");
    if (studentState.isObject() || coachDecision.isObject()) {
      repository.upsertAgentState(
          sessionId,
          studentState.isObject() ? objectMapper.writeValueAsString(studentState) : "{}",
          coachDecision.isObject() ? objectMapper.writeValueAsString(coachDecision) : "{}");
    }
  }

  private void insertActionEvent(
      Long sessionId, int seq, String event, String cardKey, JsonNode result) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("event", event);
    payload.put("card_key", cardKey);
    JsonNode coachDecision = result.path("coach_decision");
    if (coachDecision.isObject()) {
      payload.put("coach_decision", coachDecision);
    }
    repository.insertActionEvent(
        sessionId, seq, "CARD_GENERATED", cardKey, objectMapper.writeValueAsString(payload));
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }
}
