package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

@Serializable
data class WorldSnapshotDto(
    val worldSessionId: Long,
    val dayIndex: Int,
    val monthIndex: Int,
    val weekday: String,
    val season: String,
    val timeOfDay: String,
    val weather: String,
    val inGameMinutes: Int,
    val startedAt: String? = null,
    val lastActiveAt: String? = null,
)
