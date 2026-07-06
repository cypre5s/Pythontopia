package com.pythontopia.world.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.learner.mastery.MasteryEvaluator;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.world.skill.dto.LearnerSkillDto;
import com.pythontopia.world.skill.dto.SkillNodeDto;
import com.pythontopia.world.skill.dto.SkillsResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 技能树查询与解锁流程。解锁条件以 JSONB 存储，schema 如下
 *
 * <pre>{
 *   "type":"KC_MASTERY",
 *   "kcCode":"PY43-KC-001",
 *   "min":0.6
 * }</pre>
 *
 * 也可以是 {@code "type":"ALL"} 组合条件，配合 {@code "conditions":[...]} 数组。
 */
@Service
public class SkillService {

  private final SkillRepository repository;
  private final MasteryEvaluator masteryEvaluator;

  public SkillService(SkillRepository repository, MasteryEvaluator masteryEvaluator) {
    this.repository = repository;
    this.masteryEvaluator = masteryEvaluator;
  }

  public SkillsResponse list(Long userId) {
    List<SkillNodeDto> nodes =
        repository.findAllNodes().stream().map(SkillNodeDto::from).toList();
    List<LearnerSkillDto> unlocked =
        repository.findUnlocked(userId).stream().map(LearnerSkillDto::from).toList();
    return new SkillsResponse(nodes, unlocked);
  }

  public List<LearnerSkillDto> unlocked(Long userId) {
    return repository.findUnlocked(userId).stream().map(LearnerSkillDto::from).toList();
  }

  @Transactional
  public LearnerSkillDto unlock(Long userId, String skillKey) {
    SkillNode node =
        repository
            .findNode(skillKey)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "SKILL_NOT_FOUND", "Unknown skill " + skillKey));
    if (repository.findLearnerSkill(userId, skillKey).isPresent()) {
      return LearnerSkillDto.from(repository.findLearnerSkill(userId, skillKey).get());
    }
    Map<String, MasterySnapshot> masteryByKc =
        masteryEvaluator.snapshots(userId).stream()
            .collect(Collectors.toMap(MasterySnapshot::kcCode, s -> s));
    if (!evaluate(node.unlockCondition(), masteryByKc)) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "SKILL_LOCKED",
          "Unlock condition not met for skill " + skillKey);
    }
    return LearnerSkillDto.from(repository.unlockSkill(userId, skillKey));
  }

  static boolean evaluate(JsonNode condition, Map<String, MasterySnapshot> masteryByKc) {
    if (condition == null || condition.isMissingNode() || condition.isNull() || !condition.isObject()) {
      return true;
    }
    String type = condition.path("type").asText("");
    return switch (type) {
      case "", "ANY_TIME" -> true;
      case "KC_MASTERY" -> meetsKcMastery(condition, masteryByKc);
      case "ALL" -> {
        JsonNode conditions = condition.get("conditions");
        if (conditions == null || !conditions.isArray()) {
          yield true;
        }
        for (JsonNode child : conditions) {
          if (!evaluate(child, masteryByKc)) {
            yield false;
          }
        }
        yield true;
      }
      case "ANY" -> {
        JsonNode conditions = condition.get("conditions");
        if (conditions == null || !conditions.isArray() || conditions.size() == 0) {
          yield false;
        }
        for (JsonNode child : conditions) {
          if (evaluate(child, masteryByKc)) {
            yield true;
          }
        }
        yield false;
      }
      default -> false;
    };
  }

  private static boolean meetsKcMastery(JsonNode condition, Map<String, MasterySnapshot> masteryByKc) {
    String kcCode = condition.path("kcCode").asText("");
    BigDecimal threshold = BigDecimal.valueOf(condition.path("min").asDouble(1.0));
    if (kcCode.isBlank()) {
      return false;
    }
    MasterySnapshot snapshot = masteryByKc.get(kcCode);
    if (snapshot == null || snapshot.masteryScore() == null) {
      return false;
    }
    return snapshot.masteryScore().compareTo(threshold) >= 0;
  }
}
