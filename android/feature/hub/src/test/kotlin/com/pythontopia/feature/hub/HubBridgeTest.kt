package com.pythontopia.feature.hub

import app.cash.turbine.test
import com.pythontopia.game.HubEvent
import com.pythontopia.game.HubGameBridge
import com.pythontopia.game.TileCoord
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [HubBridge] JVM 单测；MockK 验证 raw bridge 转发，Turbine 断言 Flow 行为。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HubBridgeTest {

    @Test
    fun `drainOnce mirrors raw player tile into playerTile StateFlow`() = runTest {
        val raw: HubGameBridge = mockk()
        every { raw.latestPlayerTile() } returnsMany listOf(
            TileCoord(25, 20),
            TileCoord(26, 20),
        )
        every { raw.poll() } returns null

        val bridge = HubBridge(raw)
        assertEquals(TileCoord(25, 20), bridge.playerTile.value)

        bridge.drainOnce()
        assertEquals(TileCoord(26, 20), bridge.playerTile.value)
    }

    @Test
    fun `drainOnce republishes raw events through events SharedFlow in order`() = runTest {
        val raw: HubGameBridge = mockk()
        every { raw.latestPlayerTile() } returns TileCoord(25, 20)
        every { raw.poll() } returnsMany listOf(
            HubEvent.NpcInRange("mayor_py", 25, 30),
            HubEvent.NpcOutOfRange,
            null,
        )

        val bridge = HubBridge(raw)
        bridge.events.test {
            bridge.drainOnce()

            val first = awaitItem()
            assertTrue(first is HubEvent.NpcInRange)
            assertEquals("mayor_py", (first as HubEvent.NpcInRange).npcKey)

            val second = awaitItem()
            assertTrue(second is HubEvent.NpcOutOfRange)

            expectNoEvents()
        }
    }

    @Test
    fun `start coroutine pumps events until stopped`() = runTest(UnconfinedTestDispatcher()) {
        val raw: HubGameBridge = mockk()
        every { raw.latestPlayerTile() } returns TileCoord(25, 20)
        every { raw.poll() } returnsMany listOf(
            HubEvent.NpcInRange("mayor_py", 25, 30),
            null,
            null,
            null,
        )

        val bridge = HubBridge(raw)
        bridge.events.test {
            bridge.start(backgroundScope)
            // UnconfinedTestDispatcher 下协程立即执行至 delay 挂起点
            val first = awaitItem()
            assertTrue(first is HubEvent.NpcInRange)

            // 推进多个 poll 周期，验证无新事件
            advanceTimeBy(HubBridge.POLL_INTERVAL_MS * 3 + 1)
            bridge.stop()
            expectNoEvents()
        }
    }

    @Test
    fun `requestInteract delegates to raw bridge`() {
        val raw: HubGameBridge = mockk()
        every { raw.latestPlayerTile() } returns TileCoord(25, 20)
        justRun { raw.requestInteract() }

        HubBridge(raw).requestInteract()

        verify(exactly = 1) { raw.requestInteract() }
    }

    @Test
    fun `submitPlayerTile updates raw bridge and StateFlow atomically`() = runTest {
        val raw: HubGameBridge = mockk()
        every { raw.latestPlayerTile() } returns TileCoord(25, 20)
        justRun { raw.updatePlayerTile(any(), any()) }

        val bridge = HubBridge(raw)
        bridge.playerTile.test {
            assertEquals(TileCoord(25, 20), awaitItem())

            bridge.submitPlayerTile(30, 25)
            assertEquals(TileCoord(30, 25), awaitItem())
        }
        verify(exactly = 1) { raw.updatePlayerTile(30, 25) }
    }

    @Test
    fun `submitRuntimeState and setPaused update runtimeState StateFlow`() = runTest {
        val raw: HubGameBridge = mockk()
        every { raw.latestPlayerTile() } returns TileCoord(25, 20)

        val bridge = HubBridge(raw)
        bridge.runtimeState.test {
            val initial = awaitItem()
            assertFalse(initial.paused)
            assertEquals(1f, initial.zoom)

            bridge.setPaused(true)
            val paused = awaitItem()
            assertTrue(paused.paused)
            assertEquals(1f, paused.zoom)

            bridge.submitRuntimeState(HubRuntimeState(paused = false, zoom = 1.5f, timeScale = 2f))
            val zoomed = awaitItem()
            assertFalse(zoomed.paused)
            assertEquals(1.5f, zoomed.zoom)
            assertEquals(2f, zoomed.timeScale)
        }
    }

    @Test
    fun `drainOnce skips StateFlow update when tile unchanged so collectors are not woken up`() = runTest {
        val raw: HubGameBridge = mockk()
        every { raw.latestPlayerTile() } returns TileCoord(25, 20)
        every { raw.poll() } returns null

        val bridge = HubBridge(raw)
        bridge.playerTile.test {
            assertEquals(TileCoord(25, 20), awaitItem())

            repeat(3) { bridge.drainOnce() }

            expectNoEvents()
        }
    }

    @Test
    fun `start replaces previous pump job without leaking`() = runTest {
        val raw: HubGameBridge = mockk()
        every { raw.latestPlayerTile() } returns TileCoord(25, 20)
        every { raw.poll() } returns null

        val bridge = HubBridge(raw)

        bridge.start(backgroundScope)
        bridge.start(backgroundScope)
        bridge.start(backgroundScope)
        bridge.stop()

        assertNotNull(bridge.playerTile)
    }
}
