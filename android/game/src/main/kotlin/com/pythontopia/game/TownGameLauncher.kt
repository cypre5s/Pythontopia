package com.pythontopia.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

/**
 * R1 期暴露给 :app 的最小启动入口。
 *
 * Android-R6 真正实现 GameSurfaceFragment 时,
 * 由该 Fragment 调用 [applicationConfiguration] / [newGame]
 * 完成 LibGDX 的 AndroidFragmentApplication 初始化。
 */
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
