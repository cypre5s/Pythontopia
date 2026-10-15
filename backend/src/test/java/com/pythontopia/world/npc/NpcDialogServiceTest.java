package com.pythontopia.world.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pythontopia.common.error.BusinessException;
import com.pythontopia.world.npc.dto.NpcTalkRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class NpcDialogServiceTest {

  private final NpcDialogStateRepository stateRepository = mock(NpcDialogStateRepository.class);
  private final NpcDialogService service = new NpcDialogService(stateRepository, new ObjectMapper());

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
