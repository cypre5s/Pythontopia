package com.pythontopia.game

import com.badlogic.gdx.Game

/** LibGDX [Game] 外壳；[create] 挂载 [WorldRenderScreen]（Kenney 瓦片 + LPC 精灵 + 镜头跟随）。 */
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
