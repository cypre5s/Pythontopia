package com.pythontopia.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 仅验证 [HubGameBridge] 的 Compose ↔ 渲染线程契约：不触碰任何 LibGDX 类型，
 * 在普通 JVM 单测环境就能跑通。
 */
class HubGameBridgeTest {

    @Test
    fun `default player tile is spawn coords`() {
        val bridge = HubGameBridge()
        val tile = bridge.latestPlayerTile()
        assertEquals(HubGameBridge.SPAWN_TILE_X, tile.x)
        assertEquals(HubGameBridge.SPAWN_TILE_Y, tile.y)
    }

    @Test
    fun `emit then poll returns the same event in FIFO order`() {
        val bridge = HubGameBridge()
        bridge.emit(HubEvent.NpcInRange("mayor_py", 25, 10))
        bridge.emit(HubEvent.NpcOutOfRange)

        val first = bridge.poll()
        val second = bridge.poll()
        val third = bridge.poll()

        assertTrue(first is HubEvent.NpcInRange)
        assertEquals("mayor_py", (first as HubEvent.NpcInRange).npcKey)
        assertTrue(second is HubEvent.NpcOutOfRange)
        assertNull(third)
    }

    @Test
    fun `interact request is consumed exactly once`() {
        val bridge = HubGameBridge()
        bridge.requestInteract()

        assertTrue(bridge.consumeInteractRequest())
        assertFalse(bridge.consumeInteractRequest())
    }

    @Test
    fun `updatePlayerTile reflects in latestPlayerTile`() {
        val bridge = HubGameBridge()
        bridge.updatePlayerTile(7, 42)
        val tile = bridge.latestPlayerTile()
        assertEquals(7, tile.x)
        assertEquals(42, tile.y)
    }
}
