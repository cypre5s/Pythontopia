package com.pythontopia.game

/**
 * 小地图公开数据（供 :feature:hub 的 Compose HUD 渲染）。
 * [WorldMaps] 为 internal，这里暴露一个只读快照，避免外部模块直接耦合内部地图定义。
 */
data class MiniRect(val x: Int, val y: Int, val w: Int, val h: Int)

data class MiniNpc(val key: String, val x: Int, val y: Int)

data class MinimapData(
    val key: String,
    val width: Int,
    val height: Int,
    val buildings: List<MiniRect>,
    val npcs: List<MiniNpc>,
)

/** 任意 key 都回落到 town_hub（与 [WorldMaps.byKey] 同语义）。 */
fun minimapData(mapKey: String?): MinimapData {
    val m = WorldMaps.byKey(mapKey)
    return MinimapData(
        key = m.key,
        width = m.width,
        height = m.height,
        buildings = m.buildings.map { MiniRect(it.x, it.y, it.w, it.h) },
        npcs = m.npcs.map { MiniNpc(it.key, it.x, it.y) },
    )
}
