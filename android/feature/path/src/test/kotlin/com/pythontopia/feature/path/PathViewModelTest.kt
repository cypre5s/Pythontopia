package com.pythontopia.feature.path

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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PathViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: PathRepository = mockk(relaxed = true)
    private lateinit var viewModel: PathViewModel

    private val samplePath = PathDto(
        pathId = "p-1",
        userId = 42L,
        packId = 43L,
        currentNodeId = "n-002",
        nodes = listOf(
            PathNodeDto("n-001", PathNodeType.READ, "节点 1", "KC-1", PathNodeStatus.DONE, listOf("n-002")),
            PathNodeDto("n-002", PathNodeType.PRACTICE, "节点 2", "KC-2", PathNodeStatus.IN_PROGRESS, listOf("n-003")),
            PathNodeDto("n-003", PathNodeType.QUEST, "节点 3", "KC-3", PathNodeStatus.UNLOCKED, listOf("n-004")),
            PathNodeDto("n-004", PathNodeType.DEBUG, "节点 4", "KC-4", PathNodeStatus.LOCKED, emptyList()),
        ),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PathViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty + not loading`() {
        val s = viewModel.state.value
        assertFalse(s.isLoading)
        assertTrue(s.nodes.isEmpty())
        assertNull(s.pathId)
        assertNull(s.currentNodeId)
        assertNull(s.error)
    }

    @Test
    fun `load populates state on success`() = runTest {
        coEvery { repository.fetchPath(42L) } returns samplePath
        viewModel.load(42L)
        advanceUntilIdle()
        val s = viewModel.state.value
        assertFalse(s.isLoading)
        assertEquals("p-1", s.pathId)
        assertEquals(4, s.nodes.size)
        assertEquals("n-002", s.currentNodeId)
        assertNull(s.error)
    }

    @Test
    fun `load surfaces error message when repository throws`() = runTest {
        coEvery { repository.fetchPath(42L) } throws RuntimeException("boom")
        viewModel.load(42L)
        advanceUntilIdle()
        val s = viewModel.state.value
        assertFalse(s.isLoading)
        assertEquals("boom", s.error)
        assertTrue(s.nodes.isEmpty())
    }

    @Test
    fun `toggleStatusFilter is a set toggle`() {
        viewModel.toggleStatusFilter(PathNodeStatus.DONE)
        assertEquals(setOf(PathNodeStatus.DONE), viewModel.state.value.statusFilter)
        viewModel.toggleStatusFilter(PathNodeStatus.IN_PROGRESS)
        assertEquals(
            setOf(PathNodeStatus.DONE, PathNodeStatus.IN_PROGRESS),
            viewModel.state.value.statusFilter,
        )
        viewModel.toggleStatusFilter(PathNodeStatus.DONE)
        assertEquals(setOf(PathNodeStatus.IN_PROGRESS), viewModel.state.value.statusFilter)
    }

    @Test
    fun `toggleTypeFilter is a set toggle`() {
        viewModel.toggleTypeFilter(PathNodeType.READ)
        viewModel.toggleTypeFilter(PathNodeType.PRACTICE)
        assertEquals(setOf(PathNodeType.READ, PathNodeType.PRACTICE), viewModel.state.value.typeFilter)
        viewModel.toggleTypeFilter(PathNodeType.READ)
        assertEquals(setOf(PathNodeType.PRACTICE), viewModel.state.value.typeFilter)
    }

    @Test
    fun `clearFilters resets both filters`() {
        viewModel.toggleStatusFilter(PathNodeStatus.DONE)
        viewModel.toggleTypeFilter(PathNodeType.READ)
        viewModel.clearFilters()
        assertTrue(viewModel.state.value.statusFilter.isEmpty())
        assertTrue(viewModel.state.value.typeFilter.isEmpty())
    }

    @Test
    fun `advanceNode on UNLOCKED calls markNodeStarted`() = runTest {
        coEvery { repository.fetchPath(42L) } returns samplePath
        val started = samplePath.copy(
            nodes = samplePath.nodes.map {
                if (it.id == "n-003") it.copy(status = PathNodeStatus.IN_PROGRESS) else it
            },
            currentNodeId = "n-003",
        )
        coEvery { repository.markNodeStarted("p-1", "n-003") } returns started
        viewModel.load(42L)
        advanceUntilIdle()

        val unlocked = samplePath.nodes.first { it.id == "n-003" }
        viewModel.advanceNode(unlocked)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.markNodeStarted("p-1", "n-003") }
        val s = viewModel.state.value
        assertEquals("n-003", s.currentNodeId)
        assertNull(s.advancingNodeId)
        assertEquals(PathNodeStatus.IN_PROGRESS, s.nodes.first { it.id == "n-003" }.status)
    }

    @Test
    fun `advanceNode on IN_PROGRESS calls markNodeDone`() = runTest {
        coEvery { repository.fetchPath(42L) } returns samplePath
        val done = samplePath.copy(
            nodes = samplePath.nodes.map {
                when (it.id) {
                    "n-002" -> it.copy(status = PathNodeStatus.DONE)
                    "n-003" -> it.copy(status = PathNodeStatus.UNLOCKED)
                    else -> it
                }
            },
            currentNodeId = "n-003",
        )
        coEvery { repository.markNodeDone("p-1", "n-002") } returns done
        viewModel.load(42L)
        advanceUntilIdle()

        val inProgress = samplePath.nodes.first { it.id == "n-002" }
        viewModel.advanceNode(inProgress)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.markNodeDone("p-1", "n-002") }
        assertEquals(PathNodeStatus.DONE, viewModel.state.value.nodes.first { it.id == "n-002" }.status)
    }

    @Test
    fun `advanceNode on LOCKED is a no-op`() = runTest {
        coEvery { repository.fetchPath(42L) } returns samplePath
        viewModel.load(42L)
        advanceUntilIdle()

        val locked = samplePath.nodes.first { it.id == "n-004" }
        viewModel.advanceNode(locked)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.markNodeStarted(any(), any()) }
        coVerify(exactly = 0) { repository.markNodeDone(any(), any()) }
    }

    @Test
    fun `advanceNode on DONE is a no-op`() = runTest {
        coEvery { repository.fetchPath(42L) } returns samplePath
        viewModel.load(42L)
        advanceUntilIdle()

        val done = samplePath.nodes.first { it.id == "n-001" }
        viewModel.advanceNode(done)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.markNodeStarted(any(), any()) }
        coVerify(exactly = 0) { repository.markNodeDone(any(), any()) }
    }

    @Test
    fun `advanceNode is rejected when another node is in flight`() = runTest {
        coEvery { repository.fetchPath(42L) } returns samplePath
        viewModel.load(42L)
        advanceUntilIdle()

        // 首笔 advance 挂起，模拟 inflight
        coEvery { repository.markNodeStarted("p-1", "n-003") } coAnswers {
            kotlinx.coroutines.delay(10_000)
            samplePath
        }
        val unlocked = samplePath.nodes.first { it.id == "n-003" }
        viewModel.advanceNode(unlocked)
        // inflight 期间重复点击应被忽略
        viewModel.advanceNode(unlocked)

        coVerify(exactly = 1) { repository.markNodeStarted("p-1", "n-003") }
    }

    @Test
    fun `consumeError clears error`() = runTest {
        coEvery { repository.fetchPath(42L) } throws RuntimeException("net down")
        viewModel.load(42L)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
        viewModel.consumeError()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `filteredNodes respects statusFilter union`() = runTest {
        coEvery { repository.fetchPath(42L) } returns samplePath
        viewModel.load(42L)
        advanceUntilIdle()

        viewModel.toggleStatusFilter(PathNodeStatus.DONE)
        viewModel.toggleStatusFilter(PathNodeStatus.IN_PROGRESS)
        val visible = viewModel.state.value.filteredNodes.map { it.id }.toSet()
        assertEquals(setOf("n-001", "n-002"), visible)
    }

    @Test
    fun `filteredNodes respects typeFilter intersected with statusFilter`() = runTest {
        coEvery { repository.fetchPath(42L) } returns samplePath
        viewModel.load(42L)
        advanceUntilIdle()

        viewModel.toggleStatusFilter(PathNodeStatus.UNLOCKED)
        viewModel.toggleTypeFilter(PathNodeType.QUEST)
        val visible = viewModel.state.value.filteredNodes.map { it.id }.toSet()
        assertEquals(setOf("n-003"), visible)
    }
}
