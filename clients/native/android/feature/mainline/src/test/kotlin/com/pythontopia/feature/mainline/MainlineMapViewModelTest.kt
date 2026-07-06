package com.pythontopia.feature.mainline

import com.pythontopia.core.data.dto.mainline.ChapterOverviewDto
import com.pythontopia.core.data.dto.mainline.MainlineOverviewDto
import com.pythontopia.core.data.dto.mainline.StageOverviewDto
import io.mockk.coEvery
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainlineMapViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: MainlineRepository = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load populates chapters and totalStars`() = runTest {
        coEvery { repository.overview() } returns MainlineOverviewDto(
            totalStars = 5,
            chapters = listOf(
                ChapterOverviewDto(
                    chapter = 1,
                    title = "入门",
                    unlocked = true,
                    stars = 2,
                    stages = listOf(StageOverviewDto(id = 10L, index = 1, title = "变量")),
                ),
            ),
        )

        val viewModel = MainlineMapViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(5, state.totalStars)
        assertEquals(1, state.chapters.size)
        assertEquals(10L, state.chapters.first().stages.first().id)
        assertNull(state.error)
    }

    @Test
    fun `load surfaces error message on failure`() = runTest {
        coEvery { repository.overview() } throws RuntimeException("boom")

        val viewModel = MainlineMapViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("boom", state.error)
    }
}
