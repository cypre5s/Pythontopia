package com.pythontopia.world.save;

import static org.mockito.Mockito.times;
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
import com.pythontopia.common.web.ApiRequestBodyLimitFilter;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(SaveSlotController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class SaveSlotControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  @Autowired ObjectMapper objectMapper;

  MockMvc mockMvc;

  @MockBean SaveSlotService service;
  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockBean TutorDailyRequestLimitService requestLimitService;
  @MockBean StudentWriteThrottle studentWriteThrottle;

  @BeforeEach
  void setUp() {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "stu-save", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilters(new ApiRequestBodyLimitFilter(objectMapper))
            .build();
  }

  @Test
  void writeEndpointsRejectWhenAccountBudgetIsExhausted() throws Exception {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);
    when(studentWriteThrottle.tryAcquire(7L, 10)).thenReturn(false);

    mockMvc
        .perform(
            post("/api/world/saves/0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"slot\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("STUDENT_WRITE_RATE_LIMITED"));
    mockMvc
        .perform(post("/api/world/saves/0/activate"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("STUDENT_WRITE_RATE_LIMITED"));
    mockMvc
        .perform(
            post("/api/world/saves/0/snapshot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("STUDENT_WRITE_RATE_LIMITED"));

    verify(studentWriteThrottle, times(2)).tryAcquire(7L, 1);
    verify(studentWriteThrottle).tryAcquire(7L, 10);
    verifyNoInteractions(service);
  }

  @Test
  void rejectsTeacherForEverySaveRoute() throws Exception {
    authenticateAs(Role.TEACHER);

    mockMvc.perform(get("/api/world/saves")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/world/saves/0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"slot\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/world/saves/0/activate")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/world/saves/0/snapshot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service, studentWriteThrottle);
  }

  @Test
  void rejectsOversizedSnapshotWithoutContentLengthBeforeServiceInvocation() throws Exception {
    String snapshot = "x".repeat(256 * 1024 + 1);
    RequestBuilder requestWithoutContentLength =
        servletContext -> {
          MockHttpServletRequest request =
              new MockHttpServletRequest(servletContext) {
                @Override
                public int getContentLength() {
                  return -1;
                }

                @Override
                public long getContentLengthLong() {
                  return -1;
                }
              };
          request.setMethod("POST");
          request.setRequestURI("/api/world/saves/0/snapshot");
          request.setServletPath("/api/world/saves/0/snapshot");
          request.setContentType(MediaType.APPLICATION_JSON_VALUE);
          request.setContent(snapshot.getBytes(StandardCharsets.UTF_8));
          return request;
        };

    mockMvc
        .perform(requestWithoutContentLength)
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("SAVE_SLOT_SNAPSHOT_TOO_LARGE"));

    verifyNoInteractions(service, studentWriteThrottle);
  }

  @Test
  void rejectsOversizedSnapshotFromDeclaredContentLengthBeforeServiceInvocation() throws Exception {
    when(studentWriteThrottle.tryAcquire(7L, 10)).thenReturn(true);
    String snapshot = "中".repeat((ApiRequestBodyLimitFilter.MAX_SNAPSHOT_BYTES / 3) + 1);
    int contentLength = snapshot.getBytes(StandardCharsets.UTF_8).length;

    mockMvc
        .perform(
            post("/api/world/saves/0/snapshot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(snapshot)
                .header("Content-Length", contentLength))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("SAVE_SLOT_SNAPSHOT_TOO_LARGE"));

    verifyNoInteractions(service, studentWriteThrottle);
  }

  @Test
  void acceptsSnapshotAtByteLimitAndPreservesTheBody() throws Exception {
    when(studentWriteThrottle.tryAcquire(7L, 10)).thenReturn(true);
    String snapshot = "x".repeat(ApiRequestBodyLimitFilter.MAX_SNAPSHOT_BYTES);

    mockMvc
        .perform(
            post("/api/world/saves/0/snapshot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(snapshot))
        .andExpect(status().isOk());

    verify(service).saveSnapshot(7L, 0, snapshot);
  }

  private void authenticateAs(Role role) {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "account", role);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }
}
