package com.pythontopia.feature.arena

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [CountdownTimer] 纯函数单测：[formatRemaining]、[classifyPhase]、[phaseLabel]（Composable 渲染未覆盖）。 */
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
