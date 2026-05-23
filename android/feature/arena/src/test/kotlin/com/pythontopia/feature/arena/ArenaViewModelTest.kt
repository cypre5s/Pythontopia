package com.pythontopia.feature.arena

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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [ArenaViewModel] 单元测试。
 *
 * 覆盖维度（对应任务"题目切换 / 提交 / 排行刷新"三个口径）：
 *  - bootstrap：init 后 session / leaderboard 都填充，加载 flag 复位
 *  - selectProblem：边界（越界忽略）+ 合法切换
 *  - updateCode：写入到 [ArenaUiState.codeBuffers]，并体现在 currentCode
 *  - submitCurrent：三类终态分别落到 [ArenaUiState.lastSubmission]，且仅 PASSED 会再刷一次排行榜
 *  - refreshLeaderboard：显式调用即可
 *  - selectTab：切到 LEADERBOARD 触发懒加载（在 leaderboard 已有数据时不重复请求）
 *
 * 仍然使用 [UnconfinedTestDispatcher] + `Dispatchers.setMain` 套件，与
 * `HubViewModelTest` 保持一致，避免协程在 init/调用时延迟到 advanceUntilIdle 才执行。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArenaViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository: ArenaRepository = mockk(relaxed = false)

    private val demoSession = ArenaSession(
        sessionId = 7L,
        title = "单测·练习赛",
        classroomId = 42L,
        startEpochMs = 0L,
        endEpochMs = 60_000L,
        problems = listOf(
            ArenaProblem(1L, "T1", "题目一", "def t1():\n    pass\n", scoreReward = 100),
            ArenaProblem(2L, "T2", "题目二", "def t2():\n    pass\n", scoreReward = 200),
        ),
    )
    private val demoLeaderboard = listOf(
        LeaderboardEntry(rank = 1, userId = 901L, displayName = "甲", score = 300, solvedCount = 2),
        LeaderboardEntry(rank = 2, userId = 104L, displayName = "我", score = 100, solvedCount = 1),
    )

    private lateinit var vm: ArenaViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.todaysSession() } returns demoSession
        coEvery { repository.leaderboard(any()) } returns demoLeaderboard
        coEvery { repository.submit(any(), any()) } returns Submission(
            submissionId = 1L,
            problemId = 1L,
            status = ArenaRepository.STATUS_FAILED,
            message = "default mock",
            scoreAwarded = 0,
        )
        vm = ArenaViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads session and leaderboard`() = runTest {
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.isLoadingSession)
        assertFalse(state.isLoadingLeaderboard)
        assertEquals(7L, state.session?.sessionId)
        assertEquals(2, state.leaderboard.size)
    }

    @Test
    fun `selectProblem switches index when valid and ignores out-of-range`() = runTest {
        advanceUntilIdle()

        vm.selectProblem(1)
        assertEquals(1, vm.state.value.currentProblemIdx)
        assertEquals(2L, vm.state.value.currentProblem?.problemId)

        vm.selectProblem(99)
        assertEquals(1, vm.state.value.currentProblemIdx, "out-of-range index should be ignored")

        vm.selectProblem(-1)
        assertEquals(1, vm.state.value.currentProblemIdx, "negative index should be ignored")
    }

    @Test
    fun `updateCode stores per-problem buffer and exposes through currentCode`() = runTest {
        advanceUntilIdle()
        vm.updateCode("print('hello T1')")
        assertEquals("print('hello T1')", vm.state.value.currentCode)
        assertEquals("print('hello T1')", vm.state.value.codeBuffers[1L])

        vm.selectProblem(1)
        assertEquals(
            "def t2():\n    pass\n",
            vm.state.value.currentCode,
            "switching to T2 should fall back to starterCode",
        )
        vm.updateCode("def t2(): return 1")
        assertEquals("def t2(): return 1", vm.state.value.codeBuffers[2L])

        vm.selectProblem(0)
        assertEquals(
            "print('hello T1')",
            vm.state.value.currentCode,
            "switching back to T1 must restore the previously edited buffer",
        )
    }

    @Test
    fun `submit PASSED updates lastSubmission and triggers leaderboard refresh`() = runTest {
        advanceUntilIdle()
        coEvery { repository.submit(1L, any()) } returns Submission(
            submissionId = 11L,
            problemId = 1L,
            status = ArenaRepository.STATUS_PASSED,
            message = "ok",
            scoreAwarded = 100,
        )

        vm.updateCode("print('ok')")
        vm.submitCurrent()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSubmitting)
        assertNotNull(state.lastSubmission)
        assertEquals(ArenaRepository.STATUS_PASSED, state.lastSubmission!!.status)
        assertEquals(100, state.lastSubmission!!.scoreAwarded)

        coVerify(atLeast = 2) { repository.leaderboard(any()) }
    }

    @Test
    fun `submit FAILED keeps leaderboard fetch count unchanged`() = runTest {
        advanceUntilIdle()
        coEvery { repository.submit(1L, any()) } returns Submission(
            submissionId = 12L,
            problemId = 1L,
            status = ArenaRepository.STATUS_FAILED,
            message = "wrong",
            scoreAwarded = 0,
        )

        vm.updateCode("foo")
        vm.submitCurrent()
        advanceUntilIdle()

        assertEquals(ArenaRepository.STATUS_FAILED, vm.state.value.lastSubmission?.status)
        coVerify(exactly = 1) { repository.leaderboard(any()) }
    }

    @Test
    fun `submit RUNTIME_ERROR routes to runtime error status`() = runTest {
        advanceUntilIdle()
        coEvery { repository.submit(1L, any()) } returns Submission(
            submissionId = 13L,
            problemId = 1L,
            status = ArenaRepository.STATUS_RUNTIME_ERROR,
            message = "zero div",
            scoreAwarded = 0,
        )

        vm.updateCode("1/0")
        vm.submitCurrent()
        advanceUntilIdle()

        assertEquals(ArenaRepository.STATUS_RUNTIME_ERROR, vm.state.value.lastSubmission?.status)
    }

    @Test
    fun `refreshLeaderboard exposes data and uses classroomId from session`() = runTest {
        advanceUntilIdle()
        coEvery { repository.leaderboard(42L) } returns demoLeaderboard.reversed()

        vm.refreshLeaderboard()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.leaderboard.size)
        coVerify(atLeast = 1) { repository.leaderboard(42L) }
    }

    @Test
    fun `selectTab toggles selectedTab and skips reload when leaderboard already loaded`() = runTest {
        advanceUntilIdle()

        vm.selectTab(ArenaTab.LEADERBOARD)
        assertEquals(ArenaTab.LEADERBOARD, vm.state.value.selectedTab)
        coVerify(exactly = 1) { repository.leaderboard(any()) }

        vm.selectTab(ArenaTab.TODAY)
        assertEquals(ArenaTab.TODAY, vm.state.value.selectedTab)
    }

    @Test
    fun `myRank picks the row whose userId matches myUserId`() = runTest {
        advanceUntilIdle()
        val rank = vm.state.value.myRank
        assertNotNull(rank)
        assertEquals(104L, rank!!.userId)
        assertEquals(2, rank.rank)
    }

    @Test
    fun `errorMessage surfaces when repository throws on submit`() = runTest {
        advanceUntilIdle()
        coEvery { repository.submit(any(), any()) } throws IllegalStateException("backend 500")

        vm.submitCurrent()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSubmitting, "isSubmitting must release even on failure")
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("backend 500"))
    }
}
