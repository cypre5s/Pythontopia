package com.pythontopia.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

/**
 * R-12 demo 唯一暴露给上层（:feature:hub）的 LibGDX 启动入口。
 *
 * 与既有 [TownGameLauncher] 区别：返回的 [Game] 是 [HubGame]（带程序化 hub 渲染 + 玩家移动 +
 * NPC 距离事件），且强制要求外部传入 [HubGameBridge]，避免渲染线程隐式持有 Compose / ViewModel 引用。
 */
object HubGameLauncher {

    fun applicationConfiguration(): AndroidApplicationConfiguration =
        AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            // Hub 场景仅 ShapeRenderer，深度/模板缓冲都不需要：
            depth = 0
            stencil = 0
        }

    fun newGame(bridge: HubGameBridge): Game = HubGame(bridge)
}
