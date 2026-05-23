package com.pythontopia.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.Screen
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [PythontopiaGame] 单元测试。
 *
 * 关于 headless 测试的取舍：
 * `gdx-backend-headless`（或 lwjgl3）在本仓库 R-12 阶段未引入（详见 `:game/build.gradle.kts`
 * 与 `gradle/libs.versions.toml`，AGENTS.md 禁止本期新增依赖）。因此本测试用 MockK 把
 * [Gdx] 的 `graphics` / `input` / `gl` 静态字段塞入 relaxed mock，再用 mocked [Screen]
 * 替代 [HubScreen]（后者构造时就 `new ShapeRenderer()` 需要真实 GL 上下文），
 * 验证 [PythontopiaGame.switchScreen] / [PythontopiaGame.dispose] 的生命周期契约。
 *
 * 真实的 OpenGL 帧绘制回归留给 R-13 接入 `gdx-backend-headless` 后的 Roborazzi 截图测试
 * （docs/android/03-rendering-libgdx-vs-compose.md §9.1）。
 */
class PythontopiaGameTest {

    private val bridge = HubGameBridge()

    @BeforeEach
    fun installFakeGdx() {
        Gdx.graphics = mockk<Graphics>(relaxed = true).also {
            every { it.width } returns FAKE_VIEWPORT_WIDTH
            every { it.height } returns FAKE_VIEWPORT_HEIGHT
        }
        Gdx.input = mockk(relaxed = true)
        Gdx.gl = mockk(relaxed = true)
        Gdx.gl20 = Gdx.gl
        Gdx.app = mockk(relaxed = true)
    }

    @AfterEach
    fun uninstallFakeGdx() {
        Gdx.graphics = null
        Gdx.input = null
        Gdx.gl = null
        Gdx.gl20 = null
        Gdx.app = null
    }

    @Test
    fun `constructor leaves screen and currentScreenKey null until create runs`() {
        val game = PythontopiaGame(bridge)
        assertNull(game.screen)
        assertNull(game.currentScreenKey)
    }

    @Test
    fun `switchScreen for unknown key fails fast with descriptive message`() {
        val game = PythontopiaGame(bridge)
        val exception = assertThrows(IllegalStateException::class.java) {
            game.switchScreen(PythontopiaGame.ScreenKey.FARM_NORTH)
        }
        assert(exception.message!!.contains("FARM_NORTH")) {
            "Exception message should name the missing key. Got: ${exception.message}"
        }
    }

    @Test
    fun `switchScreen invokes the registered factory exactly once and updates currentScreenKey`() {
        val game = PythontopiaGame(bridge)
        val mockScreen: Screen = mockk(relaxed = true)
        var factoryInvocations = 0

        game.registerScreen(PythontopiaGame.ScreenKey.HUB) {
            factoryInvocations++
            mockScreen
        }
        game.switchScreen(PythontopiaGame.ScreenKey.HUB)

        assertEquals(1, factoryInvocations)
        assertEquals(PythontopiaGame.ScreenKey.HUB, game.currentScreenKey)
        assertEquals(mockScreen, game.screen)
        verify(exactly = 1) { mockScreen.show() }
        verify(exactly = 1) { mockScreen.resize(FAKE_VIEWPORT_WIDTH, FAKE_VIEWPORT_HEIGHT) }
    }

    @Test
    fun `switchScreen hides previous screen before showing the next one`() {
        val game = PythontopiaGame(bridge)
        val first: Screen = mockk(relaxed = true)
        val second: Screen = mockk(relaxed = true)

        game.registerScreen(PythontopiaGame.ScreenKey.HUB) { first }
        game.registerScreen(PythontopiaGame.ScreenKey.FARM_NORTH) { second }
        game.switchScreen(PythontopiaGame.ScreenKey.HUB)
        game.switchScreen(PythontopiaGame.ScreenKey.FARM_NORTH)

        verify(exactly = 1) { first.show() }
        verify(exactly = 1) { first.hide() }
        verify(exactly = 1) { second.show() }
        assertEquals(PythontopiaGame.ScreenKey.FARM_NORTH, game.currentScreenKey)
        assertEquals(second, game.screen)
    }

    @Test
    fun `dispose on uncreated game is safe and frees no screen`() {
        val game = PythontopiaGame(bridge)
        game.dispose()
        assertNull(game.screen)
    }

    @Test
    fun `dispose forwards to current screen exactly once and clears Game without leak`() {
        val game = PythontopiaGame(bridge)
        val mockScreen: Screen = mockk(relaxed = true)
        justRun { mockScreen.dispose() }
        game.registerScreen(PythontopiaGame.ScreenKey.HUB) { mockScreen }
        game.switchScreen(PythontopiaGame.ScreenKey.HUB)

        game.dispose()

        verify(exactly = 1) { mockScreen.dispose() }
        // 双 dispose 不应再次触发，验证 Game 内部"夹钳"住单次 dispose 不重复。
        game.dispose()
        verify(exactly = 1) { mockScreen.dispose() }
    }

    @Test
    fun `registerScreen with same key overwrites previous factory`() {
        val game = PythontopiaGame(bridge)
        val firstFactory: Screen = mockk(relaxed = true)
        val secondFactory: Screen = mockk(relaxed = true)

        game.registerScreen(PythontopiaGame.ScreenKey.HUB) { firstFactory }
        game.registerScreen(PythontopiaGame.ScreenKey.HUB) { secondFactory }
        game.switchScreen(PythontopiaGame.ScreenKey.HUB)

        assertEquals(secondFactory, game.screen)
        verify(exactly = 0) { firstFactory.show() }
        verify(exactly = 1) { secondFactory.show() }
    }

    @Test
    fun `onPlayerMoved callback is plumbed through constructor without invoking on construction`() {
        var lastMove: Triple<Int, Int, String>? = null
        val callback: (Int, Int, String) -> Unit = { x, y, dir -> lastMove = Triple(x, y, dir) }

        val game = PythontopiaGame(bridge, onPlayerMoved = callback)
        assertNull(lastMove) { "Constructor must not invoke onPlayerMoved" }
        assertNull(game.currentScreenKey)
    }

    companion object {
        private const val FAKE_VIEWPORT_WIDTH = 1080
        private const val FAKE_VIEWPORT_HEIGHT = 1920
    }
}
