package com.pythontopia.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

/** 暴露给 :feature:hub 的 LibGDX 启动入口；[newGame] 须传入 [HubGameBridge]，避免渲染线程隐式持有 UI 引用。 */
object HubGameLauncher {

    fun applicationConfiguration(): AndroidApplicationConfiguration =
        AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            // 2D 精灵渲染无需深度/模板缓冲：
            depth = 0
            stencil = 0
        }

    fun newGame(bridge: HubGameBridge): Game = HubGame(bridge)
}
