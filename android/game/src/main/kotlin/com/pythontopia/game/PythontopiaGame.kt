package com.pythontopia.game

import com.badlogic.gdx.Game

/**
 * Pythontopia LibGDX 主入口（R-12 第二阶段）。
 *
 * 与既有 [HubGame] 的差异（两者可共存，[HubGame] 走老 demo 路径）：
 * - 把屏幕切换抽象成 [ScreenKey] + [registerScreen] 工厂，方便 R-13 接入 5 张地图
 *   （hub / farm_north / mine_south / campus_east / port_west），切图时只需
 *   `switchScreen(ScreenKey.FARM_NORTH)`，不用直接 new 各种 Screen。
 * - 默认屏幕用 [HubScreen]（基于 [PlayerActor]+[InputController]+[PixelTheme]），
 *   不再是 [HubScene]；后者保留是为了不破坏既有 [HubLibGdxFragment] demo。
 * - 提供 [onPlayerMoved] 回调把玩家每步的世界坐标 + 朝向冒泡给 Compose 层，
 *   Compose 通过 [HubBridge]（feature:hub 内）转给 `HubRepository.reportMove` 上报后端。
 *
 * 单 Game 实例约束：由 `:feature:hub` 的 `HubLibGdxView` 在 Application 级保存当前 Game 引用
 * 保证；本类不做静态缓存，避免 GL 上下文跨 Activity 错挂。
 */
class PythontopiaGame(
    private val bridge: HubGameBridge,
    private val onPlayerMoved: ((tileX: Int, tileY: Int, direction: String) -> Unit)? = null,
) : Game() {

    private val screens: MutableMap<ScreenKey, () -> com.badlogic.gdx.Screen> = mutableMapOf()

    /**
     * 当前激活的屏幕 key；外部读用于状态显示（如"现在在 farm_north"）。
     * 在 [create] / [switchScreen] 时更新；尚未 create 时为 null。
     */
    var currentScreenKey: ScreenKey? = null
        private set

    private var disposed: Boolean = false

    init {
        registerScreen(ScreenKey.HUB) { HubScreen(bridge, onPlayerMoved) }
    }

    override fun create() {
        switchScreen(ScreenKey.HUB)
    }

    /**
     * 注册一个屏幕工厂。同 key 重复注册会覆盖；工厂函数延迟到 [switchScreen] 时调用，
     * 避免在 Game.create 之前过早实例化（部分 Screen 子类构造里直接 new GL 资源会崩）。
     *
     * R-13 拓展示例：
     * ```
     * game.registerScreen(ScreenKey.FARM_NORTH) { FarmNorthScreen(bridge) }
     * game.switchScreen(ScreenKey.FARM_NORTH)
     * ```
     */
    fun registerScreen(key: ScreenKey, factory: () -> com.badlogic.gdx.Screen) {
        screens[key] = factory
    }

    /**
     * 切换屏幕。调用 [Game.setScreen] 时旧 Screen 会自动 hide → 新 Screen show。
     * 若 [key] 未注册，抛 [IllegalStateException]（让上层快速发现路由错配）。
     */
    fun switchScreen(key: ScreenKey) {
        val factory = screens[key]
            ?: throw IllegalStateException("ScreenKey $key not registered with PythontopiaGame")
        val next = factory()
        currentScreenKey = key
        setScreen(next)
    }

    /**
     * LibGDX 默认 [Game.dispose] 仅调用 `screen.hide()`，会导致 SpriteBatch / Texture / ShapeRenderer
     * 等 GL 资源泄漏（文档 §11.3 警告）。此处覆写：super 调用 hide → 显式 dispose 当前 Screen。
     * 用 [disposed] 哨兵避免重复 dispose（[com.badlogic.gdx.backends.android.AndroidApplication.onDestroy]
     * 与显式 dispose 可能双触发）。
     *
     * 不再额外 `setScreen(null)`，因为它会再次触发 `screen.hide()`（虽然 hide 通常幂等，但浪费一帧
     * 调用栈）；Game 实例本身在 dispose 后会被 GC，未释放的引用不影响下次 `initializeForView` 的新
     * Game 实例。
     */
    override fun dispose() {
        if (disposed) return
        disposed = true
        val current = screen
        super.dispose()
        current?.dispose()
        currentScreenKey = null
    }

    /**
     * 5 张地图 + 占位的 R-13 演进枚举。本 R-12 仅 [HUB] 一项可用，其它常量保留是为了
     * IDE 自动补全和路由码可读性，避免后期再大改这个 enum 触发跨模块改动。
     */
    enum class ScreenKey {
        HUB,
        FARM_NORTH,
        MINE_SOUTH,
        CAMPUS_EAST,
        PORT_WEST,
    }
}
