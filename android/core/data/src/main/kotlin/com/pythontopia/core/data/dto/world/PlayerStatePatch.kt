package com.pythontopia.core.data.dto.world

import kotlinx.serialization.Serializable

/**
 * 8 方向降级写入：input.ts 在 Web 端返回 ExtendedDirection（8 向），
 * Android 端同样支持，但后端 V113 迁移已落地 8 向，无需再走 toServerDirection 降级。
 */
@Serializable
data class PlayerStatePatch(
    val tileX: Int,
    val tileY: Int,
    val direction: String,
)
