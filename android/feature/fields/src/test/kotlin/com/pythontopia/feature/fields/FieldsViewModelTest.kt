package com.pythontopia.feature.fields

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
class FieldsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository: FieldsRepository = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty and not loading after construction with empty repo`() = runTest {
        coEvery { repository.fetchKcGrid() } returns emptyList()
        val vm = FieldsViewModel(repository)
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state.tiles.isEmpty())
        assertFalse(state.isLoading)
        assertEquals("暂无法加载知识田，请稍后下拉重试", state.errorMessage)
    }

    @Test
    fun `refresh populates 65 KC grid and clears loading`() = runTest {
        coEvery { repository.fetchKcGrid() } returns sampleGrid()
        val vm = FieldsViewModel(repository)
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(KcSeedCatalog.EXPECTED_KC_COUNT, state.tiles.size)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `refresh emits loading then loaded transitions via turbine`() = runTest {
        val gate = CompletableDeferred<List<KcTileState>>()
        coEvery { repository.fetchKcGrid() } coAnswers { gate.await() }
        val vm = FieldsViewModel(repository)
        vm.state.test {
            val initial = awaitItem()
            // init { refresh() } 已写过一次 isLoading=true，因此第一帧必然在 loading 态
            assertTrue(initial.isLoading)
            assertTrue(initial.tiles.isEmpty())

            gate.complete(sampleGrid())
            advanceUntilIdle()

            val loaded = expectMostRecentItem()
            assertEquals(KcSeedCatalog.EXPECTED_KC_COUNT, loaded.tiles.size)
            assertFalse(loaded.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `water updates target tile mastery`() = runTest {
        coEvery { repository.fetchKcGrid() } returns sampleGrid()
        coEvery { repository.waterKc("PY43-KC-001") } returns KcTileState(
            kcCode = "PY43-KC-001",
            title = "中国计算机发展成就",
            chapterOrder = 1,
            mastery = 0.1,
            status = KcStatus.SEED,
        )
        val vm = FieldsViewModel(repository)
        advanceUntilIdle()

        vm.water("PY43-KC-001")
        advanceUntilIdle()

        val updated = vm.state.value.tiles.first { it.kcCode == "PY43-KC-001" }
        assertEquals(0.1, updated.mastery)
        assertNull(vm.state.value.pendingActionKc)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `water rejects harvested tile with friendly error`() = runTest {
        val harvestedSample = sampleGrid().map {
            if (it.kcCode == "PY43-KC-001") it.copy(status = KcStatus.HARVESTED, mastery = 1.0) else it
        }
        coEvery { repository.fetchKcGrid() } returns harvestedSample
        val vm = FieldsViewModel(repository)
        advanceUntilIdle()

        vm.water("PY43-KC-001")
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("已收获"))
    }

    @Test
    fun `harvest succeeds only when status is MATURE`() = runTest {
        val mature = sampleGrid().map {
            if (it.kcCode == "PY43-KC-031") it.copy(mastery = 0.9, status = KcStatus.MATURE) else it
        }
        coEvery { repository.fetchKcGrid() } returns mature
        coEvery { repository.harvestKc("PY43-KC-031") } returns KcTileState(
            kcCode = "PY43-KC-031",
            title = "for循环与遍历",
            chapterOrder = 4,
            mastery = 1.0,
            status = KcStatus.HARVESTED,
        )
        val vm = FieldsViewModel(repository)
        advanceUntilIdle()

        vm.harvest("PY43-KC-031")
        advanceUntilIdle()

        val updated = vm.state.value.tiles.first { it.kcCode == "PY43-KC-031" }
        assertEquals(KcStatus.HARVESTED, updated.status)
        assertEquals(1.0, updated.mastery)
    }

    @Test
    fun `harvest emits error when tile is not mature`() = runTest {
        coEvery { repository.fetchKcGrid() } returns sampleGrid()
        val vm = FieldsViewModel(repository)
        advanceUntilIdle()

        vm.harvest("PY43-KC-001")
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("尚未成熟"))
    }

    @Test
    fun `water fallback to errorMessage when repository throws`() = runTest {
        coEvery { repository.fetchKcGrid() } returns sampleGrid()
        coEvery { repository.waterKc(any()) } throws RuntimeException("offline")
        val vm = FieldsViewModel(repository)
        advanceUntilIdle()

        vm.water("PY43-KC-001")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("浇水失败，请检查网络后重试", state.errorMessage)
        assertNull(state.pendingActionKc)
    }

    @Test
    fun `selectKc toggles selection and clears error`() = runTest {
        coEvery { repository.fetchKcGrid() } returns sampleGrid()
        val vm = FieldsViewModel(repository)
        advanceUntilIdle()

        vm.selectKc("PY43-KC-005")
        assertEquals("PY43-KC-005", vm.state.value.selectedKc)
        vm.selectKc("PY43-KC-005")
        assertNull(vm.state.value.selectedKc)
    }

    @Test
    fun `pendingActionKc blocks second concurrent water call`() = runTest {
        coEvery { repository.fetchKcGrid() } returns sampleGrid()
        val gate = CompletableDeferred<Unit>()
        // 用 CompletableDeferred 把第一次 water 阻塞在 gate.await()，
        // 这样第二次 water 调用时 pending 槽位仍非空，必然被 ViewModel 直接 return。
        coEvery { repository.waterKc("PY43-KC-001") } coAnswers {
            gate.await()
            KcTileState("PY43-KC-001", "中国计算机发展成就", 1, 0.1, KcStatus.SEED)
        }
        val vm = FieldsViewModel(repository)
        advanceUntilIdle()

        vm.water("PY43-KC-001")
        // 第一次已进入 pending；此时 pendingActionKc 必须不为 null
        assertEquals("PY43-KC-001", vm.state.value.pendingActionKc)
        vm.water("PY43-KC-002")
        // 第二次因 pending 占位被立即拒绝（早返回，不抛错也不写 errorMessage）
        assertEquals("PY43-KC-001", vm.state.value.pendingActionKc)

        gate.complete(Unit)
        advanceUntilIdle()

        val updatedOne = vm.state.value.tiles.first { it.kcCode == "PY43-KC-001" }
        val notUpdatedTwo = vm.state.value.tiles.first { it.kcCode == "PY43-KC-002" }
        assertEquals(0.1, updatedOne.mastery)
        assertEquals(0.0, notUpdatedTwo.mastery)
        assertNull(vm.state.value.pendingActionKc)
    }

    @Test
    fun `dismissError clears outstanding error message`() = runTest {
        coEvery { repository.fetchKcGrid() } returns emptyList()
        val vm = FieldsViewModel(repository)
        advanceUntilIdle()
        assertNotNull(vm.state.value.errorMessage)

        vm.dismissError()
        assertNull(vm.state.value.errorMessage)
    }

    private fun sampleGrid(): List<KcTileState> = KcSeedCatalog.entries.map { seed ->
        KcTileState(
            kcCode = seed.kcCode,
            title = seed.title,
            chapterOrder = seed.chapterOrder,
            mastery = 0.0,
            status = KcStatus.SEED,
        )
    }
}
