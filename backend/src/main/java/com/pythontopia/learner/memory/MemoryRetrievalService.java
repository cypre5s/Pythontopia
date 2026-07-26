package com.pythontopia.learner.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.repository.CoursePackRepository;
import com.pythontopia.learner.event.LearningEventLogRepository;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 记忆流检索服务：对单个学生的近期记忆按 {@link MemoryScorer} 的 recency×importance×relevance 综合分排序，
 * 返回 topK「关于你的观察」。纯读、零 LLM。
 *
 * <p>relevance 的「同章」判定需要 KC→章节映射，从课程包（{@code packId=43}）解析；课程包读失败时降级为
 * 仅精确命中（不阻断检索）。
 */
@Service
public class MemoryRetrievalService {

  /** 唯一课程知识底座 packId（AGENTS.md 硬约束）。 */
  private static final long PACK_ID = 43L;

  /** 候选扫描窗口：限制单次打分的事件数量，避免长历史拖慢检索。 */
  private static final int SCAN_WINDOW = 200;

  private static final int DEFAULT_LIMIT = 8;
  private static final int MIN_LIMIT = 1;
  private static final int MAX_LIMIT = 30;

  private static final Logger log = LoggerFactory.getLogger(MemoryRetrievalService.class);

  private final LearningEventLogRepository eventRepository;
  private final CoursePackRepository coursePackRepository;
  private final EmbeddingClient embeddingClient;
  private final MemoryEmbeddingRepository memoryEmbeddingRepository;

  public MemoryRetrievalService(
      LearningEventLogRepository eventRepository,
      CoursePackRepository coursePackRepository,
      EmbeddingClient embeddingClient,
      MemoryEmbeddingRepository memoryEmbeddingRepository) {
    this.eventRepository = eventRepository;
    this.coursePackRepository = coursePackRepository;
    this.embeddingClient = embeddingClient;
    this.memoryEmbeddingRepository = memoryEmbeddingRepository;
  }

  /**
   * 取该生 topK 记忆。
   *
   * @param focusKc 可空；非空时启用相关性加权（精确命中 1.0 / 同章 0.5）。
   * @param limit 期望条数；clamp 到 {@value #MIN_LIMIT}..{@value #MAX_LIMIT}。
   */
  @Transactional(readOnly = true)
  public List<MemoryItemDto> retrieve(Long userId, String focusKc, int limit) {
    return retrieve(userId, focusKc, null, limit);
  }

  /**
   * 取该生 topK 记忆，支持可选语义查询。
   *
   * @param queryText 可空；非空且 embedding 可用时按余弦相似度并入 relevance（{@code max(KC 匹配, 余弦)}）；
   *     embedding 离线 / 返回空时静默退化为纯 KC 匹配（零回归）。
   */
  @Transactional(readOnly = true)
  public List<MemoryItemDto> retrieve(Long userId, String focusKc, String queryText, int limit) {
    return retrieve(userId, focusKc, queryText, limit, false);
  }

  /**
   * 权威记忆召回：仅使用 backend/graph 已确认事件，供路由、权限和教学策略决策使用。
   */
  @Transactional(readOnly = true)
  public List<MemoryItemDto> retrieveAuthoritative(
      Long userId, String focusKc, String queryText, int limit) {
    return retrieve(userId, focusKc, queryText, limit, true);
  }

  private List<MemoryItemDto> retrieve(
      Long userId, String focusKc, String queryText, int limit, boolean authoritative) {
    int safeLimit = clampLimit(limit);
    List<MemoryRow> rows =
        authoritative
            ? eventRepository.findRecentAuthoritativeForMemory(userId, SCAN_WINDOW)
            : eventRepository.findRecentForMemory(userId, SCAN_WINDOW);
    if (rows.isEmpty()) {
      return List.of();
    }
    Set<String> sameChapterKcCodes = resolveSameChapterKcCodes(focusKc);
    Map<Long, Double> semanticSims = resolveSemanticSims(userId, queryText);
    Instant now = Instant.now();
    return rows.stream()
        .map(
            row ->
                toDto(
                    row,
                    MemoryScorer.score(
                        row,
                        focusKc,
                        sameChapterKcCodes,
                        semanticSims.getOrDefault(row.eventId(), 0.0),
                        now)))
        .sorted(Comparator.comparingDouble(MemoryItemDto::totalScore).reversed())
        .limit(safeLimit)
        .toList();
  }

  /**
   * queryText → 查询向量 → 该用户记忆余弦相似度 {@code map(eventId→sim)}。无 queryText / embedding 离线
   * / 无命中 → 空 map（检索退化为纯 KC 匹配，不抛、不调向量库）。
   */
  private Map<Long, Double> resolveSemanticSims(Long userId, String queryText) {
    if (queryText == null || queryText.isBlank()) {
      return Map.of();
    }
    Optional<float[]> queryVector = embeddingClient.embed(queryText);
    if (queryVector.isEmpty()) {
      return Map.of();
    }
    List<MemoryEmbeddingRepository.ScoredEvent> hits =
        memoryEmbeddingRepository.searchByCosine(userId, queryVector.get(), SCAN_WINDOW);
    Map<Long, Double> sims = new HashMap<>();
    for (MemoryEmbeddingRepository.ScoredEvent hit : hits) {
      sims.put(hit.eventId(), hit.similarity());
    }
    return sims;
  }

  private static int clampLimit(int limit) {
    if (limit <= 0) {
      return MIN_LIMIT;
    }
    return Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);
  }

  /** focusKc 所在章节的全部 KC code（含自身）；focusKc 空 / 未知 / 课程包读失败时返回空集。 */
  private Set<String> resolveSameChapterKcCodes(String focusKc) {
    if (focusKc == null || focusKc.isBlank()) {
      return Set.of();
    }
    try {
      List<CourseKc> kcs = coursePackRepository.findKcs(PACK_ID, null);
      Long focusChapterId = null;
      for (CourseKc kc : kcs) {
        if (focusKc.equals(kc.kcCode())) {
          focusChapterId = kc.chapterId();
          break;
        }
      }
      if (focusChapterId == null) {
        return Set.of();
      }
      Set<String> codes = new HashSet<>();
      for (CourseKc kc : kcs) {
        if (focusChapterId.equals(kc.chapterId()) && kc.kcCode() != null) {
          codes.add(kc.kcCode());
        }
      }
      return codes;
    } catch (RuntimeException ex) {
      log.warn("memory same-chapter resolution degraded for focusKc={}: {}", focusKc, ex.getMessage());
      return Set.of();
    }
  }

  private MemoryItemDto toDto(MemoryRow row, MemoryScorer.ScoreBreakdown score) {
    return new MemoryItemDto(
        row.eventId(),
        row.eventType(),
        summaryZh(row),
        row.kcCodes(),
        row.createdAt() == null ? null : row.createdAt().toString(),
        row.importance(),
        round(score.recency()),
        round(score.importance()),
        round(score.relevance()),
        round(score.total()));
  }

  private static double round(double v) {
    return Math.round(v * 1_000.0) / 1_000.0;
  }

  /** 由 eventType + payload 拼成一句中文摘要（如「练习失败：PY43-KC-016（WA）」）。 */
  private static String summaryZh(MemoryRow row) {
    String kc = primaryKc(row);
    String detail = payloadText(row.payload());
    return switch (row.eventType()) {
      case "practice_failed" -> "练习失败：" + kc + paren(field(row.payload(), "failureKind"));
      case "practice_succeeded" -> "练习通过：" + kc;
      case "practice_retried" -> "再次尝试：" + kc;
      case "practice_abandoned" -> "中途放弃：" + kc;
      case "comeback_solved" -> "逆袭通过：" + kc;
      case "debug_resolved" -> "调试修复：" + kc;
      case "resource_consumed" -> "学习资源：" + kc + paren(field(row.payload(), "resourceKind"));
      case "media_consumed" -> "多模态学习：" + orDefault(field(row.payload(), "mediaType"), "视频");
      case "tutor_style_engaged" ->
          "采纳讲法：" + orDefault(field(row.payload(), "style"), "") + paren(kc);
      case "probe_choice_made" -> "世界探针选择";
      case "profile_bootstrapped" -> "画像初始化";
      default -> detail.isBlank() ? "学习事件：" + row.eventType() : detail;
    };
  }

  private static String primaryKc(MemoryRow row) {
    List<String> codes = row.kcCodes();
    return codes == null || codes.isEmpty() ? "未关联知识点" : codes.get(0);
  }

  private static String field(JsonNode payload, String key) {
    if (payload == null) {
      return "";
    }
    JsonNode node = payload.get(key);
    return node == null || node.isNull() ? "" : node.asText("");
  }

  private static String payloadText(JsonNode payload) {
    return payload == null ? "" : payload.toString();
  }

  private static String paren(String value) {
    return value == null || value.isBlank() ? "" : "（" + value + "）";
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
