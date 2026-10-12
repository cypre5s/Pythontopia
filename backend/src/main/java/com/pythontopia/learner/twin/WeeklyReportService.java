package com.pythontopia.learner.twin;

import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.event.LearningEventLogRepository;
import com.pythontopia.learner.event.LearningEventLogRepository.AuthoritativeEventCounts;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import com.pythontopia.learner.twin.dto.WeeklyReportDto;
import com.pythontopia.learner.twin.dto.WeeklyReportDto.InsightBrief;
import com.pythontopia.learner.twin.dto.WeeklyReportDto.MasteryDeltaBrief;
import com.pythontopia.learner.twin.dto.WeeklyReportDto.MemoryBrief;
import com.pythontopia.learner.twin.dto.WeeklyReportDto.ResourceRunBrief;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数字孪生周报：聚合本周记忆流、反思洞察、掌握度变化与资源运行摘要，纯读、零
 * LLM。所有查询均按 userId 隔离，仅返回本人数据。
 */
@Service
public class WeeklyReportService {

  private static final int MEMORY_LIMIT = 30;
  private static final int INSIGHT_LIMIT = 5;
  private static final int RUN_LIMIT = 10;

  private final MemoryRetrievalService memoryService;
  private final LearnerInsightRepository insightRepository;
  private final MasteryRepository masteryRepository;
  private final LearningEventLogRepository eventRepository;
  private final JdbcTemplate jdbc;

  public WeeklyReportService(
      MemoryRetrievalService memoryService,
      LearnerInsightRepository insightRepository,
      MasteryRepository masteryRepository,
      LearningEventLogRepository eventRepository,
      JdbcTemplate jdbc) {
    this.memoryService = memoryService;
    this.insightRepository = insightRepository;
    this.masteryRepository = masteryRepository;
    this.eventRepository = eventRepository;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public WeeklyReportDto build(Long userId) {
    Instant weekStart = LocalDate.now(ZoneOffset.UTC)
        .minusDays(7)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant();

    MemoryBrief memoryBrief = buildMemoryBrief(userId, weekStart);
    List<InsightBrief> insights = buildInsightBriefs(userId, weekStart);
    List<MasteryDeltaBrief> masteryDeltas = buildMasteryDeltas(userId, weekStart);
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

  private MemoryBrief buildMemoryBrief(Long userId, Instant weekStart) {
    AuthoritativeEventCounts counts = eventRepository.summarizeAuthoritativeSince(userId, weekStart);
    List<MemoryItemDto> memories =
        memoryService.retrieveAuthoritative(userId, null, null, MEMORY_LIMIT).stream()
            .filter(memory -> isInWindow(memory.createdAt(), weekStart))
            .toList();
    List<String> kcCodes = new ArrayList<>();
    List<String> highlights = new ArrayList<>();
    for (MemoryItemDto m : memories) {
      if ("practice_failed".equals(m.eventType())) {
        if (highlights.size() < 4) {
          highlights.add(m.summaryZh());
        }
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
    return new MemoryBrief(
        counts.totalEvents(), counts.failedPractices(), counts.succeededPractices(), kcCodes, highlights);
  }

  private List<InsightBrief> buildInsightBriefs(Long userId, Instant weekStart) {
    List<LearnerInsightDto> raw = insightRepository.findByUser(userId, INSIGHT_LIMIT);
    return raw.stream()
        .filter(i -> isInWindow(i.createdAt(), weekStart))
        .map(i -> new InsightBrief(
            i.id(), i.text(), i.dimensionKey(), i.tags(), i.createdAt()))
        .toList();
  }

  private List<MasteryDeltaBrief> buildMasteryDeltas(Long userId, Instant weekStart) {
    List<MasterySnapshot> snapshots = masteryRepository.findAll(userId);
    var currentScores = snapshots.stream()
        .filter(snapshot -> snapshot.masteryScore() != null)
        .collect(java.util.stream.Collectors.toMap(
            MasterySnapshot::kcCode, snapshot -> snapshot.masteryScore().doubleValue()));
    var confidenceScores = snapshots.stream()
        .filter(snapshot -> snapshot.confidence() != null)
        .collect(java.util.stream.Collectors.toMap(
            MasterySnapshot::kcCode, snapshot -> snapshot.confidence().doubleValue()));
    OffsetDateTime since = weekStart.atOffset(ZoneOffset.UTC);
    return masteryRepository.findWeeklyEvidence(userId, since).stream()
        .map(evidence -> new MasteryDeltaBrief(
            evidence.kcCode(),
            currentScores.getOrDefault(evidence.kcCode(), 0.0),
            confidenceScores.getOrDefault(evidence.kcCode(), 0.0),
            evidence.weeklyDelta() == null ? 0.0 : evidence.weeklyDelta().doubleValue(),
            evidence.evidenceCount(),
            evidence.evidenceSources()))
        .toList();
  }

  private static boolean isInWindow(String value, Instant weekStart) {
    if (value == null || value.isBlank()) return false;
    try {
      Instant createdAt = OffsetDateTime.parse(value).toInstant();
      return !createdAt.isBefore(weekStart) && !createdAt.isAfter(Instant.now());
    } catch (java.time.format.DateTimeParseException ignored) {
      return false;
    }
  }

  private List<ResourceRunBrief> buildResourceRunBriefs(Long userId, Instant weekStart) {
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
        new SqlParameterValue(
            Types.TIMESTAMP_WITH_TIMEZONE, weekStart.atOffset(ZoneOffset.UTC)),
        RUN_LIMIT);
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
