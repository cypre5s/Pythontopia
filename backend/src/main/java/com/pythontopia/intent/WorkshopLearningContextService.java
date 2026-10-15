package com.pythontopia.intent;

import com.pythontopia.intent.dto.IntentRouteResponse;
import com.pythontopia.learner.event.LearningEventLogRepository;
import com.pythontopia.learner.mastery.MasteryService;
import com.pythontopia.learner.mastery.dto.MasterySnapshotDto;
import com.pythontopia.learner.memory.MemoryBriefService;
import com.pythontopia.learner.memory.dto.MemoryBriefDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.submission.repository.PracticeSubmissionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 复用现有画像、记忆、掌握度、正式提交和资源消费事件，构建服务端可信的路由上下文。 */
@Service
public class WorkshopLearningContextService {

  private static final Logger log = LoggerFactory.getLogger(WorkshopLearningContextService.class);
  private static final int RECENT_SUBMISSION_LIMIT = 12;
  private static final int RECENT_RESOURCE_LIMIT = 8;
  private static final Set<String> RESOURCE_TYPES =
      Set.of("HANDOUT", "MINDMAP", "PRACTICE", "CODE_LAB", "READING", "STORYBOARD");

  private final MemoryBriefService memoryBriefService;
  private final PassiveProfileService profileService;
  private final MasteryService masteryService;
  private final PracticeSubmissionRepository submissionRepository;
  private final LearningEventLogRepository eventRepository;

  public WorkshopLearningContextService(
      MemoryBriefService memoryBriefService,
      PassiveProfileService profileService,
      MasteryService masteryService,
      PracticeSubmissionRepository submissionRepository,
      LearningEventLogRepository eventRepository) {
    this.memoryBriefService = memoryBriefService;
    this.profileService = profileService;
    this.masteryService = masteryService;
    this.submissionRepository = submissionRepository;
    this.eventRepository = eventRepository;
  }

  public LearnerRoutingContext build(Long userId, String query) {
    if (userId == null) {
      return LearnerRoutingContext.empty();
    }
    String profileBrief = safeProfileBrief(userId);
    List<ProfileDimensionDto> dimensions = safeDimensions(userId);
    MemoryBriefDto brief = safeMemoryBrief(userId, query);
    List<MasterySnapshotDto> mastery = safeMastery(userId);
    List<PracticeSubmissionRepository.PlannerSubmission> submissions = safeSubmissions(userId);
    List<LearningEventLogRepository.ResourceConsumption> resources = safeResources(userId);

    Map<String, Double> masteryScores = new LinkedHashMap<>();
    Map<String, Double> masteryConfidence = new LinkedHashMap<>();
    for (MasterySnapshotDto snapshot : mastery) {
      if (snapshot == null || blank(snapshot.kcCode())) {
        continue;
      }
      if (snapshot.masteryScore() != null) {
        masteryScores.put(snapshot.kcCode(), snapshot.masteryScore().doubleValue());
      }
      if (snapshot.confidence() != null) {
        masteryConfidence.put(snapshot.kcCode(), snapshot.confidence().doubleValue());
      }
    }

    LinkedHashSet<String> recentErrorKcs = new LinkedHashSet<>(brief.weakKcs());
    int recentFailureCount = 0;
    List<LearnerRoutingContext.RecentSubmission> recentSubmissions = new ArrayList<>();
    for (PracticeSubmissionRepository.PlannerSubmission submission : submissions) {
      if (submission == null || submission.problemId() == null || blank(submission.kcCode())) {
        continue;
      }
      recentSubmissions.add(
          new LearnerRoutingContext.RecentSubmission(
              submission.problemId(), submission.kcCode(), submission.passed()));
      if (!submission.passed()) {
        recentFailureCount++;
        recentErrorKcs.add(submission.kcCode());
      }
    }

    List<LearnerRoutingContext.RecentResource> recentResources = new ArrayList<>();
    for (LearningEventLogRepository.ResourceConsumption resource : resources) {
      if (resource == null || blank(resource.kcCode()) || blank(resource.resourceKind())) {
        continue;
      }
      String type = resource.resourceKind().trim().toUpperCase(Locale.ROOT);
      if (!RESOURCE_TYPES.contains(type)) {
        continue;
      }
      recentResources.add(
          new LearnerRoutingContext.RecentResource(
              resource.kcCode(),
              type,
              resource.createdAt() == null ? "" : resource.createdAt().toString()));
    }

    Map<String, ProfileDimensionDto> dimensionIndex = indexDimensions(dimensions);
    String cognitiveStyle = "";
    LinkedHashSet<String> preferences = new LinkedHashSet<>();
    preferences.addAll(tags(dimensionIndex.get("learning_preference")));
    preferences.addAll(brief.stablePreferences());
    String cognitiveLoad = cognitiveLoad(recentFailureCount, brief);
    String frustrationLevel = "LOW";

    return new LearnerRoutingContext(
        profileBrief,
        masteryScores,
        masteryConfidence,
        brief.weakKcs(),
        List.copyOf(recentErrorKcs),
        cognitiveLoad,
        frustrationLevel,
        cognitiveStyle,
        List.copyOf(preferences),
        profileMajor(dimensionIndex.get("professional_background")),
        pythonLevel(dimensionIndex.get("knowledge_base")),
        recentResources,
        recentSubmissions,
        brief.strugglePatterns(),
        brief.recentWins(),
        brief.interventionHints(),
        brief.asOf());
  }

  /** 路由本身只写观察事件，不直接修改 mastery/画像；真实工具领域事件负责学习状态更新。 */
  public void recordRoute(
      Long userId, IntentRouteResponse response, LearnerRoutingContext context) {
    if (userId == null || response == null || response.routeDecision() == null) {
      return;
    }
    try {
      var decision = response.routeDecision();
      String strategy =
          decision.personalization() == null ? "STANDARD" : decision.personalization().strategy();
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("routeId", decision.routeId());
      payload.put("taskFamily", decision.task() == null ? "UNKNOWN" : decision.task().family());
      payload.put(
          "topology",
          decision.orchestration() == null ? "DIRECT" : decision.orchestration().topology());
      payload.put("strategy", strategy == null ? "STANDARD" : strategy);
      payload.put("kcCodes", response.kcCodes() == null ? List.of() : response.kcCodes());
      payload.put("cognitiveLoad", context == null ? "LOW" : context.cognitiveLoad());
      payload.put("frustrationLevel", context == null ? "LOW" : context.frustrationLevel());
      payload.put("masteryWrite", false);
      eventRepository.insert(userId, "workshop_route_selected", payload, "backend", 2);
    } catch (RuntimeException ex) {
      log.warn(
          "workshop route observation degraded for user={} errorType={}",
          userId,
          ex.getClass().getSimpleName());
    }
  }

  private String safeProfileBrief(Long userId) {
    try {
      String brief = profileService.profileBrief(userId);
      return brief == null ? "" : brief;
    } catch (RuntimeException ex) {
      log.warn(
          "workshop profile brief degraded for user={} errorType={}",
          userId,
          ex.getClass().getSimpleName());
      return "";
    }
  }

  private List<ProfileDimensionDto> safeDimensions(Long userId) {
    try {
      List<ProfileDimensionDto> values = profileService.getDimensions(userId);
      return values == null ? List.of() : values;
    } catch (RuntimeException ex) {
      log.warn(
          "workshop profile dimensions degraded for user={} errorType={}",
          userId,
          ex.getClass().getSimpleName());
      return List.of();
    }
  }

  private MemoryBriefDto safeMemoryBrief(Long userId, String query) {
    try {
      return memoryBriefService.buildAuthoritativeBrief(userId, query);
    } catch (RuntimeException ex) {
      log.warn(
          "workshop memory brief degraded for user={} errorType={}",
          userId,
          ex.getClass().getSimpleName());
      return new MemoryBriefDto(List.of(), List.of(), List.of(), "");
    }
  }

  private List<MasterySnapshotDto> safeMastery(Long userId) {
    try {
      List<MasterySnapshotDto> values = masteryService.listSnapshots(userId);
      return values == null ? List.of() : values;
    } catch (RuntimeException ex) {
      log.warn(
          "workshop mastery degraded for user={} errorType={}",
          userId,
          ex.getClass().getSimpleName());
      return List.of();
    }
  }

  private List<PracticeSubmissionRepository.PlannerSubmission> safeSubmissions(Long userId) {
    try {
      List<PracticeSubmissionRepository.PlannerSubmission> values =
          submissionRepository.findRecentForPlanner(userId, RECENT_SUBMISSION_LIMIT);
      return values == null ? List.of() : values;
    } catch (RuntimeException ex) {
      log.warn(
          "workshop submissions degraded for user={} errorType={}",
          userId,
          ex.getClass().getSimpleName());
      return List.of();
    }
  }

  private List<LearningEventLogRepository.ResourceConsumption> safeResources(Long userId) {
    try {
      List<LearningEventLogRepository.ResourceConsumption> values =
          eventRepository.findRecentResourceConsumptions(userId, RECENT_RESOURCE_LIMIT);
      return values == null ? List.of() : values;
    } catch (RuntimeException ex) {
      log.warn(
          "workshop resource usage degraded for user={} errorType={}",
          userId,
          ex.getClass().getSimpleName());
      return List.of();
    }
  }

  private static Map<String, ProfileDimensionDto> indexDimensions(
      List<ProfileDimensionDto> dimensions) {
    Map<String, ProfileDimensionDto> index = new LinkedHashMap<>();
    for (ProfileDimensionDto dimension : dimensions) {
      if (dimension != null && !blank(dimension.key())) {
        index.put(dimension.key(), dimension);
      }
    }
    return index;
  }

  private static String cognitiveLoad(int failures, MemoryBriefDto brief) {
    if (failures >= 2 || brief.strugglePatterns().size() >= 2) {
      return "HIGH";
    }
    return failures > 0 || !brief.weakKcs().isEmpty() ? "MEDIUM" : "LOW";
  }

  private static int pythonLevel(ProfileDimensionDto knowledge) {
    if (knowledge == null || knowledge.confidence() < 0.3) {
      return 2;
    }
    return Math.max(1, Math.min(5, (int) Math.round(knowledge.score() * 4.0) + 1));
  }

  private static String profileMajor(ProfileDimensionDto professional) {
    String text = String.join(" ", tags(professional)).toLowerCase(Locale.ROOT);
    if (text.contains("计算机") || text.contains("软件") || text.contains("computer")) {
      return "CS";
    }
    if (text.contains("理工") || text.contains("工科") || text.contains("stem")) {
      return "NON_CS_STEM";
    }
    if (text.contains("文科") || text.contains("人文") || text.contains("humanities")) {
      return "NON_CS_HUMANITIES";
    }
    return "OTHER";
  }

  private static List<String> tags(ProfileDimensionDto dimension) {
    if (dimension == null || dimension.confidence() < 0.3 || dimension.tags() == null) {
      return List.of();
    }
    return dimension.tags().stream().filter(tag -> !blank(tag)).map(String::trim).toList();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
