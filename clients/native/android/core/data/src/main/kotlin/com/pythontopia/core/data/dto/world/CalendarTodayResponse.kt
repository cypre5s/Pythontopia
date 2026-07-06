package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

@Serializable
data class CalendarTodayResponse(
    val dayIndex: Int,
    val monthIndex: Int,
    val weekday: String,
    val season: String,
    val timeOfDay: String,
    val weather: String,
    val inGameMinutes: Int,
)
