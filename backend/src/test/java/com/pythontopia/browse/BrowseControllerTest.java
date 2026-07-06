package com.pythontopia.browse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.auth.security.Role;
import com.pythontopia.browse.controller.BrowseController;
import com.pythontopia.browse.dto.BrowseCitationDto;
import com.pythontopia.browse.dto.BrowseRunCreateRequest;
import com.pythontopia.browse.dto.BrowseRunDetailDto;
import com.pythontopia.browse.dto.BrowseRunDto;
import com.pythontopia.browse.dto.BrowseStepDto;
import com.pythontopia.browse.entity.BrowseRunStatus;
import com.pythontopia.browse.service.BrowseRunService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.common.web.WebConfig;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link BrowseController} MockMvc 单测（零外网，service mock）。覆盖契约矩阵：①创建 200 ②detail 含
 * steps/citations ③非 student 403 ④graph 失败→统一 ApiError 不 500；外加请求校验（空 goal / 越界
 * max_steps → 400）与明细 404。
 *
 * <p>{@code addFilters=false}：未认证 401 由 SecurityConfig 在集成层把关；这里以 SecurityContext 直接
 * 注入登录态主体驱动 {@code @CurrentUser} 解析。
 */
@WebMvcTest(BrowseController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class BrowseControllerTest {

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @MockBean BrowseRunService service;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

  @BeforeEach
  void authenticateAsStudent() {
    authenticate(new CurrentUserPrincipal(1L, "stu", Role.STUDENT), "ROLE_STUDENT");
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(CurrentUserPrincipal principal, String authority) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority))));
  }

  private BrowseRunDto runDto() {
    return new BrowseRunDto(
        42L, "去 python 官网查 sorted", BrowseRunStatus.SUCCESS, "sorted 稳定", false, null,
        OffsetDateTime.parse("2026-06-17T10:00:00Z"));
  }

  // ① 创建 → 200，返回会话头（含 status 枚举序列化为字符串）。
  @Test
  void createReturns200WithRunDto() throws Exception {
    when(service.create(eq(1L), any(BrowseRunCreateRequest.class))).thenReturn(runDto());

    mockMvc
        .perform(
            post("/api/browse/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BrowseRunCreateRequest("去 python 官网查 sorted", null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(42))
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.truncated").value(false));
  }

  // ③ 非 student → 403（且不触达 service）。
  @Test
  void createRejectsNonStudentWith403() throws Exception {
    authenticate(new CurrentUserPrincipal(2L, "teacher", Role.TEACHER), "ROLE_TEACHER");

    mockMvc
        .perform(
            post("/api/browse/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BrowseRunCreateRequest("goal", null))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(service);
  }

  // ② detail → 200，含 steps[] 与 citations[]（外部标注）。
  @Test
  void detailReturns200WithStepsAndCitations() throws Exception {
    BrowseRunDetailDto detail =
        new BrowseRunDetailDto(
            42L, "goal", BrowseRunStatus.SUCCESS, "summary", false, null,
            OffsetDateTime.parse("2026-06-17T10:00:00Z"),
            List.of(new BrowseStepDto(0, "navigate", "https://docs.python.org/3/", "打开")),
            List.of(BrowseCitationDto.external("https://docs.python.org/3/")));
    when(service.detail(1L, 42L)).thenReturn(detail);

    mockMvc
        .perform(get("/api/browse/runs/42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps.length()").value(1))
        .andExpect(jsonPath("$.steps[0].action").value("navigate"))
        .andExpect(jsonPath("$.steps[0].url").value("https://docs.python.org/3/"))
        .andExpect(jsonPath("$.citations.length()").value(1))
        .andExpect(jsonPath("$.citations[0].url").value("https://docs.python.org/3/"))
        .andExpect(jsonPath("$.citations[0].label").value("外部内容，非课件"));
  }

  @Test
  void detailRejectsNonStudentWith403() throws Exception {
    authenticate(new CurrentUserPrincipal(2L, "teacher", Role.TEACHER), "ROLE_TEACHER");

    mockMvc.perform(get("/api/browse/runs/42")).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  // ④ graph 失败 → 统一 ApiError(502)，绝不 500。
  @Test
  void createGraphFailureReturnsUnifiedBadGatewayNot500() throws Exception {
    when(service.create(eq(1L), any(BrowseRunCreateRequest.class)))
        .thenThrow(
            new BusinessException(HttpStatus.BAD_GATEWAY, "BROWSE_GRAPH_UNAVAILABLE", "联网查证服务暂不可用"));

    mockMvc
        .perform(
            post("/api/browse/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BrowseRunCreateRequest("goal", null))))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("BROWSE_GRAPH_UNAVAILABLE"));
  }

  // 校验：空 goal → 400。
  @Test
  void createRejectsBlankGoalWith400() throws Exception {
    mockMvc
        .perform(
            post("/api/browse/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BrowseRunCreateRequest("   ", null))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  // 校验：越界 max_steps → 400（有界红线）。
  @Test
  void createRejectsOutOfRangeMaxStepsWith400() throws Exception {
    mockMvc
        .perform(
            post("/api/browse/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BrowseRunCreateRequest("goal", 99))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  // 明细不存在/越权 → 404（统一 ApiError）。
  @Test
  void detailNotFoundReturns404() throws Exception {
    when(service.detail(anyLong(), anyLong()))
        .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "BROWSE_RUN_NOT_FOUND", "联网查证会话不存在"));

    mockMvc
        .perform(get("/api/browse/runs/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BROWSE_RUN_NOT_FOUND"));
  }
}
