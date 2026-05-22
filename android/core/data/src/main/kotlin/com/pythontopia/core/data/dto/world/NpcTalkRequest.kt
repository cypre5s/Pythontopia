package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

@Serializable
data class NpcTalkRequest(
    val nodeId: String? = null,
    val nextNodeId: String? = null,
    val effects: List<NpcDialogEffectDto>? = null,
)
