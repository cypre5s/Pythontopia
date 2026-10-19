package com.pythontopia.learner.profile;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtService;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.learner.profile.dto.DimensionDelta;
import com.pythontopia.learner.profile.dto.ProfileDimensionProjectionOutboxDto;
import com.pythontopia.learner.profile.dto.ProfileDimensionProjectionOutboxPageDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    authenticate(Role.ADMIN, 900L);
    when(service.repairAndReplay(
            41L,
            900L,
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
            900L,
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
  void nonAdminCannotReadFailedOutbox() throws Exception {
    authenticate(Role.STUDENT);

    mockMvc
        .perform(get("/api/admin/profile-projection-outbox/failed"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void nonAdminCannotInspectSingleOutbox() throws Exception {
    authenticate(Role.STUDENT);

    mockMvc
        .perform(get("/api/admin/profile-projection-outbox/41"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void adminCanPageFailedOutboxUsingSafeMetadata() throws Exception {
    authenticate(Role.ADMIN, 900L);
    when(service.pageFailed(0, 50))
        .thenReturn(
            new ProfileDimensionProjectionOutboxPageDto(
                List.of(sampleOutbox("FAILED")), 0, 50, 1, 1));

    mockMvc
        .perform(get("/api/admin/profile-projection-outbox/failed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(41))
        .andExpect(jsonPath("$.content[0].userId").value(77))
        .andExpect(jsonPath("$.content[0].status").value("FAILED"))
        .andExpect(jsonPath("$.content[0].delta").doesNotExist())
        .andExpect(jsonPath("$.content[0].idempotencyKey").doesNotExist())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));

    verify(service).pageFailed(0, 50);
  }

  @Test
  void adminCanInspectOutboxStatusAndReplayAttribution() throws Exception {
    authenticate(Role.ADMIN, 900L);
    when(service.findForAdmin(41L)).thenReturn(Optional.of(sampleOutbox("APPLIED")));

    mockMvc
        .perform(get("/api/admin/profile-projection-outbox/41"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPLIED"))
        .andExpect(jsonPath("$.manualReplayCount").value(1))
        .andExpect(jsonPath("$.lastReplayedBy").value(900))
        .andExpect(jsonPath("$.lastReplayedAt").isNotEmpty())
        .andExpect(jsonPath("$.delta").doesNotExist());

    verify(service).findForAdmin(41L);
  }

  @Test
  void missingOutboxReturnsNotFound() throws Exception {
    authenticate(Role.ADMIN, 900L);
    when(service.findForAdmin(404L)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/admin/profile-projection-outbox/404"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROFILE_PROJECTION_OUTBOX_NOT_FOUND"));
  }

  @Test
  void invalidFailedOutboxPageIsRejected() throws Exception {
    authenticate(Role.ADMIN, 900L);

    mockMvc
        .perform(get("/api/admin/profile-projection-outbox/failed?page=-1&size=101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

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
                41L,
                900L,
                "knowledge_base",
                new DimensionDelta(0.2, 1, null, null, "SUBMISSION")))
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
    authenticate(role, 1L);
  }

  private void authenticate(Role role, long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CurrentUserPrincipal(userId, "operator", role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }

  private ProfileDimensionProjectionOutboxDto sampleOutbox(String status) {
    OffsetDateTime timestamp = OffsetDateTime.parse("2026-10-19T06:00:00Z");
    return new ProfileDimensionProjectionOutboxDto(
        41,
        77,
        "knowledge_base",
        status,
        status.equals("FAILED") ? 8 : 1,
        status.equals("FAILED") ? "DataAccessResourceFailureException" : null,
        1,
        900L,
        timestamp,
        timestamp.minusHours(1),
        status.equals("FAILED") ? timestamp : null,
        timestamp,
        status.equals("APPLIED") ? timestamp : null);
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
