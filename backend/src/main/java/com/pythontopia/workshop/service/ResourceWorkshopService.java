package com.pythontopia.workshop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.agent.run.MetaReflectionService;
import com.pythontopia.agent.run.RunStatus;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.event.LearningEvent;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.profile.service.ProfileService;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.workshop.config.WorkshopProperties;
import com.pythontopia.workshop.dto.ContentFormat;
import com.pythontopia.workshop.dto.LearningResourceDto;
import com.pythontopia.workshop.dto.ResourceCheckEvidenceType;
import com.pythontopia.workshop.dto.ResourceCheckResponse;
import com.pythontopia.workshop.dto.ResourceCheckStatus;
import com.pythontopia.workshop.dto.ResourceCheckSubmitRequest;
import com.pythontopia.workshop.dto.ResourceConsumptionResponse;
import com.pythontopia.workshop.dto.ResourceCreateRequest;
import com.pythontopia.workshop.dto.ResourceCreateResponse;
import com.pythontopia.workshop.dto.ResourceFeedbackRequest;
import com.pythontopia.workshop.dto.ResourceFeedbackStatus;
import com.pythontopia.workshop.dto.ResourceListItemDto;
import com.pythontopia.workshop.dto.ResourceStatusFlag;
import com.pythontopia.workshop.dto.ResourceType;
import com.pythontopia.workshop.graph.ResourceProduceRequestDto;
import com.pythontopia.workshop.graph.ResourceProduceResponseDto;
import com.pythontopia.workshop.graph.WorkshopGraphClient;
import com.pythontopia.workshop.repository.LearningResourceRepository;
import com.pythontopia.workshop.repository.ResourceLearningCheckRepository;
import com.pythontopia.workshop.repository.ResourceFeedbackRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ResourceWorkshopService {

  private static final Logger log = LoggerFactory.getLogger(ResourceWorkshopService.class);

  static final Long DEFAULT_PACK_ID = 43L;
  private static final Set<String> GRAPH_MAJOR_VALUES =
      Set.of("CS", "NON_CS_STEM", "NON_CS_HUMANITIES", "OTHER");

  /** cognitive_style 注入资源生成的置信度门控（与答疑 profile_brief 一致，信号不足不下传）。 */
  private static final double STYLE_CONFIDENCE_GATE = 0.3;

  /** 注入资源 prompt 的近期记忆要点条数（Task 2.1）。 */
  private static final int MEMORY_HIGHLIGHT_LIMIT = 5;

  private final WorkshopProperties properties;
  private final WorkshopGraphClient graphClient;
  private final LearningResourceRepository resourceRepository;
  private final ResourceLearningCheckRepository checkRepository;
  private final CoursePackRepository coursePackRepository;
  private final WorkshopRunRecorder runRecorder;
  private final MasteryEvaluator masteryEvaluator;
  private final ProfileService profileService;
  private final PassiveProfileService passiveProfileService;
  private final LearnerInsightRepository insightRepository;
  private final MemoryRetrievalService memoryRetrievalService;
  private final MetaReflectionService metaReflectionService;
  private final ResourceFeedbackRepository resourceFeedbackRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public ResourceWorkshopService(
      WorkshopProperties properties,
      WorkshopGraphClient graphClient,
      LearningResourceRepository resourceRepository,
      ResourceLearningCheckRepository checkRepository,
      CoursePackRepository coursePackRepository,
      WorkshopRunRecorder runRecorder,
      MasteryEvaluator masteryEvaluator,
      ProfileService profileService,
      PassiveProfileService passiveProfileService,
      LearnerInsightRepository insightRepository,
      MemoryRetrievalService memoryRetrievalService,
      MetaReflectionService metaReflectionService,
      ResourceFeedbackRepository resourceFeedbackRepository,
      ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.graphClient = graphClient;
    this.resourceRepository = resourceRepository;
    this.checkRepository = checkRepository;
    this.coursePackRepository = coursePackRepository;
    this.runRecorder = runRecorder;
    this.masteryEvaluator = masteryEvaluator;
    this.profileService = profileService;
    this.passiveProfileService = passiveProfileService;
    this.insightRepository = insightRepository;
    this.memoryRetrievalService = memoryRetrievalService;
    this.metaReflectionService = metaReflectionService;
    this.resourceFeedbackRepository = resourceFeedbackRepository;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public ResourceCreateResponse create(Long userId, ResourceCreateRequest request) {
    enforceThrottle(userId);
    Map<String, CourseKc> kcIndex = kcIndex();
    List<String> validatedKcCodes = validateKcCodes(request.kcCodes(), kcIndex);
    Long chapterId = pickChapterId(validatedKcCodes, kcIndex);
    LearnerProfileDto profile =
        profileService.findProfile(userId).orElse(null);

    String targetDifficulty = computeTargetDifficulty(userId, validatedKcCodes);
    ResourceProduceRequestDto graphRequest =
        new ResourceProduceRequestDto(
            userId,
            validatedKcCodes,
            request.type().name(),
            request.focusHint(),
            profileSnapshot(userId, focusKc(validatedKcCodes), profile, safeDimensions(userId)),
            targetDifficulty,
            agentStrategyDirective(request.type()));
    ResourceProduceResponseDto graphResponse = graphClient.produce(graphRequest);
    if (graphResponse == null) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "RESOURCE_GRAPH_EMPTY", "资源生成服务返回空响应");
    }

    RunStatus runStatus = parseRunStatus(graphResponse.status());
    Long runId =
        runRecorder.recordInNewTransaction(
            userId,
            agentName(request.type()),
            scenarioForType(request.type()),
            Map.of(
                "kcCodes", validatedKcCodes,
                "type", request.type().name(),
                "focusHint", Optional.ofNullable(request.focusHint()).orElse("")),
            graphResponse.artifact(),
            Optional.ofNullable(graphResponse.evidencePageIds()).orElse(List.of()),
            graphResponse.safetyResult(),
            graphResponse.groundingResult(),
            graphResponse.graphTrace(),
            UUID.randomUUID(),
            runStatus);

    if (runStatus == RunStatus.FAILED) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "RESOURCE_GENERATION_FAILED",
          "资源生成失败：" + safetyReason(graphResponse));
    }

    ResourceProduceResponseDto.ResourceDraftDto artifact = graphResponse.artifact();
    if (artifact == null) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "RESOURCE_GRAPH_NO_ARTIFACT", "资源生成结果缺失 artifact");
    }
    JsonNode content = artifact.contentPayload();
    if (content == null) {
      content = objectMapper.createObjectNode();
    }
    ResourceStatusFlag safetyFlag = flagFromSafety(graphResponse.safetyResult());
    ResourceStatusFlag groundingFlag = flagFromGrounding(graphResponse.groundingResult());

    Long resourceId =
        resourceRepository.insert(
            userId,
            DEFAULT_PACK_ID,
            validatedKcCodes,
            chapterId,
            request.type(),
            artifact.title(),
            ContentFormat.valueOf(artifact.contentFormat()),
            content,
            Optional.ofNullable(graphResponse.evidencePageIds()).orElse(List.of()),
            artifact.summary(),
            safetyFlag,
            groundingFlag,
            runId);

    return new ResourceCreateResponse(runId, resourceId, runStatus.name());
  }

  public SseEmitter createStream(Long userId, ResourceCreateRequest request) {
    enforceThrottle(userId);
    Map<String, CourseKc> kcIdx = kcIndex();
    List<String> validatedKcCodes = validateKcCodes(request.kcCodes(), kcIdx);
    LearnerProfileDto profile = profileService.findProfile(userId).orElse(null);

    String targetDifficulty = computeTargetDifficulty(userId, validatedKcCodes);
    ResourceProduceRequestDto graphRequest =
        new ResourceProduceRequestDto(
            userId,
            validatedKcCodes,
            request.type().name(),
            request.focusHint(),
            profileSnapshot(userId, focusKc(validatedKcCodes), profile, safeDimensions(userId)),
            targetDifficulty,
            agentStrategyDirective(request.type()));

    SseEmitter emitter = new SseEmitter(120_000L);
    Long chapterId = pickChapterId(validatedKcCodes, kcIdx);

    graphClient.produceStream(graphRequest, emitter, resultJson -> {
      ResourceProduceResponseDto graphResponse;
      try {
        graphResponse = objectMapper.readValue(resultJson, ResourceProduceResponseDto.class);
        if (graphResponse == null) {
          throw new BusinessException(
              HttpStatus.BAD_GATEWAY, "RESOURCE_GRAPH_EMPTY", "资源生成服务返回空响应");
        }
        ResourceCreateResponse response =
            persistResource(userId, request, validatedKcCodes, chapterId, graphResponse);
        return objectMapper.writeValueAsString(response);
      } catch (BusinessException e) {
        throw e;
      } catch (Exception e) {
        throw new BusinessException(
            HttpStatus.BAD_GATEWAY, "RESOURCE_STREAM_RESULT_FAILED", "资源生成结果保存失败");
      }
    });
    return emitter;
  }

  private ResourceCreateResponse persistResource(
      Long userId,
      ResourceCreateRequest request,
      List<String> validatedKcCodes,
      Long chapterId,
      ResourceProduceResponseDto graphResponse) {
    RunStatus runStatus = parseRunStatus(graphResponse.status());
    ResourceProduceResponseDto.ResourceDraftDto artifact = graphResponse.artifact();
    if (artifact == null) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "RESOURCE_GRAPH_NO_ARTIFACT", "资源生成结果缺失 artifact");
    }
    com.fasterxml.jackson.databind.JsonNode content = artifact.contentPayload();
    if (content == null) {
      content = objectMapper.createObjectNode();
    }
    ResourceStatusFlag safetyFlag = flagFromSafety(graphResponse.safetyResult());
    ResourceStatusFlag groundingFlag = flagFromGrounding(graphResponse.groundingResult());

    Long runId =
        runRecorder.recordInNewTransaction(
            userId,
            agentName(request.type()),
            scenarioForType(request.type()),
            Map.of(
                "kcCodes", validatedKcCodes,
                "type", request.type().name(),
                "focusHint", Optional.ofNullable(request.focusHint()).orElse("")),
            graphResponse.artifact(),
            Optional.ofNullable(graphResponse.evidencePageIds()).orElse(List.of()),
            graphResponse.safetyResult(),
            graphResponse.groundingResult(),
            graphResponse.graphTrace(),
            UUID.randomUUID(),
            runStatus);

    if (runStatus == RunStatus.FAILED) {
      throw new BusinessException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "RESOURCE_GENERATION_FAILED",
          "资源生成失败：" + safetyReason(graphResponse));
    }

    Long resourceId = resourceRepository.insert(
        userId,
        DEFAULT_PACK_ID,
        validatedKcCodes,
        chapterId,
        request.type(),
        artifact.title(),
        ContentFormat.valueOf(artifact.contentFormat()),
        content,
        Optional.ofNullable(graphResponse.evidencePageIds()).orElse(List.of()),
        artifact.summary(),
        safetyFlag,
        groundingFlag,
        runId);

    return new ResourceCreateResponse(runId, resourceId, runStatus.name());
  }

  @Transactional(readOnly = true)
  public List<ResourceListItemDto> list(Long userId, ResourceType type, String kcCode, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 50));
    int safeOffset = Math.max(0, offset);
    return resourceRepository.list(userId, type, kcCode, safeLimit, safeOffset);
  }

  @Transactional(readOnly = true)
  public LearningResourceDto findOne(Long userId, Long resourceId) {
    return resourceRepository
        .findById(userId, resourceId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "未找到该学习资源"));
  }

  @Transactional
  public ResourceConsumptionResponse consume(Long userId, Long resourceId) {
    LearningResourceDto resource = findOne(userId, resourceId);
    List<String> kcCodes = resource.kcCodes() == null ? List.of() : resource.kcCodes();
    String resourceKind = resource.type() == null ? "UNKNOWN" : resource.type().name();
    for (String kcCode : kcCodes) {
      if (kcCode == null || kcCode.isBlank()) {
        continue;
      }
      eventPublisher.publishEvent(
          new LearningEvent.ResourceConsumed(
              userId, resource.id(), kcCode, resourceKind, BigDecimal.ZERO));
    }
    return new ResourceConsumptionResponse(
        resource.id(), kcCodes, "micro_check", microCheckPrompt(resource, kcCodes));
  }

  @Transactional
  public ResourceCheckResponse submitCheck(
      Long userId, Long resourceId, ResourceCheckSubmitRequest request) {
    LearningResourceDto resource = findOne(userId, resourceId);
    String kcCode = request.kcCode() == null ? "" : request.kcCode().trim();
    if (resource.kcCodes() == null || !resource.kcCodes().contains(kcCode)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "RESOURCE_CHECK_KC_MISMATCH", "该 KC 不属于该资源");
    }

    CheckEvaluation evaluation = evaluateCheck(resource, request);
    BigDecimal delta =
        evaluation.passed()
            ? cappedResourceDelta(userId, kcCode)
            : new BigDecimal("-0.02");
    ResourceType remediationType = evaluation.passed() ? null : ResourceType.PRACTICE;
    ResourceCheckStatus status =
        evaluation.passed() ? ResourceCheckStatus.PASSED : ResourceCheckStatus.FAILED;
    Long checkId =
        checkRepository.insert(
            new ResourceLearningCheckRepository.CheckInsert(
                userId,
                resource.id(),
                kcCode,
                request.evidenceType(),
                request.answerText(),
                request.operationEvidence(),
                status,
                delta,
                remediationType,
                evaluation.reason()));
    eventPublisher.publishEvent(
        new LearningEvent.ResourceCheckCompleted(
            userId,
            checkId,
            resource.id(),
            kcCode,
            request.evidenceType().name(),
            evaluation.passed(),
            delta,
            remediationType == null ? null : remediationType.name()));
    return new ResourceCheckResponse(
        checkId,
        resource.id(),
        kcCode,
        status,
        delta,
        evaluation.passed() ? "continue" : "remediation",
        remediationType,
        evaluation.feedback());
  }

  @Transactional
  public void submitFeedback(Long userId, Long resourceId, ResourceFeedbackRequest request) {
    LearningResourceDto resource = findOne(userId, resourceId);
    resourceRepository.updateFeedback(userId, resource.id(), request.status(), request.note());
    if (request.status() == ResourceFeedbackStatus.NONE) {
      return;
    }
    // P4.1：把采纳/评分落 resource_feedback（按生成它的 agent 归类），供 meta-reflection 据真实反馈进化策略。
    recordResourceFeedback(userId, resource, request.status());
    java.util.LinkedHashMap<String, Object> diff = new java.util.LinkedHashMap<>();
    diff.put("resourceId", resource.id());
    diff.put("status", request.status().name());
    diff.put("kcCodes", resource.kcCodes() == null ? List.of() : resource.kcCodes());
    diff.put("resourceType", resource.type() == null ? null : resource.type().name());
    profileService.applyPassiveRevision(userId, "RESOURCE_FEEDBACK", diff);
  }

  private void enforceThrottle(Long userId) {
    if (properties.isAllowlisted(userId)) {
      return;
    }
    OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
    int recent = resourceRepository.countCreatedAfter(userId, since);
    if (recent >= properties.maxResourcesPerHour()) {
      throw new BusinessException(
          HttpStatus.TOO_MANY_REQUESTS,
          "RESOURCE_THROTTLED",
          "每小时最多生成 " + properties.maxResourcesPerHour() + " 份资源");
    }
  }

  private List<String> validateKcCodes(List<String> requested, Map<String, CourseKc> kcIndex) {
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    List<String> unknown = new ArrayList<>();
    for (String code : requested) {
      if (code == null || code.isBlank()) {
        continue;
      }
      String trimmed = code.trim();
      if (!kcIndex.containsKey(trimmed)) {
        unknown.add(trimmed);
      } else {
        unique.add(trimmed);
      }
    }
    if (!unknown.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "RESOURCE_KC_UNKNOWN",
          "未知 KC code: " + String.join(",", unknown));
    }
    if (unique.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "RESOURCE_KC_EMPTY", "至少提供一个 KC");
    }
    return List.copyOf(unique);
  }

  private Map<String, CourseKc> kcIndex() {
    return coursePackRepository.findKcs(DEFAULT_PACK_ID, null).stream()
        .collect(Collectors.toMap(CourseKc::kcCode, kc -> kc, (a, b) -> a, HashMap::new));
  }

  private Long pickChapterId(List<String> kcCodes, Map<String, CourseKc> kcIndex) {
    for (String code : kcCodes) {
      CourseKc kc = kcIndex.get(code);
      if (kc != null && kc.chapterId() != null) {
        return kc.chapterId();
      }
    }
    return null;
  }

  private ResourceProduceRequestDto.ProfileSnapshotDto profileSnapshot(
      Long userId, String focusKc, LearnerProfileDto profile, List<ProfileDimensionDto> dims) {
    // 把认知风格 + 易错点 + 最新洞察 + 近期记忆要点下传给资源生成 graph，让产物随真实学情自适应。
    String cognitiveStyle = cognitiveStyleTag(dims);
    List<String> errorTags = errorTags(dims);
    String recentInsight = safeRecentInsight(userId);
    List<String> memoryHighlights = safeMemoryHighlights(userId, focusKc);
    if (profile == null) {
      return new ResourceProduceRequestDto.ProfileSnapshotDto(
          "OTHER", 2, List.of(), cognitiveStyle, errorTags, recentInsight, memoryHighlights);
    }
    String major = graphMajor(profile.major());
    int level =
        profile.baseLevel() == null || profile.baseLevel().pythonSelfRatedLevel() == null
            ? 2
            : profile.baseLevel().pythonSelfRatedLevel();
    List<String> media =
        profile.learningPreference() == null || profile.learningPreference().mediaPriority() == null
            ? List.of()
            : List.copyOf(profile.learningPreference().mediaPriority());
    return new ResourceProduceRequestDto.ProfileSnapshotDto(
        major, level, media, cognitiveStyle, errorTags, recentInsight, memoryHighlights);
  }

  /** 取最新一条学情洞察文本注入资源生成（Task 1.3）；任何失败降级为空（不阻断生成）。 */
  private String safeRecentInsight(Long userId) {
    try {
      return insightRepository.findLatestText(userId).orElse("");
    } catch (RuntimeException ex) {
      log.warn("workshop recent insight degraded for user {}: {}", userId, ex.getMessage());
      return "";
    }
  }

  /** 取近期记忆 topK 中文摘要注入资源生成（Task 2.1）；任何失败降级为空（不阻断生成）。 */
  private List<String> safeMemoryHighlights(Long userId, String focusKc) {
    try {
      return memoryRetrievalService.retrieve(userId, focusKc, MEMORY_HIGHLIGHT_LIMIT).stream()
          .map(MemoryItemDto::summaryZh)
          .filter(s -> s != null && !s.isBlank())
          .toList();
    } catch (RuntimeException ex) {
      log.warn("workshop memory highlights degraded for user {}: {}", userId, ex.getMessage());
      return List.of();
    }
  }

  /** 取首个有效 KC 作为记忆检索的聚焦点；无则 null（按 recency+importance 检索）。 */
  private static String focusKc(List<String> kcCodes) {
    return kcCodes == null || kcCodes.isEmpty() ? null : kcCodes.get(0);
  }

  /** 取画像维度，任何失败降级为空（资源生成不因画像读失败而中断）。 */
  private List<ProfileDimensionDto> safeDimensions(Long userId) {
    try {
      return passiveProfileService.getDimensions(userId);
    } catch (RuntimeException ex) {
      log.warn("workshop profile dimensions degraded for user {}: {}", userId, ex.getMessage());
      return List.of();
    }
  }

  /** 自适应难度的置信度门控：低于此值视为「信号不足」，降一档建立信心。 */
  private static final double LOW_CONFIDENCE = 0.4;

  /** 自适应难度的高置信门控：达到此值且逼近本档上沿时升一档促成长。 */
  private static final double HIGH_CONFIDENCE = 0.7;

  /** confidence 缺失时的中性默认值（既不降档也不升档）。 */
  private static final double NEUTRAL_CONFIDENCE = 0.5;

  /**
   * 据首要 KC 的<b>掌握度 + 置信度</b>计算自适应难度目标。基础档由掌握度划分（薄弱→basic、临界→medium、
   * 强→hard）；置信度做二次调节：置信度低（信号不足）则降一档建立信心，置信度高且逼近本档上沿则升一档
   * 促成长。无快照时返回 {@code null}（由 graph 自行决定难度）。
   */
  private String computeTargetDifficulty(Long userId, List<String> kcCodes) {
    if (kcCodes == null || kcCodes.isEmpty()) return null;
    try {
      var snap = masteryEvaluator.snapshot(userId, kcCodes.get(0));
      if (snap.isEmpty() || snap.get().masteryScore() == null) return null;
      double score = snap.get().masteryScore().doubleValue();
      double confidence =
          snap.get().confidence() == null
              ? NEUTRAL_CONFIDENCE
              : snap.get().confidence().doubleValue();
      return difficultyBand(score, confidence);
    } catch (RuntimeException e) {
      log.debug("targetDifficulty degraded for user {}", userId);
      return null;
    }
  }

  /**
   * 掌握度 + 置信度 → 难度档（basic/medium/hard）的纯函数，便于直接单测。
   *
   * <ul>
   *   <li>基础档：score&lt;0.4→basic，&lt;0.7→medium，否则 hard；
   *   <li>低置信度（&lt;{@value #LOW_CONFIDENCE}）：信号不足，降一档（hard→medium→basic，basic 保持）；
   *   <li>高置信度（≥{@value #HIGH_CONFIDENCE}）且逼近本档上沿：升一档（basic→medium→hard，hard 保持）。
   * </ul>
   */
  static String difficultyBand(double score, double confidence) {
    String band = score < 0.4 ? "basic" : (score < 0.7 ? "medium" : "hard");
    if (confidence < LOW_CONFIDENCE) {
      return ease(band);
    }
    if (confidence >= HIGH_CONFIDENCE && nearUpperThreshold(score)) {
      return harder(band);
    }
    return band;
  }

  /** 逼近本档上沿：basic 档 [0.3,0.4) 或 medium 档 [0.6,0.7)（hard 档无上沿可升）。 */
  private static boolean nearUpperThreshold(double score) {
    return (score >= 0.3 && score < 0.4) || (score >= 0.6 && score < 0.7);
  }

  private static String ease(String band) {
    return switch (band) {
      case "hard" -> "medium";
      case "medium" -> "basic";
      default -> "basic";
    };
  }

  private static String harder(String band) {
    return switch (band) {
      case "basic" -> "medium";
      case "medium" -> "hard";
      default -> "hard";
    };
  }

  /** 认知风格主 tag：仅在 cognitive_style 置信度≥门控时给出（信号不足不下传，防幻觉）。 */
  static String cognitiveStyleTag(List<ProfileDimensionDto> dims) {
    if (dims == null) {
      return "";
    }
    for (ProfileDimensionDto d : dims) {
      if ("cognitive_style".equals(d.key())
          && d.confidence() >= STYLE_CONFIDENCE_GATE
          && !d.tags().isEmpty()) {
        return d.tags().get(d.tags().size() - 1);
      }
    }
    return "";
  }

  /** 易错点：error_pattern 维度最近至多 3 个 tag。 */
  static List<String> errorTags(List<ProfileDimensionDto> dims) {
    if (dims == null) {
      return List.of();
    }
    for (ProfileDimensionDto d : dims) {
      if ("error_pattern".equals(d.key()) && !d.tags().isEmpty()) {
        List<String> t = d.tags();
        return t.size() > 3 ? List.copyOf(t.subList(t.size() - 3, t.size())) : List.copyOf(t);
      }
    }
    return List.of();
  }

  private CheckEvaluation evaluateCheck(
      LearningResourceDto resource, ResourceCheckSubmitRequest request) {
    String answer = Optional.ofNullable(request.answerText()).orElse("").trim();
    if (request.evidenceType() == ResourceCheckEvidenceType.OPERATION) {
      return evaluateOperationCheck(answer, request.operationEvidence());
    }
    return evaluateMicroQuiz(resource, answer);
  }

  private CheckEvaluation evaluateMicroQuiz(LearningResourceDto resource, String answer) {
    if (answer.length() < 4) {
      return CheckEvaluation.failed("回答太短，无法证明已经理解。", "answer too short");
    }
    String lowerAnswer = answer.toLowerCase(Locale.ROOT);
    for (String keyword : resourceKeywords(resource)) {
      String normalized = keyword.toLowerCase(Locale.ROOT).trim();
      if (normalized.length() >= 2 && lowerAnswer.contains(normalized)) {
        return CheckEvaluation.passed("已命中资源关键点：" + keyword, "hit keyword:" + keyword);
      }
    }
    return CheckEvaluation.failed(
        "这次回答还没有抓住资源里的关键点，建议先做一道迁移练习巩固。",
        "missing resource keyword");
  }

  private CheckEvaluation evaluateOperationCheck(String answer, JsonNode operationEvidence) {
    String evidenceText = jsonText(operationEvidence).toLowerCase(Locale.ROOT);
    String answerText = Optional.ofNullable(answer).orElse("").toLowerCase(Locale.ROOT);
    String combined = answerText + "\n" + evidenceText;
    boolean hasStructuredEvidence =
        operationEvidence != null
            && (operationEvidence.hasNonNull("code")
                || operationEvidence.hasNonNull("submissionId")
                || operationEvidence.hasNonNull("result")
                || operationEvidence.hasNonNull("output"));
    boolean looksLikeCode =
        combined.contains("input(")
            || combined.contains("print(")
            || combined.contains("```")
            || combined.contains("=")
            || answer.contains("\n")
            || evidenceText.contains("\n");
    if (hasStructuredEvidence && looksLikeCode) {
      return CheckEvaluation.passed("已收到可复查的操作证据。", "operation evidence accepted");
    }
    return CheckEvaluation.failed(
        "需要提交可复查的操作证据，例如代码片段、运行输出或提交编号。",
        "operation evidence missing");
  }

  private List<String> resourceKeywords(LearningResourceDto resource) {
    List<String> values = new ArrayList<>();
    addKeywords(values, resource.title());
    addKeywords(values, resource.summary());
    if (resource.kcCodes() != null) {
      resource.kcCodes().forEach(code -> addKeywords(values, code));
    }
    addKeywords(values, jsonText(resource.content()));
    return values.stream().distinct().limit(80).toList();
  }

  private void addKeywords(List<String> values, String raw) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    String cleaned = raw.trim();
    values.add(cleaned);
    for (String token : cleaned.split("[^\\p{IsHan}\\p{Alnum}_]+")) {
      if (token != null && token.trim().length() >= 2) {
        values.add(token.trim());
      }
    }
  }

  private String jsonText(JsonNode node) {
    if (node == null || node.isNull()) {
      return "";
    }
    if (node.isValueNode()) {
      return node.asText("");
    }
    StringBuilder builder = new StringBuilder();
    node.elements().forEachRemaining(child -> builder.append(' ').append(jsonText(child)));
    node.fields()
        .forEachRemaining(
            entry -> builder.append(' ').append(entry.getKey()).append(' ').append(jsonText(entry.getValue())));
    return builder.toString();
  }

  private record CheckEvaluation(boolean passed, String feedback, String reason) {
    static CheckEvaluation passed(String feedback, String reason) {
      return new CheckEvaluation(true, feedback, reason);
    }

    static CheckEvaluation failed(String feedback, String reason) {
      return new CheckEvaluation(false, feedback, reason);
    }
  }

  private String graphMajor(String rawMajor) {
    if (rawMajor == null || rawMajor.isBlank()) {
      return "OTHER";
    }
    String normalized = rawMajor.trim().toUpperCase(Locale.ROOT);
    if (GRAPH_MAJOR_VALUES.contains(normalized)) {
      return normalized;
    }
    String lower = rawMajor.trim().toLowerCase(Locale.ROOT);
    if (lower.contains("计算机")
        || lower.contains("软件")
        || lower.contains("computer")
        || lower.contains("cs")) {
      return "CS";
    }
    if (lower.contains("理工")
        || lower.contains("工科")
        || lower.contains("stem")
        || lower.contains("engineering")) {
      return "NON_CS_STEM";
    }
    if (lower.contains("文科")
        || lower.contains("人文")
        || lower.contains("humanities")) {
      return "NON_CS_HUMANITIES";
    }
    return "OTHER";
  }

  private ResourceStatusFlag flagFromSafety(ResourceProduceResponseDto.SafetyResultDto safety) {
    if (safety == null) {
      return ResourceStatusFlag.PASSED;
    }
    return Boolean.FALSE.equals(safety.passed()) ? ResourceStatusFlag.FAILED : ResourceStatusFlag.PASSED;
  }

  private ResourceStatusFlag flagFromGrounding(
      ResourceProduceResponseDto.GroundingResultDto grounding) {
    if (grounding == null) {
      return ResourceStatusFlag.PASSED;
    }
    if (Boolean.FALSE.equals(grounding.passed())) {
      return ResourceStatusFlag.FAILED;
    }
    if (Boolean.TRUE.equals(grounding.noEvidenceFound())) {
      return ResourceStatusFlag.NO_EVIDENCE;
    }
    return ResourceStatusFlag.PASSED;
  }

  private BigDecimal cappedResourceDelta(Long userId, String kcCode) {
    BigDecimal cap = properties.masteryDailyCapPerKc();
    OffsetDateTime startOfDay =
        OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
    BigDecimal already = resourceRepository.sumMasteryRewardsToday(userId, kcCode, startOfDay);
    BigDecimal remaining = cap.subtract(already);
    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal proposed = new BigDecimal("0.05");
    return proposed.min(remaining);
  }

  private String microCheckPrompt(LearningResourceDto resource, List<String> kcCodes) {
    String target =
        kcCodes == null || kcCodes.isEmpty()
            ? resource.title()
            : kcCodes.stream().filter(code -> code != null && !code.isBlank()).collect(Collectors.joining("、"));
    if (target == null || target.isBlank()) {
      target = "这份资料";
    }
    return "用 1 分钟做个小检查：请用自己的话说出 " + target + " 的一个关键点，或继续生成一道迁移练习。";
  }

  private String scenarioForType(ResourceType type) {
    return "RESOURCE_" + type.name();
  }

  /** 取目标 agent（按资源类型映射的 canonical 名）最新策略 directive，闭合自我进化闭环；无策略/异常返回 null。 */
  private String agentStrategyDirective(ResourceType type) {
    try {
      return metaReflectionService.latestDirective(agentName(type));
    } catch (RuntimeException ex) {
      log.debug("agent strategy directive degraded for type {}: {}", type, ex.getMessage());
      return null;
    }
  }

  /** 把一次采纳/评分落 resource_feedback，归类到生成该资源的 agent（供 meta-reflection 进化）。 */
  private void recordResourceFeedback(
      Long userId, LearningResourceDto resource, ResourceFeedbackStatus status) {
    String agent = resourceFeedbackRepository.findAgentForResource(resource.id());
    if (agent == null) {
      log.debug("resource {} has no generating agent run; skip resource_feedback", resource.id());
      return;
    }
    boolean adopted = status == ResourceFeedbackStatus.LIKED;
    int rating = adopted ? 5 : 2;
    resourceFeedbackRepository.insert(userId, resource.id(), agent, rating, adopted);
  }

  private String agentName(ResourceType type) {
    return switch (type) {
      case HANDOUT -> "ResourceDesignerAgent.handout";
      case MINDMAP -> "MindMapAgent";
      case PRACTICE -> "PracticeAgent";
      case CODE_LAB -> "CodeLabAgent";
      case READING -> "ResourceDesignerAgent.reading";
      case STORYBOARD -> "StoryboardAgent";
    };
  }

  private RunStatus parseRunStatus(String status) {
    if (status == null) {
      return RunStatus.PARTIAL;
    }
    try {
      return RunStatus.valueOf(status.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return RunStatus.PARTIAL;
    }
  }

  private String safetyReason(ResourceProduceResponseDto graphResponse) {
    ResourceProduceResponseDto.SafetyResultDto safety = graphResponse.safetyResult();
    if (safety != null && safety.reasons() != null && !safety.reasons().isEmpty()) {
      return String.join(",", safety.reasons());
    }
    ResourceProduceResponseDto.GroundingResultDto grounding = graphResponse.groundingResult();
    if (grounding != null && grounding.reasons() != null && !grounding.reasons().isEmpty()) {
      return String.join(",", grounding.reasons());
    }
    return "未知原因";
  }

  @SuppressWarnings("unused")
  private ObjectNode emptyObject() {
    return objectMapper.createObjectNode();
  }
}
