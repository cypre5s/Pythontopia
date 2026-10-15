package com.pythontopia.world.npc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.auth.security.JwtAuthenticationFilter;
import com.pythontopia.auth.security.Role;
import com.pythontopia.common.error.GlobalExceptionHandler;
import com.pythontopia.common.web.CurrentUserArgumentResolver;
import com.pythontopia.tutor.TutorDailyRequestLimitService;
import com.pythontopia.common.web.WebConfig;
import com.pythontopia.world.npc.dto.NpcDialogStateDto;
import com.pythontopia.world.npc.dto.NpcTalkRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(NpcDialogController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebConfig.class, CurrentUserArgumentResolver.class})
class NpcDialogControllerTest {

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @MockBean NpcDialogService service;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockBean TutorDailyRequestLimitService requestLimitService;

  @BeforeEach
  void setUp() {
    CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "stu-npc", Role.STUDENT);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
  }

  @Test
  void getStateReturnsDto() throws Exception {
    NpcDialogStateDto dto =
        new NpcDialogStateDto(
            "mayor_py",
            "intro_1",
            List.of("intro_0"),
            OffsetDateTime.parse("2026-05-22T08:00:00Z"),
            objectMapper.createObjectNode().put("met", true));
    when(service.getOrInit(eq(7L), eq("mayor_py"))).thenReturn(dto);

    mockMvc
        .perform(get("/api/world/npcs/mayor_py/state"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.npcKey").value("mayor_py"))
        .andExpect(jsonPath("$.flags.met").value(true));
  }

  @Test
  void getStateRejectsOversizedNpcKeyBeforeDatabaseAccess() throws Exception {
    mockMvc
        .perform(get("/api/world/npcs/{npcKey}/state", "a".repeat(256)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_NPC_KEY"));
  }

  @Test
  void talkAppliesEffectsAndReturnsState() throws Exception {
    NpcDialogStateDto dto =
        new NpcDialogStateDto(
            "mayor_py",
            "intro_2",
            List.of("intro_0", "intro_1"),
            OffsetDateTime.parse("2026-05-22T08:05:00Z"),
            objectMapper.createObjectNode());
    when(service.talk(eq(7L), eq("mayor_py"), any(NpcTalkRequest.class))).thenReturn(dto);
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "nodeId", "intro_1",
                "nextNodeId", "intro_2",
                "effects",
                    List.of(Map.of("type", "COMPLETE_NODE", "key", "intro_1"))));

    mockMvc
        .perform(
            post("/api/world/npcs/mayor_py/talk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentNodeId").value("intro_2"))
        .andExpect(jsonPath("$.completedNodeIds.length()").value(2));
  }

  @Test
  void talkRejectsNodeIdsLongerThanTheDatabaseColumn() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of("nodeId", "n".repeat(129), "nextNodeId", "intro_2", "effects", List.of()));

    mockMvc
        .perform(
            post("/api/world/npcs/mayor_py/talk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void talkRejectsExcessiveEffectsAndValuesBeforeServiceCall() throws Exception {
    List<Map<String, String>> effects =
        java.util.Collections.nCopies(33, Map.of("type", "SET_FLAG", "key", "visited", "value", "yes"));
    String body =
        objectMapper.writeValueAsString(
            Map.of("nodeId", "intro_1", "effects", effects));

    mockMvc
        .perform(
            post("/api/world/npcs/mayor_py/talk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void talkRejectsOversizedFlagValueBeforeServiceCall() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "nodeId", "intro_1",
                "effects",
                    List.of(Map.of("type", "SET_FLAG", "key", "visited", "value", "v".repeat(1025)))));

    mockMvc
        .perform(
            post("/api/world/npcs/mayor_py/talk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }
}
