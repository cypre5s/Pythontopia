package com.pythontopia.feature.retro

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetroViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: RetroRepository = mockk(relaxed = true)
    private lateinit var viewModel: RetroViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = RetroViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has loading false and no report`() {
        val s = viewModel.state.value
        assertFalse(s.loading)
        assertNull(s.currentReport)
        assertTrue(s.pastReports.isEmpty())
        assertNull(s.error)
    }

    @Test
    fun `load fetches this week and past weeks then settles state`() = runTest {
        val current = report(weekIndex = 2812L, reportId = "this-week")
        val past = listOf(
            report(weekIndex = 2811L, reportId = "wk-1"),
            report(weekIndex = 2810L, reportId = "wk-2"),
            report(weekIndex = 2809L, reportId = "wk-3"),
        )
        coEvery { repository.thisWeek(userId = "alice", weekIndex = any()) } returns current
        coEvery { repository.pastWeeks(userId = "alice", count = 3, baseWeekIndex = 2812L) } returns past

        viewModel.load(userId = "alice", pastWeeks = 3)
        advanceUntilIdle()

        val s = viewModel.state.value
        assertFalse(s.loading)
        assertEquals("this-week", s.currentReport?.reportId)
        assertEquals(2812L, s.thisWeekIndex)
        assertEquals(listOf("wk-1", "wk-2", "wk-3"), s.pastReports.map { it.reportId })
        assertNull(s.error)
        coVerify(exactly = 1) {
            repository.pastWeeks(userId = "alice", count = 3, baseWeekIndex = 2812L)
        }
    }

    @Test
    fun `load with pastWeeks zero skips history call`() = runTest {
        coEvery {
            repository.thisWeek(userId = "bob", weekIndex = any())
        } returns report(weekIndex = 100L, reportId = "only")

        viewModel.load(userId = "bob", pastWeeks = 0)
        advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals("only", s.currentReport?.reportId)
        assertTrue(s.pastReports.isEmpty())
        coVerify(exactly = 0) {
            repository.pastWeeks(userId = any(), count = any(), baseWeekIndex = any())
        }
    }

    @Test
    fun `load surfaces error message when repository throws`() = runTest {
        coEvery {
            repository.thisWeek(userId = "carol", weekIndex = any())
        } throws RuntimeException("backend 504")

        viewModel.load(userId = "carol")
        advanceUntilIdle()

        val s = viewModel.state.value
        assertFalse(s.loading)
        assertNull(s.currentReport)
        assertEquals("backend 504", s.error)
    }

    @Test
    fun `selectWeek swaps current report with one from history pool`() = runTest {
        val current = report(weekIndex = 2812L, reportId = "this-week")
        val past = listOf(
            report(weekIndex = 2811L, reportId = "wk-1"),
            report(weekIndex = 2810L, reportId = "wk-2"),
        )
        coEvery { repository.thisWeek(userId = "alice", weekIndex = any()) } returns current
        coEvery {
            repository.pastWeeks(userId = "alice", count = 3, baseWeekIndex = 2812L)
        } returns past
        viewModel.load(userId = "alice", pastWeeks = 3)
        advanceUntilIdle()

        viewModel.selectWeek(2811L)

        val s = viewModel.state.value
        assertEquals("wk-1", s.currentReport?.reportId)
        // 旧的 currentReport 现在出现在 pastReports 里，按 weekIndex 降序排序
        assertEquals(listOf("this-week", "wk-2"), s.pastReports.map { it.reportId })
        // thisWeekIndex 保持指向真正的本周，便于 chip 标识「本周」
        assertEquals(2812L, s.thisWeekIndex)
    }

    @Test
    fun `selectWeek with same week is a no-op`() = runTest {
        val current = report(weekIndex = 2812L, reportId = "this-week")
        coEvery { repository.thisWeek(userId = "alice", weekIndex = any()) } returns current
        coEvery {
            repository.pastWeeks(userId = "alice", count = 3, baseWeekIndex = 2812L)
        } returns emptyList()
        viewModel.load(userId = "alice", pastWeeks = 3)
        advanceUntilIdle()

        viewModel.selectWeek(2812L)

        assertEquals("this-week", viewModel.state.value.currentReport?.reportId)
    }

    @Test
    fun `selectWeek with unknown weekIndex does nothing`() = runTest {
        coEvery {
            repository.thisWeek(userId = "alice", weekIndex = any())
        } returns report(weekIndex = 100L, reportId = "only")
        coEvery {
            repository.pastWeeks(userId = "alice", count = 3, baseWeekIndex = 100L)
        } returns emptyList()
        viewModel.load(userId = "alice", pastWeeks = 3)
        advanceUntilIdle()

        viewModel.selectWeek(99L)

        assertEquals("only", viewModel.state.value.currentReport?.reportId)
    }

    @Test
    fun `markRead delegates to repository`() = runTest {
        coEvery { repository.markRead("r-42") } returns Unit

        viewModel.markRead("r-42")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.markRead("r-42") }
    }

    @Test
    fun `markRead swallows repository failure to avoid blocking UI`() = runTest {
        coEvery { repository.markRead("r-down") } throws RuntimeException("offline")

        viewModel.markRead("r-down")
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
    }

    private fun report(weekIndex: Long, reportId: String): RetroReport = RetroReport(
        reportId = reportId,
        userId = "alice",
        weekIndex = weekIndex,
        periodStart = "2026-05-18",
        periodEnd = "2026-05-24",
        kcGains = listOf(
            KcGain(kcId = "kc-1", kcName = "变量与赋值", masteryDelta = 0.12, masteryAfter = 0.62),
        ),
        attempts = 8,
        bugMineCount = 1,
        generatedResources = listOf(
            ResourceDto(
                resourceId = "res-1",
                title = "本周脑图",
                resourceType = "mindmap",
                url = "https://example.org/$reportId.png",
            ),
        ),
    )
}
