package com.pythontopia.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CoursePage;
import com.pythontopia.coursepack.repository.CoursePackRepository;
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
import com.pythontopia.tutor.dto.TutorWorkflowAttachmentDto;
import com.pythontopia.tutor.dto.TutorWorkflowFeedbackRequest;
import com.pythontopia.tutor.dto.TutorWorkflowRunRequest;
import com.pythontopia.tutor.dto.TutorWorkflowSessionDto;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  private static final int MAX_RESTORE_CARDS = 12;
  private static final int MAX_RESTORE_CHAT_TURNS = 20;
  private static final int MAX_CHAT_MESSAGE_LENGTH = 2000;
  private static final int MAX_REPLAY_RESULT_BYTES = 1_048_576;
  private static final Pattern IMAGE_DATA_URI =
      Pattern.compile("(?i)data:image/(?:png|jpeg|gif|webp);base64,");
  private static final Pattern EXPLICIT_REFERENCE =
      Pattern.compile("@card:([A-Za-z0-9:_-]+)|@courseware:([0-9]+)");

  /** 唯一课程知识底座 Alethicode packId=43（AGENTS 硬约束）。 */
  private static final long COURSE_PACK_ID = 43L;

  /** 连错统计回看窗口（最近 N 条同题提交）。 */
  private static final int CONSECUTIVE_ERROR_WINDOW = 10;

  private static final SecureRandom RANDOM = new SecureRandom();

  private final TutorWorkflowRepository repository;
  private final TutorWorkflowAttachmentService attachmentService;
  private final TutorWorkflowGraphClient graphClient;
  private final TutorService tutorService;
  private final PublicProblemRepository problemRepository;
  private final CoursePackRepository coursePackRepository;
  private final SubmissionService submissionService;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final TutorDailyRequestLimitService requestLimitService;

  public TutorWorkflowService(
      TutorWorkflowRepository repository,
      TutorWorkflowAttachmentService attachmentService,
      TutorWorkflowGraphClient graphClient,
      TutorService tutorService,
      PublicProblemRepository problemRepository,
      CoursePackRepository coursePackRepository,
      SubmissionService submissionService,
      ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper,
      TutorDailyRequestLimitService requestLimitService) {
    this.repository = repository;
    this.attachmentService = attachmentService;
    this.graphClient = graphClient;
    this.tutorService = tutorService;
    this.problemRepository = problemRepository;
    this.coursePackRepository = coursePackRepository;
    this.submissionService = submissionService;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
    this.requestLimitService = requestLimitService;
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
    repository
        .findAgentState(session.id())
        .map(this::readStoredState)
        .map(state -> state.path("episode_plan"))
        .filter(JsonNode::isObject)
        .ifPresent(
            plan ->
                snapshot.put(
                    "episodePlan",
                    objectMapper.convertValue(plan, new TypeReference<Map<String, Object>>() {})));
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
    Map<String, Object> eventData = new LinkedHashMap<>(request.eventDataOrEmpty());
    validateEventData(request.event(), eventData);
    resolveReferences(request.event(), session, eventData);
    Map<String, Object> diagnosis = resolveDiagnosis(userId, request, eventData);
    if (diagnosis != null) {
      body.put("diagnosis", diagnosis);
    }
    body.put("event_data", eventData);
    body.put("profile_brief", tutorService.resolveProfileBrief(userId, null));
    body.put("coach_mode", true);
    // 投影回放：graph 进程内已有会话时忽略；重启后据此恢复阶段（持久化为权威）。
    body.put("restore_phase", session.phase());
    body.put("restore_seq", session.seq());
    List<TutorWorkflowCardDto> recentCards =
        repository.findRecentCards(session.id(), MAX_RESTORE_CARDS);
    body.put("restore_cards", restoreCards(session, recentCards));
    body.put("restore_chat_history", restoreChatHistory(recentCards));
    repository.findAgentState(session.id()).ifPresent(state -> restoreAgentState(body, state));

    boolean idempotentRequest = request.requestId() != null;
    String requestFingerprint = requestFingerprint(body, idempotentRequest);
    UUID proposedRunId = idempotentRequest ? request.requestId() : UUID.randomUUID();
    if (idempotentRequest) {
      SseEmitter replay = replayCompletedRun(session, proposedRunId, requestFingerprint);
      if (replay != null) {
        return replay;
      }
    }
    Optional<TutorWorkflowRepository.AutoGenCheckpoint> claimed =
        repository.claimAutoGenRun(session.id(), proposedRunId, requestFingerprint);
    if (claimed.isEmpty() && idempotentRequest) {
      SseEmitter replay = replayCompletedRun(session, proposedRunId, requestFingerprint);
      if (replay != null) {
        return replay;
      }
      assertRequestIdMatches(session, proposedRunId, requestFingerprint);
    }
    TutorWorkflowRepository.AutoGenCheckpoint run =
        claimed.orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.CONFLICT,
                    "TUTOR_WORKFLOW_RUN_ACTIVE",
                    "导学会话正在处理其他请求"));
    UUID runId = run.runId();
    body.put("run_id", runId.toString());
    body.put("request_fingerprint", requestFingerprint);
    if (run.stageIndex() >= 0) {
      try {
        JsonNode checkpoint = objectMapper.readTree(run.checkpointJson());
        if (checkpoint == null || !checkpoint.isObject()) {
          throw new IllegalArgumentException("AutoGen checkpoint must be an object");
        }
        body.put("restore_autogen_checkpoint", objectMapper.convertValue(checkpoint, Map.class));
      } catch (Exception e) {
        repository.transitionAutoGenRun(session.id(), runId, "FAILED");
        throw new BusinessException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "TUTOR_WORKFLOW_CHECKPOINT_INVALID",
            "导学恢复点无效");
      }
    }

    try {
      requestLimitService.reserve(userId);
    } catch (RuntimeException e) {
      repository.transitionAutoGenRun(session.id(), runId, "FAILED");
      throw e;
    }

    SseEmitter emitter = new SseEmitter(180_000L);
    graphClient.runStream(
        userId,
        body,
        emitter,
        resultJson -> {
          try {
            persistProjection(session, request, runId, resultJson);
          } catch (Exception e) {
            throw new IllegalStateException("Failed to persist tutor workflow projection", e);
          }
        },
        errorData -> repository.transitionAutoGenRun(session.id(), runId, "FAILED"),
        () -> repository.transitionAutoGenRun(session.id(), runId, "INTERRUPTED"));
    return emitter;
  }

  /** Persist one owner-scoped, text-only AutoGen stage checkpoint. */
  public void persistAutoGenCheckpoint(
      Long userId, String sessionKey, UUID runId, String requestFingerprint, JsonNode checkpoint) {
    TutorWorkflowSessionDto session =
        repository
            .findBySessionKey(userId, sessionKey)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "WORKFLOW_SESSION_NOT_FOUND", "导学会话不存在"));
    if (requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}")) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "TUTOR_WORKFLOW_CHECKPOINT_INVALID", "导学恢复点无效");
    }
    if (checkpoint == null || !checkpoint.isObject()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "TUTOR_WORKFLOW_CHECKPOINT_INVALID", "导学恢复点无效");
    }
    JsonNode stageIndexNode = checkpoint.path("stage_index");
    JsonNode stageNameNode = checkpoint.path("stage_name");
    JsonNode teamState = checkpoint.path("team_state");
    JsonNode workflowState = checkpoint.path("workflow_state");
    List<String> stageNames =
        List.of("validate_transition", "coach_decide", "fetch_evidence", "generate_card", "decide_actions");
    if (!stageIndexNode.canConvertToInt()
        || stageIndexNode.asInt() < 0
        || stageIndexNode.asInt() >= stageNames.size()
        || !stageNameNode.isTextual()
        || !stageNames.get(stageIndexNode.asInt()).equals(stageNameNode.asText())
        || !teamState.isObject()
        || !workflowState.isTextual()
        || workflowState.asText().length() > 1_000_000) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "TUTOR_WORKFLOW_CHECKPOINT_INVALID", "导学恢复点无效");
    }
    try {
      ensureTextOnly(checkpoint);
      String json = objectMapper.writeValueAsString(checkpoint);
      if (json.getBytes(StandardCharsets.UTF_8).length > 1_048_576) {
        throw new IllegalArgumentException("checkpoint exceeds the size limit");
      }
      TutorWorkflowRepository.AutoGenCheckpoint current =
          repository
              .findAutoGenCheckpoint(session.id(), runId)
              .orElseThrow(() -> new IllegalArgumentException("checkpoint run not found"));
      if (!"RUNNING".equals(current.status())
          || !requestFingerprint.equals(current.requestFingerprint())) {
        throw new IllegalArgumentException("checkpoint run is no longer active");
      }
      repository.saveAutoGenCheckpoint(
          session.id(),
          runId,
          requestFingerprint,
          stageIndexNode.asInt(),
          stageNameNode.asText(),
          json);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "TUTOR_WORKFLOW_CHECKPOINT_INVALID", "导学恢复点无效");
    }
  }

  private String requestFingerprint(Map<String, Object> body, boolean stableClientIntent) {
    Map<String, Object> scope = new TreeMap<>();
    List<String> keys =
        stableClientIntent
            ? List.of("session_id", "user_id", "event", "problem", "diagnosis", "event_data")
            : List.of(
                "session_id",
                "user_id",
                "event",
                "problem",
                "diagnosis",
                "event_data",
                "profile_brief",
                "coach_mode",
                "student_state",
                "episode_plan");
    for (String key : keys) {
      if (body.containsKey(key)) {
        scope.put(key, canonicalValue(body.get(key)));
      }
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(scope));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        hex.append(String.format(Locale.ROOT, "%02x", value));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to fingerprint tutor workflow request", e);
    }
  }

  private SseEmitter replayCompletedRun(
      TutorWorkflowSessionDto session, UUID requestId, String requestFingerprint) {
    Optional<TutorWorkflowRepository.AutoGenCheckpoint> existing =
        repository.findAutoGenCheckpoint(session.id(), requestId);
    if (existing.isEmpty()) {
      return null;
    }
    assertRequestIdMatches(session, requestId, requestFingerprint);
    TutorWorkflowRepository.AutoGenCheckpoint checkpoint = existing.orElseThrow();
    if ("FAILED".equals(checkpoint.status())) {
      repository.retryFailedAutoGenRun(session.id(), requestId, requestFingerprint);
      return null;
    }
    if (!"COMPLETED".equals(checkpoint.status())) {
      return null;
    }
    String resultJson =
        repository
            .findAutoGenRunResult(session.id(), requestId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "TUTOR_WORKFLOW_RESULT_UNAVAILABLE",
                        "导学请求已完成，但结果暂不可恢复，请刷新会话"));
    SseEmitter emitter = new SseEmitter(180_000L);
    try {
      emitter.send(SseEmitter.event().name("result").data(resultJson));
      emitter.complete();
    } catch (IOException e) {
      emitter.completeWithError(e);
    }
    return emitter;
  }

  private void assertRequestIdMatches(
      TutorWorkflowSessionDto session, UUID requestId, String requestFingerprint) {
    Optional<TutorWorkflowRepository.AutoGenCheckpoint> existing =
        repository.findAutoGenCheckpoint(session.id(), requestId);
    if (existing.isPresent()
        && !requestFingerprint.equals(existing.orElseThrow().requestFingerprint())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "TUTOR_WORKFLOW_REQUEST_ID_REUSED",
          "请求标识已用于不同的导学请求");
    }
  }

  private Object canonicalValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> canonical = new TreeMap<>();
      map.forEach((key, item) -> canonical.put(String.valueOf(key), canonicalValue(item)));
      return canonical;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(this::canonicalValue).toList();
    }
    return value;
  }

  public TutorWorkflowAttachmentDto uploadAttachment(
      Long userId, String sessionKey, org.springframework.web.multipart.MultipartFile file) {
    return attachmentService.upload(userId, sessionKey, file);
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

  private void validateEventData(String event, Map<String, Object> eventData) {
    Object rawImages = eventData.get("images");
    if (rawImages != null && !"CHAT".equals(event)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "TUTOR_IMAGES_UNSUPPORTED", "图片输入仅支持导学对话");
    }
    if (rawImages != null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "TUTOR_IMAGES_UNSUPPORTED", "图片必须先上传为会话私有附件");
    }
    Object rawAttachmentIds = eventData.get("attachment_ids");
    if (rawAttachmentIds != null && !"CHAT".equals(event)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "TUTOR_IMAGES_UNSUPPORTED", "图片输入仅支持导学对话");
    }
    if (!"CHAT".equals(event)) {
      return;
    }

    Object rawMessage = eventData.getOrDefault("message", "");
    if (!(rawMessage instanceof String message)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "TUTOR_MESSAGE_INVALID", "导学消息必须是文本");
    }
    if (message.length() > MAX_CHAT_MESSAGE_LENGTH || IMAGE_DATA_URI.matcher(message).find()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "TUTOR_MESSAGE_INVALID", "导学消息超过长度限制或包含图片数据");
    }

    List<?> attachmentIds = List.of();
    if (rawAttachmentIds != null) {
      if (!(rawAttachmentIds instanceof List<?> ids)) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "TUTOR_IMAGES_INVALID", "图片附件数量无效");
      }
      if (ids.size() > 3 || ids.stream().anyMatch(id -> !(id instanceof String))) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "TUTOR_IMAGES_INVALID", "图片附件数量无效");
      }
      for (Object attachmentId : ids) {
        try {
          UUID.fromString((String) attachmentId);
        } catch (IllegalArgumentException e) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST, "TUTOR_IMAGES_INVALID", "图片附件引用无效");
        }
      }
      attachmentIds = ids;
      if (message.isBlank() && !ids.isEmpty()) {
        eventData.put("message", "请结合图片帮我分析。");
      }
    }
    if (message.isBlank() && attachmentIds.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "TUTOR_MESSAGE_REQUIRED", "请输入问题或上传图片");
    }
  }

  private void resolveReferences(
      String event, TutorWorkflowSessionDto session, Map<String, Object> eventData) {
    eventData.remove("references");
    if (!"CHAT".equals(event) && !"AGENT_FEEDBACK".equals(event)) {
      return;
    }

    Object rawMessage = eventData.get("message");
    String message = rawMessage instanceof String text ? text : "";
    Matcher matcher = EXPLICIT_REFERENCE.matcher(message);
    List<String> tokens = new ArrayList<>();
    while (matcher.find()) {
      String token =
          matcher.group(1) == null
              ? "courseware:" + matcher.group(2)
              : "card:" + matcher.group(1);
      if (!tokens.contains(token)) {
        tokens.add(token);
      }
      if (tokens.size() > 5) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "TUTOR_REFERENCES_INVALID", "最多引用 5 条内容");
      }
    }

    List<Map<String, Object>> references = new ArrayList<>();
    List<TutorWorkflowCardDto> cards = null;
    for (String token : tokens) {
      if (token.startsWith("card:")) {
        if (cards == null) {
          cards = repository.findCards(session.id());
        }
        String cardId = token.substring("card:".length());
        TutorWorkflowCardDto card =
            cards.stream()
                .filter(candidate -> matchesCardId(session, candidate, cardId))
                .findFirst()
                .orElseThrow(this::referenceNotFound);
        JsonNode payload = card.payload();
        references.add(
            Map.of(
                "kind", "card",
                "card_id", cardId,
                "card_key", card.cardKey(),
                "snippet", cardShortText(card.cardKey(), payload)));
      } else {
        long pageId;
        try {
          pageId = Long.parseLong(token.substring("courseware:".length()));
        } catch (NumberFormatException e) {
          throw referenceNotFound();
        }
        CoursePage page =
            coursePackRepository
                .findPageById(pageId)
                .filter(candidate -> Long.valueOf(COURSE_PACK_ID).equals(candidate.packId()))
                .orElseThrow(this::referenceNotFound);
        String snippet =
            page.excerpt() == null || page.excerpt().isBlank()
                ? nullSafe(page.contentMarkdown()).trim()
                : page.excerpt().trim();
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("kind", "courseware");
        reference.put("page_id", page.id());
        reference.put("page_no", page.pageNo());
        reference.put("chapter_id", page.chapterId());
        reference.put("title", nullSafe(page.title()));
        reference.put("snippet", snippet.substring(0, Math.min(2000, snippet.length())));
        references.add(reference);
      }
    }
    eventData.put("references", references);
  }

  private boolean matchesCardId(
      TutorWorkflowSessionDto session, TutorWorkflowCardDto card, String requestedId) {
    String storedId = card.payload().path("card_id").asText("");
    return requestedId.equals(storedId)
        || requestedId.equals(session.sessionKey() + ":" + card.seq())
        || requestedId.equals("seq-" + card.seq());
  }

  private BusinessException referenceNotFound() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "TUTOR_REFERENCE_NOT_FOUND", "引用内容不可用");
  }

  private List<Map<String, Object>> restoreCards(
      TutorWorkflowSessionDto session, List<TutorWorkflowCardDto> cards) {
    return cards.stream()
        .map(
            card -> {
              JsonNode payload = card.payload();
              Map<String, Object> restored = new LinkedHashMap<>();
              restored.put(
                  "card_id", payload.path("card_id").asText(session.sessionKey() + ":" + card.seq()));
              restored.put("card_key", card.cardKey());
              restored.put("short_text", cardShortText(card.cardKey(), payload));
              String direction = payload.path("next_practice_direction").asText("");
              if (!direction.isBlank()) {
                restored.put("next_practice_direction", direction);
              }
              return restored;
            })
        .toList();
  }

  private String cardShortText(String cardKey, JsonNode payload) {
    for (String field :
        List.of(
            "problem_restatement",
            "analysis",
            "description",
            "root_cause",
            "success_summary",
            "review_content",
            "summary",
            "drill_goal",
            "title",
            "content")) {
      String text = payload.path(field).asText("").trim();
      if (!text.isEmpty()) {
        return text.substring(0, Math.min(200, text.length()));
      }
    }
    return cardKey;
  }

  private List<Map<String, String>> restoreChatHistory(List<TutorWorkflowCardDto> cards) {
    List<Map<String, String>> history = new ArrayList<>();
    for (TutorWorkflowCardDto card : cards) {
      if (!"ai_reply".equals(card.cardKey())) {
        continue;
      }
      String userMessage = card.payload().path("user_message").asText("").trim();
      String answer = card.payload().path("content").asText("").trim();
      if (userMessage.isEmpty()
          || answer.isEmpty()
          || IMAGE_DATA_URI.matcher(userMessage).find()) {
        continue;
      }
      history.add(Map.of("role", "user", "content", limitText(userMessage)));
      history.add(Map.of("role", "assistant", "content", limitText(answer)));
    }
    return history.size() <= MAX_RESTORE_CHAT_TURNS
        ? history
        : history.subList(history.size() - MAX_RESTORE_CHAT_TURNS, history.size());
  }

  private String limitText(String value) {
    return value.substring(0, Math.min(MAX_CHAT_MESSAGE_LENGTH, value.length()));
  }

  private String chatMessage(TutorWorkflowRunRequest request) {
    Map<String, Object> eventData = request.eventDataOrEmpty();
    Object message = eventData.get("message");
    if (message instanceof String text && !text.isBlank()) {
      return text;
    }
    Object attachments = eventData.get("attachment_ids");
    return attachments instanceof List<?> list && !list.isEmpty() ? "请结合图片帮我分析。" : "";
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

  /** 最近提交里该题的连续失败次数（客观事实，仅供选择分步补救内容）。 */
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

  /** result 投影落库：同一 run_id 下 progress/state/card/event 原子提交。 */
  private void persistProjection(
      TutorWorkflowSessionDto session,
      TutorWorkflowRunRequest request,
      UUID runId,
      String resultJson)
      throws Exception {
    JsonNode root = objectMapper.readTree(resultJson);
    // AG-UI RUN_FINISHED 形状是 {"result": {...}}；历史名 result 是裸体。两种都接。
    JsonNode result = root.has("result") && root.path("result").isObject() ? root.path("result") : root;
    String phase = result.path("phase").asText(session.phase());
    int seq = result.path("seq").asInt(session.seq());
    String event = result.path("event").asText(request.event());

    TutorWorkflowAgentStateDto previousState =
        repository
            .findAgentState(session.id())
            .orElse(new TutorWorkflowAgentStateDto(session.id(), "{}", "{}"));
    String checkpointJson = checkpointJson(result, previousState);
    JsonNode coachDecision = result.path("coach_decision");
    String decisionJson =
        coachDecision.isObject()
            ? objectMapper.writeValueAsString(coachDecision)
            : safeJson(previousState.decisionJson(), "{}");

    JsonNode card = result.path("card");
    String cardKey = result.path("card_key").asText(null);
    String cardPayloadJson = null;
    if (card.isObject() && cardKey != null && !cardKey.isBlank()) {
      JsonNode payload = card;
      if ("ai_reply".equals(cardKey)) {
        ObjectNode enriched = card.deepCopy();
        enriched.put("user_message", chatMessage(request));
        payload = enriched;
      }
      ensureTextOnly(payload);
      cardPayloadJson = objectMapper.writeValueAsString(payload);
    }
    String actionPayloadJson =
        actionPayloadJson(runId, event, cardKey, result);
    repository.persistRunProjection(
        session.id(),
        new TutorWorkflowRepository.RunProjection(
            runId,
            phase,
            seq,
            event,
            checkpointJson,
            decisionJson,
            cardPayloadJson == null ? null : cardKey,
            cardPayloadJson,
            actionPayloadJson,
            replayResultJson(session, event, phase, seq, result)));
  }

  private String replayResultJson(
      TutorWorkflowSessionDto session,
      String event,
      String phase,
      int seq,
      JsonNode result)
      throws Exception {
    ObjectNode replay = objectMapper.createObjectNode();
    replay.put("session_id", session.sessionKey());
    replay.put("event", event);
    replay.put("phase", phase);
    replay.put("seq", seq);
    if (result.hasNonNull("card_key")) {
      replay.set("card_key", result.path("card_key"));
    } else {
      replay.putNull("card_key");
    }
    if (result.hasNonNull("card")) {
      replay.set("card", result.path("card"));
    } else {
      replay.putNull("card");
    }
    replay.set(
        "available_actions",
        result.path("available_actions").isArray()
            ? result.path("available_actions")
            : objectMapper.createArrayNode());
    for (String field : List.of("episode_plan", "episode_patch", "resume_chat_history")) {
      if (result.has(field)) {
        replay.set(field, result.path(field));
      }
    }
    ensureTextOnly(replay);
    String json = objectMapper.writeValueAsString(replay);
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_REPLAY_RESULT_BYTES) {
      throw new IllegalArgumentException("Tutor workflow replay result exceeds the size limit");
    }
    return json;
  }

  private void restoreAgentState(
      Map<String, Object> body, TutorWorkflowAgentStateDto agentState) {
    try {
      JsonNode stored = objectMapper.readTree(agentState.stateJson());
      if (stored == null || !stored.isObject()) {
        return;
      }
      boolean wrapped =
          stored.has("student_state") || stored.has("episode_plan") || stored.has("chat_history");
      JsonNode studentState = wrapped ? stored.path("student_state") : stored;
      if (studentState.isObject() && !studentState.isEmpty()) {
        body.put(
            "student_state",
            objectMapper.convertValue(studentState, new TypeReference<Map<String, Object>>() {}));
      }
      JsonNode episodePlan = stored.path("episode_plan");
      if (wrapped && episodePlan.isObject()) {
        body.put(
            "episode_plan",
            objectMapper.convertValue(episodePlan, new TypeReference<Map<String, Object>>() {}));
      }
      JsonNode chatHistory = stored.path("chat_history");
      if (wrapped && chatHistory.isArray()) {
        body.put("restore_chat_history", safeChatHistory(chatHistory));
      }
    } catch (Exception e) {
      log.warn("Failed to restore tutor workflow agent state", e);
    }
  }

  private String checkpointJson(JsonNode result, TutorWorkflowAgentStateDto previous)
      throws Exception {
    JsonNode previousState = readStoredState(previous);
    boolean wrapped =
        previousState.has("student_state")
            || previousState.has("episode_plan")
            || previousState.has("chat_history");
    JsonNode studentState = result.path("student_state");
    JsonNode episodePlan = result.path("episode_plan");
    JsonNode resumeChatHistory = result.path("resume_chat_history");
    ObjectNode checkpoint = objectMapper.createObjectNode();
    JsonNode previousStudentState = wrapped ? previousState.path("student_state") : previousState;
    checkpoint.set(
        "student_state",
        studentState.isObject() ? studentState : objectOrEmpty(previousStudentState));
    JsonNode previousPlan = previousState.path("episode_plan");
    JsonNode savedPlan = episodePlan.isObject() ? episodePlan : previousPlan;
    if (savedPlan.isObject()) {
      checkpoint.set("episode_plan", savedPlan);
    }
    JsonNode historySource =
        resumeChatHistory.isArray() ? resumeChatHistory : previousState.path("chat_history");
    checkpoint.set("chat_history", objectMapper.valueToTree(safeChatHistory(historySource)));
    ensureTextOnly(checkpoint);
    return objectMapper.writeValueAsString(checkpoint);
  }

  private JsonNode readStoredState(TutorWorkflowAgentStateDto agentState) {
    return readStoredState(agentState.stateJson());
  }

  private JsonNode readStoredState(String stateJson) {
    try {
      JsonNode state = objectMapper.readTree(stateJson);
      return state != null && state.isObject() ? state : objectMapper.createObjectNode();
    } catch (Exception e) {
      log.warn("Failed to read tutor workflow checkpoint", e);
      return objectMapper.createObjectNode();
    }
  }

  private JsonNode objectOrEmpty(JsonNode value) {
    return value != null && value.isObject() ? value : objectMapper.createObjectNode();
  }

  private List<Map<String, String>> safeChatHistory(JsonNode history) {
    if (history == null || !history.isArray()) {
      return List.of();
    }
    List<Map<String, String>> safe = new ArrayList<>();
    for (JsonNode turn : history) {
      if (!turn.isObject()) {
        continue;
      }
      String role = turn.path("role").asText("");
      String content = turn.path("content").asText("");
      if (!("user".equals(role) || "assistant".equals(role))
          || content.isBlank()
          || IMAGE_DATA_URI.matcher(content).find()) {
        continue;
      }
      safe.add(Map.of("role", role, "content", limitText(content)));
    }
    return safe.size() <= MAX_RESTORE_CHAT_TURNS
        ? safe
        : safe.subList(safe.size() - MAX_RESTORE_CHAT_TURNS, safe.size());
  }

  private void ensureTextOnly(JsonNode node) {
    if (node.isTextual()) {
      if (IMAGE_DATA_URI.matcher(node.asText()).find()) {
        throw new IllegalArgumentException("Tutor checkpoint cannot persist image data");
      }
      return;
    }
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              field -> {
                String key = field.getKey().toLowerCase(Locale.ROOT);
                if (key.contains("image") || key.contains("base64") || key.contains("bytes")) {
                  throw new IllegalArgumentException("Tutor checkpoint cannot persist image data");
                }
                ensureTextOnly(field.getValue());
              });
      return;
    }
    if (node.isArray()) {
      node.forEach(this::ensureTextOnly);
    }
  }

  private String actionPayloadJson(UUID runId, String event, String cardKey, JsonNode result)
      throws Exception {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("run_id", runId.toString());
    payload.put("event", event);
    if (cardKey != null && !cardKey.isBlank()) {
      payload.put("card_key", cardKey);
    }
    JsonNode coachDecision = result.path("coach_decision");
    if (coachDecision.isObject()) {
      payload.set("coach_decision", coachDecision);
    }
    JsonNode episodePlan = result.path("episode_plan");
    if (episodePlan.isObject()) {
      payload.set("episode_plan", episodePlan);
    }
    JsonNode episodePatch = result.path("episode_patch");
    if (episodePatch.isObject()) {
      payload.set("episode_patch", episodePatch);
    }
    ensureTextOnly(payload);
    return objectMapper.writeValueAsString(payload);
  }

  private String safeJson(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      JsonNode parsed = objectMapper.readTree(value);
      return parsed != null && parsed.isObject() ? objectMapper.writeValueAsString(parsed) : fallback;
    } catch (Exception e) {
      return fallback;
    }
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }
}
