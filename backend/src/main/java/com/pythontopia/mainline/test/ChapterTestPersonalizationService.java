package com.pythontopia.mainline.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.bugmine.dto.BugMineErrorBookItemDto;
import com.pythontopia.bugmine.service.BugMineService;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把后端已有的 8 维画像与 Bug 矿洞错题本投影为 Graph 章测契约。
 *
 * <p>只发送带证据的画像维度（常规 confidence ≥ 0.3，情绪 ≥ 0.25）和当前章节仍打开的真实错题记录；无信号时发送空对象，
 * 不用演示标签或静态样例补齐。
 */
@Service
public class ChapterTestPersonalizationService {

  private static final double PROFILE_CONFIDENCE_FLOOR = 0.30;
  private static final double EMOTION_CONFIDENCE_FLOOR = 0.25;
  private static final int PROFILE_TAG_LIMIT = 3;
  private static final int ERROR_KC_LIMIT = 8;
  private static final int ERROR_TAG_LIMIT = 8;

  private final PassiveProfileService profileService;
  private final BugMineService bugMineService;
  private final ObjectMapper objectMapper;

  public ChapterTestPersonalizationService(
      PassiveProfileService profileService,
      BugMineService bugMineService,
      ObjectMapper objectMapper) {
    this.profileService = profileService;
    this.bugMineService = bugMineService;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public PersonalizationContext build(long userId, List<String> chapterKcCodes) {
    return new PersonalizationContext(
        profileBrief(profileService.getDimensions(userId)),
        errorbookBrief(bugMineService.errorBook(userId), chapterKcCodes));
  }

  private ObjectNode profileBrief(List<ProfileDimensionDto> dimensions) {
    Map<String, ProfileDimensionDto> byKey = new LinkedHashMap<>();
    if (dimensions != null) {
      for (ProfileDimensionDto dimension : dimensions) {
        if (dimension != null && dimension.key() != null) {
          byKey.put(dimension.key(), dimension);
        }
      }
    }

    ObjectNode node = objectMapper.createObjectNode();
    ProfileDimensionDto knowledge = eligible(byKey.get("knowledge_base"), PROFILE_CONFIDENCE_FLOOR);
    if (knowledge != null) {
      node.put("knowledge_level", 1 + (int) Math.round(clamp01(knowledge.score()) * 4));
    }
    putTags(node, "major", eligible(byKey.get("professional_background"), PROFILE_CONFIDENCE_FLOOR));
    putTags(node, "learning_goal", eligible(byKey.get("learning_goal"), PROFILE_CONFIDENCE_FLOOR));
    putTags(node, "cognitive_style", eligible(byKey.get("cognitive_style"), PROFILE_CONFIDENCE_FLOOR));
    putTags(
        node,
        "learning_preference",
        eligible(byKey.get("learning_preference"), PROFILE_CONFIDENCE_FLOOR));

    ProfileDimensionDto errors = eligible(byKey.get("error_pattern"), PROFILE_CONFIDENCE_FLOOR);
    List<String> errorTags = recentTags(errors);
    if (!errorTags.isEmpty()) {
      ArrayNode array = node.putArray("error_patterns");
      errorTags.forEach(array::add);
    }

    ProfileDimensionDto practice = eligible(byKey.get("practical_ability"), PROFILE_CONFIDENCE_FLOOR);
    if (practice != null) {
      node.put("practice_ability", band(practice.score(), "偏弱", "一般", "较强"));
    }
    ProfileDimensionDto emotion =
        eligible(byKey.get("emotion_persistence"), EMOTION_CONFIDENCE_FLOOR);
    if (emotion != null) {
      node.put("persistence", emotion.score() < 0.5 ? "需鼓励" : "稳定");
    }
    return node;
  }

  private ObjectNode errorbookBrief(
      List<BugMineErrorBookItemDto> errorBook, List<String> chapterKcCodes) {
    Set<String> allowedKcs = cleanSet(chapterKcCodes, Integer.MAX_VALUE);
    LinkedHashSet<String> frequentKcs = new LinkedHashSet<>();
    LinkedHashSet<String> topTags = new LinkedHashSet<>();
    int includedItems = 0;
    if (errorBook != null) {
      for (BugMineErrorBookItemDto item : errorBook) {
        if (item == null || !allowedKcs.contains(clean(item.kcCode()))) {
          continue;
        }
        includedItems++;
        addLimited(frequentKcs, item.kcCode(), ERROR_KC_LIMIT);
        if (item.mistakePatterns() != null) {
          for (String pattern : item.mistakePatterns()) {
            addLimited(topTags, pattern, ERROR_TAG_LIMIT);
          }
        }
      }
    }

    ObjectNode node = objectMapper.createObjectNode();
    if (!frequentKcs.isEmpty()) {
      ArrayNode array = node.putArray("frequent_error_kcs");
      frequentKcs.forEach(array::add);
    }
    if (!topTags.isEmpty()) {
      ArrayNode array = node.putArray("top_error_tags");
      topTags.forEach(array::add);
    }
    if (includedItems > 0) {
      node.put(
          "summary",
          "open_items="
              + includedItems
              + "; kcs="
              + String.join(",", frequentKcs)
              + "; tags="
              + String.join(",", topTags));
    }
    return node;
  }

  private void putTags(ObjectNode node, String field, ProfileDimensionDto dimension) {
    List<String> tags = recentTags(dimension);
    if (!tags.isEmpty()) {
      node.put(field, String.join(",", tags));
    }
  }

  private static ProfileDimensionDto eligible(ProfileDimensionDto dimension, double floor) {
    return dimension != null && dimension.confidence() >= floor ? dimension : null;
  }

  private static List<String> recentTags(ProfileDimensionDto dimension) {
    if (dimension == null || dimension.tags() == null) {
      return List.of();
    }
    List<String> clean = cleanSet(dimension.tags(), Integer.MAX_VALUE).stream().toList();
    return clean.size() <= PROFILE_TAG_LIMIT
        ? clean
        : clean.subList(clean.size() - PROFILE_TAG_LIMIT, clean.size());
  }

  private static Set<String> cleanSet(List<String> values, int limit) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    if (values != null) {
      for (String value : values) {
        addLimited(result, value, limit);
      }
    }
    return result;
  }

  private static void addLimited(Set<String> values, String raw, int limit) {
    String value = clean(raw);
    if (!value.isEmpty() && values.size() < limit) {
      values.add(value);
    }
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private static String band(double score, String low, String middle, String high) {
    double value = clamp01(score);
    return value < 0.45 ? low : value <= 0.70 ? middle : high;
  }

  public record PersonalizationContext(JsonNode profileBrief, JsonNode errorbookBrief) {}
}
