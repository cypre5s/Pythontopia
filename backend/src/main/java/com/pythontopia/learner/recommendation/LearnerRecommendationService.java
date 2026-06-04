package com.pythontopia.learner.recommendation;

import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.profile.service.ProfileService;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LearnerRecommendationService {

  private static final Long DEFAULT_PACK_ID = 43L;
  private static final int WEAKEST_KC_COUNT = 3;
  private static final int MAX_RECOMMENDATIONS = 3;

  private final JdbcTemplate jdbcTemplate;
  private final CoursePackRepository coursePackRepository;
  private final ProfileService profileService;
  private final PassiveProfileService passiveProfileService;

  public LearnerRecommendationService(
      JdbcTemplate jdbcTemplate,
      CoursePackRepository coursePackRepository,
      ProfileService profileService,
      PassiveProfileService passiveProfileService) {
    this.jdbcTemplate = jdbcTemplate;
    this.coursePackRepository = coursePackRepository;
    this.profileService = profileService;
    this.passiveProfileService = passiveProfileService;
  }

  public List<RecommendationDto> recommend(Long userId) {
    List<String> mediaPriority = resolveMediaPriority(userId);
    List<WeakKc> weakKcs = findWeakestKcs(userId);

    if (weakKcs.isEmpty()) {
      return buildColdStartRecommendations();
    }

    List<RecommendationDto> results = new ArrayList<>();
    Map<String, CourseKc> kcIndex = coursePackRepository.findKcs(DEFAULT_PACK_ID, null).stream()
        .collect(Collectors.toMap(CourseKc::kcCode, kc -> kc, (a, b) -> a));

    for (WeakKc wk : weakKcs) {
      List<RecommendationDto> forKc = findResourcesForKc(userId, wk.kcCode, kcIndex, mediaPriority);
      if (!forKc.isEmpty()) {
        results.add(forKc.get(0));
      } else {
        CourseKc kc = kcIndex.get(wk.kcCode);
        String kcName = kc != null ? kc.kcName() : wk.kcCode;
        results.add(new RecommendationDto(
            null, kcName + " 入门", "LEARNING_ENTRY", wk.kcCode, kcName,
            "当前掌握度较低，建议从 " + kcName + " 的基础内容开始"));
      }
      if (results.size() >= MAX_RECOMMENDATIONS) {
        break;
      }
    }
    return results;
  }

  /**
   * 媒介优先级 = 画像结构化偏好 + 多模态消费/评估回流信号（learning_preference 维度 tags）。
   *
   * <p>把 Phase2 的「看视频/动画/图解 → learning_preference tag」与评估写入的偏好回灌到推荐：
   * 学生最近多消费哪类媒介，推荐就把该媒介的资源排在前面，闭合「消费/评估 → 推送策略」回路。
   */
  private List<String> resolveMediaPriority(Long userId) {
    LinkedHashSet<String> priority = new LinkedHashSet<>();
    profileService.findProfile(userId)
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
        userId, WEAKEST_KC_COUNT);
  }

  private List<RecommendationDto> findResourcesForKc(
      Long userId,
      String kcCode,
      Map<String, CourseKc> kcIndex,
      List<String> mediaPriority) {
    CourseKc kc = kcIndex.get(kcCode);
    String kcName = kc != null ? kc.kcName() : kcCode;

    List<RecommendationDto> candidates = jdbcTemplate.query(
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
        (rs, rowNum) -> new RecommendationDto(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("type"),
            kcCode,
            kcName,
            "针对薄弱知识点 " + kcName + " 的已有学习资源"),
        userId, kcCode);

    if (candidates.isEmpty() || mediaPriority.isEmpty()) {
      return candidates;
    }

    return candidates.stream()
        .sorted(Comparator.comparingInt(r -> mediaPriorityIndex(r.type(), mediaPriority)))
        .toList();
  }

  private int mediaPriorityIndex(String type, List<String> mediaPriority) {
    if (type == null) return mediaPriority.size();
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
        .map(kc -> new RecommendationDto(
            null, kc.kcName() + " 入门", "LEARNING_ENTRY",
            kc.kcCode(), kc.kcName(),
            "尚无学习记录，建议从 " + kc.kcName() + " 开始"))
        .toList();
  }

  private record WeakKc(String kcCode, BigDecimal masteryScore) {}
}
