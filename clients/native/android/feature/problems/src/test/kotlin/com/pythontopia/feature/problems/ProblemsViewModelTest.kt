package com.pythontopia.feature.problems

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProblemsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: ProblemsRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init 自动加载题目`() = runTest {
        coEvery { repository.load() } returns Result.success(listOf(row(1), row(2)))
        val vm = ProblemsViewModel(repository)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(2, s.all.size)
        assertEquals(2, s.visible.size)
    }

    @Test
    fun `难度过滤只留命中项`() = runTest {
        coEvery { repository.load() } returns Result.success(
            listOf(
                row(1, difficulty = ProblemDifficulty.LOW),
                row(2, difficulty = ProblemDifficulty.HIGH),
            ),
        )
        val vm = ProblemsViewModel(repository)
        advanceUntilIdle()

        vm.setDifficulty(ProblemDifficulty.HIGH)

        val visible = vm.state.value.visible
        assertEquals(1, visible.size)
        assertEquals(2L, visible.first().problemId)
    }

    @Test
    fun `搜索按标题与 KC 命中`() = runTest {
        coEvery { repository.load() } returns Result.success(
            listOf(
                row(1, title = "读取输入", kcCode = "PY43-KC-016"),
                row(2, title = "循环求和", kcCode = "PY43-KC-022"),
            ),
        )
        val vm = ProblemsViewModel(repository)
        advanceUntilIdle()

        vm.setSearch("循环")
        assertEquals(listOf(2L), vm.state.value.visible.map { it.problemId })

        vm.setSearch("KC-016")
        assertEquals(listOf(1L), vm.state.value.visible.map { it.problemId })
    }

    @Test
    fun `clearFilters 复位全部条件`() = runTest {
        coEvery { repository.load() } returns Result.success(listOf(row(1), row(2)))
        val vm = ProblemsViewModel(repository)
        advanceUntilIdle()
        vm.setSearch("x")
        vm.setDifficulty(ProblemDifficulty.LOW)
        vm.setStatus(ProblemStatus.PASSED)

        vm.clearFilters()

        val s = vm.state.value
        assertFalse(s.hasActiveFilters)
        assertEquals(2, s.visible.size)
    }

    @Test
    fun `load 失败写入错误`() = runTest {
        coEvery { repository.load() } returns Result.failure(RuntimeException("后端 503"))
        val vm = ProblemsViewModel(repository)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertTrue(s.error?.contains("后端 503") == true)
        assertTrue(s.all.isEmpty())
    }

    private fun row(
        id: Long,
        title: String = "题目 $id",
        kcCode: String = "PY43-KC-0${id}",
        difficulty: ProblemDifficulty = ProblemDifficulty.MEDIUM,
    ) = ProblemRow(
        problemId = id,
        title = title,
        kcCode = kcCode,
        kcName = "知识点 $id",
        chapterOrder = id.toInt(),
        difficulty = difficulty,
        statementMarkdown = "题面 $id",
        inputDescription = "",
        outputDescription = "",
        sourceLabel = "",
        status = ProblemStatus.NOT_TRIED,
        attempts = 0,
    )
}
