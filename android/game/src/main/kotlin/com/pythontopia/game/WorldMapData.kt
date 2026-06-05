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
        // R-14「世界收缩方案 A」：收敛为单一中央小镇 hub，移除通往外围 4 图的边缘出口。
        // 外围图定义仍保留在下方（数据 / 单测约束需要），但正常游玩无法再从 hub 走到它们。
        exits = emptyList(),
        // NPC 锚点对齐 Web 端 frontend/public/assets/maps/town_hub.json 的 npc 对象层（y-up = height-1-tiledRow）。
        // R-14 把原外围图的 mine_keeper / foreman_bug / captain_def / class_president 一并收进 hub，
        // 使收缩后这些 NPC 仍可达；田老（farmer_kc）按 R-14 移除农场玩法，不再进 hub（仅留在 farm_north 数据里供测试）。
        npcs = listOf(
            WorldNpc("mayor_py", 25, 29),
            WorldNpc("librarian_doc", 12, 27),
            WorldNpc("coach_q", 44, 21),
            WorldNpc("sage_review", 38, 27),
            WorldNpc("gardener", 6, 33),
            WorldNpc("chef", 38, 7),
            WorldNpc("farmer", 6, 35),
            WorldNpc("professor", 12, 29),
            WorldNpc("student_a", 15, 25),
            WorldNpc("sailor", 4, 11),
            WorldNpc("miner", 24, 3),
            WorldNpc("tutor_ai", 30, 23),
            WorldNpc("workshop_master", 42, 13),
            WorldNpc("arena_guard", 38, 3),
            WorldNpc("trainer", 10, 3),
            WorldNpc("mine_keeper", 21, 6),
            WorldNpc("foreman_bug", 24, 6),
            WorldNpc("captain_def", 4, 15),
            WorldNpc("class_president", 16, 23),
        ),
    )

    // --- R-14 起以下 4 张外围图不再作为主线场景（town_hub 已无出口可达）。 ---
    // 保留定义仅为：① 满足 “5 图 / 20 NPC 锚点” 单测约束；② 让通往 hub 的回程出口存在
    // （`all exits point at registered maps` 单测要求出口集合非空）。其瓦片 JSON 也仍留在
    // assets/maps/ 内，但运行时不会被加载（见 WorldRenderScreen.show 强制载入 town_hub）。
    val farmNorth: WorldMapDefinition = WorldMapDefinition(
        key = "farm_north",
        width = 40,
        height = 40,
        spawnX = 20,
        spawnY = 3,
        groundColor = PixelTheme.success,
        pathColor = PixelTheme.primary100,
        buildings = listOf(
            TileRect(26, 28, 5, 4),
            TileRect(28, 19, 4, 3),
        ),
        exits = listOf(
            WorldExit(DEFAULT_WORLD_MAP_KEY, TileRect(18, 0, 4, 1)),
        ),
        npcs = listOf(
            WorldNpc("farmer_kc", 28, 27),
        ),
    )

    val mineSouth: WorldMapDefinition = WorldMapDefinition(
        key = "mine_south",
        width = 30,
        height = 30,
        spawnX = 15,
        spawnY = 28,
        groundColor = PixelTheme.gray700,
        pathColor = PixelTheme.gray500,
        buildings = listOf(
            TileRect(8, 16, 2, 2),
            TileRect(20, 16, 2, 2),
        ),
        exits = listOf(
            WorldExit(DEFAULT_WORLD_MAP_KEY, TileRect(13, 29, 4, 1)),
        ),
        npcs = listOf(
            WorldNpc("foreman_bug", 14, 25),
            WorldNpc("mine_keeper", 15, 20),
        ),
    )

    val campusEast: WorldMapDefinition = WorldMapDefinition(
        key = "campus_east",
        width = 40,
        height = 30,
        spawnX = 2,
        spawnY = 15,
        groundColor = PixelTheme.primary50,
        pathColor = PixelTheme.gray300,
        buildings = listOf(
            TileRect(8, 22, 5, 4),
            TileRect(18, 22, 5, 4),
            TileRect(28, 22, 5, 4),
            TileRect(26, 5, 4, 3),
            TileRect(16, 8, 5, 4),
        ),
        exits = listOf(
            WorldExit(DEFAULT_WORLD_MAP_KEY, TileRect(0, 13, 1, 4)),
        ),
        npcs = listOf(
            WorldNpc("librarian_doc", 10, 21),
            WorldNpc("coach_q", 20, 21),
            WorldNpc("sage_review", 30, 21),
        ),
    )

    val portWest: WorldMapDefinition = WorldMapDefinition(
        key = "port_west",
        width = 35,
        height = 25,
        spawnX = 33,
        spawnY = 13,
        groundColor = PixelTheme.accentBlue,
        pathColor = PixelTheme.primary100,
        buildings = listOf(
            TileRect(3, 17, 2, 4),
        ),
        exits = listOf(
            WorldExit(DEFAULT_WORLD_MAP_KEY, TileRect(34, 11, 1, 4)),
        ),
        npcs = listOf(
            WorldNpc("class_president", 25, 16),
            WorldNpc("captain_def", 5, 16),
        ),
    )

    val all: List<WorldMapDefinition> = listOf(townHub, farmNorth, mineSouth, campusEast, portWest)

    fun byKey(key: String?): WorldMapDefinition =
        all.firstOrNull { it.key == key } ?: townHub
}

const val DEFAULT_WORLD_MAP_KEY: String = "town_hub"
internal const val ANDROID_TILE_SIZE: Int = 32
