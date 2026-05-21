package com.pythontopia.workshop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.agent.run.RunStatus;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySource;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.service.ProfileService;
import com.pythontopia.workshop.config.WorkshopProperties;
import com.pythontopia.world.crop.CropStateService;
import com.pythontopia.workshop.dto.ContentFormat;
import com.pythontopia.workshop.dto.LearningResourceDto;
import com.pythontopia.workshop.dto.ResourceCreateRequest;
import com.pythontopia.workshop.dto.ResourceCreateResponse;
import com.pythontopia.workshop.dto.ResourceFeedbackRequest;
import com.pythontopia.workshop.dto.ResourceListItemDto;
import com.pythontopia.workshop.dto.ResourceStatusFlag;
import com.pythontopia.workshop.dto.ResourceType;
import com.pythontopia.workshop.graph.ResourceProduceRequestDto;
import com.pythontopia.workshop.graph.ResourceProduceResponseDto;
import com.pythontopia.workshop.graph.WorkshopGraphClient;
import com.pythontopia.workshop.repository.LearningResourceRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceWorkshopService {

  static final Long DEFAULT_PACK_ID = 43L;

  private final WorkshopProperties properties;
  private final WorkshopGraphClient graphClient;
  private final LearningResourceRepository resourceRepository;
  private final CoursePackRepository coursePackRepository;
  private final WorkshopRunRecorder runRecorder;
  private final MasteryEvaluator masteryEvaluator;
  private final CropStateService cropStateService;
  private final ProfileService profileService;
  private final ObjectMapper objectMapper;

  public ResourceWorkshopService(
      WorkshopProperties properties,
      WorkshopGraphClient graphClient,
      LearningResourceRepository resourceRepository,
      CoursePackRepository coursePackRepository,
      WorkshopRunRecorder runRecorder,
      MasteryEvaluator masteryEvaluator,
      CropStateService cropStateService,
      ProfileService profileService,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.graphClient = graphClient;
    this.resourceRepository = resourceRepository;
    this.coursePackRepository = coursePackRepository;
    this.runRecorder = runRecorder;
    this.masteryEvaluator = masteryEvaluator;
    this.cropStateService = cropStateService;
    this.profileService = profileService;
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

    ResourceProduceRequestDto graphRequest =
        new ResourceProduceRequestDto(
            userId,
            validatedKcCodes,
            request.type().name(),
            request.focusHint(),
            profileSnapshot(profile));
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

    awardMastery(userId, validatedKcCodes, request.type());
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
  public void submitFeedback(Long userId, Long resourceId, ResourceFeedbackRequest request) {
    LearningResourceDto resource = findOne(userId, resourceId);
    resourceRepository.updateFeedback(userId, resource.id(), request.status(), request.note());
    if (request.status() == com.pythontopia.workshop.dto.ResourceFeedbackStatus.NONE) {
      return;
    }
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

  private ResourceProduceRequestDto.ProfileSnapshotDto profileSnapshot(LearnerProfileDto profile) {
    if (profile == null) {
      return new ResourceProduceRequestDto.ProfileSnapshotDto("OTHER", 2, List.of());
    }
    String major = profile.major() == null ? "OTHER" : profile.major();
    int level =
        profile.baseLevel() == null || profile.baseLevel().pythonSelfRatedLevel() == null
            ? 2
            : profile.baseLevel().pythonSelfRatedLevel();
    List<String> media =
        profile.learningPreference() == null || profile.learningPreference().mediaPriority() == null
            ? List.of()
            : List.copyOf(profile.learningPreference().mediaPriority());
    return new ResourceProduceRequestDto.ProfileSnapshotDto(major, level, media);
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

  private void awardMastery(Long userId, List<String> kcCodes, ResourceType type) {
    BigDecimal cap = properties.masteryDailyCapPerKc();
    OffsetDateTime startOfDay = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
    for (String code : kcCodes) {
      BigDecimal already = resourceRepository.sumMasteryRewardsToday(userId, code, startOfDay);
      BigDecimal remaining = cap.subtract(already);
      if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      BigDecimal proposed = new BigDecimal("0.05");
      BigDecimal delta = proposed.min(remaining);
      masteryEvaluator.applyEvent(userId, code, MasterySource.RESOURCE_READ, delta, "workshop:" + type.name());
      cropStateService.applyMasteryUpdate(userId, code);
    }
  }

  private String scenarioForType(ResourceType type) {
    return "RESOURCE_" + type.name();
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
