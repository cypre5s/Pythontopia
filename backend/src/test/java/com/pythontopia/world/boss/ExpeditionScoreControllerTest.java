package com.pythontopia.world.boss;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.world.boss.dto.ExpeditionLeaderboardDto;
import com.pythontopia.world.boss.dto.ExpeditionLeaderboardEntry;
import com.pythontopia.world.boss.dto.ExpeditionScoreSubmitDto;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExpeditionScoreController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class ExpeditionScoreControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockBean ExpeditionScoreService service;
  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

  @BeforeEach
  void authenticateAsStudent() {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void postScoreRecordsAndReturnsLeaderboard() throws Exception {
    ExpeditionLeaderboardDto board =
        new ExpeditionLeaderboardDto(
            List.of(new ExpeditionLeaderboardEntry(1, 7L, "小明", 320, 6, true)), 1, 320, true);
    when(service.leaderboard(7L)).thenReturn(board);

    mockMvc
        .perform(
            post("/api/world/expedition/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ExpeditionScoreSubmitDto("123e4567-e89b-12d3-a456-426614174000"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries[0].displayName").value("小明"))
        .andExpect(jsonPath("$.entries[0].self").value(true))
        .andExpect(jsonPath("$.selfRank").value(1))
        .andExpect(jsonPath("$.inClass").value(true));

    verify(service).record(eq(7L), any(ExpeditionScoreSubmitDto.class));
  }

  @Test
  void getLeaderboardReturnsBoardForCurrentStudent() throws Exception {
    when(service.leaderboard(7L))
        .thenReturn(new ExpeditionLeaderboardDto(List.of(), null, 0, false));

    mockMvc
        .perform(get("/api/world/expedition/leaderboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inClass").value(false))
        .andExpect(jsonPath("$.selfBest").value(0));
  }

  @Test
  void teacherCannotAccessLeaderboard() throws Exception {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                teacher, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));

    mockMvc.perform(get("/api/world/expedition/leaderboard")).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }
}
