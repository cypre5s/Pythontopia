package com.pythontopia.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldMapDataTest {

    @Test
    fun `native town exposes five maps for compat`() {
        assertEquals(
            setOf("town_hub", "farm_north", "mine_south", "campus_east", "port_west"),
            WorldMaps.all.map { it.key }.toSet(),
        )
    }

    @Test
    fun `town hub aligns twelve main npc anchors with web town_hub json`() {
        val npcKeys = WorldMaps.townHub.npcs.map { it.key }.toSet()

        assertEquals(TownHubNpcKeys.expected, npcKeys)
        assertEquals(12, npcKeys.size)
    }

    @Test
    fun `all exits point at registered maps`() {
        val mapKeys = WorldMaps.all.map { it.key }.toSet()
        val targets = WorldMaps.all.flatMap { it.exits }.map { it.targetMap }

        assertTrue(targets.isNotEmpty())
        assertTrue(targets.all { it in mapKeys }, "Unknown map target in $targets")
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
