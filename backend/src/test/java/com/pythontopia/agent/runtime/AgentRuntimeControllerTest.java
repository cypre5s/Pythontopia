package com.pythontopia.agent.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.loop.LoopRuntimeService;
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

@WebMvcTest(AgentRuntimeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class AgentRuntimeControllerTest {

  private static final int MAX_JSON_PAYLOAD_BYTES = 1024 * 1024;

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @MockBean AgentRuntimeEventService eventService;

  @MockBean LoopRuntimeService runtimeService;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockBean TutorDailyRequestLimitService requestLimitService;
  @MockBean StudentWriteThrottle studentWriteThrottle;

  @BeforeEach
  void authenticateAsStudent() {
    authenticate(new CurrentUserPrincipal(7L, "stu-agent-runtime", Role.STUDENT), "ROLE_STUDENT");
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(true);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void eventEndpointPersistsAuthenticatedStudentEvent() throws Exception {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("submissionId", 88);
    payload.putArray("kcCodes").add("PY43-KC-001");
    when(eventService.recordEvent(eq(7L), eq("answer_feedback"), eq(payload)))
        .thenReturn(
            new AgentRuntimeEventService.EventRecord(
                701L,
                7L,
                "answer_feedback",
                payload,
                "frontend",
                1,
                OffsetDateTime.parse("2026-06-30T00:00:00Z")));

    ObjectNode body = objectMapper.createObjectNode();
    body.put("eventType", "answer_feedback");
    body.set("payload", payload);
    body.put("source", "graph");
    body.put("importance", 5);

    mockMvc
        .perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(701))
        .andExpect(jsonPath("$.userId").value(7))
        .andExpect(jsonPath("$.eventType").value("answer_feedback"))
        .andExpect(jsonPath("$.payload.submissionId").value(88))
        .andExpect(jsonPath("$.source").value("frontend"))
        .andExpect(jsonPath("$.importance").value(1));

    verify(eventService).recordEvent(7L, "answer_feedback", payload);
  }

  @Test
  void eventEndpointRejectsNonStudent() throws Exception {
    authenticate(new CurrentUserPrincipal(2L, "teacher", Role.TEACHER), "ROLE_TEACHER");

    ObjectNode body = objectMapper.createObjectNode();
    body.put("eventType", "resource.generated");

    mockMvc
        .perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(eventService, runtimeService);
  }

  @Test
  void eventEndpointRejectsClientReportedPracticeEvidence() throws Exception {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("eventType", "practice_failed");
    body.put("source", "graph");
    body.put("importance", 5);
    body.set("payload", objectMapper.createObjectNode().put("kcCode", "PY43-KC-001"));

    mockMvc
        .perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(eventService);
  }

  /**
   * run 的创建/查询只保留 {@code /api/loop/runs} 一个入口；这里曾镜像出一套完全等价的
   * {@code /api/agent-runs}（同 DTO 同 service 方法），外加一个恒报 FINISHED 的假 SSE
   * {@code /stream}（{@code produces=text/event-stream} 却返回一次性拼好的字符串，而
   * {@code LoopRunDto} 根本没有 status 字段）。三条路由必须保持删除状态。
   */
  @Test
  void duplicatedRunRoutesAndFakeSseStreamAreGone() throws Exception {
    mockMvc
        .perform(
            post("/api/agent-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loopRunBody())))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/agent-runs/99")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/agent-runs/99/stream")).andExpect(status().isNotFound());
  }

  @Test
  void loopStateAndPolicyDecisionEndpointsPersistRuntimeControlRecords() throws Exception {
    ObjectNode state = objectMapper.createObjectNode().put("node", "observe");
    when(runtimeService.saveLoopState(
            eq(7L), eq(99L), eq("LEARNER"), eq("observe"), eq("READY"), any(), eq("agentic_loop_v1"), eq("trace-99")))
        .thenReturn(
            new LoopRuntimeService.LoopStateRecord(
                801L,
                99L,
                "LEARNER",
                "observe",
                "READY",
                state,
                "agentic_loop_v1",
                "trace-99",
                OffsetDateTime.parse("2026-06-30T00:00:00Z")));

    ObjectNode loopStateBody = objectMapper.createObjectNode();
    loopStateBody.put("loopType", "LEARNER");
    loopStateBody.put("stateKey", "observe");
    loopStateBody.put("status", "READY");
    loopStateBody.set("state", state);
    loopStateBody.put("policyVersion", "agentic_loop_v1");
    loopStateBody.put("traceId", "trace-99");

    mockMvc
        .perform(
            post("/api/agent-runs/99/loop-states")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loopStateBody)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(801))
        .andExpect(jsonPath("$.loopType").value("LEARNER"))
        .andExpect(jsonPath("$.stateKey").value("observe"))
        .andExpect(jsonPath("$.policyVersion").value("agentic_loop_v1"))
        .andExpect(jsonPath("$.traceId").value("trace-99"));

    ObjectNode decision = objectMapper.createObjectNode().put("maxSteps", 4);
    when(runtimeService.recordPolicyDecision(
            eq(7L),
            eq(99L),
            eq("student-loop-start"),
            eq("agentic_loop_v1"),
            eq("MEDIUM"),
            eq(false),
            any(),
            eq("bounded loop within student policy"),
            eq("trace-99")))
        .thenReturn(
            new LoopRuntimeService.PolicyDecisionRecord(
                901L,
                99L,
                "student-loop-start",
                "agentic_loop_v1",
                "MEDIUM",
                false,
                decision,
                "bounded loop within student policy",
                "trace-99",
                OffsetDateTime.parse("2026-06-30T00:00:01Z")));

    ObjectNode decisionBody = objectMapper.createObjectNode();
    decisionBody.put("decisionKey", "student-loop-start");
    decisionBody.put("policyVersion", "agentic_loop_v1");
    decisionBody.put("riskLevel", "MEDIUM");
    decisionBody.put("approvalRequired", false);
    decisionBody.set("decision", decision);
    decisionBody.put("reason", "bounded loop within student policy");
    decisionBody.put("traceId", "trace-99");

    mockMvc
        .perform(
            post("/api/agent-runs/99/policy-decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(decisionBody)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(901))
        .andExpect(jsonPath("$.decisionKey").value("student-loop-start"))
        .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
        .andExpect(jsonPath("$.approvalRequired").value(false))
        .andExpect(jsonPath("$.traceId").value("trace-99"));
  }

  @Test
  void loopStateEndpointRejectsWritesWhenAccountBudgetIsFull() throws Exception {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    ObjectNode body = objectMapper.createObjectNode();
    body.put("loopType", "LEARNER");
    body.put("stateKey", "observe");
    body.put("status", "READY");
    body.put("policyVersion", "agentic_loop_v1");
    body.put("traceId", "trace-99");

    mockMvc
        .perform(
            post("/api/agent-runs/99/loop-states")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("STUDENT_WRITE_RATE_LIMITED"));

    verifyNoInteractions(runtimeService);
  }

  @Test
  void loopStateEndpointRejectsOversizedJsonBeforeReservingBudget() throws Exception {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("loopType", "LEARNER");
    body.put("stateKey", "observe");
    body.put("status", "READY");
    body.set(
        "state",
        objectMapper.createObjectNode().put("blob", "x".repeat(MAX_JSON_PAYLOAD_BYTES + 1)));
    body.put("policyVersion", "agentic_loop_v1");
    body.put("traceId", "trace-99");

    mockMvc
        .perform(
            post("/api/agent-runs/99/loop-states")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("LOOP_RUNTIME_PAYLOAD_TOO_LARGE"));

    verifyNoInteractions(studentWriteThrottle, runtimeService);
  }

  private ObjectNode loopRunBody() {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("userId", 7);
    body.put("sessionId", "agentic-session-1");
    body.put("turn", 1);
    body.put("maxSteps", 4);
    body.put("stepsUsed", 3);
    body.put("replanCount", 1);
    body.putArray("toolsUsed").add("course_rag.search").add("judge.run");
    body.put("groundingPass", true);
    body.put("safetyPass", true);
    body.put("inputTokens", 120);
    body.put("outputTokens", 80);
    body.put("traceId", "trace-agent-run");
    return body;
  }

  private void authenticate(CurrentUserPrincipal principal, String authority) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority))));
  }
}
