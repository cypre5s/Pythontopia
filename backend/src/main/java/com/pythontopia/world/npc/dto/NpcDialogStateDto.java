package com.pythontopia.world.npc.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.pythontopia.world.npc.NpcDialogState;
import java.time.OffsetDateTime;
import java.util.List;

public record NpcDialogStateDto(
    String npcKey,
    String currentNodeId,
    List<String> completedNodeIds,
    int friendship,
    OffsetDateTime lastTalkedAt,
    JsonNode flags) {

  public static NpcDialogStateDto from(NpcDialogState state) {
    return new NpcDialogStateDto(
        state.npcKey(),
        state.currentNodeId(),
        state.completedNodeIds(),
        state.friendship() == null ? 0 : state.friendship(),
        state.lastTalkedAt(),
        state.flags());
  }
}
