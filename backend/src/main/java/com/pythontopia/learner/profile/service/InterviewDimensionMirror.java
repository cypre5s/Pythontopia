package com.pythontopia.learner.profile.service;

import com.pythontopia.learner.profile.dto.BaseLevelDto;
import com.pythontopia.learner.profile.dto.CognitiveStyleDto;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.EmotionPersistenceDto;
import com.pythontopia.learner.profile.dto.ErrorPatternsDto;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.LearningGoalDto;
import com.pythontopia.learner.profile.dto.LearningPreferenceDto;
import com.pythontopia.learner.profile.dto.PracticeCapabilityDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把 ProfileAgent 访谈产出的 {@link LearnerProfileDto} 草稿镜像进 {@code learner_profile.dimensions} 八维聚合。
 *
 * <p>为什么需要这一步：访谈只写 learner_profile 的旧列（major / base_level / ...），而 8 维消费方
 * （TutorService / NpcGreetingService / WorkshopLearningContextService / MemoryBriefService /
 * LearnerRecommendationService）一律只读 dimensions。缺了镜像，学生做完访谈后每个维度的 evidence_count
 * 仍是 0，{@code profileBrief()} 被 confidence 门槛整体挡下返回空串，答疑完全没有个性化。
 *
 * <p>镜像口径：
 *
 * <ul>
 *   <li>只写 <b>tags + evidence + metadata</b>，不写 score。score 是行为证据累积出来的量表，自评不直接改分。
 *   <li>tags 只镜像与消费方词表对齐的分类信号；{@code cognitive_style} 的两个连续量、
 *       {@code practical_ability} 的成功率、{@code emotion_persistence} 的自述心情都只进 metadata，
 *       避免脏标签污染「取末尾 tag」的下游（DialogContextService 会把不认识的风格标签归一成 unknown，
 *       反而盖掉此前有效的 visual/logical/trial 推断）。
 *   <li>{@code learning_preference} 的 mediaPriority 是「首位=最偏好」，而全仓消费方约定「末尾 tag=主导」，
 *       故按倒序追加，使学生的首选媒介落在数组末尾。
 * </ul>
 */
@Component
public class InterviewDimensionMirror {

  private static final Logger log = LoggerFactory.getLogger(InterviewDimensionMirror.class);

  /** revision 审计来源；与 profile_revision_source_check 约束保持一致。 */
  private static final String SOURCE = "INTERVIEW";

  /**
   * 一次完整访谈计入的证据条数。取 3 使 confidence = 3/8 = 0.375 越过 profile_brief 的 0.3 门槛，
   * 同时仍低于长期被动累积的饱和值（{@code EVIDENCE_SATURATION = 8}），自评不至于压过行为证据。
   */
  private static final int INTERVIEW_EVIDENCE = 3;

  /** 单维度镜像 tag 数上限（Agent 产出是自由文本，防长尾标签灌进下游 prompt）。 */
  private static final int MAX_TAGS = 8;

  /** 单个 tag 字符上限。 */
  private static final int MAX_TAG_LENGTH = 64;

  private final PassiveProfileService passiveProfileService;

  public InterviewDimensionMirror(PassiveProfileService passiveProfileService) {
    this.passiveProfileService = passiveProfileService;
  }

  /**
   * 将草稿的 8 个字段逐一写成 {@code source=INTERVIEW} 的维度增量。
   *
   * <p>单个维度失败不影响其余维度，也不影响访谈本身（调用方已完成 upsert）。
   */
  public void mirror(Long userId, LearnerProfileDto draft) {
    if (userId == null || draft == null) {
      return;
    }
    apply(userId, "professional_background", professionalBackground(draft));
    apply(userId, "knowledge_base", knowledgeBase(draft.baseLevel()));
    apply(userId, "learning_goal", learningGoal(draft.learningGoal()));
    apply(userId, "cognitive_style", cognitiveStyle(draft.cognitiveStyle()));
    apply(userId, "learning_preference", learningPreference(draft.learningPreference()));
    apply(userId, "error_pattern", errorPattern(draft.errorPatterns()));
    apply(userId, "practical_ability", practicalAbility(draft.practiceCapability()));
    apply(userId, "emotion_persistence", emotionPersistence(draft.emotionPersistence()));
  }

  private void apply(Long userId, String dimensionKey, DimensionDelta delta) {
    if (delta == null) {
      return;
    }
    try {
      passiveProfileService.applyDimensionUpdate(userId, dimensionKey, delta);
    } catch (RuntimeException ex) {
      log.warn(
          "Interview dimension mirror skipped userId={} dim={}: {}",
          userId,
          dimensionKey,
          ex.getMessage());
    }
  }

  private static DimensionDelta professionalBackground(LearnerProfileDto draft) {
    if (isBlank(draft.major())) {
      return null;
    }
    return delta(List.of(draft.major()), Map.of("major", draft.major()));
  }

  private static DimensionDelta knowledgeBase(BaseLevelDto baseLevel) {
    if (baseLevel == null) {
      return null;
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    putIfPresent(meta, "programming_years", baseLevel.programmingYears());
    putIfPresent(meta, "python_self_rated_level", baseLevel.pythonSelfRatedLevel());
    putIfPresent(meta, "languages_known", baseLevel.languagesKnown());
    return delta(baseLevel.languagesKnown(), meta);
  }

  private static DimensionDelta learningGoal(LearningGoalDto goal) {
    if (goal == null) {
      return null;
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    putIfPresent(meta, "kind", goal.kind());
    putIfPresent(meta, "deadline_iso", goal.deadlineIso());
    putIfPresent(meta, "target_kc_codes", goal.targetKcCodes());
    return delta(isBlank(goal.kind()) ? List.of() : List.of(goal.kind()), meta);
  }

  private static DimensionDelta cognitiveStyle(CognitiveStyleDto style) {
    if (style == null) {
      return null;
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    putIfPresent(meta, "field_dependent", style.fieldDependent());
    putIfPresent(meta, "sequential_vs_holistic", style.sequentialVsHolistic());
    // 刻意不写 tag：本维的 tag 词表是 visual/logical/trial，连续量无法安全映射。
    return delta(List.of(), meta);
  }

  private static DimensionDelta learningPreference(LearningPreferenceDto preference) {
    if (preference == null) {
      return null;
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    putIfPresent(meta, "media_priority", preference.mediaPriority());
    putIfPresent(meta, "session_minutes_per_day", preference.sessionMinutesPerDay());
    return delta(reversed(preference.mediaPriority()), meta);
  }

  private static DimensionDelta errorPattern(ErrorPatternsDto patterns) {
    if (patterns == null) {
      return null;
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    putIfPresent(meta, "common_categories", patterns.commonCategories());
    putIfPresent(meta, "frequent_kc_codes", patterns.frequentKcCodes());
    return delta(patterns.commonCategories(), meta);
  }

  private static DimensionDelta practicalAbility(PracticeCapabilityDto capability) {
    if (capability == null) {
      return null;
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    putIfPresent(meta, "avg_attempts_per_problem", capability.avgAttemptsPerProblem());
    putIfPresent(meta, "success_rate", capability.successRate());
    return delta(List.of(), meta);
  }

  private static DimensionDelta emotionPersistence(EmotionPersistenceDto emotion) {
    if (emotion == null) {
      return null;
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    putIfPresent(meta, "resilience", emotion.resilience());
    putIfPresent(meta, "recent_mood", emotion.recentMood());
    // recent_mood 是自由文本，只入 metadata，不做 tag（brief 的情绪段按 score 判定）。
    return delta(List.of(), meta);
  }

  /** 有 tag 或有 metadata 才算一条访谈证据；两者皆空返回 null（不写空 revision）。 */
  private static DimensionDelta delta(List<String> rawTags, Map<String, Object> metadata) {
    List<String> tags = sanitize(rawTags);
    Map<String, Object> meta = metadata == null ? Map.of() : metadata;
    if (tags.isEmpty() && meta.isEmpty()) {
      return null;
    }
    return new DimensionDelta(null, INTERVIEW_EVIDENCE, tags, meta, SOURCE);
  }

  private static List<String> sanitize(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }
    List<String> clean = new ArrayList<>();
    for (String tag : tags) {
      if (isBlank(tag)) {
        continue;
      }
      String trimmed = tag.trim();
      clean.add(
          trimmed.length() > MAX_TAG_LENGTH ? trimmed.substring(0, MAX_TAG_LENGTH) : trimmed);
      if (clean.size() == MAX_TAGS) {
        break;
      }
    }
    return List.copyOf(clean);
  }

  private static List<String> reversed(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    List<String> copy = new ArrayList<>(values);
    java.util.Collections.reverse(copy);
    return copy;
  }

  private static void putIfPresent(Map<String, Object> target, String key, Object value) {
    if (value == null) {
      return;
    }
    if (value instanceof String text && text.isBlank()) {
      return;
    }
    if (value instanceof List<?> list && list.isEmpty()) {
      return;
    }
    target.put(key, value);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
