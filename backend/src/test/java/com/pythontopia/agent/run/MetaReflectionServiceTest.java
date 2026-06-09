package com.pythontopia.agent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/** P4.1：meta-reflection 据真实 resource_feedback 采纳/评分驱动策略并落审计；无信号不动（成本守恒）。 */
@ExtendWith(MockitoExtension.class)
class MetaReflectionServiceTest {

  private static final String AGENT = "PracticeAgent";

  @Mock private JdbcTemplate jdbc;
  private MetaReflectionService service;

  @BeforeEach
  void setUp() {
    service = new MetaReflectionService(jdbc);
  }

  @Test
  @DisplayName("低采纳率反馈 → 调整策略并写审计行（INSERT agent_strategy）")
  void lowAdoptionFeedbackAdjustsStrategyAndWritesAudit() {
    // 5 条反馈仅采纳 1 → 采纳率 20% < 40% → 触发调整（不依赖 run 失败率）。
    when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("resource_feedback")), eq(AGENT)))
        .thenReturn(List.of(feedbackRow(5L, 1L, 2.0)));
    when(jdbc.queryForObject(
            argThat(sql -> sql != null && sql.contains("MAX(version)")), eq(Integer.class), eq(AGENT)))
        .thenReturn(1);

    service.reflect(AGENT);

    verify(jdbc, times(1))
        .update(
            argThat(sql -> sql != null && sql.contains("INSERT INTO agent_strategy")),
            eq(AGENT),
            argThat(json -> json != null && json.toString().contains("basic")),
            eq(1),
            argThat(reason -> reason != null && reason.toString().contains("采纳率")));
  }

  @Test
  @DisplayName("无反馈 + run 失败率中性 → 不调整、不写审计（成本守恒）")
  void noFeedbackAndNeutralRunsNoChange() {
    when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("resource_feedback")), eq(AGENT)))
        .thenReturn(List.of(feedbackRow(0L, 0L, null)));
    when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("resource_run")), eq(AGENT)))
        .thenReturn(List.of(statusRow("OK", 8L), statusRow("FAILED", 2L))); // 失败率 20% 中性

    service.reflect(AGENT);

    verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("反馈不足 + run 不足 MIN_RUNS → 不调整（成本守恒）")
  void tooFewSignalsNoChange() {
    when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("resource_feedback")), eq(AGENT)))
        .thenReturn(List.of(feedbackRow(0L, 0L, null)));
    when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("resource_run")), eq(AGENT)))
        .thenReturn(List.of(statusRow("OK", 2L)));

    service.reflect(AGENT);

    verify(jdbc, never()).update(anyString(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("无策略返回空")
  void emptyStrategyWhenNone() {
    when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("strategy_params")), eq(AGENT)))
        .thenReturn(List.of());
    assertThat(service.latestStrategy(AGENT)).isEmpty();
  }

  @Test
  @DisplayName("有策略返回最新")
  void returnsLatestStrategy() {
    Map<String, Object> params = Map.of("complexity", (Object) "basic");
    Map<String, Object> row = new HashMap<>();
    row.put("strategy_params", params);
    when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("strategy_params")), eq(AGENT)))
        .thenReturn(List.of(row));
    assertThat(service.latestStrategy(AGENT)).containsEntry("complexity", "basic");
  }

  @Test
  @DisplayName("有策略时 latestDirective 折算为可下传字符串")
  void latestDirectiveFromStrategy() {
    Map<String, Object> params = new HashMap<>();
    params.put("complexity", "basic");
    Map<String, Object> row = new HashMap<>();
    row.put("strategy_params", params);
    when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("strategy_params")), eq(AGENT)))
        .thenReturn(List.of(row));
    assertThat(service.latestDirective(AGENT)).contains("complexity=basic");
  }

  private static Map<String, Object> feedbackRow(long total, long adopted, Double avgRating) {
    Map<String, Object> row = new HashMap<>();
    row.put("total", total);
    row.put("adopted_cnt", adopted);
    row.put("avg_rating", avgRating);
    return row;
  }

  private static Map<String, Object> statusRow(String status, long cnt) {
    Map<String, Object> row = new HashMap<>();
    row.put("status", status);
    row.put("cnt", cnt);
    return row;
  }
}
