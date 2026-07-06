package com.pythontopia.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TiledMapVisualTest {

    @Test
    fun `parses dimensions and flips tiled rows into y-up grid`() {
        // 2×2 地图：Tiled 行优先自顶向下；顶行 buildings 含实心格，验证 y-up 翻转。
        val json = """
            {
              "width": 2, "height": 2,
              "tilesets": [{"firstgid": 1, "image": "../tilesets/town.png"}],
              "layers": [
                {"type": "tilelayer", "name": "ground", "data": [1,1, 1,1]},
                {"type": "tilelayer", "name": "buildings", "data": [0,5, 0,0]}
              ]
            }
        """.trimIndent()

        val map = TiledMapVisual.parse("town_hub", json)

        assertEquals(2, map.width)
        assertEquals(2, map.height)
        assertFalse(map.usesDungeon)
        // Tiled 顶行（北）应对应 y-up 最高行 y=1。
        assertTrue(map.isSolid(1, 1), "top-right solid tile should land at y-up (x=1, y=1)")
        assertFalse(map.isSolid(0, 1))
        assertFalse(map.isSolid(0, 0))
        assertFalse(map.isSolid(1, 0))
    }

    @Test
    fun `out of bounds is always solid`() {
        val json = """
            {"width":1,"height":1,"tilesets":[{"firstgid":1,"image":"town.png"}],
             "layers":[{"type":"tilelayer","name":"buildings","data":[0]}]}
        """.trimIndent()
        val map = TiledMapVisual.parse("town_hub", json)
        assertTrue(map.isSolid(-1, 0))
        assertTrue(map.isSolid(0, -1))
        assertTrue(map.isSolid(1, 0))
        assertTrue(map.isSolid(0, 1))
        assertFalse(map.isSolid(0, 0))
    }

    @Test
    fun `mine map is detected as dungeon tileset`() {
        val json = """
            {"width":1,"height":1,
             "tilesets":[{"firstgid":1,"image":"../pixel/tilesets/kenney-tiny-dungeon/Tilemap/tilemap_packed.png"}],
             "layers":[]}
        """.trimIndent()
        val map = TiledMapVisual.parse("mine_south", json)
        assertTrue(map.usesDungeon)
    }
}
