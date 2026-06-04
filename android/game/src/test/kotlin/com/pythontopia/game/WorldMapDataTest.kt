package com.pythontopia.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldMapDataTest {

    @Test
    fun `native town exposes five maps`() {
        assertEquals(
            setOf("town_hub", "farm_north", "mine_south", "campus_east", "port_west"),
            WorldMaps.all.map { it.key }.toSet(),
        )
    }

    @Test
    fun `native town contains twenty unique npc anchors`() {
        val npcKeys = WorldMaps.all.flatMap { it.npcs }.map { it.key }.toSet()

        assertEquals(DemoNpcKeys.expected, npcKeys)
        assertEquals(20, npcKeys.size)
    }

    @Test
    fun `all exits point at registered maps`() {
        val mapKeys = WorldMaps.all.map { it.key }.toSet()
        val targets = WorldMaps.all.flatMap { it.exits }.map { it.targetMap }

        assertTrue(targets.isNotEmpty())
        assertTrue(targets.all { it in mapKeys }, "Unknown map target in $targets")
    }
}

private object DemoNpcKeys {
    val expected: Set<String> = setOf(
        "mayor_py",
        "librarian_doc",
        "coach_q",
        "sage_review",
        "gardener",
        "chef",
        "farmer",
        "professor",
        "student_a",
        "sailor",
        "miner",
        "tutor_ai",
        "workshop_master",
        "arena_guard",
        "trainer",
        "farmer_kc",
        "foreman_bug",
        "mine_keeper",
        "class_president",
        "captain_def",
    )
}
