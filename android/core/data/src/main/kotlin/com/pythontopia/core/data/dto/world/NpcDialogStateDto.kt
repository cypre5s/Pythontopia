package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NpcDialogStateDto(
    val npcKey: String,
    val currentNodeId: String? = null,
    val completedNodeIds: List<String> = emptyList(),
    val friendship: Int = 0,
    val lastTalkedAt: String? = null,
    val flags: JsonElement? = null,
)
