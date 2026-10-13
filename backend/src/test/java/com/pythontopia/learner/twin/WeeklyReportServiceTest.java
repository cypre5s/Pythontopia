package com.pythontopia.learner.twin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.mastery.MasteryRepository.WeeklyEvidence;
import com.pythontopia.learner.event.LearningEventLogRepository;
import com.pythontopia.learner.event.LearningEventLogRepository.AuthoritativeEventCounts;
import com.pythontopia.learner.memory.MemoryRetrievalService;
import com.pythontopia.learner.memory.dto.MemoryItemDto;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import com.pythontopia.learner.twin.dto.WeeklyReportDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.BadSqlGrammarException;

@ExtendWith(MockitoExtension.class)
class WeeklyReportServiceTest {

  @Mock
  private MemoryRetrievalService memoryService;
  @Mock
  private LearnerInsightRepository insightRepository;
  @Mock
  private MasteryRepository masteryRepository;
  @Mock
  private LearningEventLogRepository eventRepository;
  @Mock
  private JdbcTemplate jdbc;

  private WeeklyReportService service;

  @BeforeEach
  void setUp() {
    service = new WeeklyReportService(
        memoryService, insightRepository, masteryRepository, eventRepository, jdbc);
    when(memoryService.retrieveAuthoritativeBetween(anyLong(), any(), any(), anyInt()))
        .thenReturn(List.of());
    when(eventRepository.summarizeAuthoritativeBetween(anyLong(), any(), any()))
        .thenReturn(new AuthoritativeEventCounts(0, 0, 0));
    when(masteryRepository.findWeeklyEvidence(anyLong(), any(), any()))
        .thenReturn(List.of());
    when(insightRepository.findByUserBetween(anyLong(), any(), any(), anyInt()))
        .thenReturn(List.of());
  }

  @Test
  @DisplayName("空记忆返回空摘要且不崩溃")
  void emptyMemoryReturnsEmptyReport() {
    when(insightRepository.findByUserBetween(anyLong(), any(), any(), anyInt()))
        .thenReturn(List.of());
    when(masteryRepository.findAll(anyLong())).thenReturn(List.of());
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyReportDto report = service.build(1L);

    assertThat(report).isNotNull();
    assertThat(report.periodStart()).isNotNull();
    assertThat(report.memoryBrief().totalEvents()).isZero();
  }

  @Test
  @DisplayName("有记忆事件时统计正确的通过/失败数")
  void countsMemoryEvents() {
    when(memoryService.retrieveAuthoritativeBetween(anyLong(), any(), any(), anyInt()))
        .thenReturn(List.of(
            new MemoryItemDto(1L, "practice_failed", "练习失败：KC01（WA）",
                List.of("KC01"), java.time.Instant.now().minusSeconds(86400).toString(), 3, 0.8, 0.6, 0.0, 0.5),
            new MemoryItemDto(2L, "practice_succeeded", "练习通过：KC02",
                List.of("KC02"), java.time.Instant.now().minusSeconds(3600).toString(), 2, 0.9, 0.4, 0.0, 0.52)));
    when(eventRepository.summarizeAuthoritativeBetween(anyLong(), any(), any()))
        .thenReturn(new AuthoritativeEventCounts(12, 4, 3));
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyReportDto report = service.build(1L);

    assertThat(report.memoryBrief().totalEvents()).isEqualTo(12);
    assertThat(report.memoryBrief().failedPractices()).isEqualTo(4);
    assertThat(report.memoryBrief().succeededPractices()).isEqualTo(3);
    assertThat(report.memoryBrief().topKcCodes()).containsExactly("KC01", "KC02");
  }

  @Test
  @DisplayName("有掌握度时返回 delta 摘要")
  void includesMasteryDeltas() {
    when(masteryRepository.findAll(anyLong()))
        .thenReturn(List.of(
            new MasterySnapshot(1L, 1L, "KC01",
                java.math.BigDecimal.valueOf(0.75),
                java.math.BigDecimal.valueOf(0.8),
                null, null)));
    when(masteryRepository.findWeeklyEvidence(anyLong(), any(), any()))
        .thenReturn(List.of(new WeeklyEvidence("KC01", java.math.BigDecimal.valueOf(0.12), 2,
            List.of("PRACTICE_PASS", "MICRO_CHECK_PASS"))));
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyReportDto report = service.build(1L);

    assertThat(report.masteryDeltas()).hasSize(1);
    assertThat(report.masteryDeltas().get(0).kcCode()).isEqualTo("KC01");
    assertThat(report.masteryDeltas().get(0).currentScore()).isEqualTo(0.75);
    assertThat(report.masteryDeltas().get(0).confidence()).isEqualTo(0.8);
    assertThat(report.masteryDeltas().get(0).weeklyDelta()).isEqualTo(0.12);
    assertThat(report.masteryDeltas().get(0).evidenceCount()).isEqualTo(2);
    assertThat(report.masteryDeltas().get(0).evidenceSources())
        .containsExactly("PRACTICE_PASS", "MICRO_CHECK_PASS");
  }

  @Test
  @DisplayName("周报所有统计共享上海时区的起止窗口")
  void usesOneBoundedWindowForWeeklySources() {
    when(masteryRepository.findAll(anyLong())).thenReturn(List.of());
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any(), any()))
        .thenReturn(List.of());

    service.build(1L);

    verify(memoryService).retrieveAuthoritativeBetween(eq(1L), any(), any(), eq(30));
    verify(insightRepository).findByUserBetween(eq(1L), any(), any(), eq(5));
    verify(eventRepository).summarizeAuthoritativeBetween(eq(1L), any(), any());
    verify(masteryRepository).findWeeklyEvidence(eq(1L), any(), any());
  }

  @Test
  @DisplayName("无法读取当前掌握度快照时不伪造为 0")
  void doesNotDefaultMissingMasterySnapshotToZero() {
    when(masteryRepository.findAll(anyLong())).thenReturn(List.of());
    when(masteryRepository.findWeeklyEvidence(anyLong(), any(), any()))
        .thenReturn(List.of(new WeeklyEvidence("KC01", java.math.BigDecimal.valueOf(0.1), 1,
            List.of("PRACTICE_PASS"))));
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyReportDto report = service.build(1L);

    assertThat(report.masteryDeltas().getFirst().currentScore()).isNull();
  }

  @Test
  @DisplayName("缺少可重建的变化值时明确返回空值")
  void preservesUnknownWeeklyDelta() {
    when(masteryRepository.findAll(anyLong())).thenReturn(List.of());
    when(masteryRepository.findWeeklyEvidence(anyLong(), any(), any()))
        .thenReturn(List.of(new WeeklyEvidence("KC01", null, 1, List.of("PRACTICE_PASS"))));
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyReportDto report = service.build(1L);

    assertThat(report.masteryDeltas().getFirst().weeklyDelta()).isNull();
  }

  @Test
  @DisplayName("有洞察时返回洞察列表")
  void includesRecentInsights() {
    when(insightRepository.findByUserBetween(anyLong(), any(), any(), anyInt()))
        .thenReturn(List.of(
            new LearnerInsightDto(1L, "需要加强错误处理", "error_pattern",
                List.of("KC01"), List.of(1L, 2L), 0.8, "GRAPH",
                java.time.Instant.now().minusSeconds(3600).toString())));
    when(masteryRepository.findAll(anyLong())).thenReturn(List.of());
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyReportDto report = service.build(1L);

    assertThat(report.insights()).hasSize(1);
    assertThat(report.insights().get(0).text()).contains("错误处理");
  }

  @Test
  @DisplayName("resource_run 查询失败时向上传播数据库异常")
  void propagatesResourceRunQueryFailure() {
    BadSqlGrammarException failure =
        new BadSqlGrammarException("weekly report", "SELECT", new java.sql.SQLException("boom"));
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any(), any()))
        .thenThrow(failure);

    assertThatThrownBy(() -> service.build(1L)).isSameAs(failure);
  }
}
