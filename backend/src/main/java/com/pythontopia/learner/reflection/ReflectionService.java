package com.pythontopia.learner.reflection;

import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.service.PassiveProfileService;
import com.pythontopia.learner.reflection.graph.InsightPayload;
import com.pythontopia.learner.reflection.graph.MemoryItemPayload;
import com.pythontopia.learner.reflection.graph.ReflectionGraphClient;
import com.pythontopia.learner.reflection.graph.ReflectionGraphResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 反思编排：取 topK 记忆 → 交 graph ReflectionAgent 综合洞察 → 回写画像（source=REFLECTION）+ 落库
 * {@code learner_insight}。借鉴 Stanford Generative Agents 的「反思」，但剥离幻觉：
 *
 * <ul>
 *   <li>每条洞察必须引用 event id（graph 端强校验，规则兜底也只引用真实失败事件 id）。
 *   <li>仅回写 5 个白名单维度。
 *   <li>graph 不可用 → 规则兜底（最高频失败 KC → error_pattern），{@code source=RULE}，全程不抛、不阻塞事件主链路。
 * </ul>
 *
 * <p>本方法预期在独立线程执行（由 {@link ReflectionTrigger} 提交线程池），不直接挂事件发布线程。
 */
@Service
public class ReflectionService {

  /** 反思可回写的维度白名单（防越权写非学情维度）。 */
  static final Set<String> REFLECTION_DIMENSIONS =
      Set.of(
          "error_pattern",
          "cognitive_style",
          "emotion_persistence",
          "practical_ability",
          "knowledge_base");

  private static final int MEMORY_LIMIT = 12;
  private static final String FAILURE_EVENT_TYPE = "practice_failed";
  private static final double RULE_CONFIDENCE = 0.4;
  private static final int MAX_INSIGHTS = 3;

  private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

  private final MemoryRetrievalService memoryRetrievalService;
  private final ReflectionGraphClient graphClient;
  private final PassiveProfileService passiveProfileService;
  private final LearnerInsightRepository insightRepository;

  public ReflectionService(
      MemoryRetrievalService memoryRetrievalService,
      ReflectionGraphClient graphClient,
      PassiveProfileService passiveProfileService,
      LearnerInsightRepository insightRepository) {
    this.memoryRetrievalService = memoryRetrievalService;
    this.graphClient = graphClient;
    this.passiveProfileService = passiveProfileService;
    this.insightRepository = insightRepository;
  }

  /** 为该用户做一次反思。best-effort：任何异常都被吞掉（记忆/反思失败不得影响主链路）。 */
  public void reflect(Long userId) {
    try {
      List<MemoryItemDto> memories = memoryRetrievalService.retrieve(userId, null, MEMORY_LIMIT);
      if (memories.isEmpty()) {
        return;
      }

      List<InsightPayload> insights;
      String sourceTag;
      try {
        ReflectionGraphResponse response = graphClient.reflect(userId, toPayloads(memories));
        insights = response.safeInsights();
        sourceTag = "GRAPH";
        // graph 显式返回空 = 证据不足，不回写、不兜底。
        if (insights.isEmpty()) {
          return;
        }
      } catch (RuntimeException ex) {
        log.warn("reflection graph unavailable, falling back to rule for user {}: {}", userId, ex.getMessage());
        insights = ruleFallback(memories);
        sourceTag = "RULE";
      }

      if (insights.isEmpty()) {
        return;
      }
      int written = 0;
      for (InsightPayload insight : insights) {
        if (written >= MAX_INSIGHTS) {
          break;
        }
        if (persist(userId, insight, sourceTag)) {
          written++;
        }
      }
    } catch (RuntimeException ex) {
      log.warn("reflection skipped for user {}: {}", userId, ex.getMessage());
    }
  }

  private List<MemoryItemPayload> toPayloads(List<MemoryItemDto> memories) {
    return memories.stream()
        .map(
            m ->
                new MemoryItemPayload(
                    m.eventId(),
                    m.eventType(),
                    m.kcCodes(),
                    m.importance(),
                    m.createdAt(),
                    m.summaryZh()))
        .toList();
  }

  /**
   * 回写画像 + 落库一条洞察。
   *
   * @return 是否成功写入（维度非白名单则跳过）。
   */
  private boolean persist(Long userId, InsightPayload insight, String sourceTag) {
    String dimensionKey = insight.dimensionKey();
    if (dimensionKey == null || !REFLECTION_DIMENSIONS.contains(dimensionKey)) {
      return false;
    }
    List<String> tags = insight.safeTags();
    List<Long> evidence = insight.safeEvidence();
    // scoreDelta=0：反思不擅自移动雷达分（练习事件已驱动分数），只贡献 tags + evidence_count + 审计来源 REFLECTION。
    passiveProfileService.applyDimensionUpdate(
        userId, dimensionKey, new DimensionDelta(0.0, 1, tags, null, "REFLECTION"));
    insightRepository.insert(
        userId, insight.text(), dimensionKey, tags, evidence, insight.confidence(), sourceTag);
    return true;
  }

  /**
   * 离线/无 key 规则兜底：统计记忆里 practice_failed 的最高频 KC，产出至多一条 error_pattern 洞察，
   * evidence 引用这些真实失败事件 id（不编造）。无失败记忆则空。
   */
  private List<InsightPayload> ruleFallback(List<MemoryItemDto> memories) {
    Map<String, List<Long>> failuresByKc = new LinkedHashMap<>();
    for (MemoryItemDto memory : memories) {
      if (!FAILURE_EVENT_TYPE.equals(memory.eventType())) {
        continue;
      }
      String kc = memory.kcCodes().isEmpty() ? null : memory.kcCodes().get(0);
      if (kc == null) {
        continue;
      }
      failuresByKc.computeIfAbsent(kc, k -> new java.util.ArrayList<>()).add(memory.eventId());
    }
    if (failuresByKc.isEmpty()) {
      return List.of();
    }
    Map.Entry<String, List<Long>> top = null;
    for (Map.Entry<String, List<Long>> entry : failuresByKc.entrySet()) {
      if (top == null || entry.getValue().size() > top.getValue().size()) {
        top = entry;
      }
    }
    String kc = top.getKey();
    return List.of(
        new InsightPayload(
            "近期在 " + kc + " 上反复失败，建议重点巩固。",
            "error_pattern",
            List.of(kc),
            List.copyOf(top.getValue()),
            RULE_CONFIDENCE));
  }
}
