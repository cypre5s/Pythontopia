package com.pythontopia.learner.twin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
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

@ExtendWith(MockitoExtension.class)
class WeeklyReportServiceTest {

  @Mock
  private MemoryRetrievalService memoryService;
  @Mock
  private LearnerInsightRepository insightRepository;
  @Mock
  private MasteryRepository masteryRepository;
  @Mock
  private JdbcTemplate jdbc;

  private WeeklyReportService service;

  @BeforeEach
  void setUp() {
    service = new WeeklyReportService(memoryService, insightRepository, masteryRepository, jdbc);
  }

  @Test
  @DisplayName("空记忆返回空摘要且不崩溃")
  void emptyMemoryReturnsEmptyReport() {
    when(memoryService.retrieve(anyLong(), isNull(), anyInt())).thenReturn(List.of());
    when(insightRepository.findByUser(anyLong(), anyInt())).thenReturn(List.of());
    when(masteryRepository.findAll(anyLong())).thenReturn(List.of());
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyReportDto report = service.build(1L);

    assertThat(report).isNotNull();
    assertThat(report.periodStart()).isNotNull();
    assertThat(report.memoryBrief().totalEvents()).isZero();
  }

  @Test
  @DisplayName("有记忆事件时统计正确的通过/失败数")
  void countsMemoryEvents() {
    when(memoryService.retrieve(anyLong(), isNull(), anyInt()))
        .thenReturn(List.of(
            new MemoryItemDto(1L, "practice_failed", "练习失败：KC01（WA）",
                List.of("KC01"), "2026-06-08T10:00:00Z", 3, 0.8, 0.6, 0.0, 0.5),
            new MemoryItemDto(2L, "practice_succeeded", "练习通过：KC02",
                List.of("KC02"), "2026-06-09T10:00:00Z", 2, 0.9, 0.4, 0.0, 0.52)));
    when(insightRepository.findByUser(anyLong(), anyInt())).thenReturn(List.of());
    when(masteryRepository.findAll(anyLong())).thenReturn(List.of());
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyReportDto report = service.build(1L);

    assertThat(report.memoryBrief().totalEvents()).isEqualTo(2);
    assertThat(report.memoryBrief().failedPractices()).isEqualTo(1);
    assertThat(report.memoryBrief().succeededPractices()).isEqualTo(1);
  }

  @Test
  @DisplayName("有掌握度时返回 delta 摘要")
  void includesMasteryDeltas() {
    when(memoryService.retrieve(anyLong(), isNull(), anyInt())).thenReturn(List.of());
    when(insightRepository.findByUser(anyLong(), anyInt())).thenReturn(List.of());
    when(masteryRepository.findAll(anyLong()))
        .thenReturn(List.of(
            new MasterySnapshot(1L, 1L, "KC01",
                java.math.BigDecimal.valueOf(0.75),
                java.math.BigDecimal.valueOf(0.8),
                null, null)));
    when(masteryRepository.recentEvents(anyLong(), eq("KC01"), anyInt()))
        .thenReturn(List.of());
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyReportDto report = service.build(1L);

    assertThat(report.masteryDeltas()).hasSize(1);
    assertThat(report.masteryDeltas().get(0).kcCode()).isEqualTo("KC01");
    assertThat(report.masteryDeltas().get(0).currentScore()).isEqualTo(0.75);
  }

  @Test
  @DisplayName("有洞察时返回洞察列表")
  void includesRecentInsights() {
    when(memoryService.retrieve(anyLong(), isNull(), anyInt())).thenReturn(List.of());
    when(insightRepository.findByUser(anyLong(), anyInt()))
        .thenReturn(List.of(
            new LearnerInsightDto(1L, "需要加强错误处理", "error_pattern",
                List.of("KC01"), List.of(1L, 2L), 0.8, "GRAPH", "2026-06-09T00:00:00Z")));
    when(masteryRepository.findAll(anyLong())).thenReturn(List.of());
    when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any()))
        .thenReturn(List.of());

    WeeklyReportDto report = service.build(1L);

    assertThat(report.insights()).hasSize(1);
    assertThat(report.insights().get(0).text()).contains("错误处理");
  }
}
