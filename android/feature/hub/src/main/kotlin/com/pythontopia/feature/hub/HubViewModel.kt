package com.pythontopia.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythontopia.core.data.dto.world.CalendarTodayResponse
import com.pythontopia.core.data.dto.world.GameBootstrapResponseDto
import com.pythontopia.game.HubGameBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HubUiState(
    val isBootstrapping: Boolean = true,
    val world: GameBootstrapResponseDto? = null,
    val calendar: CalendarTodayResponse? = null,
    val nearbyNpcKey: String? = null,
    val dialogOpenedFor: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class HubViewModel @Inject constructor(
    private val repository: HubRepository,
) : ViewModel() {

    /**
     * 单实例桥，跟随 ViewModel 生命周期；LibGDX Fragment 与 Compose UI 都通过它通信。
     * ViewModel 销毁时 Hilt 会回收，桥对象自然失效；渲染线程检测到 null 也能优雅退出。
     */
    val bridge: HubGameBridge = HubGameBridge()

    private val _state = MutableStateFlow(HubUiState())
    val state: StateFlow<HubUiState> = _state.asStateFlow()

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val bootstrap = repository.bootstrap()
            bridge.updatePlayerTile(bootstrap.player.tileX, bootstrap.player.tileY)
            val calendar = repository.today()
            _state.update {
                it.copy(
                    isBootstrapping = false,
                    world = bootstrap,
                    calendar = calendar,
                    errorMessage = if (bootstrap.world.worldSessionId == -1L) {
                        "未连接到后端，使用本地默认快照（账号未登录时此提示正常）"
                    } else {
                        null
                    },
                )
            }
        }
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
                    it.copy(errorMessage = "地图 ${event.targetMap} 留给 Android-R13+")
                }
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
        viewModelScope.launch {
            repository.reportMove(tileX, tileY, direction)
        }
    }
}
