package com.pythontopia.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pythontopia.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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

  @RestController
  static class PublicRouteController {
    @PostMapping(value = "/api/auth/login", produces = MediaType.TEXT_PLAIN_VALUE)
    String login() {
      return "login";
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
  }
}
