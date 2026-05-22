package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

/**
 * `POST /api/game/bootstrap` 聚合响应的 Android demo 子集。
 * 后端 GameBootstrapResponse 还包含 profile/quests/mastery/path/skills/saveSlot/pendingEvents 等字段，
 * 由于 Android-R12 demo 只用到 world / player / inventory / currency / npcStates，
 * 这里仅声明 demo 直接读到的字段；其余字段保留给 Android-R13+ 扩展。
 */
@Serializable
data class GameBootstrapResponseDto(
    val world: WorldSnapshotDto,
    val player: PlayerStateDto,
    val inventory: List<InventoryItemDto> = emptyList(),
    val currency: PlayerCurrencyDto? = null,
    val npcStates: List<NpcDialogStateDto> = emptyList(),
)
