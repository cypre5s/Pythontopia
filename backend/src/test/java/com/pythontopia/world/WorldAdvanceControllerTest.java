package com.pythontopia.world;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.world.dto.WorldAdvanceResponse;
import com.pythontopia.world.dto.WorldSnapshotDto;
import java.time.OffsetDateTime;
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

@WebMvcTest(WorldAdvanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class WorldAdvanceControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean WorldAdvanceService service;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockBean TutorDailyRequestLimitService requestLimitService;

  @BeforeEach
  void setUp() {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(9L, "stu-sleep", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
  }

  @Test
  void sleepAdvancesDayIndex() throws Exception {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-22T08:00:00Z");
    WorldSnapshotDto snapshot =
        new WorldSnapshotDto(
            100L, 8, 1, "tuesday", "spring", "morning", "RAIN", 480, now, now);
    when(service.sleep(eq(9L))).thenReturn(new WorldAdvanceResponse(snapshot, List.of()));

    mockMvc
        .perform(post("/api/world/sleep"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.world.dayIndex").value(8))
        .andExpect(jsonPath("$.world.weekday").value("tuesday"))
        .andExpect(jsonPath("$.world.weather").value("RAIN"))
        .andExpect(jsonPath("$.newEvents.length()").value(0));
  }
}
