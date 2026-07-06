package com.pythontopia.game

import com.badlogic.gdx.Game

/**
 * LibGDX 主入口：以 [ScreenKey] + [registerScreen] 工厂管理多屏切换；
 * 默认 [HubScreen]；[onPlayerMoved] 向 Compose 冒泡步进坐标与朝向。
 * 单 Game 实例由 :feature:hub 持有，本类不做静态缓存。
 */
class PythontopiaGame(
    private val bridge: HubGameBridge,
    private val onPlayerMoved: ((tileX: Int, tileY: Int, direction: String) -> Unit)? = null,
) : Game() {

    private val screens: MutableMap<ScreenKey, () -> com.badlogic.gdx.Screen> = mutableMapOf()

    /** 当前屏 key；[create] / [switchScreen] 时更新，未 create 时为 null。 */
    var currentScreenKey: ScreenKey? = null
        private set

    private var disposed: Boolean = false

    init {
        registerScreen(ScreenKey.HUB) { HubScreen(bridge, onPlayerMoved) }
    }

    override fun create() {
        switchScreen(ScreenKey.HUB)
    }

    /** 注册屏工厂；同 key 覆盖；延迟到 [switchScreen] 实例化，避免 create 前 new GL 资源。 */
    fun registerScreen(key: ScreenKey, factory: () -> com.badlogic.gdx.Screen) {
        screens[key] = factory
    }

    /** 切换屏；未注册 [key] 时抛 [IllegalStateException]。 */
    fun switchScreen(key: ScreenKey) {
        val factory = screens[key]
            ?: error("ScreenKey $key not registered with PythontopiaGame")
        val next = factory()
        currentScreenKey = key
        setScreen(next)
    }

    /** 覆写以显式 dispose 当前 Screen 的 GL 资源；[disposed] 防 onDestroy 与显式 dispose 双触发。 */
    override fun dispose() {
        if (disposed) return
        disposed = true
        val current = screen
        super.dispose()
        current?.dispose()
        currentScreenKey = null
    }

    /** 地图屏路由 key；当前仅 [HUB] 已注册，其余预留给多图扩展。 */
    enum class ScreenKey {
        HUB,
        FARM_NORTH,
        MINE_SOUTH,
        CAMPUS_EAST,
        PORT_WEST,
    }
}
