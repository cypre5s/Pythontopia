package com.pythontopia.game

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** LibGDX 渲染线程与 Compose 主线程单向桥；事件经 Channel 推送，玩家坐标经 AtomicReference 共享。 */
class HubGameBridge {

    /** 渲染线程产出的事件流；Compose 线程消费。 */
    private val eventChannel: Channel<HubEvent> = Channel(
        capacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Compose 线程置 true，渲染线程检测后置 false 并触发 NPC 交互。 */
    @Volatile
    private var interactRequest: Boolean = false

    /** 渲染线程写、Compose 线程读：玩家当前地图与 tile 坐标。 */
    private val playerTile: AtomicReference<TileCoord> =
        AtomicReference(TileCoord(SPAWN_TILE_X, SPAWN_TILE_Y, DEFAULT_WORLD_MAP_KEY))

    /** Compose 线程写、渲染线程读：日夜/天气环境（驱动光照着色与天气粒子）。 */
    @Volatile
    private var environment: EnvState = EnvState()

    /** Compose 线程置请求、渲染线程消费：小地图点选瞬移到目标 tile。 */
    private val teleportRequest: AtomicReference<TileCoord?> = AtomicReference(null)

    fun emit(event: HubEvent) {
        eventChannel.trySend(event)
    }

    /**
     * 生产 UI 使用 Flow 订阅事件，只有真实游戏事件到达时才唤醒 Compose。
     */
    fun events(): Flow<HubEvent> = eventChannel.receiveAsFlow()

    /**
     * JVM 单测 / 兼容入口。真实 UI 不再走固定频率轮询；测试仍可同步断言 FIFO。
     */
    fun poll(): HubEvent? = eventChannel.tryReceive().getOrNull()

    fun requestInteract() {
        interactRequest = true
    }

    fun consumeInteractRequest(): Boolean {
        if (interactRequest) {
            interactRequest = false
            return true
        }
        return false
    }

    fun updatePlayerTile(x: Int, y: Int, mapKey: String? = null) {
        val current = playerTile.get()
        playerTile.set(TileCoord(x, y, mapKey ?: current.mapKey))
    }

    fun latestPlayerTile(): TileCoord = playerTile.get()

    /** 设置日夜/天气（来自后端日历）；渲染线程下一帧应用光照与粒子。 */
    fun setEnvironment(timeOfDay: String?, weather: String?) {
        environment = EnvState(timeOfDay, weather)
    }

    fun environment(): EnvState = environment

    /** 请求瞬移到 [x],[y]（小地图点选）；渲染线程下一帧消费并落到最近可走格。 */
    fun requestTeleport(x: Int, y: Int) {
        teleportRequest.set(TileCoord(x, y))
    }

    fun consumeTeleportRequest(): TileCoord? = teleportRequest.getAndSet(null)

    companion object {
        const val SPAWN_TILE_X = 25
        const val SPAWN_TILE_Y = 20
        const val EVENT_BUFFER_CAPACITY = 64
    }
}

data class TileCoord(
    val x: Int,
    val y: Int,
    val mapKey: String = DEFAULT_WORLD_MAP_KEY,
)

/** 日夜/天气环境快照（驱动 LibGDX 光照着色与天气粒子）。 */
data class EnvState(
    val timeOfDay: String? = null,
    val weather: String? = null,
)

sealed interface HubEvent {
    /** 玩家走入 [npcKey] 站点 1 格范围内。 */
    data class NpcInRange(val npcKey: String, val tileX: Int, val tileY: Int) : HubEvent
    /** 玩家离开所有 NPC 范围。 */
    data object NpcOutOfRange : HubEvent
    /** 玩家完成一次原生地图切换。 */
    data class ExitTouched(val targetMap: String) : HubEvent
    /** 玩家完成一步移动；Compose 侧据此异步上报后端存档。 */
    data class PlayerMoved(val mapKey: String, val tileX: Int, val tileY: Int, val direction: String) : HubEvent
}
