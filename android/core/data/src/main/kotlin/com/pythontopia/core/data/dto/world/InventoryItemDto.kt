package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

@Serializable
data class InventoryItemDto(
    val itemKey: String,
    val qty: Int,
    val displayName: String? = null,
    val category: String? = null,
    val rarity: String? = null,
)
