package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

@Serializable
data class PlayerStateDto(
    val tileX: Int,
    val tileY: Int,
    val direction: String,
    val mapKey: String? = null,
)
