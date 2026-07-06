package com.pythontopia.game

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20

/** :app 占位 [Game]；[create] 挂载纯色清屏屏。 */
class TownGame : Game() {
    override fun create() {
        setScreen(PlaceholderScreen())
    }
}

class PlaceholderScreen : ScreenAdapter() {
    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.10f, 0.08f, 0.06f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }
}
