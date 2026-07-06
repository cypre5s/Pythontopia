package com.pythontopia.world.boss;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.world.boss.dto.BossAttemptRequest;
import com.pythontopia.world.boss.dto.BossAttemptResultDto;
import com.pythontopia.world.boss.dto.BossProblemDto;
import com.pythontopia.world.boss.dto.BossProblemRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BossController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BossControllerTest {

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @MockBean BossService service;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

  @Test
  void returns200WithBossProblem() throws Exception {
    BossProblemDto dto =
        new BossProblemDto(
            "ai-1", 1, 1, 2, "PY43-KC-001", "stmt", List.of(), "in", "out", 3, true, true);
    when(service.fetchProblem(anyInt(), any(), any())).thenReturn(dto);

    mockMvc
        .perform(
            post("/api/world/boss/1/problem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BossProblemRequest(1, null, 2, null, null, null, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.problemId").value("ai-1"))
        .andExpect(jsonPath("$.aiGenerated").value(true))
        .andExpect(jsonPath("$.kcCode").value("PY43-KC-001"));
  }

  @Test
  void rejects400WhenStageOutOfRange() throws Exception {
    mockMvc
        .perform(
            post("/api/world/boss/1/problem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BossProblemRequest(99, null, 2, null, null, null, null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejects400WhenChapterOutOfRange() throws Exception {
    mockMvc
        .perform(
            post("/api/world/boss/0/problem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BossProblemRequest(1, null, 1, null, null, null, null))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/world/boss/8/problem")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BossProblemRequest(1, null, 1, null, null, null, null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void evaluateAttemptReturns200WithDto() throws Exception {
    BossAttemptResultDto result =
        new BossAttemptResultDto(
            "ai-1", true, 3, 3, null, 95, "通过");
    when(service.evaluateAttempt(any(), any())).thenReturn(result);

    mockMvc
        .perform(
            post("/api/world/boss/1/attempt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new BossAttemptRequest("ai-1", 1, 1, "print(5)", 10))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(true))
        .andExpect(jsonPath("$.score").value(95));
  }

  @Test
  void rejects400WhenAttemptHasEmptyCode() throws Exception {
    mockMvc
        .perform(
            post("/api/world/boss/1/attempt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new BossAttemptRequest("ai-1", 1, 1, "", 10))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejects500WhenChapterParamMismatchBody() throws Exception {
    // Service-layer IllegalArgumentException 会被 GlobalExceptionHandler 兜底成 500
    mockMvc
        .perform(
            post("/api/world/boss/2/attempt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new BossAttemptRequest("ai-1", 1, 1, "print(1)", 5))))
        .andExpect(status().is5xxServerError());
  }
}
