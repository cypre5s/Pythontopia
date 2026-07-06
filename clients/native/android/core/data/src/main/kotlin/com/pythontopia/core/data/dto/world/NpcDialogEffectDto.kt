package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

/** NPC 对话效果；type：COMPLETE_NODE（完成节点）、SET_FLAG（flags 键值）。 */
@Serializable
data class NpcDialogEffectDto(
    val type: String,
    val key: String? = null,
    val value: String? = null,
)
