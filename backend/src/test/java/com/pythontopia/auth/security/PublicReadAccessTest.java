package com.pythontopia.auth.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.domain.UserAccount;
import com.pythontopia.auth.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(PublicReadAccessTest.PublicRouteController.class)
@Import({
  SecurityConfig.class,
  JwtAuthenticationFilter.class,
  PublicReadAccessTest.PublicRouteController.class
})
@TestPropertySource(
    properties = {
      "pythontopia.services.graph-url=http://localhost:18091",
      "pythontopia.security.jwt.secret=public-read-access-test-signing-secret-32-bytes",
      "pythontopia.security.jwt.ttl-minutes=720",
      "pythontopia.security.teacher-invite-code=test-teacher-invite-code-32"
    })
class PublicReadAccessTest {

  private static final int MAX_LOOP_RUNTIME_REQUEST_BYTES = 3 * 1024 * 1024;
  private static final int MAX_LEARNING_EVENT_REQUEST_BYTES = 16 * 1024;
  private static final int MAX_LEARNING_EVENT_BATCH_REQUEST_BYTES = 512 * 1024;
  private static final int MAX_AUTH_REQUEST_BYTES = 4 * 1024;

  @Autowired private MockMvc mockMvc;

  @MockBean private JwtService jwtService;
  @MockBean private UserRepository userRepository;

  @Test
  void publicRoutesAllowOnlyDocumentedAnonymousMethods() throws Exception {
    mockMvc.perform(post("/api/auth/login")).andExpect(status().isOk());
    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/course/security-test")).andExpect(status().isOk());
    mockMvc.perform(head("/api/course/security-test")).andExpect(status().isOk());
    mockMvc.perform(post("/api/course/security-test")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/media/assets/1")).andExpect(status().isOk());
    mockMvc.perform(head("/api/media/assets/1")).andExpect(status().isOk());
    mockMvc.perform(post("/api/media/assets/1")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/downloads/security-test")).andExpect(status().isOk());
    mockMvc.perform(post("/api/downloads/security-test/hit")).andExpect(status().isOk());
    mockMvc.perform(post("/api/downloads/security-test")).andExpect(status().isUnauthorized());
  }

  @Test
  void anonymousAuthRoutesRejectOversizedBodiesBeforeMvcBinding() throws Exception {
    for (String route : List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/register")) {
      mockMvc
          .perform(
              post(route)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("x".repeat(MAX_AUTH_REQUEST_BYTES + 1)))
          .andExpect(status().isPayloadTooLarge())
          .andExpect(jsonPath("$.code").value("AUTH_REQUEST_TOO_LARGE"));
    }
  }

  @Test
  void anonymousAuthRoutesAcceptBodiesAtTheConfiguredLimit() throws Exception {
    for (String route : List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/register")) {
      mockMvc
          .perform(
              post(route)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("x".repeat(MAX_AUTH_REQUEST_BYTES)))
          .andExpect(status().isOk());
    }
  }

  @Test
  void saveSnapshotBodyLimitRunsAfterAuthenticationAndBeforeMvcBinding() throws Exception {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    when(jwtService.parse("signed-test-token")).thenReturn(principal);
    when(userRepository.findById(7L))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    7L, "student", "Student", "hash", null, Role.STUDENT, "ACTIVE", 0)));

    mockMvc
        .perform(
            post("/api/world/saves/0/snapshot")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(256 * 1024 + 1)))
        .andExpect(status().isPayloadTooLarge());

    mockMvc
        .perform(
            post("/api/world/saves/0/snapshot")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(256 * 1024)))
        .andExpect(status().isOk())
        .andExpect(content().string(Integer.toString(256 * 1024)));
  }

  @Test
  void agentLoopBodyLimitsRejectOversizedRequestsBeforeMvcBinding() throws Exception {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    when(jwtService.parse("signed-test-token")).thenReturn(principal);
    when(userRepository.findById(7L))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    7L, "student", "Student", "hash", null, Role.STUDENT, "ACTIVE", 0)));
    String oversizedBody = "x".repeat(MAX_LOOP_RUNTIME_REQUEST_BYTES + 1);

    mockMvc
        .perform(
            post("/api/agent-runs/99/loop-states")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversizedBody))
        .andExpect(status().isPayloadTooLarge());

    mockMvc
        .perform(
            post("/api/loop/runs/99/tool-calls")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversizedBody))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void learningEventBodyLimitsRejectOversizedRequestsBeforeMvcBinding() throws Exception {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    when(jwtService.parse("signed-test-token")).thenReturn(principal);
    when(userRepository.findById(7L))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    7L, "student", "Student", "hash", null, Role.STUDENT, "ACTIVE", 0)));

    mockMvc
        .perform(
            post("/api/learning/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(MAX_LEARNING_EVENT_REQUEST_BYTES + 1)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("LEARNING_EVENT_REQUEST_TOO_LARGE"));

    mockMvc
        .perform(
            post("/api/learning/events/batch")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(MAX_LEARNING_EVENT_BATCH_REQUEST_BYTES + 1)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("LEARNING_EVENT_REQUEST_TOO_LARGE"));
  }

  @Test
  void learningEventBodyLimitsAcceptRequestsAtTheirByteBoundaries() throws Exception {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "student", Role.STUDENT);
    when(jwtService.parse("signed-test-token")).thenReturn(principal);
    when(userRepository.findById(7L))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    7L, "student", "Student", "hash", null, Role.STUDENT, "ACTIVE", 0)));

    mockMvc
        .perform(
            post("/api/learning/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(MAX_LEARNING_EVENT_REQUEST_BYTES)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/learning/events/batch")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(MAX_LEARNING_EVENT_BATCH_REQUEST_BYTES)))
        .andExpect(status().isOk());
  }

  @RestController
  static class PublicRouteController {
    @PostMapping(value = "/api/auth/login", produces = MediaType.TEXT_PLAIN_VALUE)
    String login(
        @org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
      return "login";
    }

    @PostMapping(value = "/api/auth/refresh", produces = MediaType.TEXT_PLAIN_VALUE)
    String refresh(
        @org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
      return "refresh";
    }

    @PostMapping(value = "/api/auth/register", produces = MediaType.TEXT_PLAIN_VALUE)
    String register(
        @org.springframework.web.bind.annotation.RequestBody(required = false) String body) {
      return "register";
    }

    @PostMapping(value = "/api/auth/logout", produces = MediaType.TEXT_PLAIN_VALUE)
    String logout() {
      return "logout";
    }

    @GetMapping(value = "/api/course/security-test", produces = MediaType.TEXT_PLAIN_VALUE)
    String readCourse() {
      return "course";
    }

    @PostMapping(value = "/api/course/security-test", produces = MediaType.TEXT_PLAIN_VALUE)
    String writeCourse() {
      return "course";
    }

    @GetMapping(value = "/api/media/assets/1", produces = MediaType.TEXT_PLAIN_VALUE)
    String readAsset() {
      return "asset";
    }

    @PostMapping(value = "/api/media/assets/1", produces = MediaType.TEXT_PLAIN_VALUE)
    String writeAsset() {
      return "asset";
    }

    @GetMapping(value = "/api/downloads/security-test", produces = MediaType.TEXT_PLAIN_VALUE)
    String readDownloadCount() {
      return "count";
    }

    @PostMapping(value = "/api/downloads/security-test/hit", produces = MediaType.TEXT_PLAIN_VALUE)
    String recordDownload() {
      return "count";
    }

    @PostMapping(value = "/api/downloads/security-test", produces = MediaType.TEXT_PLAIN_VALUE)
    String mutateDownloadConfig() {
      return "count";
    }

    @PostMapping(
        value = "/api/world/saves/{slotIndex}/snapshot",
        produces = MediaType.TEXT_PLAIN_VALUE)
    String saveSnapshot(@org.springframework.web.bind.annotation.RequestBody String snapshot) {
      return Integer.toString(snapshot.length());
    }

    @PostMapping(value = "/api/agent-runs/{id}/loop-states", produces = MediaType.TEXT_PLAIN_VALUE)
    String saveAgentLoopState(@org.springframework.web.bind.annotation.RequestBody String state) {
      return Integer.toString(state.length());
    }

    @PostMapping(value = "/api/loop/runs/{id}/tool-calls", produces = MediaType.TEXT_PLAIN_VALUE)
    String saveLoopToolCall(@org.springframework.web.bind.annotation.RequestBody String call) {
      return Integer.toString(call.length());
    }

    @PostMapping(value = "/api/learning/events", produces = MediaType.TEXT_PLAIN_VALUE)
    String saveLearningEvent(@org.springframework.web.bind.annotation.RequestBody String event) {
      return Integer.toString(event.length());
    }

    @PostMapping(value = "/api/learning/events/batch", produces = MediaType.TEXT_PLAIN_VALUE)
    String saveLearningEventBatch(
        @org.springframework.web.bind.annotation.RequestBody String events) {
      return Integer.toString(events.length());
    }
  }
}
