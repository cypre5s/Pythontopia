package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

/** NPC 对话效果；type：FRIENDSHIP_DELTA（好感度）、COMPLETE_NODE（完成节点）、SET_FLAG（flags 键值）。 */
@Serializable
data class NpcDialogEffectDto(
    val type: String,
    val delta: Int? = null,
    val key: String? = null,
    val value: String? = null,
)
