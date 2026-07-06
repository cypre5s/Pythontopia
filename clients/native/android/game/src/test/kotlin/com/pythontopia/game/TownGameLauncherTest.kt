package com.pythontopia.game

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** 验证 [TownGameLauncher] 配置契约；不实例化 Game、不触碰 GL/Android 原生调用。 */
class TownGameLauncherTest {

    @Test
    fun `applicationConfiguration returns non-null config with accelerometer disabled`() {
        val config = TownGameLauncher.applicationConfiguration()

        assertNotNull(config)
        assertFalse(config.useAccelerometer)
    }
}
