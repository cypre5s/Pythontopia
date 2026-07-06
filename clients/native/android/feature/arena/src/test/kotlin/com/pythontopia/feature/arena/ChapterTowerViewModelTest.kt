package com.pythontopia.feature.arena

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterTowerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads chapter 1 with 10 levels`() = runTest {
        val vm = ChapterTowerViewModel()
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(1, state.selectedChapter)
        assertEquals(10, state.levels.size)
        assertEquals("1-1", state.levels.first().id)
        assertEquals("1-10", state.levels.last().id)
    }

    @Test
    fun `selectChapter changes chapter and reloads levels`() = runTest {
        val vm = ChapterTowerViewModel()
        advanceUntilIdle()
        vm.selectChapter(3)
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(3, state.selectedChapter)
        assertEquals("3-1", state.levels.first().id)
    }

    @Test
    fun `selectLevel sets selectedLevelId for unlocked level`() = runTest {
        val vm = ChapterTowerViewModel()
        advanceUntilIdle()
        vm.selectLevel("1-1")
        assertEquals("1-1", vm.state.value.selectedLevelId)
    }

    @Test
    fun `selectLevel is no-op for locked level`() = runTest {
        val vm = ChapterTowerViewModel()
        advanceUntilIdle()
        vm.selectLevel("1-5")
        assertNull(vm.state.value.selectedLevelId)
    }

    @Test
    fun `markCleared updates level and increments chapterCleared`() = runTest {
        val vm = ChapterTowerViewModel()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.chapterCleared)
        vm.markCleared("1-1")
        assertEquals(1, vm.state.value.chapterCleared)
        assertTrue(vm.state.value.levels.first().cleared)
    }

    @Test
    fun `level 10 is boss kind`() = runTest {
        val vm = ChapterTowerViewModel()
        advanceUntilIdle()
        assertEquals("boss", vm.state.value.levels.last().kind)
    }

    @Test
    fun `selectChapter out of range is no-op`() = runTest {
        val vm = ChapterTowerViewModel()
        advanceUntilIdle()
        vm.selectChapter(0)
        assertEquals(1, vm.state.value.selectedChapter)
        vm.selectChapter(8)
        assertEquals(1, vm.state.value.selectedChapter)
    }

    @Test
    fun `first level is always unlocked`() = runTest {
        val vm = ChapterTowerViewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.levels.first().unlocked)
    }

    @Test
    fun `levels have correct difficulty range`() = runTest {
        val vm = ChapterTowerViewModel()
        advanceUntilIdle()
        for (level in vm.state.value.levels) {
            assertTrue(level.difficulty in 1..5)
        }
    }

    @Test
    fun `all 7 chapters produce valid levels`() = runTest {
        val vm = ChapterTowerViewModel()
        advanceUntilIdle()
        for (ch in 1..7) {
            vm.selectChapter(ch)
            advanceUntilIdle()
            assertEquals(10, vm.state.value.levels.size, "Chapter $ch should have 10 levels")
            assertEquals("$ch-1", vm.state.value.levels.first().id)
            assertEquals("$ch-10", vm.state.value.levels.last().id)
        }
    }
}
