package com.pythontopia.game

import com.badlogic.gdx.graphics.Color

internal data class TileRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun contains(tileX: Int, tileY: Int): Boolean =
        tileX in x until (x + w) && tileY in y until (y + h)
}

internal data class WorldNpc(val key: String, val x: Int, val y: Int)

internal data class WorldExit(val targetMap: String, val area: TileRect)

internal data class WorldMapDefinition(
    val key: String,
    val width: Int,
    val height: Int,
    val spawnX: Int,
    val spawnY: Int,
    val groundColor: Color,
    val pathColor: Color,
    val buildings: List<TileRect>,
    val exits: List<WorldExit>,
    val npcs: List<WorldNpc>,
) {
    val pixelWidth: Int = width * ANDROID_TILE_SIZE
    val pixelHeight: Int = height * ANDROID_TILE_SIZE
}

internal object WorldMaps {

    val townHub: WorldMapDefinition = WorldMapDefinition(
        key = DEFAULT_WORLD_MAP_KEY,
        width = 50,
        height = 40,
        spawnX = 25,
        spawnY = 20,
        groundColor = PixelTheme.ground,
        pathColor = PixelTheme.path,
        buildings = listOf(
            TileRect(23, 31, 4, 5),
            TileRect(16, 31, 4, 4),
            TileRect(30, 31, 4, 4),
            TileRect(8, 8, 4, 4),
            TileRect(22, 8, 4, 4),
            TileRect(36, 8, 4, 4),
            TileRect(4, 33, 4, 4),
            TileRect(10, 28, 5, 4),
            TileRect(36, 28, 4, 4),
            TileRect(40, 12, 4, 4),
            TileRect(22, 4, 3, 2),
            TileRect(36, 3, 4, 3),
            TileRect(8, 3, 4, 3),
        ),
        // R-14 世界收缩（方案 A）：主线仅 town_hub，单图世界、无边缘出口。
        exits = emptyList(),
        // R-14：12 主线 NPC 锚点对齐 frontend/public/assets/maps/town_hub.json（y-up = height-1-tiledRow）。
        npcs = listOf(
            WorldNpc("mayor_py", 25, 29),
            WorldNpc("librarian_doc", 11, 27),
            WorldNpc("professor", 14, 27),
            WorldNpc("coach_q", 10, 6),
            WorldNpc("sage_review", 37, 27),
            WorldNpc("student_a", 22, 17),
            WorldNpc("workshop_master", 42, 11),
            WorldNpc("arena_guard", 37, 2),
            WorldNpc("mine_keeper", 21, 6),
            WorldNpc("foreman_bug", 25, 6),
            WorldNpc("captain_def", 5, 15),
            WorldNpc("class_president", 18, 18),
        ),
    )

    // R-14 世界收缩（方案 A）：仅保留单一中央小镇 town_hub；旧 5 图世界的外围图
    // （farm_north / mine_south / campus_east / port_west）已整体移除，不再作主线/支线场景。
    val all: List<WorldMapDefinition> = listOf(townHub)

    /** 单图世界下任何 key 都回落到 town_hub（防御性：历史存档可能停在已删除的外围图）。 */
    fun byKey(key: String?): WorldMapDefinition =
        all.firstOrNull { it.key == key } ?: townHub
}

const val DEFAULT_WORLD_MAP_KEY: String = "town_hub"
internal const val ANDROID_TILE_SIZE: Int = 32
