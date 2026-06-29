package com.pythontopia.learner.memory;

import com.pythontopia.learner.mastery.review.ReviewState;
import com.pythontopia.learner.mastery.review.ReviewStateRepository;
import com.pythontopia.learner.memory.dto.MemoryBriefDto;
import com.pythontopia.learner.memory.provider.MemoryProviderRouter;
import com.pythontopia.learner.memory.provider.RecalledMemory;
import com.pythontopia.learner.profile.dto.ProfileDimensionDto;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 记忆 brief 聚合服务（§3.2 / §4 M backend）：把三路信号合成 {@link MemoryBriefDto}，喂闭环 {@code understand}。
 *
 * <ul>
 *   <li><b>facts</b>：{@link MemoryProviderRouter#recall} 召回（local 复用 memory_embedding / 外部 Mem0·Zep
 *       fail-soft 回退 local），按近→远时序排序。
 *   <li><b>weak_kcs</b>：召回中的「受阻」记忆 KC + {@code kc_review_state} 到期/有 lapse 的 KC（近期反复受阻）。
 *   <li><b>style_notes</b>：8 维画像（{@link PassiveProfileService#getDimensions}）的认知风格 / 偏好 / 情绪提示。
 *   <li><b>as_of</b>：生成时刻 ISO-8601（体现「截至此刻」的时序）。
 * </ul>
 *
 * <p>三路信号各自 fail-soft：任一来源故障仅降级为空，brief 仍返回，绝不拖垮主链路（§0 红线）。
 */
@Service
public class MemoryBriefService {

  private static final Logger log = LoggerFactory.getLogger(MemoryBriefService.class);

  private static final int MAX_FACTS = 6;
  private static final int RECALL_LIMIT = 8;
  private static final int MAX_WEAK_KCS = 6;
  private static final int MAX_STYLE_NOTES = 4;

  /** 与 {@link PassiveProfileService} 对齐：信号不足不贴标签（防幻觉）。 */
  private static final double CONFIDENCE_FLOOR = 0.3;

  private static final double EMOTION_CONFIDENCE_FLOOR = 0.25;
  private static final int STYLE_TAG_LIMIT = 3;

  private final MemoryProviderRouter providerRouter;
  private final PassiveProfileService profileService;
  private final ReviewStateRepository reviewStateRepository;
  private final Clock clock;

  @Autowired
  public MemoryBriefService(
      MemoryProviderRouter providerRouter,
      PassiveProfileService profileService,
      ReviewStateRepository reviewStateRepository) {
    this(providerRouter, profileService, reviewStateRepository, Clock.systemUTC());
  }

  MemoryBriefService(
      MemoryProviderRouter providerRouter,
      PassiveProfileService profileService,
      ReviewStateRepository reviewStateRepository,
      Clock clock) {
    this.providerRouter = providerRouter;
    this.profileService = profileService;
    this.reviewStateRepository = reviewStateRepository;
    this.clock = clock;
  }

  /**
   * 为 {@code userId} 合成记忆 brief。{@code query} 可空（空白时召回退化为最近记忆）。
   *
   * <p>active provider 由 {@code MEMORY_PROVIDER} 决定；外部 provider 故障已在 router 内 fail-soft 回退 local。
   */
  public MemoryBriefDto buildBrief(long userId, String query) {
    Instant now = Instant.now(clock);
    List<RecalledMemory> recalled = safeRecall(userId, query);

    List<String> facts = toFacts(recalled);
    List<String> weakKcs = toWeakKcs(userId, recalled, now);
    List<String> styleNotes = toStyleNotes(userId);
    List<String> stablePreferences = toStablePreferences(recalled, styleNotes);
    List<String> strugglePatterns = toStrugglePatterns(recalled);
    List<String> recentWins = toRecentWins(recalled);
    List<String> interventionHints =
        toInterventionHints(weakKcs, styleNotes, stablePreferences, strugglePatterns);

    return new MemoryBriefDto(
        facts,
        weakKcs,
        styleNotes,
        DateTimeFormatter.ISO_INSTANT.format(now),
        stablePreferences,
        strugglePatterns,
        recentWins,
        interventionHints);
  }

  private List<RecalledMemory> safeRecall(long userId, String query) {
    try {
      List<RecalledMemory> recalled = providerRouter.recall(userId, query, RECALL_LIMIT);
      return recalled == null ? List.of() : recalled;
    } catch (RuntimeException ex) {
      log.warn("memory brief recall degraded for user {}: {}", userId, ex.getMessage());
      return List.of();
    }
  }

  /** 召回事实按近→远（occurredAt desc，缺时间置后），同分按 score desc；文本去重，截断 MAX_FACTS。 */
  private static List<String> toFacts(List<RecalledMemory> recalled) {
    return recalled.stream()
        .filter(m -> m.text() != null && !m.text().isBlank())
        .sorted(recencyThenScore())
        .map(RecalledMemory::text)
        .map(String::trim)
        .distinct()
        .limit(MAX_FACTS)
        .toList();
  }

  private static Comparator<RecalledMemory> recencyThenScore() {
    Comparator<RecalledMemory> byRecency =
        Comparator.comparing(
            RecalledMemory::occurredAt,
            Comparator.nullsLast(Comparator.reverseOrder()));
    return byRecency.thenComparing(Comparator.comparingDouble(RecalledMemory::score).reversed());
  }

  private static List<String> toStablePreferences(
      List<RecalledMemory> recalled, List<String> styleNotes) {
    LinkedHashSet<String> preferences = new LinkedHashSet<>();
    for (RecalledMemory memory : recalled) {
      String kind = safeLower(memory.kind());
      String text = safeTrim(memory.text());
      if (text.isBlank()) {
        continue;
      }
      if (kind.contains("preference") || text.contains("偏好") || text.contains("喜欢")) {
        preferences.add(text);
      }
    }
    for (String note : styleNotes) {
      String clean = safeTrim(note);
      if (clean.startsWith("认知风格:") || clean.startsWith("偏好:")) {
        preferences.add(clean);
      }
    }
    return preferences.stream().limit(5).toList();
  }

  private static List<String> toStrugglePatterns(List<RecalledMemory> recalled) {
    LinkedHashSet<String> struggles = new LinkedHashSet<>();
    for (RecalledMemory memory : recalled) {
      String kind = safeLower(memory.kind());
      String text = safeTrim(memory.text());
      if (text.isBlank()) {
        continue;
      }
      if (memory.isStruggle()
          || kind.contains("failed")
          || kind.contains("abandoned")
          || text.contains("未过")
          || text.contains("失败")
          || text.contains("卡")) {
        struggles.add(text);
      }
    }
    return struggles.stream().limit(6).toList();
  }

  private static List<String> toRecentWins(List<RecalledMemory> recalled) {
    LinkedHashSet<String> wins = new LinkedHashSet<>();
    for (RecalledMemory memory : recalled) {
      String kind = safeLower(memory.kind());
      String text = safeTrim(memory.text());
      if (text.isBlank()) {
        continue;
      }
      if (kind.contains("succeeded")
          || kind.contains("passed")
          || kind.contains("comeback")
          || kind.contains("resolved")
          || text.contains("通过")
          || text.contains("修复")) {
        wins.add(text);
      }
    }
    return wins.stream().limit(5).toList();
  }

  private static List<String> toInterventionHints(
      List<String> weakKcs,
      List<String> styleNotes,
      List<String> stablePreferences,
      List<String> strugglePatterns) {
    LinkedHashSet<String> hints = new LinkedHashSet<>();
    String preferenceText = String.join(" ", stablePreferences).toLowerCase();
    String styleText = String.join(" ", styleNotes).toLowerCase();
    if (preferenceText.contains("图") || styleText.contains("visual")) {
      hints.add("优先图解/结构化示例。");
    }
    if (!weakKcs.isEmpty()) {
      hints.add("围绕 " + weakKcs.get(0) + " 做一次短补救，再换题验证。");
    }
    if (styleNotes.stream().anyMatch(note -> note.contains("需鼓励")) || !strugglePatterns.isEmpty()) {
      hints.add("短步骤+鼓励反馈：先给一个小目标，再用微练习确认。");
    }
    if (!strugglePatterns.isEmpty()) {
      hints.add("不要只重讲概念，必须接一个可判定的小证据。");
    }
    return hints.stream().limit(5).toList();
  }

  /**
   * weak_kcs：先取召回中「受阻」记忆的 KC（与 query 相关的受阻优先），再补 {@code kc_review_state} 中
   * 有 lapse 或已到期的 KC（按 lapse 多→少、到期早→晚）；去重截断 MAX_WEAK_KCS。
   */
  private List<String> toWeakKcs(long userId, List<RecalledMemory> recalled, Instant now) {
    LinkedHashSet<String> weak = new LinkedHashSet<>();

    for (RecalledMemory memory : recalled) {
      if (memory.isStruggle() && isPresent(memory.kcCode())) {
        weak.add(memory.kcCode().trim());
      }
    }

    for (ReviewState state : sortedStruggleReviews(userId, now)) {
      if (isPresent(state.kcCode())) {
        weak.add(state.kcCode().trim());
      }
    }

    return weak.stream().limit(MAX_WEAK_KCS).toList();
  }

  private List<ReviewState> sortedStruggleReviews(long userId, Instant now) {
    List<ReviewState> reviews;
    try {
      reviews = reviewStateRepository.findAll(userId);
    } catch (RuntimeException ex) {
      log.warn("memory brief review-state degraded for user {}: {}", userId, ex.getMessage());
      return List.of();
    }
    if (reviews == null || reviews.isEmpty()) {
      return List.of();
    }
    return reviews.stream()
        .filter(state -> state.lapses() > 0 || isDue(state, now))
        .sorted(
            Comparator.comparingInt(ReviewState::lapses)
                .reversed()
                .thenComparing(MemoryBriefService::dueInstant, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  private static Instant dueInstant(ReviewState state) {
    return state.dueAt() == null ? null : state.dueAt().toInstant();
  }

  private static boolean isDue(ReviewState state, Instant now) {
    Instant due = dueInstant(state);
    return due != null && !due.isAfter(now);
  }

  /** style_notes：认知风格（最近主导标签）+ 学习偏好（最近标签）+ 情绪（低落则需鼓励）。 */
  private List<String> toStyleNotes(long userId) {
    Map<String, ProfileDimensionDto> byKey = dimensionsByKey(userId);
    if (byKey.isEmpty()) {
      return List.of();
    }
    List<String> notes = new ArrayList<>();

    ProfileDimensionDto cognitive = byKey.get("cognitive_style");
    if (passesFloor(cognitive, CONFIDENCE_FLOOR)) {
      List<String> tags = recentTags(cognitive);
      if (!tags.isEmpty()) {
        notes.add("认知风格: " + tags.get(tags.size() - 1));
      }
    }

    ProfileDimensionDto preference = byKey.get("learning_preference");
    if (passesFloor(preference, CONFIDENCE_FLOOR)) {
      List<String> tags = recentTags(preference);
      if (!tags.isEmpty()) {
        notes.add("偏好: " + String.join(",", tags));
      }
    }

    ProfileDimensionDto emotion = byKey.get("emotion_persistence");
    if (passesFloor(emotion, EMOTION_CONFIDENCE_FLOOR) && emotion.score() < 0.5) {
      notes.add("情绪: 需鼓励");
    }

    return notes.size() > MAX_STYLE_NOTES ? List.copyOf(notes.subList(0, MAX_STYLE_NOTES)) : notes;
  }

  private Map<String, ProfileDimensionDto> dimensionsByKey(long userId) {
    List<ProfileDimensionDto> dims;
    try {
      dims = profileService.getDimensions(userId);
    } catch (RuntimeException ex) {
      log.warn("memory brief profile degraded for user {}: {}", userId, ex.getMessage());
      return Map.of();
    }
    if (dims == null || dims.isEmpty()) {
      return Map.of();
    }
    Map<String, ProfileDimensionDto> byKey = new LinkedHashMap<>();
    for (ProfileDimensionDto dim : dims) {
      byKey.put(dim.key(), dim);
    }
    return byKey;
  }

  private static boolean passesFloor(ProfileDimensionDto dim, double floor) {
    return dim != null && dim.confidence() >= floor;
  }

  private static List<String> recentTags(ProfileDimensionDto dim) {
    if (dim == null || dim.tags() == null || dim.tags().isEmpty()) {
      return List.of();
    }
    List<String> clean = new ArrayList<>();
    for (String tag : dim.tags()) {
      if (tag != null && !tag.isBlank()) {
        clean.add(tag.trim());
      }
    }
    return clean.size() > STYLE_TAG_LIMIT
        ? List.copyOf(clean.subList(clean.size() - STYLE_TAG_LIMIT, clean.size()))
        : clean;
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }

  private static String safeLower(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private static String safeTrim(String value) {
    return value == null ? "" : value.trim();
  }
}
