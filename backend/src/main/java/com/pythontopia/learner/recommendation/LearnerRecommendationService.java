package com.pythontopia.learner.recommendation;

import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.evidence.EvidenceMemoryService;
import com.pythontopia.learner.evidence.EvidenceMemorySignalDto;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.profile.service.ProfileService;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import com.pythontopia.learner.twin.LearnerTwinService;
import com.pythontopia.world.boss.dto.FrontierPickDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 「今日推送」复用 Twin 的确定性 KC 顺序；学习洞察与证据图只补充可溯源推荐理由。 */
@Service
public class LearnerRecommendationService {

  private static final Long DEFAULT_PACK_ID = 43L;
  private static final int MAX_RECOMMENDATIONS = 3;

  /** 失败记忆扫描条数（topK），用于把「近期反复受挫的 KC」纳入推送排序。 */
  private static final int MEMORY_SCAN_LIMIT = 8;

  /** 洞察文本注入推荐理由的最大摘要长度，避免理由过长。 */
  private static final int INSIGHT_BRIEF_MAX = 40;

  private final JdbcTemplate jdbcTemplate;
  private final CoursePackRepository coursePackRepository;
  private final LearnerTwinService learnerTwinService;
  private final ProfileService profileService;
  private final PassiveProfileService passiveProfileService;
  private final LearnerInsightRepository insightRepository;
  private final EvidenceMemoryService evidenceMemoryService;
  private final MemoryRetrievalService memoryRetrievalService;

  public LearnerRecommendationService(
      JdbcTemplate jdbcTemplate,
      CoursePackRepository coursePackRepository,
      LearnerTwinService learnerTwinService,
      ProfileService profileService,
      PassiveProfileService passiveProfileService,
      LearnerInsightRepository insightRepository,
      EvidenceMemoryService evidenceMemoryService,
      MemoryRetrievalService memoryRetrievalService) {
    this.jdbcTemplate = jdbcTemplate;
    this.coursePackRepository = coursePackRepository;
    this.learnerTwinService = learnerTwinService;
    this.profileService = profileService;
    this.passiveProfileService = passiveProfileService;
    this.insightRepository = insightRepository;
    this.evidenceMemoryService = evidenceMemoryService;
    this.memoryRetrievalService = memoryRetrievalService;
  }

  public List<RecommendationDto> recommend(Long userId) {
    List<String> mediaPriority = resolveMediaPriority(userId);
    List<FrontierPickDto> picks =
        learnerTwinService.frontier(userId, null, List.of(), MAX_RECOMMENDATIONS).picks();

    if (picks.isEmpty()) {
      return buildColdStartRecommendations();
    }

    Map<String, CourseKc> kcIndex =
        coursePackRepository.findKcs(DEFAULT_PACK_ID, null).stream()
            .collect(Collectors.toMap(CourseKc::kcCode, kc -> kc, (a, b) -> a));

    // 孪生信号：最新 error_pattern 洞察（可溯源 event id）+ Evidence Memory 2.0 + 近期失败记忆。
    ErrorInsight insight = latestErrorPatternInsight(userId);
    Map<String, EvidenceMemorySignalDto> evidenceMemorySignals = evidenceMemorySignals(userId);
    Set<String> memoryWeakKcs = memoryWeakKcCodes(userId);

    List<RecommendationDto> results = new ArrayList<>();
    for (FrontierPickDto pick : picks) {
      CourseKc kc = kcIndex.get(pick.kcCode());
      String kcName = kc != null ? kc.kcName() : pick.kcCode();
      boolean insightGrounded = insight != null && insight.kcTags().contains(pick.kcCode());
      EvidenceMemorySignalDto evidenceSignal = evidenceMemorySignals.get(pick.kcCode());
      boolean evidenceMemoryGrounded = evidenceSignal != null;
      boolean memoryGrounded = memoryWeakKcs.contains(pick.kcCode());

      List<ResourceCandidate> candidates = findResourcesForKc(userId, pick.kcCode(), mediaPriority);
      Long resourceId;
      String title;
      String type;
      if (!candidates.isEmpty()) {
        ResourceCandidate top = candidates.get(0);
        resourceId = top.id();
        title = top.title();
        type = top.type();
      } else {
        resourceId = null;
        title = kcName + " 入门";
        type = "LEARNING_ENTRY";
      }

      String reason;
      List<Long> evidence;
      List<Long> checkIds = List.of();
      List<Long> pageIds = List.of();
      if (insightGrounded) {
        reason = "因近期在「" + kcName + "」反复出错" + insightSuffix(insight);
        evidence = insight.evidenceEventIds();
      } else if (evidenceMemoryGrounded) {
        reason = "学习证据图显示「" + kcName + "」仍需巩固：" + evidenceSignal.reason();
        evidence = evidenceSignal.evidenceEventIds();
        checkIds = evidenceSignal.checkIds();
        pageIds = evidenceSignal.pageIds();
      } else if (memoryGrounded) {
        reason = "近期在「" + kcName + "」的练习记忆中多次受挫，建议优先巩固";
        evidence = List.of();
      } else if (resourceId != null) {
        reason = pick.reason() + "；已有学习资源可供巩固";
        evidence = List.of();
      } else {
        reason = pick.reason();
        evidence = List.of();
      }

      results.add(
          new RecommendationDto(
              resourceId, title, type, pick.kcCode(), kcName, reason, evidence, checkIds, pageIds));
      if (results.size() >= MAX_RECOMMENDATIONS) {
        break;
      }
    }
    return results;
  }

  /** 取最新一条 error_pattern 洞察（含 tags 与 evidence event id），无则 null；读失败降级 null。 */
  private ErrorInsight latestErrorPatternInsight(Long userId) {
    try {
      for (LearnerInsightDto insight : insightRepository.findByUser(userId, 5)) {
        if ("error_pattern".equals(insight.dimensionKey())
            && insight.tags() != null
            && !insight.tags().isEmpty()) {
          List<Long> events =
              insight.evidenceEventIds() == null ? List.of() : insight.evidenceEventIds();
          return new ErrorInsight(insight.text(), new LinkedHashSet<>(insight.tags()), events);
        }
      }
    } catch (RuntimeException ex) {
      return null;
    }
    return null;
  }

  /** 近期失败记忆（topK）里反复出现的 KC code 集合；读失败降级空集（不阻断推荐）。 */
  private Set<String> memoryWeakKcCodes(Long userId) {
    try {
      Set<String> codes = new LinkedHashSet<>();
      for (MemoryItemDto item : memoryRetrievalService.retrieve(userId, null, MEMORY_SCAN_LIMIT)) {
        if (!"practice_failed".equals(item.eventType()) || item.kcCodes() == null) {
          continue;
        }
        codes.addAll(item.kcCodes());
      }
      return codes;
    } catch (RuntimeException ex) {
      return Set.of();
    }
  }

  /** Evidence Memory 2.0 只把直接证据图信号用于抢占排序；关系扩散信号留给解释与后续路径规划。 */
  private Map<String, EvidenceMemorySignalDto> evidenceMemorySignals(Long userId) {
    try {
      Map<String, EvidenceMemorySignalDto> signals = new LinkedHashMap<>();
      for (EvidenceMemorySignalDto signal : evidenceMemoryService.signalsForUser(userId)) {
        if (!"DIRECT".equals(signal.signalKind())) {
          continue;
        }
        EvidenceMemorySignalDto existing = signals.get(signal.kcCode());
        if (existing == null || signal.priorityScore() > existing.priorityScore()) {
          signals.put(signal.kcCode(), signal);
        }
      }
      return signals;
    } catch (RuntimeException ex) {
      return Map.of();
    }
  }

  private String insightSuffix(ErrorInsight insight) {
    if (insight.text() == null || insight.text().isBlank()) {
      return "";
    }
    String text = insight.text().strip();
    String brief =
        text.length() > INSIGHT_BRIEF_MAX ? text.substring(0, INSIGHT_BRIEF_MAX) + "…" : text;
    return "（学情洞察：" + brief + "）";
  }

  /**
   * 媒介优先级 = 画像结构化偏好 + 多模态消费/评估回流信号（learning_preference 维度 tags）。
   *
   * <p>学生最近多消费哪类媒介，就把该媒介的资源排在前面，形成「消费/评估 → 推送策略」的闭环。
   */
  private List<String> resolveMediaPriority(Long userId) {
    LinkedHashSet<String> priority = new LinkedHashSet<>();
    profileService
        .findProfile(userId)
        .map(LearnerProfileDto::learningPreference)
        .filter(pref -> pref != null && pref.mediaPriority() != null)
        .ifPresent(pref -> priority.addAll(pref.mediaPriority()));
    priority.addAll(mediaPriorityFromConsumption(userId));
    return new ArrayList<>(priority);
  }

  /**
   * 从 learning_preference 维度累积的 tags 推导媒介类别顺序（多模态消费/评估回流信号）。
   *
   * <p><b>倒序遍历</b>：全仓约定 tags 数组「末尾 = 最近/主导信号」（PassiveProfileService 按时间追加， INTERVIEW 自评还会把所选 tag
   * 移到末尾；DialogContextService / NpcGreetingService / MemoryBriefService
   * 均取末尾）。按正序遍历会把学生最新、最明确表达的偏好排到推荐优先级的最后一位。
   */
  private List<String> mediaPriorityFromConsumption(Long userId) {
    try {
      for (ProfileDimensionDto dim : passiveProfileService.getDimensions(userId)) {
        if ("learning_preference".equals(dim.key())) {
          return mediaPriorityFromTags(dim.tags());
        }
      }
      return List.of();
    } catch (RuntimeException ex) {
      // 画像维度不可用时不阻塞推荐（降级为仅用结构化偏好）。
      return List.of();
    }
  }

  /** tags → 媒介类别（去重、末尾优先）。抽成静态方法便于直接回归排序口径。 */
  static List<String> mediaPriorityFromTags(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }
    List<String> media = new ArrayList<>();
    for (int i = tags.size() - 1; i >= 0; i--) {
      String category = mapTagToMedia(tags.get(i));
      if (category != null && !media.contains(category)) {
        media.add(category);
      }
    }
    return List.copyOf(media);
  }

  private static String mapTagToMedia(String tag) {
    if (tag == null) {
      return null;
    }
    return switch (tag.toLowerCase()) {
      case "video", "storyboard", "animation", "narrative" -> "narrative";
      case "illustration", "diagram", "image", "visual", "mindmap" -> "visual";
      case "ppt", "handout", "reading", "text" -> "text";
      case "practice", "code_lab", "interactive" -> "interactive";
      default -> null;
    };
  }

  private List<ResourceCandidate> findResourcesForKc(
      Long userId, String kcCode, List<String> mediaPriority) {
    List<ResourceCandidate> candidates =
        jdbcTemplate.query(
            """
            SELECT id, title, type, kc_codes
            FROM learning_resource
            WHERE user_id = ?
              AND ? = ANY(kc_codes)
              AND safety_status = 'PASSED'
              AND grounding_status = 'PASSED'
            ORDER BY created_at DESC
            LIMIT 10
            """,
            (rs, rowNum) ->
                new ResourceCandidate(
                    rs.getLong("id"), rs.getString("title"), rs.getString("type")),
            userId,
            kcCode);

    if (candidates.isEmpty() || mediaPriority.isEmpty()) {
      return candidates;
    }

    return candidates.stream()
        .sorted(Comparator.comparingInt(c -> mediaPriorityIndex(c.type(), mediaPriority)))
        .toList();
  }

  private int mediaPriorityIndex(String type, List<String> mediaPriority) {
    if (type == null) {
      return mediaPriority.size();
    }
    String normalized = type.toLowerCase();
    for (int i = 0; i < mediaPriority.size(); i++) {
      if (mediaPriority.get(i).equalsIgnoreCase(normalized)
          || mediaPriority.get(i).equalsIgnoreCase(mapTypeToMedia(type))) {
        return i;
      }
    }
    return mediaPriority.size();
  }

  private String mapTypeToMedia(String type) {
    return switch (type) {
      case "HANDOUT", "READING" -> "text";
      case "MINDMAP" -> "visual";
      case "PRACTICE", "CODE_LAB" -> "interactive";
      case "STORYBOARD" -> "narrative";
      default -> type.toLowerCase();
    };
  }

  private List<RecommendationDto> buildColdStartRecommendations() {
    List<CourseKc> kcs = coursePackRepository.findKcs(DEFAULT_PACK_ID, null);
    return kcs.stream()
        .limit(MAX_RECOMMENDATIONS)
        .map(
            kc ->
                new RecommendationDto(
                    null,
                    kc.kcName() + " 入门",
                    "LEARNING_ENTRY",
                    kc.kcCode(),
                    kc.kcName(),
                    "尚无学习记录，建议从 " + kc.kcName() + " 开始",
                    List.of()))
        .toList();
  }

  private record ResourceCandidate(Long id, String title, String type) {}

  /** 最新 error_pattern 洞察的精简投影：理由文本 + 命中 KC 标签 + 可溯源 event id。 */
  private record ErrorInsight(String text, Set<String> kcTags, List<Long> evidenceEventIds) {}
}
