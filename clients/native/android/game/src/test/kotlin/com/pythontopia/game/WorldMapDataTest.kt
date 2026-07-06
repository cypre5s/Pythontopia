package com.pythontopia.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldMapDataTest {

    @Test
    fun `native town exposes only town_hub after R-14 single-hub convergence`() {
        assertEquals(listOf("town_hub"), WorldMaps.all.map { it.key })
    }

    @Test
    fun `town hub aligns twelve main npc anchors with web town_hub json`() {
        val npcKeys = WorldMaps.townHub.npcs.map { it.key }.toSet()

        assertEquals(TownHubNpcKeys.expected, npcKeys)
        assertEquals(12, npcKeys.size)
    }

    @Test
    fun `town hub is a single self-contained map and byKey falls back to it`() {
        assertTrue(WorldMaps.townHub.exits.isEmpty())
        // 历史存档若停在已删除的外围图，byKey 回落到 town_hub。
        assertEquals(WorldMaps.townHub, WorldMaps.byKey("mine_south"))
        assertEquals(WorldMaps.townHub, WorldMaps.byKey(null))
    }
}

private object TownHubNpcKeys {
    val expected: Set<String> = setOf(
        "mayor_py",
        "librarian_doc",
        "professor",
        "coach_q",
        "sage_review",
        "student_a",
        "workshop_master",
        "arena_guard",
        "mine_keeper",
        "foreman_bug",
        "captain_def",
        "class_president",
    )
}
