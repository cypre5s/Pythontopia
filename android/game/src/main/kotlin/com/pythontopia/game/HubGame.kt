package com.pythontopia.game

import com.badlogic.gdx.Game

/**
 * Android LibGDX [Game] shell for the native five-map learning town.
 *
 * 渲染屏使用 [WorldRenderScreen]（Kenney 瓦片 + LPC 精灵 + 镜头跟随）。旧的
 * [HubScreen]（ShapeRenderer 色块）保留仅供单测与回退参考，运行时不再使用。
 */
class HubGame(
    private val bridge: HubGameBridge,
) : Game() {

    override fun create() {
        setScreen(WorldRenderScreen(bridge))
    }

    override fun dispose() {
        screen?.dispose()
        super.dispose()
    }
}
