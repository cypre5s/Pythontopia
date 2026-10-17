package com.pythontopia.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Drives {@link BilibiliInsightController} with a mocked {@link BilibiliInsightService}, covering the
 * camelCase JSON contract the frontend consumes ({@code keyPoints[].tSec}/{@code basedOn}), request
 * binding ({@code bvid}/{@code question}/{@code kcCodes}), validation (missing {@code bvid} → 400) and
 * the {@code VIDEO_INSIGHT_UNAVAILABLE} error passthrough. Mirrors {@link BilibiliVideoControllerTest}.
 *
 * <p>Filters are disabled ({@code addFilters = false}); the unauthenticated-401 path is enforced by
 * {@code SecurityConfig.anyRequest().authenticated()} and verified by the integration suite.
 */
@WebMvcTest(BilibiliInsightController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BilibiliInsightControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean BilibiliInsightService service;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

  @Test
  void returnsMappedInsightWithCamelCaseFields() throws Exception {
    when(service.insight(any(BilibiliInsightRequest.class)))
        .thenReturn(
            new BilibiliInsightResponse(
                "BV1xx411c7XD",
                "前 2 分钟讲 for 循环语法，建议先看 00:30 这段。",
                List.of(
                    new BilibiliKeyPointDto(30, "for 循环语法", "ai_conclusion"),
                    new BilibiliKeyPointDto(95, "range 用法", "pages")),
                "ai_conclusion",
                false));

    mockMvc
        .perform(
            post("/api/videos/bilibili/insight")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"bvid\":\"BV1xx411c7XD\",\"question\":\"for 循环怎么用\",\"kcCodes\":[\"PY43-KC-030\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bvid").value("BV1xx411c7XD"))
        .andExpect(jsonPath("$.overview").value("前 2 分钟讲 for 循环语法，建议先看 00:30 这段。"))
        .andExpect(jsonPath("$.keyPoints.length()").value(2))
        .andExpect(jsonPath("$.keyPoints[0].tSec").value(30))
        .andExpect(jsonPath("$.keyPoints[0].label").value("for 循环语法"))
        .andExpect(jsonPath("$.keyPoints[0].source").value("ai_conclusion"))
        .andExpect(jsonPath("$.keyPoints[1].tSec").value(95))
        .andExpect(jsonPath("$.keyPoints[1].source").value("pages"))
        .andExpect(jsonPath("$.basedOn").value("ai_conclusion"))
        .andExpect(jsonPath("$.degraded").value(false));
  }

  @Test
  void bindsRequestBodyToService() throws Exception {
    when(service.insight(any(BilibiliInsightRequest.class)))
        .thenReturn(new BilibiliInsightResponse("BV1", "", List.of(), "metadata_only", false));

    mockMvc
        .perform(
            post("/api/videos/bilibili/insight")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"bvid\":\"BV1\",\"question\":\"列表怎么遍历\",\"kcCodes\":[\"PY43-KC-040\",\"PY43-KC-041\"]}"))
        .andExpect(status().isOk());

    ArgumentCaptor<BilibiliInsightRequest> captor =
        ArgumentCaptor.forClass(BilibiliInsightRequest.class);
    verify(service).insight(captor.capture());
    assertThat(captor.getValue().bvid()).isEqualTo("BV1");
    assertThat(captor.getValue().question()).isEqualTo("列表怎么遍历");
    assertThat(captor.getValue().kcCodes()).containsExactly("PY43-KC-040", "PY43-KC-041");
  }

  @Test
  void missingBvidReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/videos/bilibili/insight")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"for 循环怎么用\",\"kcCodes\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void oversizedKcCodeReturns400BeforeCallingGraph() throws Exception {
    String oversizedKc = "K".repeat(33);

    mockMvc
        .perform(
            post("/api/videos/bilibili/insight")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bvid\":\"BV1\",\"kcCodes\":[\"" + oversizedKc + "\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    verify(service, never()).insight(any(BilibiliInsightRequest.class));
  }

  @Test
  void graphUnavailableReturnsStandardApiError() throws Exception {
    when(service.insight(any(BilibiliInsightRequest.class)))
        .thenThrow(
            new BusinessException(
                HttpStatus.BAD_GATEWAY, "VIDEO_INSIGHT_UNAVAILABLE", "B站视频概述服务不可用"));

    mockMvc
        .perform(
            post("/api/videos/bilibili/insight")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bvid\":\"BV1xx411c7XD\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("VIDEO_INSIGHT_UNAVAILABLE"));
  }
}
