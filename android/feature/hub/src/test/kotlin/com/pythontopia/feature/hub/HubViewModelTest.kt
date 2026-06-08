package com.pythontopia.feature.hub

import com.pythontopia.core.data.dto.world.GameBootstrapResponseDto
import com.pythontopia.core.data.dto.world.PlayerStateDto
import com.pythontopia.core.data.dto.world.WorldSnapshotDto
import com.pythontopia.game.HubEvent
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: HubRepository = mockk()
    private lateinit var viewModel: HubViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.bootstrap() } returns GameBootstrapResponseDto(
            world = WorldSnapshotDto(
                worldSessionId = 42L,
                dayIndex = 3,
                monthIndex = 1,
                weekday = "WEDNESDAY",
                season = "SPRING",
                timeOfDay = "MORNING",
                weather = "CLEAR",
                inGameMinutes = 480,
            ),
            player = PlayerStateDto(tileX = 25, tileY = 20, direction = "DOWN", mapKey = "town_hub"),
        )
        coEvery { repository.today() } returns null
        viewModel = HubViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `bootstrap clears isBootstrapping and updates bridge spawn tile`() = runTest {
        advanceUntilIdle()
        val state = viewModel.state.value
        assertFalse(state.isBootstrapping)
        assertNotNull(state.world)
        assertEquals(42L, state.world!!.world.worldSessionId)
        assertEquals(25, viewModel.bridge.latestPlayerTile().x)
        assertEquals(20, viewModel.bridge.latestPlayerTile().y)
    }

    @Test
    fun `consumeBridgeEvent NpcInRange sets nearbyNpcKey`() = runTest {
        viewModel.consumeBridgeEvent(HubEvent.NpcInRange("mayor_py", 25, 10))
        assertEquals("mayor_py", viewModel.state.value.nearbyNpcKey)
    }

    @Test
    fun `consumeBridgeEvent NpcOutOfRange clears nearbyNpcKey`() = runTest {
        viewModel.consumeBridgeEvent(HubEvent.NpcInRange("mayor_py", 25, 10))
        viewModel.consumeBridgeEvent(HubEvent.NpcOutOfRange)
        assertNull(viewModel.state.value.nearbyNpcKey)
    }

    @Test
    fun `requestInteract requires nearby NPC and opens dialog`() = runTest {
        // 附近无 NPC 时不操作
        viewModel.requestInteract()
        assertNull(viewModel.state.value.dialogOpenedFor)

        viewModel.consumeBridgeEvent(HubEvent.NpcInRange("mayor_py", 25, 10))
        viewModel.requestInteract()
        assertEquals("mayor_py", viewModel.state.value.dialogOpenedFor)
    }

    @Test
    fun `dialogDismissed clears dialog target`() = runTest {
        viewModel.consumeBridgeEvent(HubEvent.NpcInRange("mayor_py", 25, 10))
        viewModel.requestInteract()
        viewModel.dialogDismissed()
        assertNull(viewModel.state.value.dialogOpenedFor)
    }
}
