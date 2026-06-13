package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 存档槽，对应后端 `SaveSlotDto`。每个用户 6 槽（`slotIndex` 0..5，后端 `@Min(0) @Max(5)`），至多一个 [isActive]。
 *
 * 背包不是独立端点：它以 [inventoryItems] JSONB 内嵌在存档快照中（迁移 V114），仅供存档读写，
 * **不**是顶层货币/商店能力（R-14 世界收缩后无背包/货币/商店 REST）。
 */
@Serializable
data class SaveSlotDto(
    val slotIndex: Int,
    val displayName: String? = null,
    val isActive: Boolean = false,
    val createdAt: String? = null,
    val lastPlayedAt: String? = null,
    val snapshot: JsonElement? = null,
    val sceneName: String? = null,
    val unlockedBuildings: List<String> = emptyList(),
    val accumulatedPlaySeconds: Long = 0L,
    val inventoryItems: JsonElement? = null,
)
