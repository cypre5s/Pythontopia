package com.pythontopia.world.npc.dto;

import java.util.List;

public record NpcTalkRequest(
    String nodeId, String nextNodeId, List<NpcDialogEffectDto> effects) {}
