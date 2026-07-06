package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

/** 玩家位置 PATCH 请求；direction 为 8 向字符串。 */
@Serializable
data class PlayerStatePatch(
    val tileX: Int,
    val tileY: Int,
    val direction: String,
)
