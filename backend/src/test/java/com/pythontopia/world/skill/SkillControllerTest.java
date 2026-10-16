package com.pythontopia.world.skill;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.common.web.StudentWriteThrottle;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SkillController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class SkillControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean SkillService service;
  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockBean TutorDailyRequestLimitService requestLimitService;
  @MockBean StudentWriteThrottle studentWriteThrottle;

  @BeforeEach
  void setUp() {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "stu-skill", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
  }

  @Test
  void unlockRejectsWhenAccountBudgetIsExhausted() throws Exception {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    mockMvc
        .perform(post("/api/world/skills/loop_mastery/unlock"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("STUDENT_WRITE_RATE_LIMITED"));

    verify(studentWriteThrottle).tryAcquire(7L, 1);
    verifyNoInteractions(service);
  }

  @Test
  void rejectsTeacherBeforeCheckingWriteBudget() throws Exception {
    authenticateAs(Role.TEACHER);

    mockMvc
        .perform(post("/api/world/skills/loop_mastery/unlock"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(service, studentWriteThrottle);
  }

  private void authenticateAs(Role role) {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "account", role);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }
}
