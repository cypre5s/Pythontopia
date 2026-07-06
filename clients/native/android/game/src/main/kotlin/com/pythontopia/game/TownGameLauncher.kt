package com.pythontopia.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

/** 暴露给 :app 的 LibGDX 启动入口；由 Fragment 调用 [applicationConfiguration] / [newGame] 完成初始化。 */
object TownGameLauncher {

    fun applicationConfiguration(): AndroidApplicationConfiguration =
        AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
        }

    fun newGame(): Game = TownGame()
}
