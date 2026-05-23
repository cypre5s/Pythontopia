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
 * Compose / Coroutine 友好的 LibGDX 桥。包装 `:game` 模块的 [HubGameBridge]（线程安全的
 * `ConcurrentLinkedQueue + AtomicReference` 接口），在 R-12 demo 期间提供 [StateFlow] / [SharedFlow]
 * 的 idiomatic API，供 [HubViewModel] / [HubScreen] 直接消费。
 *
 * 设计取舍：
 * - 不直接修改 [HubGameBridge]，因为 `:game` 模块对 `kotlinx.coroutines` 是 implementation
 *   依赖而非 api 依赖，且 LibGDX 渲染线程不能阻塞，原始队列实现仍是渲染侧的最优选择。
 *   本类在 Compose 侧做"轮询 → Flow"的转译，由 [start] 启动后台协程。
 * - 双向通道：
 *   1. LibGDX → Compose：[playerTile]（StateFlow）、[events]（SharedFlow，64 缓冲，与
 *      `docs/android/03-rendering-libgdx-vs-compose.md` §11.5 一致）。
 *   2. Compose → LibGDX：[requestInteract] 直接转发给 raw bridge；[submitRuntimeState]
 *      更新本类的 [runtimeState]（R-12 阶段 LibGDX 端尚未消费，R-13 接入 paused/zoom 时
 *      由 [com.pythontopia.game.HubScreen] 读取本类暴露的接口）。
 * - 单例约束：由 [HubViewModel] 持有 1 个实例；ViewModel 销毁时调 [stop] 取消轮询协程，
 *   raw bridge 自然失效。LibGDX 渲染线程检测到下一帧 raw bridge 操作没人响应也能优雅退出。
 *
 * 与 `HubGameBridge.poll()` 的轮询关系：本类是 R-12 阶段的"过渡桥"，文档 §13.2 已规划 R-13
 * 把 raw bridge 内部改为 `MutableSharedFlow` 后，本类降级为薄包装（仅做 runtimeState 反向通道）。
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

    /**
     * 启动后台轮询协程：把 raw bridge 的玩家 tile / 事件队列持续翻译到 Flow 上。
     * - 必须由 [HubViewModel] 在 `init { }` 中调用一次，传入 `viewModelScope`；
     * - 重复调用安全：旧 [pumpJob] 会先 cancel 再启动；
     * - 协程内部 200ms 轮询，与原 `HubScreen.LaunchedEffect { while(true) poll() }` 一致，
     *   防止 R-12 demo 突变行为；R-13 改为 SharedFlow 后这里降为空操作。
     */
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

    /**
     * 同步抽干一次 raw bridge：把所有积压事件 emit 出去 + 更新 playerTile。
     * 在 [start] 协程中循环调用；测试时可直接调用以快速推进状态。
     */
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

    /**
     * Compose → LibGDX：请求与最近 NPC 交互。raw bridge 内部置一个 volatile flag，
     * LibGDX 渲染线程下一帧检测后清掉并触发 NpcInRange 再发。
     */
    fun requestInteract() {
        raw.requestInteract()
    }

    /**
     * Compose → LibGDX：更新 spawn / 重读后端进度时把玩家 tile 同步到 raw bridge，
     * 让 LibGDX [com.pythontopia.game.HubScreen.show] 能取到正确初始位置。
     * 同时把 StateFlow 也更新，避免 Compose HUD 显示旧值。
     */
    fun submitPlayerTile(x: Int, y: Int) {
        raw.updatePlayerTile(x, y)
        _playerTile.value = TileCoord(x, y)
    }

    /**
     * Compose → LibGDX：更新运行时状态（暂停、缩放、时间倍率）。
     * R-12 demo 仅 Compose 侧读，LibGDX 侧暂不消费（与 R-13 演进项对齐）。
     */
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

/**
 * Compose 侧维护的运行时状态：暂停 / 缩放 / 时间倍率。
 * 与文档 §3.4 `GameRuntimeState` 同语义；R-13 由 LibGDX 渲染线程读取。
 */
data class HubRuntimeState(
    val paused: Boolean = false,
    val zoom: Float = 1f,
    val timeScale: Float = 1f,
)
