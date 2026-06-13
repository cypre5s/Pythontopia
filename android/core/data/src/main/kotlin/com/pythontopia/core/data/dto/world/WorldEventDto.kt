package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** 世界日历事件，对应后端 `WorldEventDto`；[payload] 为不透明 JSONB。 */
@Serializable
data class WorldEventDto(
    val id: Long,
    val dateIso: String? = null,
    val eventType: String? = null,
    val payload: JsonElement? = null,
    val isProcessed: Boolean = false,
    val createdAt: String? = null,
)
