package com.pythontopia.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [ProceduralArt] 纯函数测试（仅依赖 libGDX Color，无 GL / native）。
 * 守住程序化美术的语义：地形配色可区分、门比屋顶暗、矿洞虚空近黑、NPC 配色按种子稳定且有区分度。
 */
class ProceduralArtTest {

    @Test
    fun `grass and water are distinct`() {
        assertNotEquals(ProceduralArt.townTileColor(0), ProceduralArt.townTileColor(49))
    }

    @Test
    fun `door is darker than roof`() {
        val door = ProceduralArt.townTileColor(23)
        val roof = ProceduralArt.townTileColor(13)
        assertTrue(door.r + door.g + door.b < roof.r + roof.g + roof.b)
    }

    @Test
    fun `unlisted town frame falls back to grass`() {
        assertEquals(ProceduralArt.townTileColor(0), ProceduralArt.townTileColor(130))
    }

    @Test
    fun `dungeon void is near black`() {
        val v = ProceduralArt.dungeonTileColor(9)
        assertTrue(v.r < 0.2f && v.g < 0.2f && v.b < 0.2f)
    }

    @Test
    fun `dungeon floor differs from wall`() {
        assertNotEquals(ProceduralArt.dungeonTileColor(0), ProceduralArt.dungeonTileColor(22))
    }

    @Test
    fun `character palette is deterministic by seed`() {
        assertEquals(
            ProceduralArt.characterPalette("npc_mayor").shirt,
            ProceduralArt.characterPalette("npc_mayor").shirt,
        )
    }

    @Test
    fun `different seeds yield varied shirts`() {
        val shirts = listOf("npc_mayor", "npc_miner", "npc_coach", "npc_sage", "a", "b", "c", "d")
            .map { ProceduralArt.characterPalette(it).shirt }
            .toSet()
        assertTrue(shirts.size > 1)
    }
}
