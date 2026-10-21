package com.pythontopia.world.boss;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.common.web.StudentWriteThrottle;
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import com.pythontopia.world.boss.dto.ExpeditionRunActionRequest;
import com.pythontopia.world.boss.dto.ExpeditionRunDto;
import com.pythontopia.world.boss.dto.FrontierPickDto;
import com.pythontopia.world.boss.dto.FrontierSelectResultDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

@WebMvcTest(ExpeditionRunController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class ExpeditionRunControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @MockBean ExpeditionRunService service;
  @MockBean BossService bossService;
  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockBean TutorDailyRequestLimitService requestLimitService;
  @MockBean StudentWriteThrottle studentWriteThrottle;

  @BeforeEach
  void authenticateAsStudent() {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(true);
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "stu", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getReturns204WhenNoActiveRun() throws Exception {
    when(service.findActive(7L)).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/world/expedition/run")).andExpect(status().isNoContent());
  }

  @Test
  void getReturnsActiveRunForCurrentStudent() throws Exception {
    when(service.findActive(7L)).thenReturn(Optional.of(runDto()));

    mockMvc
        .perform(get("/api/world/expedition/run"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phase").value("map"))
        .andExpect(jsonPath("$.focusChapter").value(2))
        .andExpect(jsonPath("$.nodes[0].kcCode").value("PY43-KC-008"));
  }

  @Test
  void putUpsertsActiveRunForCurrentStudent() throws Exception {
    ExpeditionRunDto dto = runDto();
    when(service.save(eq(7L), any(ExpeditionRunDto.class))).thenReturn(dto);

    mockMvc
        .perform(
            put("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(32));

    verify(service).save(eq(7L), any(ExpeditionRunDto.class));
    verify(studentWriteThrottle).tryAcquire(7L, 1);
  }

  @Test
  void putRunIsRejectedWhenAccountWriteBudgetIsExhausted() throws Exception {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    mockMvc
        .perform(
            put("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(runDto())))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("STUDENT_WRITE_RATE_LIMITED"));

    verify(studentWriteThrottle).tryAcquire(7L, 1);
    verifyNoInteractions(service);
  }

  @Test
  void postRunActionReturnsTheCanonicalRun() throws Exception {
    ExpeditionRunDto dto = runDto();
    when(service.performAction(eq(7L), any(ExpeditionRunActionRequest.class))).thenReturn(dto);

    mockMvc
        .perform(
            post("/api/world/expedition/run/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"runId\":\""
                        + dto.runId()
                        + "\",\"nodeId\":\"L0-0\","
                        + "\"action\":\"ENTER_NODE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runId").value(dto.runId()))
        .andExpect(jsonPath("$.phase").value("map"));

    verify(service).performAction(eq(7L), any(ExpeditionRunActionRequest.class));
    verify(studentWriteThrottle).tryAcquire(7L, 1);
  }

  @Test
  void runActionIsRejectedWhenAccountWriteBudgetIsExhausted() throws Exception {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    mockMvc
        .perform(
            post("/api/world/expedition/run/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"runId\":\"123e4567-e89b-12d3-a456-426614174000\","
                        + "\"nodeId\":\"L0-0\",\"action\":\"ENTER_NODE\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("STUDENT_WRITE_RATE_LIMITED"));

    verify(studentWriteThrottle).tryAcquire(7L, 1);
    verifyNoInteractions(service, bossService);
  }

  @Test
  void postRunActionRejectsUnknownActionEnum() throws Exception {
    mockMvc
        .perform(
            post("/api/world/expedition/run/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"runId\":\"123e4567-e89b-12d3-a456-426614174000\",\"action\":\"HACK\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void postStartsRunFromServerSelectedFrontier() throws Exception {
    ExpeditionRunDto dto = runDto();
    when(bossService.selectFrontier(7L, 2, "PY43-KC-008"))
        .thenReturn(
            new FrontierSelectResultDto(List.of(new FrontierPickDto("PY43-KC-008", 2, 3, "weak"))));
    when(service.start(eq(7L), eq(2), eq(false), anyList())).thenReturn(dto);

    mockMvc
        .perform(
            post("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"focusChapter\":2,\"isDaily\":false,\"preferredKcCode\":\"PY43-KC-008\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.runId").value(dto.runId()));

    verify(bossService).selectFrontier(7L, 2, "PY43-KC-008");
    verify(service).start(eq(7L), eq(2), eq(false), anyList());
    verify(studentWriteThrottle).tryAcquire(7L, 1);
  }

  @Test
  void startRunIsRejectedWhenAccountWriteBudgetIsExhausted() throws Exception {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    mockMvc
        .perform(
            post("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"focusChapter\":2,\"isDaily\":false}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("STUDENT_WRITE_RATE_LIMITED"));

    verify(studentWriteThrottle).tryAcquire(7L, 1);
    verifyNoInteractions(service, bossService);
  }

  @Test
  void postRejectsInvalidFocusChapterBeforeSelectingFrontier() throws Exception {
    mockMvc
        .perform(
            post("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"focusChapter\":8,\"isDaily\":false}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service, bossService);
  }

  @Test
  void postRejectsInvalidPreferredKcBeforeSelectingFrontier() throws Exception {
    mockMvc
        .perform(
            post("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"focusChapter\":2,\"isDaily\":false,\"preferredKcCode\":\"other-course-kc\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service, bossService);
  }

  @Test
  void putRejectsSnapshotOutsideCurrentRunBounds() throws Exception {
    ExpeditionRunDto dto =
        new ExpeditionRunDto(
            "map", 2, List.of(), 11, null, 5, 5, 0, 0, 0, List.of(), false, List.of(), null);

    mockMvc
        .perform(
            put("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void postRunActionReturnsTheCanonicalRun() throws Exception {
    ExpeditionRunDto dto = runDto();
    when(service.performAction(eq(7L), any(ExpeditionRunActionRequest.class))).thenReturn(dto);

    mockMvc
        .perform(
            post("/api/world/expedition/run/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"runId\":\"" + dto.runId() + "\",\"nodeId\":\"L0-0\","
                        + "\"action\":\"ENTER_NODE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runId").value(dto.runId()))
        .andExpect(jsonPath("$.phase").value("map"));

    verify(service).performAction(eq(7L), any(ExpeditionRunActionRequest.class));
  }

  @Test
  void postRunActionRejectsUnknownActionEnum() throws Exception {
    mockMvc
        .perform(
            post("/api/world/expedition/run/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"123e4567-e89b-12d3-a456-426614174000\",\"action\":\"HACK\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void postStartsRunFromServerSelectedFrontier() throws Exception {
    ExpeditionRunDto dto = runDto();
    when(bossService.selectFrontier(7L, 2, "PY43-KC-008"))
        .thenReturn(
            new FrontierSelectResultDto(List.of(new FrontierPickDto("PY43-KC-008", 2, 3, "weak"))));
    when(service.start(eq(7L), eq(2), eq(false), anyList())).thenReturn(dto);

    mockMvc
        .perform(
            post("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"focusChapter\":2,\"isDaily\":false,\"preferredKcCode\":\"PY43-KC-008\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.runId").value(dto.runId()));

    verify(bossService).selectFrontier(7L, 2, "PY43-KC-008");
    verify(service).start(eq(7L), eq(2), eq(false), anyList());
  }

  @Test
  void postRejectsInvalidFocusChapterBeforeSelectingFrontier() throws Exception {
    mockMvc
        .perform(
            post("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"focusChapter\":8,\"isDaily\":false}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service, bossService);
  }

  @Test
  void postRejectsInvalidPreferredKcBeforeSelectingFrontier() throws Exception {
    mockMvc
        .perform(
            post("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"focusChapter\":2,\"isDaily\":false,\"preferredKcCode\":\"other-course-kc\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service, bossService);
  }

  @Test
  void putRejectsSnapshotOutsideCurrentRunBounds() throws Exception {
    ExpeditionRunDto dto =
        new ExpeditionRunDto(
            "map", 2, List.of(), 11, null, 5, 5, 0, 0, 0, List.of(), false, List.of(), null);

    mockMvc
        .perform(
            put("/api/world/expedition/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void deleteAbandonsActiveRunForCurrentStudent() throws Exception {
    mockMvc.perform(delete("/api/world/expedition/run")).andExpect(status().isNoContent());
    verify(service).abandon(7L);
    verify(studentWriteThrottle).tryAcquire(7L, 1);
  }

  @Test
  void deleteRunIsRejectedWhenAccountWriteBudgetIsExhausted() throws Exception {
    when(studentWriteThrottle.tryAcquire(7L, 1)).thenReturn(false);

    mockMvc
        .perform(delete("/api/world/expedition/run"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("STUDENT_WRITE_RATE_LIMITED"));

    verify(studentWriteThrottle).tryAcquire(7L, 1);
    verifyNoInteractions(service);
  }

  @Test
  void teacherCannotAccessStudentRunPersistence() throws Exception {
    CurrentUserPrincipal teacher = new CurrentUserPrincipal(2L, "teacher", Role.TEACHER);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                teacher, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));

    mockMvc.perform(get("/api/world/expedition/run")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/world/expedition/run/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"runId\":\"123e4567-e89b-12d3-a456-426614174000\","
                        + "\"action\":\"FINISH\"}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  private static ExpeditionRunDto runDto() {
    return new ExpeditionRunDto(
        "map",
        2,
        List.of(
            Map.of(
                "id", "L0-0",
                "layer", 0,
                "col", 0,
                "kind", "battle",
                "kcCode", "PY43-KC-008",
                "challengeKind", "dual_track",
                "difficulty", 3,
                "next", List.of(),
                "state", "available")),
        1,
        null,
        5,
        5,
        1,
        2,
        32,
        List.of("calm_mind"),
        false,
        List.of(),
        "123e4567-e89b-12d3-a456-426614174000");
  }
}
