package com.pythontopia.learner.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.workshop.dto.ResourceCreateRequest;
import com.pythontopia.workshop.dto.ResourceCreateResponse;
import com.pythontopia.workshop.service.ResourceWorkshopService;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/** P5.1：预防包预生成走真资源生成链并落库（带可溯源证据）；幂等去重；后台失败不抛。 */
@ExtendWith(MockitoExtension.class)
class PreventionPackServiceTest {

  private static final long USER = 7L;

  @Mock ResourceWorkshopService resourceWorkshopService;
  @Mock JdbcTemplate jdbcTemplate;
  @Mock TutorDailyRequestLimitService requestLimit;
  private PreventionPackService service;

  @BeforeEach
  void setUp() {
    service =
        new PreventionPackService(
            resourceWorkshopService, jdbcTemplate, new ObjectMapper(), requestLimit);
  }

  private OracleService.RiskPrediction prediction() {
    return new OracleService.RiskPrediction(
        "PY43-KC-016", 0.92, List.of(201L, 202L), "掌握度很低+先修薄弱链：PY43-KC-015",
        List.of("PY43-KC-015"));
  }

  @Test
  @DisplayName("无近期预防包 → 走资源生成链并落 prevention_pack（证据含 event id + 依赖路径）")
  void generatesAndPersistsWithEvidence() {
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER), anyString(), anyInt()))
        .thenReturn(0);
    when(resourceWorkshopService.create(eq(USER), any(ResourceCreateRequest.class)))
        .thenReturn(new ResourceCreateResponse(11L, 55L, "SUCCESS"));

    service.pregenerate(USER, prediction());

    ArgumentCaptor<String> evidenceCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate)
        .update(
            anyString(),
            eq(USER),
            eq("PY43-KC-016"),
            eq(55L),
            evidenceCaptor.capture());
    String evidence = evidenceCaptor.getValue();
    assertThat(evidence).contains("201").contains("202").contains("PY43-KC-015");
    verify(requestLimit).reserve(USER);
  }

  @Test
  @DisplayName("近期已有预防包 → 跳过（不生成、不落库），成本守恒")
  void skipsWhenRecentPackExists() {
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER), anyString(), anyInt()))
        .thenReturn(1);

    service.pregenerate(USER, prediction());

    verify(resourceWorkshopService, never()).create(anyLong(), any());
    verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
    verify(requestLimit, never()).reserve(USER);
  }

  @Test
  @DisplayName("账号额度耗尽 → 不调用资源生成链")
  void dailyLimitSkipsGeneration() {
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER), anyString(), anyInt()))
        .thenReturn(0);
    org.mockito.Mockito.doThrow(new RuntimeException("daily limit"))
        .when(requestLimit)
        .reserve(USER);

    assertThatCode(() -> service.pregenerate(USER, prediction())).doesNotThrowAnyException();

    verify(resourceWorkshopService, never()).create(anyLong(), any());
    verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("资源生成失败 → 后台 best-effort 不抛出、不落库")
  void swallowsGenerationFailure() {
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(USER), anyString(), anyInt()))
        .thenReturn(0);
    when(resourceWorkshopService.create(eq(USER), any(ResourceCreateRequest.class)))
        .thenThrow(new RuntimeException("graph down"));

    assertThatCode(() -> service.pregenerate(USER, prediction())).doesNotThrowAnyException();
    verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
    verify(resourceWorkshopService, times(1)).create(eq(USER), any());
  }
}
