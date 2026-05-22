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
 * 阶段 M4 - 被动画像更新服务。
 *
 * <p>将练习提交、Debug 修复、资源消费、班级任务完成等学习事件转化为 8 维画像 {@code
 * learner_profile.dimensions} JSONB 字段的真实增量，并保留 {@code profile_revision} 审计。
 *
 * <p>八维 key（英文 snake_case，与任务规格 PassiveProfile JSON Schema 对齐）：
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
 * <p>设计说明：
 *
 * <ul>
 *   <li>{@link #applyDimensionUpdate} 是事务级方法，先读 dimensions JSONB，再应用 delta，最后写回并追加 profile_revision；
 *   <li>当 {@link DimensionDelta#source()} 不在 V091 定义的 CHECK 枚举内时，会跳过 revision 写入但仍执行 dimension 更新；
 *   <li>当 {@code learner_profile.dimensions} 列在 W2-MIGRATE 完成前尚未存在时，{@link ProfileRepository#findDimensions}
 *       会返回 {@link java.util.Optional#empty()}，调用方负责吞掉异常或上层 Listener 做降级。
 * </ul>
 */
@Service
public class PassiveProfileService {

  private static final Logger log = LoggerFactory.getLogger(PassiveProfileService.class);

  /** 八维 key 的权威清单，顺序与 AGENTS.md「画像至少包含 8 维」保持一致。 */
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

  /** 学生 PUT 自评接口允许直写的 4 个偏主观维度；其余只读，由 PassiveProfileService 被动改。 */
  public static final Set<String> SELF_EDITABLE_DIMENSIONS =
      Set.of(
          "professional_background",
          "learning_goal",
          "learning_preference",
          "emotion_persistence");

  /** V091 profile_revision_source_check 允许的 source 枚举。 */
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
          "DEBUG_FIX");

  private static final double DEFAULT_SCORE = 0.5;

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
      for (String tag : delta.tagsAdd()) {
        if (tag == null || tag.isBlank()) {
          continue;
        }
        if (!arrayContains(tagsArray, tag)) {
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
}
