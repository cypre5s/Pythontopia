package com.pythontopia.auth.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.Role;
import com.pythontopia.auth.service.AuthService;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

  private final AuthService service = mock(AuthService.class);
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new AuthController(service))
          .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
          .build();

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void logoutRevokesAuthenticatedUsersTokensAndReturnsNoContent() throws Exception {
    CurrentUserPrincipal user = new CurrentUserPrincipal(9L, "stu01", Role.STUDENT, 2);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                user, null, AuthorityUtils.createAuthorityList("ROLE_STUDENT")));

    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());

    verify(service).logout(user);
  }

  @Test
  void rejectsOverlongLoginFieldsAndRefreshTokensBeforeCallingService() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\""
                        + "u".repeat(65)
                        + "\",\"password\":\"P@ssw0rd1\"}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"student\",\"password\":\""
                        + "p".repeat(73)
                        + "\"}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + "t".repeat(2049) + "\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }
}
