package com.pythontopia.world.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.npc.dto.NpcDialogEffectDto;
import com.pythontopia.world.npc.dto.NpcTalkRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NpcDialogServiceTest {

  private final NpcDialogStateRepository stateRepository = mock(NpcDialogStateRepository.class);
  private final NpcDialogService service =
      new NpcDialogService(stateRepository, new ObjectMapper());

  @Test
  void unknownNpcStateIsRejectedBeforeDatabaseAccess() {
    assertThatThrownBy(() -> service.getOrInit(7L, "invented_npc"))
        .isInstanceOf(BusinessException.class);

    verifyNoInteractions(stateRepository);
  }

  @Test
  void unknownNpcTalkIsRejectedBeforeDatabaseAccess() {
    assertThatThrownBy(() -> service.talk(7L, "invented_npc", new NpcTalkRequest(null, null, null)))
        .isInstanceOf(BusinessException.class);

    verifyNoInteractions(stateRepository);
  }

  @Test
  void talkRejectsCompletedNodeGrowthBeyondPerNpcLimit() {
    List<String> completed = new ArrayList<>();
    for (int index = 0; index < 128; index++) {
      completed.add("node-" + index);
    }
    when(stateRepository.findOrInitForUpdate(7L, "mayor_py"))
        .thenReturn(state("mayor_py", completed, new ObjectMapper().createObjectNode()));

    assertThatThrownBy(
            () ->
                service.talk(7L, "mayor_py", new NpcTalkRequest("new-node", "new-node", List.of())))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            failure ->
                assertThat(((BusinessException) failure).code())
                    .isEqualTo("NPC_DIALOG_STATE_LIMIT_REACHED"));

    verify(stateRepository).findOrInitForUpdate(7L, "mayor_py");
    verifyNoMoreInteractions(stateRepository);
  }

  @Test
  void talkRejectsFlagGrowthBeyondPerNpcLimit() {
    ObjectNode flags = new ObjectMapper().createObjectNode();
    for (int index = 0; index < 64; index++) {
      flags.put("flag-" + index, "value");
    }
    when(stateRepository.findOrInitForUpdate(7L, "mayor_py"))
        .thenReturn(state("mayor_py", List.of(), flags));

    assertThatThrownBy(
            () ->
                service.talk(
                    7L,
                    "mayor_py",
                    new NpcTalkRequest(
                        "greet",
                        "greet",
                        List.of(new NpcDialogEffectDto("SET_FLAG", "new-flag", "value")))))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            failure ->
                assertThat(((BusinessException) failure).code())
                    .isEqualTo("NPC_DIALOG_STATE_LIMIT_REACHED"));

    verify(stateRepository).findOrInitForUpdate(7L, "mayor_py");
    verifyNoMoreInteractions(stateRepository);
  }

  private static NpcDialogState state(
      String npcKey, List<String> completedNodeIds, ObjectNode flags) {
    return new NpcDialogState(1L, 7L, npcKey, "greet", completedNodeIds, null, flags, null, null);
  }

  @Test
  void androidHubCaptainRemainsSupported() {
    NpcDialogState state =
        new NpcDialogState(
            1L,
            7L,
            "captain_def",
            "greet",
            List.of(),
            null,
            new ObjectMapper().createObjectNode(),
            null,
            null);
    when(stateRepository.findOrInit(7L, "captain_def")).thenReturn(state);

    assertThat(service.getOrInit(7L, "captain_def").npcKey()).isEqualTo("captain_def");

    verify(stateRepository).findOrInit(7L, "captain_def");
  }
}
