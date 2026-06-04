package com.pythontopia.game

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * LibGDX 渲染线程 ←→ Compose 主线程 单向桥。
 *
 * - 渲染线程把"进入 NPC 交互范围"作为 [HubEvent.NpcInRange] 推到 [events]。
 * - Compose 线程 collect [events] 拿事件，并通过 [interactRequest] 标志请求"点了交互按钮"。
 * - [latestPlayerTile] 用 AtomicReference 暴露玩家当前 tile 坐标，给 Compose UI HUD 显示。
 *
 * 该类不依赖 Android / Phaser / Compose；事件推送走 kotlinx.coroutines core，避免 UI 固定轮询。
 */
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
