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
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.world.calendar.dto.WorldEventDto;
import com.pythontopia.world.dto.GameBootstrapResponse;
import com.pythontopia.world.dto.PlayerStateDto;
import com.pythontopia.world.dto.WorldSnapshotDto;
import com.pythontopia.world.save.dto.SaveSlotDto;
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

@WebMvcTest(GameBootstrapController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class GameBootstrapControllerTest {

  @Autowired MockMvc mockMvc;

  @MockBean GameBootstrapService service;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

  @BeforeEach
  void setUp() {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(42L, "stu-bootstrap", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
  }

  @Test
  void bootstrapReturnsFullPayload() throws Exception {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-22T10:00:00Z");
    GameBootstrapResponse response =
        new GameBootstrapResponse(
            new WorldSnapshotDto(
                100L, 7, 1, "monday", "spring", "morning", "SUNNY", 480, now, now),
            new PlayerStateDto(200L, "student-default", "town-beta", 12, 10, "DOWN"),
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new SaveSlotDto(0, "Slot A", true, now, now, null),
            List.<WorldEventDto>of());
    when(service.bootstrap(eq(42L))).thenReturn(response);

    mockMvc
        .perform(post("/api/game/bootstrap"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.world.dayIndex").value(7))
        .andExpect(jsonPath("$.world.weekday").value("monday"))
        .andExpect(jsonPath("$.player.tileX").value(12))
        .andExpect(jsonPath("$.activeSlot.displayName").value("Slot A"));
  }
}
