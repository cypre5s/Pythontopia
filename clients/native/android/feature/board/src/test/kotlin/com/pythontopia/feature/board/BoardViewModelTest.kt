package com.pythontopia.feature.board

import com.pythontopia.core.data.dto.learner.QuestDto
import com.pythontopia.core.data.dto.learner.QuestStatus
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: BoardRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init 加载今日任务`() = runTest {
        coEvery { repository.today() } returns Result.success(
            listOf(quest(1, QuestStatus.OPEN), quest(2, QuestStatus.IN_PROGRESS)),
        )
        val vm = BoardViewModel(repository)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(2, s.quests.size)
        assertEquals(2, s.openCount)
    }

    @Test
    fun `complete 用返回的任务原地更新`() = runTest {
        coEvery { repository.today() } returns Result.success(listOf(quest(1, QuestStatus.OPEN)))
        coEvery { repository.complete(1L, null) } returns
            Result.success(quest(1, QuestStatus.COMPLETED))
        val vm = BoardViewModel(repository)
        advanceUntilIdle()

        vm.complete(1L)
        advanceUntilIdle()

        val updated = vm.state.value.quests.first { it.id == 1L }
        assertEquals(QuestStatus.COMPLETED, updated.status)
        assertNull(vm.state.value.actionInFlightId)
    }

    @Test
    fun `skip 失败写入 notice 而非清空列表`() = runTest {
        coEvery { repository.today() } returns Result.success(listOf(quest(1, QuestStatus.OPEN)))
        coEvery { repository.skip(1L, null) } returns Result.failure(RuntimeException("网络抖动"))
        val vm = BoardViewModel(repository)
        advanceUntilIdle()

        vm.skip(1L)
        advanceUntilIdle()

        assertTrue(vm.state.value.notice?.contains("网络抖动") == true)
        assertEquals(1, vm.state.value.quests.size)
        assertNull(vm.state.value.actionInFlightId)
    }

    @Test
    fun `load 失败写入错误`() = runTest {
        coEvery { repository.today() } returns Result.failure(RuntimeException("后端 503"))
        val vm = BoardViewModel(repository)
        advanceUntilIdle()

        assertTrue(vm.state.value.error?.contains("后端 503") == true)
        assertTrue(vm.state.value.quests.isEmpty())
    }

    @Test
    fun `requiresEvidence 识别需证据任务`() {
        assertTrue(quest(1, QuestStatus.OPEN, evidence = "SUBMISSION").requiresEvidence())
        assertFalse(quest(2, QuestStatus.OPEN).requiresEvidence())
    }

    private fun quest(id: Long, status: QuestStatus, evidence: String? = null) = QuestDto(
        id = id,
        title = "任务 $id",
        status = status,
        priority = 1,
        requiredEvidenceType = evidence,
    )
}
