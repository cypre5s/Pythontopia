package com.pythontopia.feature.arena

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [CountdownTimer] 的可测部分：纯函数 [formatRemaining] / [classifyPhase] / [phaseLabel]。
 *
 * **关于"Compose UI Test"**：模块 `:feature:arena` 的 build.gradle.kts 当前没有声明
 * `ui-test-junit4` / `compose-ui-test-manifest` 等 androidTest 依赖，且任务硬约束
 * "不引入新依赖"。所以此处对 Composable [CountdownTimer] 本身的渲染断言被降级为对其
 * 内部纯函数的 JVM 单测：
 *
 * - [formatRemaining]：mm:ss 的边界（0、< 1 分、99 分、99 分以上溢出兜底）
 * - [classifyPhase]：UPCOMING / RUNNING / ENDED 三阶段在边界点的归类
 * - [phaseLabel]：三阶段的中文描述前缀正确
 *
 * 当后续引入 Compose UI Test 依赖（按 ADR-001 §1 决策 8 的 Beta+1 计划），可在
 * `src/androidTest/kotlin/com/pythontopia/feature/arena/CountdownTimerUiTest.kt` 中补
 * 充 `composeTestRule.setContent { CountdownTimer(...) }` + 截图断言，无需改本文件。
 */
class CountdownTimerTest {

    @Test
    fun `formatRemaining clamps non-positive ms to zero string`() {
        assertEquals("00:00", formatRemaining(0))
        assertEquals("00:00", formatRemaining(-1))
        assertEquals("00:00", formatRemaining(-10_000))
    }

    @Test
    fun `formatRemaining zero-pads minutes and seconds under one minute`() {
        assertEquals("00:01", formatRemaining(1_000))
        assertEquals("00:30", formatRemaining(30_000))
        assertEquals("00:59", formatRemaining(59_999))
    }

    @Test
    fun `formatRemaining converts minute boundary correctly`() {
        assertEquals("01:00", formatRemaining(60_000))
        assertEquals("05:30", formatRemaining(5L * 60L * 1000L + 30L * 1000L))
        assertEquals("99:59", formatRemaining(99L * 60L * 1000L + 59L * 1000L))
    }

    @Test
    fun `formatRemaining caps at 99 plus when exceeding two-digit minutes`() {
        assertEquals("99:59+", formatRemaining(100L * 60L * 1000L))
        assertEquals("99:59+", formatRemaining(10L * 3600L * 1000L))
    }

    @Test
    fun `classifyPhase returns UPCOMING when now is strictly before start`() {
        assertEquals(TimerPhase.UPCOMING, classifyPhase(now = 0, startEpochMs = 10, endEpochMs = 20))
        assertEquals(TimerPhase.UPCOMING, classifyPhase(now = 9, startEpochMs = 10, endEpochMs = 20))
    }

    @Test
    fun `classifyPhase returns RUNNING at start inclusive and before end exclusive`() {
        assertEquals(TimerPhase.RUNNING, classifyPhase(now = 10, startEpochMs = 10, endEpochMs = 20))
        assertEquals(TimerPhase.RUNNING, classifyPhase(now = 15, startEpochMs = 10, endEpochMs = 20))
        assertEquals(TimerPhase.RUNNING, classifyPhase(now = 19, startEpochMs = 10, endEpochMs = 20))
    }

    @Test
    fun `classifyPhase returns ENDED at end and after`() {
        assertEquals(TimerPhase.ENDED, classifyPhase(now = 20, startEpochMs = 10, endEpochMs = 20))
        assertEquals(TimerPhase.ENDED, classifyPhase(now = 100, startEpochMs = 10, endEpochMs = 20))
    }

    @Test
    fun `phaseLabel surfaces upcoming countdown to start`() {
        val label = phaseLabel(
            phase = TimerPhase.UPCOMING,
            now = 0L,
            startEpochMs = 75_000L,
            endEpochMs = 200_000L,
        )
        assertEquals("未开始 (01:15)", label)
    }

    @Test
    fun `phaseLabel surfaces remaining time when running`() {
        val label = phaseLabel(
            phase = TimerPhase.RUNNING,
            now = 30_000L,
            startEpochMs = 0L,
            endEpochMs = 90_000L,
        )
        assertEquals("剩余 01:00", label)
    }

    @Test
    fun `phaseLabel shows ended literal regardless of time math`() {
        val label = phaseLabel(
            phase = TimerPhase.ENDED,
            now = 999_999L,
            startEpochMs = 0L,
            endEpochMs = 1L,
        )
        assertEquals("已结束", label)
    }
}
