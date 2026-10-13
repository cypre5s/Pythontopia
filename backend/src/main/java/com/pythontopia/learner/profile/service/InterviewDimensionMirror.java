package com.pythontopia.learner.profile.service;

import com.pythontopia.learner.profile.dto.BaseLevelDto;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.LearnerProfileDto;
import com.pythontopia.learner.profile.dto.LearningGoalDto;
import com.pythontopia.learner.profile.dto.LearningPreferenceDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把 ProfileAgent 访谈中明确的目标、经验和媒介偏好镜像进 {@code learner_profile.dimensions}。
 *
 * <p>访谈是自述资料，不是能力证据；只镜像可用于偏好和目标的字段，不提高 evidence_count，也不镜像 AI
 * 对错误、实践能力、认知类型或情绪的猜测。
 *
 * <p>镜像口径：
 *
 * <ul>
 *   <li>只写 <b>tags + metadata</b>，不写 score 或 evidence。
 *   <li>{@code learning_preference} 的 mediaPriority 是「首位=最偏好」，而全仓消费方约定「末尾 tag=主导」，
 *       故按倒序追加，使学生的首选媒介落在数组末尾。
 * </ul>
 */
@Component
public class InterviewDimensionMirror {

  private static final Logger log = LoggerFactory.getLogger(InterviewDimensionMirror.class);

  /** revision 审计来源；与 profile_revision_source_check 约束保持一致。 */
  private static final String SOURCE = "INTERVIEW";

  /** 单维度镜像 tag 数上限（Agent 产出是自由文本，防长尾标签灌进下游 prompt）。 */
  private static final int MAX_TAGS = 8;

  /** 单个 tag 字符上限。 */
  private static final int MAX_TAG_LENGTH = 64;

  private final PassiveProfileService passiveProfileService;

  public InterviewDimensionMirror(PassiveProfileService passiveProfileService) {
    this.passiveProfileService = passiveProfileService;
  }

  /**
   * 将自述目标、已有经验和媒介偏好写成 {@code source=INTERVIEW} 的非能力信号。
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
    apply(userId, "learning_preference", learningPreference(draft.learningPreference()));
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

  private static DimensionDelta learningPreference(LearningPreferenceDto preference) {
    if (preference == null) {
      return null;
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    putIfPresent(meta, "media_priority", preference.mediaPriority());
    putIfPresent(meta, "session_minutes_per_day", preference.sessionMinutesPerDay());
    return delta(reversed(preference.mediaPriority()), meta);
  }

  /** 有 tag 或有 metadata 才写自述资料；两者皆空返回 null（不写空 revision）。 */
  private static DimensionDelta delta(List<String> rawTags, Map<String, Object> metadata) {
    List<String> tags = sanitize(rawTags);
    Map<String, Object> meta = metadata == null ? Map.of() : metadata;
    if (tags.isEmpty() && meta.isEmpty()) {
      return null;
    }
    return new DimensionDelta(null, null, tags, meta, SOURCE);
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
