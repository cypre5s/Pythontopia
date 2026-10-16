package com.pythontopia.world.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import com.pythontopia.world.dto.PlayerStateDto;
import com.pythontopia.world.dto.PlayerStatePatch;
import com.pythontopia.world.service.WorldService;
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

@WebMvcTest(WorldController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  GlobalExceptionHandler.class,
  WebConfig.class,
  CurrentUserArgumentResolver.class,
  StudentWriteThrottle.class
})
class WorldControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean WorldService service;
  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockBean TutorDailyRequestLimitService requestLimitService;

  @BeforeEach
  void authenticateAsStudent() {
    var principal = new CurrentUserPrincipal(7L, "student-world", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    when(service.patchPlayer(eq(principal), any(PlayerStatePatch.class)))
        .thenReturn(new PlayerStateDto(1L, "student_default", "town_hub", 1, 1, "RIGHT"));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void limitsRapidPlayerPositionWritesBeforeUpdatingTheWorldState() throws Exception {
    for (int request = 0; request < 11; request++) {
      var result =
          mockMvc.perform(
              patch("/api/world/player")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"tileX\":1,\"tileY\":1,\"direction\":\"RIGHT\"}"));
      if (request < 10) {
        result.andExpect(status().isOk());
      } else {
        result.andExpect(status().isTooManyRequests());
      }
    }

    verify(service, times(10))
        .patchPlayer(any(CurrentUserPrincipal.class), any(PlayerStatePatch.class));
  }
}
