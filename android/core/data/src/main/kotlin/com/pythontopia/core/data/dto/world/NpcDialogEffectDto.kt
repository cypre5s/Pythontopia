package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

/**
 * 后端 NpcDialogEffectDto 等价物，type 取值见
 * `backend/src/main/java/com/pythontopia/world/npc/dto/NpcDialogEffectDto.java`：
 * - FRIENDSHIP_DELTA：好感度增减
 * - COMPLETE_NODE：将 key 写入 completed_node_ids
 * - SET_FLAG：在 flags JSON 里写 key=value
 */
@Serializable
data class NpcDialogEffectDto(
    val type: String,
    val delta: Int? = null,
    val key: String? = null,
    val value: String? = null,
)
