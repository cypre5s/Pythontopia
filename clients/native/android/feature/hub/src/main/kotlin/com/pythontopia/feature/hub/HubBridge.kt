package com.pythontopia.feature.hub

import com.pythontopia.game.HubEvent
import com.pythontopia.game.HubGameBridge
import com.pythontopia.game.TileCoord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Compose 侧 LibGDX 桥；包装 [HubGameBridge]，轮询转 [StateFlow]/[SharedFlow]。
 * LibGDX→Compose：`playerTile`、`events`；Compose→LibGDX：`requestInteract`、`submitRuntimeState`。
 */
class HubBridge(
    val raw: HubGameBridge = HubGameBridge(),
) {

    private val _playerTile: MutableStateFlow<TileCoord> = MutableStateFlow(raw.latestPlayerTile())
    val playerTile: StateFlow<TileCoord> = _playerTile.asStateFlow()

    private val _events: MutableSharedFlow<HubEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
    )
    val events: SharedFlow<HubEvent> = _events.asSharedFlow()

    private val _runtimeState: MutableStateFlow<HubRuntimeState> = MutableStateFlow(HubRuntimeState())
    val runtimeState: StateFlow<HubRuntimeState> = _runtimeState.asStateFlow()

    private var pumpJob: Job? = null

    /** 启动后台轮询，将 raw bridge 事件翻译到 Flow；重复调用会先 cancel 旧 job。 */
    fun start(scope: CoroutineScope) {
        stop()
        pumpJob = scope.launch {
            while (isActive) {
                drainOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pumpJob?.cancel()
        pumpJob = null
    }

    /** 同步抽干 raw bridge 积压事件并更新 playerTile。 */
    fun drainOnce() {
        val latest = raw.latestPlayerTile()
        if (latest != _playerTile.value) {
            _playerTile.value = latest
        }
        while (true) {
            val event = raw.poll() ?: return
            _events.tryEmit(event)
        }
    }

    /** 请求与最近 NPC 交互，转发至 raw bridge。 */
    fun requestInteract() {
        raw.requestInteract()
    }

    /** 同步玩家 tile 到 raw bridge 与 StateFlow。 */
    fun submitPlayerTile(x: Int, y: Int) {
        raw.updatePlayerTile(x, y)
        _playerTile.value = TileCoord(x, y)
    }

    /** 更新运行时状态（暂停、缩放、时间倍率）。 */
    fun submitRuntimeState(state: HubRuntimeState) {
        _runtimeState.value = state
    }

    fun setPaused(paused: Boolean) {
        submitRuntimeState(_runtimeState.value.copy(paused = paused))
    }

    companion object {
        const val POLL_INTERVAL_MS: Long = 50L
        const val EVENT_BUFFER_CAPACITY: Int = 64
    }
}

/** Compose 侧运行时状态：暂停、缩放、时间倍率。 */
data class HubRuntimeState(
    val paused: Boolean = false,
    val zoom: Float = 1f,
    val timeScale: Float = 1f,
)
