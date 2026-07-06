package com.pythontopia.learner.twin;

import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import com.pythontopia.learner.twin.dto.WeeklyReportDto;
import com.pythontopia.learner.twin.dto.WeeklyReportDto.InsightBrief;
import com.pythontopia.learner.twin.dto.WeeklyReportDto.MasteryDeltaBrief;
import com.pythontopia.learner.twin.dto.WeeklyReportDto.MemoryBrief;
import com.pythontopia.learner.twin.dto.WeeklyReportDto.ResourceRunBrief;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数字孪生周报：聚合本周记忆流、反思洞察、掌握度变化与资源运行摘要，纯读、零
 * LLM。所有查询均按 userId 隔离，仅返回本人数据。
 */
@Service
public class WeeklyReportService {

  private static final Logger log = LoggerFactory.getLogger(WeeklyReportService.class);

  private static final int MEMORY_LIMIT = 30;
  private static final int INSIGHT_LIMIT = 5;
  private static final int RUN_LIMIT = 10;

  private final MemoryRetrievalService memoryService;
  private final LearnerInsightRepository insightRepository;
  private final MasteryRepository masteryRepository;
  private final JdbcTemplate jdbc;

  public WeeklyReportService(
      MemoryRetrievalService memoryService,
      LearnerInsightRepository insightRepository,
      MasteryRepository masteryRepository,
      JdbcTemplate jdbc) {
    this.memoryService = memoryService;
    this.insightRepository = insightRepository;
    this.masteryRepository = masteryRepository;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public WeeklyReportDto build(Long userId) {
    Instant weekStart = LocalDate.now(ZoneOffset.UTC)
        .minusDays(7)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant();

    MemoryBrief memoryBrief = buildMemoryBrief(userId);
    List<InsightBrief> insights = buildInsightBriefs(userId);
    List<MasteryDeltaBrief> masteryDeltas = buildMasteryDeltas(userId);
    List<ResourceRunBrief> resourceRuns = buildResourceRunBriefs(userId, weekStart);

    DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
    return new WeeklyReportDto(
        fmt.format(LocalDate.now(ZoneOffset.UTC).minusDays(7)),
        fmt.format(LocalDate.now(ZoneOffset.UTC)),
        memoryBrief,
        insights,
        masteryDeltas,
        resourceRuns);
  }

  private MemoryBrief buildMemoryBrief(Long userId) {
    List<MemoryItemDto> memories = memoryService.retrieve(userId, null, MEMORY_LIMIT);
    int failed = 0;
    int succeeded = 0;
    List<String> kcCodes = new ArrayList<>();
    List<String> highlights = new ArrayList<>();
    for (MemoryItemDto m : memories) {
      if ("practice_failed".equals(m.eventType())) {
        failed++;
        if (highlights.size() < 4) {
          highlights.add(m.summaryZh());
        }
      } else if ("practice_succeeded".equals(m.eventType())) {
        succeeded++;
      }
      if (kcCodes.size() < 6 && m.kcCodes() != null) {
        for (String kc : m.kcCodes()) {
          if (kcCodes.size() >= 6) break;
          if (!kcCodes.contains(kc)) {
            kcCodes.add(kc);
          }
        }
      }
    }
    return new MemoryBrief(memories.size(), failed, succeeded, kcCodes, highlights);
  }

  private List<InsightBrief> buildInsightBriefs(Long userId) {
    List<LearnerInsightDto> raw = insightRepository.findByUser(userId, INSIGHT_LIMIT);
    return raw.stream()
        .filter(i -> i.createdAt() != null)
        .map(i -> new InsightBrief(
            i.id(), i.text(), i.dimensionKey(), i.tags(), i.createdAt()))
        .toList();
  }

  private List<MasteryDeltaBrief> buildMasteryDeltas(Long userId) {
    List<MasterySnapshot> snapshots = masteryRepository.findAll(userId);
    return snapshots.stream()
        .filter(s -> s.masteryScore() != null)
        .map(s -> {
          List<String> reasons = masteryRepository.recentEvents(userId, s.kcCode(), 3)
              .stream()
              .map(e -> e.reason() != null ? e.reason() : "")
              .filter(r -> !r.isEmpty())
              .toList();
          return new MasteryDeltaBrief(
              s.kcCode(),
              s.masteryScore().doubleValue(),
              s.confidence() != null ? s.confidence().doubleValue() : 0.0,
              reasons);
        })
        .toList();
  }

  private List<ResourceRunBrief> buildResourceRunBriefs(Long userId, Instant weekStart) {
    try {
      return jdbc.query(
          """
          SELECT agent_name, scenario, status, evidence_page_ids, created_at::text AS created_at
          FROM resource_run
          WHERE user_id = ? AND created_at >= ?
          ORDER BY created_at DESC
          LIMIT ?
          """,
          (rs, rowNum) -> new ResourceRunBrief(
              rs.getString("agent_name"),
              rs.getString("scenario"),
              rs.getString("status"),
              evidenceCount(rs.getArray("evidence_page_ids")),
              rs.getString("created_at")),
          userId,
          weekStart,
          RUN_LIMIT);
    } catch (RuntimeException ex) {
      log.warn("Failed to query resource_run for user {}: {}", userId, ex.getMessage());
      return List.of();
    }
  }

  private static int evidenceCount(java.sql.Array array) {
    if (array == null) return 0;
    try {
      Object raw = array.getArray();
      if (raw instanceof Object[] arr) return arr.length;
    } catch (java.sql.SQLException ignored) {
    }
    return 0;
  }
}
