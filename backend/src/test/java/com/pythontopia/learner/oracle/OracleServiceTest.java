package com.pythontopia.learner.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.learner.mastery.MasteryRepository;
import com.pythontopia.learner.mastery.MasterySnapshot;
import com.pythontopia.learner.reflection.LearnerInsightRepository;
import com.pythontopia.learner.reflection.dto.LearnerInsightDto;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;

/** P5.1：预言家据真实信号预测高风险 KC，绑定 event 证据 + 先修依赖路径，并触发异步预防包。 */
@ExtendWith(MockitoExtension.class)
class OracleServiceTest {

  private static final long USER = 7L;

  @Mock MasteryRepository masteryRepo;
  @Mock LearnerInsightRepository insightRepo;
  @Mock JdbcTemplate jdbc;
  @Mock PreventionPackService preventionPackService;
  private OracleService service;

  @BeforeEach
  void setUp() {
    service = new OracleService(masteryRepo, insightRepo, jdbc, preventionPackService);
  }

  @Test
  @DisplayName("无掌握度信号 → 空预测且不触发预防包")
  void emptyWhenNoSignals() {
    when(masteryRepo.findAll(anyLong())).thenReturn(List.of());
    assertThat(service.predict(USER)).isEmpty();
    verify(preventionPackService, never()).pregenerate(anyLong(), any());
  }

  @Test
  @DisplayName("高掌握度 → 不预测、不触发（成本守恒）")
  void skipsHighMasteryNoTrigger() {
    when(masteryRepo.findAll(anyLong()))
        .thenReturn(
            List.of(
                new MasterySnapshot(
                    1L, USER, "PY43-KC-002", new BigDecimal("0.90"), new BigDecimal("0.80"),
                    null, null)));
    when(insightRepo.findByUser(anyLong(), anyInt())).thenReturn(List.of());
    when(jdbc.queryForList(anyString())).thenReturn(List.of()); // 先修边
    when(jdbc.queryForList(anyString(), eq(USER))).thenReturn(List.of()); // 失败计数

    assertThat(service.predict(USER)).isEmpty();
    verify(preventionPackService, never()).pregenerate(anyLong(), any());
  }

  @Test
  @DisplayName("先修弱 + 高频错 → 预测高风险 KC + 触发预防包 + 证据/依赖路径可溯源")
  void predictionsSurviveWhenPreventionQueueIsFull() {
    // KC-016 掌握度低 + 置信度低；其先修 KC-015 无掌握记录（薄弱先修链）。
    when(masteryRepo.findAll(anyLong()))
        .thenReturn(
            List.of(
                new MasterySnapshot(
                    1L, USER, "PY43-KC-016", new BigDecimal("0.25"), new BigDecimal("0.30"),
                    null, null)));
    when(insightRepo.findByUser(anyLong(), anyInt()))
        .thenReturn(
            List.of(
                new LearnerInsightDto(
                    9L, "可变对象别名反复出错", "error_pattern", List.of("PY43-KC-016"),
                    List.of(9L), 0.8, "GRAPH", "2026-06-08")));
    when(jdbc.queryForList(anyString())).thenReturn(List.of(edgeRow())); // KC-016 → {KC-015}
    when(jdbc.queryForList(anyString(), eq(USER))).thenReturn(List.of(failureRow())); // 4 次失败
    when(jdbc.queryForList(anyString(), eq(Long.class), eq(USER), anyString()))
        .thenReturn(List.of(201L, 202L)); // 真实 event id
    doThrow(new TaskRejectedException("queue full"))
        .when(preventionPackService)
        .pregenerate(eq(USER), any());

    List<OracleService.RiskPrediction> predictions = service.predict(USER);

    assertThat(predictions).hasSize(1);
    OracleService.RiskPrediction top = predictions.get(0);
    assertThat(top.kcCode()).isEqualTo("PY43-KC-016");
    assertThat(top.risk()).isGreaterThan(0.6);
    // 可溯源：真实 event id 非空 + 先修依赖路径绑定（非扁平集合）。
    assertThat(top.evidenceEventIds()).containsExactly(201L, 202L);
    assertThat(top.dependencyPath()).contains("PY43-KC-015");
    assertThat(top.reason()).contains("先修薄弱链");
    // 命中阈值 → 异步预防包触发一次。
    verify(preventionPackService, times(1)).pregenerate(eq(USER), eq(top));
  }

  private static Map<String, Object> edgeRow() {
    Map<String, Object> row = new HashMap<>();
    row.put("kc_code", "PY43-KC-016");
    row.put("prereq", "{PY43-KC-015}");
    return row;
  }

  private static Map<String, Object> failureRow() {
    Map<String, Object> row = new HashMap<>();
    row.put("kc", "PY43-KC-016");
    row.put("cnt", 4L);
    return row;
  }
}
