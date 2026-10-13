package com.pythontopia.npc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NpcRouteKeySecurityTest {

  private final NpcDialogService dialogService = mock(NpcDialogService.class);
  private final NpcGreetingService greetingService = mock(NpcGreetingService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    mockMvc = MockMvcBuilders.standaloneSetup(
            new NpcDialogController(dialogService), new NpcGreetingController(greetingService))
        .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void oversizedKeysDoNotReachNpcServices() throws Exception {
    String oversizedKey = "a".repeat(256);

    mockMvc
        .perform(
            post("/api/npc/{npcKey}/dialog", oversizedKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/npc/{npcKey}/greeting", oversizedKey))
        .andExpect(status().isNotFound());

    verifyNoInteractions(dialogService, greetingService);
  }
}
