package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

@Serializable
data class PlayerCurrencyDto(
    val coins: Long = 0L,
    val gems: Long = 0L,
)
