package com.pythontopia.video;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Drives {@link BilibiliVideoController} with a mocked {@link BilibiliVideoService}, covering the
 * camelCase JSON contract the frontend consumes ({@code coverUrl}/{@code durationSec}), the default
 * {@code limit=8}, validation (missing {@code keyword} → 400) and the {@code VIDEO_SEARCH_UNAVAILABLE}
 * error mapping. Mirrors {@link com.pythontopia.coursepack.CourseVideoControllerTest}.
 *
 * <p>Filters are disabled ({@code addFilters = false}); the unauthenticated-401 path is enforced by
 * {@code SecurityConfig.anyRequest().authenticated()} and verified by the integration suite.
 */
@WebMvcTest(BilibiliVideoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BilibiliVideoControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean BilibiliVideoService service;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

  @Test
  void returnsMappedVideosWithCamelCaseFields() throws Exception {
    when(service.search("for循环 Python", 8))
        .thenReturn(
            List.of(
                new BilibiliVideoDto(
                    "BV1xx411c7XD",
                    "for 循环 & 遍历",
                    "UP主小明",
                    "讲解 for 循环与 range 的用法",
                    "1 分钟速览 for 循环与 range 的写法",
                    "https://i2.hdslb.com/bfs/archive/abc.jpg",
                    754,
                    12345L,
                    678L,
                    1600000000L,
                    "https://www.bilibili.com/video/BV1xx411c7XD")));

    mockMvc
        .perform(get("/api/videos/bilibili").param("keyword", "for循环 Python"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].bvid").value("BV1xx411c7XD"))
        .andExpect(jsonPath("$[0].title").value("for 循环 & 遍历"))
        .andExpect(jsonPath("$[0].author").value("UP主小明"))
        .andExpect(jsonPath("$[0].description").value("讲解 for 循环与 range 的用法"))
        .andExpect(jsonPath("$[0].aiSummary").value("1 分钟速览 for 循环与 range 的写法"))
        .andExpect(jsonPath("$[0].coverUrl").value("https://i2.hdslb.com/bfs/archive/abc.jpg"))
        .andExpect(jsonPath("$[0].durationSec").value(754))
        .andExpect(jsonPath("$[0].play").value(12345))
        .andExpect(jsonPath("$[0].danmaku").value(678))
        .andExpect(jsonPath("$[0].pubdate").value(1600000000))
        .andExpect(jsonPath("$[0].url").value("https://www.bilibili.com/video/BV1xx411c7XD"));
  }

  @Test
  void defaultsLimitTo8WhenOmitted() throws Exception {
    when(service.search(eq("列表"), anyInt())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/videos/bilibili").param("keyword", "列表"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    verify(service).search("列表", 8);
  }

  @Test
  void honorsExplicitLimit() throws Exception {
    when(service.search(eq("列表"), anyInt())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/videos/bilibili").param("keyword", "列表").param("limit", "3"))
        .andExpect(status().isOk());

    verify(service).search("列表", 3);
  }

  @Test
  void rejectsLimitsOutsideOneToEightBeforeCallingGraph() throws Exception {
    mockMvc
        .perform(get("/api/videos/bilibili").param("keyword", "列表").param("limit", "9"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get("/api/videos/bilibili").param("keyword", "列表").param("limit", "0"))
        .andExpect(status().isBadRequest());

    verify(service, never()).search(eq("列表"), anyInt());
  }

  @Test
  void missingKeywordReturns400() throws Exception {
    mockMvc.perform(get("/api/videos/bilibili")).andExpect(status().isBadRequest());
  }

  @Test
  void graphUnavailableReturnsStandardApiError() throws Exception {
    when(service.search(eq("x"), anyInt()))
        .thenThrow(
            new BusinessException(
                HttpStatus.BAD_GATEWAY, "VIDEO_SEARCH_UNAVAILABLE", "B站视频检索服务不可用"));

    mockMvc
        .perform(get("/api/videos/bilibili").param("keyword", "x"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("VIDEO_SEARCH_UNAVAILABLE"));
  }
}
