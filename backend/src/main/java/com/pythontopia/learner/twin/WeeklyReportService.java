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
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
  private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

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
    Instant now = Instant.now();
    LocalDate today = now.atZone(REPORT_ZONE).toLocalDate();
    Instant weekStart = today.minusDays(7).atStartOfDay(REPORT_ZONE).toInstant();

    MemoryBrief memoryBrief = buildMemoryBrief(userId, weekStart, now);
    List<InsightBrief> insights = buildInsightBriefs(userId, weekStart, now);
    List<MasteryDeltaBrief> masteryDeltas = buildMasteryDeltas(userId, weekStart, now);
    List<ResourceRunBrief> resourceRuns = buildResourceRunBriefs(userId, weekStart, now);

    DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
    return new WeeklyReportDto(
        fmt.format(today.minusDays(7)),
        fmt.format(today),
        memoryBrief,
        insights,
        masteryDeltas,
        resourceRuns);
  }

  private MemoryBrief buildMemoryBrief(Long userId, Instant weekStart, Instant now) {
    AuthoritativeEventCounts counts =
        eventRepository.summarizeAuthoritativeBetween(userId, weekStart, now);
    List<MemoryItemDto> memories =
        memoryService.retrieveAuthoritativeBetween(userId, weekStart, now, MEMORY_LIMIT);
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

  private List<InsightBrief> buildInsightBriefs(Long userId, Instant weekStart, Instant now) {
    return insightRepository.findByUserBetween(userId, weekStart, now, INSIGHT_LIMIT).stream()
        .map(i -> new InsightBrief(
            i.id(), i.text(), i.dimensionKey(), i.tags(), i.createdAt()))
        .toList();
  }

  private List<MasteryDeltaBrief> buildMasteryDeltas(
      Long userId, Instant weekStart, Instant now) {
    List<MasterySnapshot> snapshots = masteryRepository.findAll(userId);
    var snapshotByKc = snapshots.stream()
        .collect(java.util.stream.Collectors.toMap(MasterySnapshot::kcCode, snapshot -> snapshot));
    ZonedDateTime start = weekStart.atZone(REPORT_ZONE);
    ZonedDateTime end = now.atZone(REPORT_ZONE);
    return masteryRepository.findWeeklyEvidence(
            userId, start.toOffsetDateTime(), end.toOffsetDateTime()).stream()
        .map(evidence -> {
          MasterySnapshot snapshot = snapshotByKc.get(evidence.kcCode());
          return new MasteryDeltaBrief(
              evidence.kcCode(),
              snapshot == null || snapshot.masteryScore() == null
                  ? null : snapshot.masteryScore().doubleValue(),
              snapshot == null || snapshot.confidence() == null
                  ? null : snapshot.confidence().doubleValue(),
              evidence.weeklyDelta() == null ? null : evidence.weeklyDelta().doubleValue(),
              evidence.evidenceCount(),
              evidence.evidenceSources());
        })
        .toList();
  }

  private List<ResourceRunBrief> buildResourceRunBriefs(
      Long userId, Instant weekStart, Instant now) {
    return jdbc.query(
        """
        SELECT agent_name, scenario, status, evidence_page_ids, created_at::text AS created_at
        FROM resource_run
        WHERE user_id = ? AND created_at >= ? AND created_at < ?
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
            Types.TIMESTAMP_WITH_TIMEZONE, weekStart.atZone(REPORT_ZONE).toOffsetDateTime()),
        new SqlParameterValue(
            Types.TIMESTAMP_WITH_TIMEZONE, now.atZone(REPORT_ZONE).toOffsetDateTime()),
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
