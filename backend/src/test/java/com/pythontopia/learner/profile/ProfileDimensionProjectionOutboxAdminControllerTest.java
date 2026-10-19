package com.pythontopia.learner.profile;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.security.JwtService;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfileDimensionProjectionOutboxAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  GlobalExceptionHandler.class,
  ProfileDimensionProjectionOutboxAdminControllerTest.MethodSecurity.class
})
class ProfileDimensionProjectionOutboxAdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private JwtService jwtService;
  @MockBean private UserRepository userRepository;
  @MockBean private ProfileDimensionProjectionOutboxService service;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void adminCanReplaceFailedPayloadAndOnlyReceivesAcceptedState() throws Exception {
    authenticate(Role.ADMIN);
    when(service.repairAndReplay(
            41L,
            "knowledge_base",
            new DimensionDelta(
                0.2,
                1,
                List.of("loop"),
                Map.of("weak_kcs", List.of("KC-1")),
                "SUBMISSION")))
        .thenReturn(true);

    mockMvc
        .perform(
            put("/api/admin/profile-projection-outbox/41")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dimensionKey":"knowledge_base","delta":{"scoreDelta":0.2,"evidenceIncrement":1,"tagsAdd":["loop"],"metadata":{"weak_kcs":["KC-1"]},"source":"SUBMISSION"}}
                    """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.outboxId").value(41))
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.userId").doesNotExist())
        .andExpect(jsonPath("$.delta").doesNotExist());

    verify(service)
        .repairAndReplay(
            41L,
            "knowledge_base",
            new DimensionDelta(
                0.2,
                1,
                List.of("loop"),
                Map.of("weak_kcs", List.of("KC-1")),
                "SUBMISSION"));
  }

  @Test
  void nonAdminCannotRepairOutbox() throws Exception {
    authenticate(Role.STUDENT);

    mockMvc
        .perform(
            put("/api/admin/profile-projection-outbox/41")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(service);
  }

  @Test
  void invalidDimensionDeltaAndSourceAreRejectedBeforeRepair() throws Exception {
    authenticate(Role.ADMIN);

    mockMvc
        .perform(
            put("/api/admin/profile-projection-outbox/41")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dimensionKey":"private_data","delta":{"scoreDelta":2.0,"evidenceIncrement":-1,"tagsAdd":[""],"source":"SECRET"}}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(service);
  }

  @Test
  void nonFailedOrMissingOutboxReturnsConflict() throws Exception {
    authenticate(Role.ADMIN);
    when(
            service.repairAndReplay(
                41L, "knowledge_base", new DimensionDelta(0.2, 1, null, null, "SUBMISSION")))
        .thenReturn(false);

    mockMvc
        .perform(
            put("/api/admin/profile-projection-outbox/41")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROFILE_PROJECTION_NOT_FAILED"));
  }

  private void authenticate(Role role) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "operator", null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }

  private String validRequest() {
    return """
        {"dimensionKey":"knowledge_base","delta":{"scoreDelta":0.2,"evidenceIncrement":1,"source":"SUBMISSION"}}
        """;
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class MethodSecurity {}
}
