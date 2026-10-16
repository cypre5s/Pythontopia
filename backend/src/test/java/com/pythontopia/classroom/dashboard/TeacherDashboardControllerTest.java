package com.pythontopia.classroom.dashboard;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.auth.security.Role;
import com.pythontopia.classroom.dashboard.controller.TeacherDashboardController;
import com.pythontopia.classroom.dashboard.service.ClassroomDashboardService;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TeacherDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class TeacherDashboardControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean ClassroomDashboardService service;
  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockBean TutorDailyRequestLimitService requestLimitService;

  private final CurrentUserPrincipal teacher =
      new CurrentUserPrincipal(42L, "teacher-42", Role.TEACHER);

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                teacher, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
  }

  @Test
  void heatmapRateLimitIsReturnedAsTooManyRequests() throws Exception {
    when(service.kcHeatmap(teacher, 9L))
        .thenThrow(
            new BusinessException(
                HttpStatus.TOO_MANY_REQUESTS,
                "TEACHER_DASHBOARD_RATE_LIMITED",
                "看板刷新过于频繁，请稍后再试"));

    mockMvc
        .perform(get("/api/teacher/classrooms/9/dashboard/kc-heatmap"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TEACHER_DASHBOARD_RATE_LIMITED"));

    verify(service).kcHeatmap(teacher, 9L);
  }
}
