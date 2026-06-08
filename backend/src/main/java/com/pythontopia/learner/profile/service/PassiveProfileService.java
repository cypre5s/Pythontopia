package com.pythontopia.learner.profile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.DimensionUpdateResult;
import com.pythontopia.learner.profile.repository.ProfileRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 被动画像更新服务：将练习提交、Debug 修复、资源消费、班级任务完成等学习事件转化为 8 维画像
 * {@code learner_profile.dimensions} JSONB 字段的真实增量，并保留 {@code profile_revision} 审计。
 *
 * <p>八维 key（英文 snake_case）：
 *
 * <ul>
 *   <li>{@code professional_background} 专业背景
 *   <li>{@code knowledge_base} 知识基础
 *   <li>{@code learning_goal} 学习目标
 *   <li>{@code cognitive_style} 认知风格
 *   <li>{@code learning_preference} 学习偏好
 *   <li>{@code error_pattern} 易错模式
 *   <li>{@code practical_ability} 实践能力
 *   <li>{@code emotion_persistence} 情绪与坚持度
 * </ul>
 *
 * <ul>
 *   <li>{@link #applyDimensionUpdate} 为事务方法：读 dimensions JSONB → 应用 delta → 写回并追加 profile_revision；
 *   <li>{@link DimensionDelta#source()} 不在 profile_revision 允许的 source 枚举内时，跳过 revision 写入但仍更新 dimension；
 *   <li>{@code learner_profile.dimensions} 列尚未创建时 {@link ProfileRepository#findDimensions} 返回
 *       {@link java.util.Optional#empty()}，由调用方或上层 Listener 做降级。
 * </ul>
 */
@Service
public class PassiveProfileService {

  private static final Logger log = LoggerFactory.getLogger(PassiveProfileService.class);

  /** 八维 key 的权威清单（决定 getDimensions 返回与雷达图的维度顺序）。 */
  public static final List<String> DIMENSION_KEYS =
      List.of(
          "professional_background",
          "knowledge_base",
          "learning_goal",
          "cognitive_style",
          "learning_preference",
          "error_pattern",
          "practical_ability",
          "emotion_persistence");

  /**
   * 学生 PUT 自评接口允许直写的主观维度；其余维度只读，仅由本服务被动更新。
   *
   * <p>纳入 {@code cognitive_style} 是为了让学生在画像屋纠正被推断的认知风格（防幻觉、不贴死标签）。
   */
  public static final Set<String> SELF_EDITABLE_DIMENSIONS =
      Set.of(
          "professional_background",
          "learning_goal",
          "cognitive_style",
          "learning_preference",
          "emotion_persistence");

  /** profile_revision_source_check 约束允许的 source 枚举（须与 V136 迁移保持一致）。 */
  private static final Set<String> ALLOWED_REVISION_SOURCES =
      Set.of(
          "INTERVIEW",
          "SUBMISSION",
          "DEBUG",
          "RESOURCE_FEEDBACK",
          "QUEST",
          "CLASSROOM",
          "EVALUATION",
          "PRACTICE_PASS",
          "PRACTICE_FAIL",
          "DEBUG_FIX",
          "REFLECTION");

  private static final double DEFAULT_SCORE = 0.5;

  /** confidence 饱和证据数：evidence_count 达到该值时置信度=1（读时派生，不入库）。 */
  private static final int EVIDENCE_SATURATION = 8;

  /** 读时 decay 半衰期（天）：久未更新的维度得分向中性 0.5 回归。 */
  private static final double DECAY_HALF_LIFE_DAYS = 14.0;

  private final ProfileRepository profileRepository;
  private final ObjectMapper objectMapper;

  public PassiveProfileService(ProfileRepository profileRepository, ObjectMapper objectMapper) {
    this.profileRepository = profileRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * 对单个维度应用增量更新。Score 字段被 clamp 到 [0,1]；tags 去重追加；metadata 直接覆盖写入子键。
   *
   * @throws BusinessException 当 dimensionKey 不在 {@link #DIMENSION_KEYS} 中时（400）。
   */
  @Transactional
  public DimensionUpdateResult applyDimensionUpdate(
      Long userId, String dimensionKey, DimensionDelta delta) {
    if (userId == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "PROFILE_USER_REQUIRED", "userId is required");
    }
    if (dimensionKey == null || !DIMENSION_KEYS.contains(dimensionKey)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PROFILE_DIMENSION_INVALID",
          "Unsupported dimension key: " + dimensionKey);
    }
    if (delta == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "PROFILE_DELTA_REQUIRED", "Dimension delta is required");
    }

    JsonNode existing = profileRepository.findDimensions(userId).orElse(null);
    ObjectNode dimensionsObj;
    if (existing instanceof ObjectNode obj) {
      dimensionsObj = obj;
    } else {
      dimensionsObj = objectMapper.createObjectNode();
    }

    ObjectNode dimensionObj;
    JsonNode existingDimension = dimensionsObj.get(dimensionKey);
    if (existingDimension instanceof ObjectNode current) {
      dimensionObj = current;
    } else {
      dimensionObj = objectMapper.createObjectNode();
    }

    if (delta.scoreDelta() != null) {
      double currentScore = dimensionObj.path("score").asDouble(DEFAULT_SCORE);
      double newScore = clamp01(currentScore + delta.scoreDelta());
      dimensionObj.put("score", newScore);
    }
    if (delta.evidenceIncrement() != null) {
      int currentCount = dimensionObj.path("evidence_count").asInt(0);
      dimensionObj.put("evidence_count", currentCount + delta.evidenceIncrement());
    }
    if (delta.tagsAdd() != null && !delta.tagsAdd().isEmpty()) {
      ArrayNode tagsArray;
      JsonNode existingTags = dimensionObj.get("tags");
      if (existingTags instanceof ArrayNode arr) {
        tagsArray = arr;
      } else {
        tagsArray = dimensionObj.putArray("tags");
      }
      // INTERVIEW 自评（仅 ProfileController 自评路径）把所选 tag 移到数组末尾，使其成为主导信号：
      // 即便该 tag 早先已出现也移到末尾，让纠偏覆盖旧推断（消费方 TutorService / ResourceWorkshopService 取末尾 tag）。
      // 其它 source 的被动累积保持首见去重，避免扰动 learning_preference 媒介排序等既有行为。
      boolean authoritativeSelfReport = "INTERVIEW".equals(delta.source());
      for (String tag : delta.tagsAdd()) {
        if (tag == null || tag.isBlank()) {
          continue;
        }
        boolean present = arrayContains(tagsArray, tag);
        if (authoritativeSelfReport && present) {
          removeTag(tagsArray, tag);
          tagsArray.add(tag);
        } else if (!present) {
          tagsArray.add(tag);
        }
      }
    }
    if (delta.metadata() != null) {
      delta
          .metadata()
          .forEach(
              (k, v) -> {
                if (k == null || k.isBlank()) {
                  return;
                }
                dimensionObj.set(k, objectMapper.valueToTree(v));
              });
    }
    dimensionObj.put("last_updated", Instant.now().toString());

    dimensionsObj.set(dimensionKey, dimensionObj);
    profileRepository.saveDimensions(userId, dimensionsObj);

    writeRevisionAudit(userId, dimensionKey, delta, dimensionObj);

    return new DimensionUpdateResult(userId, dimensionKey, dimensionObj);
  }

  /** 学生 PUT 自评接口是否允许直写该维度。 */
  public boolean isSelfEditable(String dimensionKey) {
    return SELF_EDITABLE_DIMENSIONS.contains(dimensionKey);
  }

  /** 八维 key → 中文标签（雷达图轴标签）。 */
  private static final Map<String, String> DIMENSION_LABELS =
      Map.of(
          "professional_background", "专业背景",
          "knowledge_base", "知识基础",
          "learning_goal", "学习目标",
          "cognitive_style", "认知风格",
          "learning_preference", "学习偏好",
          "error_pattern", "易错模式",
          "practical_ability", "实践能力",
          "emotion_persistence", "情绪与坚持");

  /**
   * 读取学生 8 维画像得分快照（供雷达图）。缺失维度按 {@link #DEFAULT_SCORE} 兜底，保证一定返回 8 维。
   */
  @Transactional(readOnly = true)
  public List<com.pythontopia.learner.profile.dto.ProfileDimensionDto> getDimensions(Long userId) {
    JsonNode dims = profileRepository.findDimensions(userId).orElse(null);
    List<com.pythontopia.learner.profile.dto.ProfileDimensionDto> out = new java.util.ArrayList<>();
    for (String key : DIMENSION_KEYS) {
      JsonNode dim = dims == null ? null : dims.get(key);
      double rawScore = DEFAULT_SCORE;
      int evidenceCount = 0;
      String lastUpdated = null;
      List<String> tags = new java.util.ArrayList<>();
      if (dim instanceof ObjectNode obj) {
        rawScore = obj.path("score").asDouble(DEFAULT_SCORE);
        evidenceCount = obj.path("evidence_count").asInt(0);
        JsonNode lu = obj.get("last_updated");
        lastUpdated = lu != null && lu.isTextual() ? lu.asText() : null;
        JsonNode tagsNode = obj.get("tags");
        if (tagsNode instanceof ArrayNode arr) {
          for (JsonNode t : arr) {
            tags.add(t.asText());
          }
        }
      }
      double confidence = Math.min(1.0, evidenceCount / (double) EVIDENCE_SATURATION);
      double effectiveScore = applyDecay(rawScore, lastUpdated);
      out.add(
          new com.pythontopia.learner.profile.dto.ProfileDimensionDto(
              key,
              DIMENSION_LABELS.getOrDefault(key, key),
              effectiveScore,
              confidence,
              evidenceCount,
              SELF_EDITABLE_DIMENSIONS.contains(key),
              tags));
    }
    return out;
  }

  /**
   * 读时半衰期 decay：久未更新的维度得分向中性 {@value #DEFAULT_SCORE} 轻微回归，raw 分不写库；
   * {@code last_updated} 缺失或不可解析时不衰减。
   */
  private static double applyDecay(double score, String lastUpdatedIso) {
    if (lastUpdatedIso == null || lastUpdatedIso.isBlank()) {
      return score;
    }
    try {
      java.time.Instant last = java.time.Instant.parse(lastUpdatedIso);
      double days =
          java.time.Duration.between(last, java.time.Instant.now()).toMillis() / 86_400_000.0;
      if (days <= 0) {
        return score;
      }
      double factor = Math.pow(0.5, days / DECAY_HALF_LIFE_DAYS);
      return DEFAULT_SCORE + (score - DEFAULT_SCORE) * factor;
    } catch (java.time.format.DateTimeParseException ex) {
      return score;
    }
  }

  private void writeRevisionAudit(
      Long userId, String dimensionKey, DimensionDelta delta, ObjectNode finalDimension) {
    if (delta.source() == null || delta.source().isBlank()) {
      return;
    }
    if (!ALLOWED_REVISION_SOURCES.contains(delta.source())) {
      log.warn(
          "PassiveProfileService skip revision write due to non-enum source userId={} dim={} source={}",
          userId,
          dimensionKey,
          delta.source());
      return;
    }
    Map<String, Object> diff = new LinkedHashMap<>();
    diff.put("dimensionKey", dimensionKey);
    diff.put("scoreDelta", delta.scoreDelta());
    diff.put("evidenceIncrement", delta.evidenceIncrement());
    diff.put("tagsAdd", delta.tagsAdd());
    diff.put("metadata", delta.metadata());
    JsonNode scoreNode = finalDimension.path("score");
    diff.put("finalScore", scoreNode.isMissingNode() ? null : scoreNode.asDouble());
    profileRepository.addRevision(userId, delta.source(), diff);
  }

  private static double clamp01(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }

  private static boolean arrayContains(ArrayNode arr, String value) {
    for (JsonNode node : arr) {
      if (value.equals(node.asText())) {
        return true;
      }
    }
    return false;
  }

  /** 从 tags 数组移除等值项（倒序遍历以安全 remove），供 INTERVIEW 自评把所选 tag 移到末尾。 */
  private static void removeTag(ArrayNode arr, String value) {
    for (int i = arr.size() - 1; i >= 0; i--) {
      if (value.equals(arr.get(i).asText())) {
        arr.remove(i);
      }
    }
  }
}
