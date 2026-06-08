package com.pythontopia.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythontopia.core.data.dto.world.CalendarTodayResponse
import com.pythontopia.core.data.dto.world.GameBootstrapResponseDto
import com.pythontopia.core.network.SessionState
import com.pythontopia.game.DEFAULT_WORLD_MAP_KEY
import com.pythontopia.game.HubGameBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 中央小镇 UI 状态；驱动 LibGDX 渲染与 HUD 覆盖层。 */
data class HubUiState(
    val isBootstrapping: Boolean = true,
    val world: GameBootstrapResponseDto? = null,
    val calendar: CalendarTodayResponse? = null,
    val mapKey: String = DEFAULT_WORLD_MAP_KEY,
    val nearbyNpcKey: String? = null,
    val dialogOpenedFor: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

/** 中央小镇 ViewModel；聚合 GameBootstrap、日历与 LibGDX 桥事件。 */
@HiltViewModel
class HubViewModel @Inject constructor(
    private val repository: HubRepository,
    private val sessionState: SessionState = SessionState(),
) : ViewModel() {

    /** 单实例 LibGDX 桥，生命周期跟随 ViewModel。 */
    val bridge: HubGameBridge = HubGameBridge()

    private val _state = MutableStateFlow(HubUiState())
    val state: StateFlow<HubUiState> = _state.asStateFlow()

    /** 玩家移动上报；debounce 合流，停顿 [MOVE_REPORT_DEBOUNCE_MS] 后上报最终落点。 */
    private val moveRequests = MutableSharedFlow<PlayerMovePatch>(
        extraBufferCapacity = MOVE_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        bootstrap()
        observeMoveReports()
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeMoveReports() {
        viewModelScope.launch {
            moveRequests
                .debounce(MOVE_REPORT_DEBOUNCE_MS)
                .collect { patch -> repository.reportMove(patch.tileX, patch.tileY, patch.direction) }
        }
    }

    private fun bootstrap() {
        if (sessionState.isGuest) {
            // 游客无令牌，跳过 bootstrap，进入自由探索模式
            _state.update {
                it.copy(
                    isBootstrapping = false,
                    statusMessage = "游客模式：自由探索像素小镇与 NPC，登录后解锁学习进度、任务与存档。",
                    errorMessage = null,
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching { repository.bootstrap() }
                .onSuccess { bootstrap ->
                    val mapKey = bootstrap.player.mapKey ?: DEFAULT_WORLD_MAP_KEY
                    bridge.updatePlayerTile(bootstrap.player.tileX, bootstrap.player.tileY, mapKey)
                    val calendar = repository.today()
                    _state.update {
                        it.copy(
                            isBootstrapping = false,
                            world = bootstrap,
                            calendar = calendar,
                            mapKey = mapKey,
                            statusMessage = null,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBootstrapping = false,
                            errorMessage = error.message ?: "世界加载失败，请确认已登录且后端可用",
                        )
                    }
                }
        }
    }

    /** 错误态重试；清错误后重新 bootstrap。 */
    fun retry() {
        _state.update { it.copy(isBootstrapping = true, errorMessage = null) }
        bootstrap()
    }

    fun consumeBridgeEvent(event: com.pythontopia.game.HubEvent) {
        when (event) {
            is com.pythontopia.game.HubEvent.NpcInRange -> {
                _state.update { it.copy(nearbyNpcKey = event.npcKey) }
            }
            com.pythontopia.game.HubEvent.NpcOutOfRange -> {
                _state.update { it.copy(nearbyNpcKey = null) }
            }
            is com.pythontopia.game.HubEvent.ExitTouched -> {
                _state.update {
                    it.copy(
                        mapKey = event.targetMap,
                        statusMessage = "已进入 ${event.targetMap}",
                        errorMessage = null,
                    )
                }
            }
            is com.pythontopia.game.HubEvent.PlayerMoved -> {
                _state.update { it.copy(mapKey = event.mapKey) }
                reportPlayerMove(event.tileX, event.tileY, event.direction)
            }
        }
    }

    fun requestInteract() {
        val target = _state.value.nearbyNpcKey ?: return
        bridge.requestInteract()
        _state.update { it.copy(dialogOpenedFor = target) }
    }

    fun dialogDismissed() {
        _state.update { it.copy(dialogOpenedFor = null) }
    }

    fun reportPlayerMove(tileX: Int, tileY: Int, direction: String) {
        moveRequests.tryEmit(PlayerMovePatch(tileX, tileY, direction))
    }

    private data class PlayerMovePatch(val tileX: Int, val tileY: Int, val direction: String)

    private companion object {
        const val MOVE_REPORT_DEBOUNCE_MS = 450L
        const val MOVE_BUFFER_CAPACITY = 64
    }
}
