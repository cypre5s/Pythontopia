package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

/** `POST /api/game/bootstrap` 客户端子集；未声明字段由 kotlinx.serialization ignoreUnknownKeys 忽略。 */
@Serializable
data class GameBootstrapResponseDto(
    val world: WorldSnapshotDto,
    val player: PlayerStateDto,
    val inventory: List<InventoryItemDto> = emptyList(),
    val currency: PlayerCurrencyDto? = null,
    val npcStates: List<NpcDialogStateDto> = emptyList(),
)
