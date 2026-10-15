package com.pythontopia.media;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class MediaTtsControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    XfyunTtsService service =
        new XfyunTtsService(new TtsProperties("app", "key", "secret", 100), new ObjectMapper());
    mockMvc = MockMvcBuilders.standaloneSetup(new MediaTtsController(service))
        .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void disabledTtsReturnsServiceUnavailableWithoutStartingUpstreamRequest() throws Exception {
    mockMvc
        .perform(post("/api/media/tts")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"测试配音\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("TTS_DISABLED"));
  }
}
