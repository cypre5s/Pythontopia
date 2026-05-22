package com.pythontopia.game

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * R1 仅验证 Kotlin 包装层的契约, 不在 JVM 上直接 import LibGDX 的
 * AndroidApplicationConfiguration 类型 (走类型推断), 也不实例化 Game 子类、
 * 不触碰任何 GL / Android 原生调用; 真实的运行时验证留给 Android-R6
 * 的 instrumentation 测试。
 */
class TownGameLauncherTest {

    @Test
    fun `applicationConfiguration returns non-null config with accelerometer disabled`() {
        val config = TownGameLauncher.applicationConfiguration()

        assertNotNull(config)
        assertFalse(config.useAccelerometer)
    }
}
