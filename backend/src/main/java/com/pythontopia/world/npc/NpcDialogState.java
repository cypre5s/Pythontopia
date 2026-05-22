package com.pythontopia.world.npc;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;

public record NpcDialogState(
    Long id,
    Long userId,
    String npcKey,
    String currentNodeId,
    List<String> completedNodeIds,
    Integer friendship,
    OffsetDateTime lastTalkedAt,
    JsonNode flags,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
