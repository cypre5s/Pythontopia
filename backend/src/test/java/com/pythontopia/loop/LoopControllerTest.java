package com.pythontopia.loop;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.loop.dto.LoopRunDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LoopController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class LoopControllerTest {

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @MockBean LoopRunService loopRunService;

  @MockBean LoopRuntimeService runtimeService;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockBean TutorDailyRequestLimitService requestLimitService;

  @BeforeEach
  void authenticateAsStudent() {
    authenticate(new CurrentUserPrincipal(7L, "stu-loop", Role.STUDENT), "ROLE_STUDENT");
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void checkpointEndpointRecordsOwnerScopedCheckpoint() throws Exception {
    ObjectNode state = objectMapper.createObjectNode().put("node", "dispatch");
    when(runtimeService.saveCheckpoint(eq(7L), eq(99L), eq(2), eq("READY"), any(), eq("cursor-2")))
        .thenReturn(
            new LoopRuntimeService.CheckpointRecord(
                301L, 99L, 2, "READY", state, "cursor-2", OffsetDateTime.parse("2026-06-30T00:00:00Z")));

    ObjectNode body = objectMapper.createObjectNode();
    body.put("stepIndex", 2);
    body.put("status", "READY");
    body.set("state", state);
    body.put("resumeCursor", "cursor-2");

    mockMvc
        .perform(
            post("/api/loop/runs/99/checkpoints")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(301))
        .andExpect(jsonPath("$.loopRunId").value(99))
        .andExpect(jsonPath("$.stepIndex").value(2))
        .andExpect(jsonPath("$.resumeCursor").value("cursor-2"));
  }

  @Test
  void runtimeSnapshotEndpointRestoresCheckpointToolAndEvalRecords() throws Exception {
    var run =
        new LoopRunDto(
            99L,
            7L,
            "sess",
            1,
            4,
            3,
            1,
            List.of("rag.query"),
            true,
            true,
            10,
            5,
            "trace",
            OffsetDateTime.parse("2026-06-30T00:00:00Z"));
    when(runtimeService.restoreSnapshot(7L, 99L))
        .thenReturn(
            new LoopRuntimeService.RuntimeSnapshot(
                run,
                List.of(
                    new LoopRuntimeService.CheckpointRecord(
                        301L, 99L, 2, "READY", objectMapper.createObjectNode(), "cursor-2",
                        OffsetDateTime.parse("2026-06-30T00:00:00Z"))),
                List.of(
                    new LoopRuntimeService.ToolCallRecord(
                        401L, 99L, 301L, "resource.generate", objectMapper.createObjectNode(),
                        objectMapper.createObjectNode(), "SUCCESS", 120,
                        OffsetDateTime.parse("2026-06-30T00:00:00Z"))),
                List.of(),
                List.of(
                    new LoopRuntimeService.EvalResultRecord(
                        501L, 99L, "agentic-ab-runtime", "learner-case", "LEARNER", "v1",
                        objectMapper.createObjectNode().put("mean_uplift", 0.2), true,
                        OffsetDateTime.parse("2026-06-30T00:00:00Z")))));

    mockMvc
        .perform(get("/api/loop/runs/99/runtime"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loopRun.id").value(99))
        .andExpect(jsonPath("$.checkpoints[0].resumeCursor").value("cursor-2"))
        .andExpect(jsonPath("$.toolCalls[0].toolName").value("resource.generate"))
        .andExpect(jsonPath("$.evalResults[0].loopType").value("LEARNER"));
  }

  @Test
  void replayEndpointReturnsExplicitResumePlan() throws Exception {
    when(runtimeService.restoreReplayPlan(7L, 99L))
        .thenReturn(
            new LoopRuntimeService.ReplayPlan(
                99L,
                "DEBUG",
                "debug-cursor-2",
                "replay failed submission in Bug Mine, then request retry",
                List.of("submission:2003", "bug-mine:44", "retry:pending"),
                List.of(
                    new LoopRuntimeService.ReplayStep(
                        2,
                        "READY_TO_RETRY",
                        "debug-cursor-2",
                        List.of("submission:2003", "bug-mine:44", "retry:pending"),
                        "replay failed submission in Bug Mine, then request retry")),
                true,
                "failed submission -> Bug Mine -> retry/recovery"));

    mockMvc
        .perform(get("/api/loop/runs/99/replay"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loopRunId").value(99))
        .andExpect(jsonPath("$.loopType").value("DEBUG"))
        .andExpect(jsonPath("$.resumeCursor").value("debug-cursor-2"))
        .andExpect(jsonPath("$.replayable").value(true))
        .andExpect(jsonPath("$.evidenceRefs[0]").value("submission:2003"))
        .andExpect(jsonPath("$.steps[0].status").value("READY_TO_RETRY"));
  }

  @Test
  void runtimeEndpointsRejectNonStudent() throws Exception {
    authenticate(new CurrentUserPrincipal(2L, "teacher", Role.TEACHER), "ROLE_TEACHER");

    mockMvc
        .perform(get("/api/loop/runs/99/runtime"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(runtimeService);
  }

  private void authenticate(CurrentUserPrincipal principal, String authority) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority))));
  }
}
