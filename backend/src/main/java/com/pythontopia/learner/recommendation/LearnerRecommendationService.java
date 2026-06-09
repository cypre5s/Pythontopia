package com.pythontopia.learner.recommendation;

import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.profile.service.ProfileService;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 「今日推送」推荐：以数字孪生为驱动——薄弱 KC（掌握度）× 最新 error_pattern 洞察 × 近期失败记忆 topK，
 * 共同决定推什么、排序与「为什么推」（绑定洞察 evidence event id，满足可溯源红线，P3.3）。
 */
@Service
public class LearnerRecommendationService {

  private static final Long DEFAULT_PACK_ID = 43L;
  private static final int WEAKEST_KC_COUNT = 3;
  private static final int MAX_RECOMMENDATIONS = 3;
  /** 失败记忆扫描条数（topK），用于把「近期反复受挫的 KC」纳入推送排序。 */
  private static final int MEMORY_SCAN_LIMIT = 8;
  /** 洞察文本注入推荐理由的最大摘要长度，避免理由过长。 */
  private static final int INSIGHT_BRIEF_MAX = 40;

  private final JdbcTemplate jdbcTemplate;
  private final CoursePackRepository coursePackRepository;
  private final ProfileService profileService;
  private final PassiveProfileService passiveProfileService;
  private final LearnerInsightRepository insightRepository;
  private final MemoryRetrievalService memoryRetrievalService;

  public LearnerRecommendationService(
      JdbcTemplate jdbcTemplate,
      CoursePackRepository coursePackRepository,
      ProfileService profileService,
      PassiveProfileService passiveProfileService,
      LearnerInsightRepository insightRepository,
      MemoryRetrievalService memoryRetrievalService) {
    this.jdbcTemplate = jdbcTemplate;
    this.coursePackRepository = coursePackRepository;
    this.profileService = profileService;
    this.passiveProfileService = passiveProfileService;
    this.insightRepository = insightRepository;
    this.memoryRetrievalService = memoryRetrievalService;
  }

  public List<RecommendationDto> recommend(Long userId) {
    List<String> mediaPriority = resolveMediaPriority(userId);
    List<WeakKc> weakKcs = findWeakestKcs(userId);

    if (weakKcs.isEmpty()) {
      return buildColdStartRecommendations();
    }

    Map<String, CourseKc> kcIndex =
        coursePackRepository.findKcs(DEFAULT_PACK_ID, null).stream()
            .collect(Collectors.toMap(CourseKc::kcCode, kc -> kc, (a, b) -> a));

    // 孪生信号：最新 error_pattern 洞察（可溯源 event id）+ 近期失败记忆里反复出现的 KC。
    ErrorInsight insight = latestErrorPatternInsight(userId);
    Set<String> memoryWeakKcs = memoryWeakKcCodes(userId);

    List<WeakKc> ranked = rerankByTwinSignals(weakKcs, insight, memoryWeakKcs);

    List<RecommendationDto> results = new ArrayList<>();
    for (WeakKc wk : ranked) {
      CourseKc kc = kcIndex.get(wk.kcCode());
      String kcName = kc != null ? kc.kcName() : wk.kcCode();
      boolean insightGrounded = insight != null && insight.kcTags().contains(wk.kcCode());
      boolean memoryGrounded = memoryWeakKcs.contains(wk.kcCode());

      List<ResourceCandidate> candidates = findResourcesForKc(userId, wk.kcCode(), mediaPriority);
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
      if (insightGrounded) {
        reason = "因近期在「" + kcName + "」反复出错" + insightSuffix(insight);
        evidence = insight.evidenceEventIds();
      } else if (memoryGrounded) {
        reason = "近期在「" + kcName + "」的练习记忆中多次受挫，建议优先巩固";
        evidence = List.of();
      } else if (resourceId != null) {
        reason = "针对薄弱知识点「" + kcName + "」的已有学习资源";
        evidence = List.of();
      } else {
        reason = "当前掌握度较低，建议从「" + kcName + "」的基础内容开始";
        evidence = List.of();
      }

      results.add(new RecommendationDto(resourceId, title, type, wk.kcCode(), kcName, reason, evidence));
      if (results.size() >= MAX_RECOMMENDATIONS) {
        break;
      }
    }
    return results;
  }

  /**
   * 据孪生信号重排薄弱 KC：洞察命中（最强信号，evidence 可溯源）优先，其次近期失败记忆命中，
   * 同优先级内保持原「掌握度升序」。{@code stream().sorted} 是稳定排序，故原顺序得以保留。
   */
  private List<WeakKc> rerankByTwinSignals(
      List<WeakKc> weakKcs, ErrorInsight insight, Set<String> memoryWeakKcs) {
    return weakKcs.stream()
        .sorted(Comparator.comparingInt(wk -> twinPriority(wk.kcCode(), insight, memoryWeakKcs)))
        .toList();
  }

  private int twinPriority(String kcCode, ErrorInsight insight, Set<String> memoryWeakKcs) {
    if (insight != null && insight.kcTags().contains(kcCode)) {
      return 0;
    }
    if (memoryWeakKcs.contains(kcCode)) {
      return 1;
    }
    return 2;
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

  /** 从 learning_preference 维度累积的 tags 推导媒介类别顺序（多模态消费/评估回流信号）。 */
  private List<String> mediaPriorityFromConsumption(Long userId) {
    List<String> media = new ArrayList<>();
    try {
      for (ProfileDimensionDto dim : passiveProfileService.getDimensions(userId)) {
        if (!"learning_preference".equals(dim.key())) {
          continue;
        }
        for (String tag : dim.tags()) {
          String category = mapTagToMedia(tag);
          if (category != null && !media.contains(category)) {
            media.add(category);
          }
        }
      }
    } catch (RuntimeException ex) {
      // 画像维度不可用时不阻塞推荐（降级为仅用结构化偏好）。
      return List.of();
    }
    return media;
  }

  private String mapTagToMedia(String tag) {
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

  private List<WeakKc> findWeakestKcs(Long userId) {
    return jdbcTemplate.query(
        """
        SELECT kc_code, mastery_score
        FROM kc_mastery
        WHERE user_id = ?
        ORDER BY mastery_score ASC, updated_at ASC
        LIMIT ?
        """,
        (rs, rowNum) -> new WeakKc(rs.getString("kc_code"), rs.getBigDecimal("mastery_score")),
        userId,
        WEAKEST_KC_COUNT);
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
                new ResourceCandidate(rs.getLong("id"), rs.getString("title"), rs.getString("type")),
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

  private record WeakKc(String kcCode, BigDecimal masteryScore) {}

  private record ResourceCandidate(Long id, String title, String type) {}

  /** 最新 error_pattern 洞察的精简投影：理由文本 + 命中 KC 标签 + 可溯源 event id。 */
  private record ErrorInsight(String text, Set<String> kcTags, List<Long> evidenceEventIds) {}
}
