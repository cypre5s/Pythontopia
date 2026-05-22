package com.pythontopia.game

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * LibGDX 渲染线程 ←→ Compose 主线程 单向桥。
 *
 * - 渲染线程把"进入 NPC 交互范围"作为 [HubEvent.NpcInRange] 入队。
 * - Compose 线程轮询 [poll] 拿事件，并通过 [interactRequest] 标志请求"点了交互按钮"。
 * - [latestPlayerTile] 用 AtomicReference 暴露玩家当前 tile 坐标，给 Compose UI HUD 显示。
 *
 * 该类不依赖 Android / Phaser / Compose，只用 java.util.concurrent；保持 :game 模块上游零依赖。
 */
class HubGameBridge {

    /** 渲染线程产出的事件队列；Compose 线程消费。 */
    private val events: ConcurrentLinkedQueue<HubEvent> = ConcurrentLinkedQueue()

    /** Compose 线程置 true，渲染线程检测后置 false 并触发 NPC 交互。 */
    @Volatile
    private var interactRequest: Boolean = false

    /** 渲染线程写、Compose 线程读：玩家当前 tile 坐标。 */
    private val playerTile: AtomicReference<TileCoord> = AtomicReference(TileCoord(SPAWN_TILE_X, SPAWN_TILE_Y))

    fun emit(event: HubEvent) {
        events.add(event)
    }

    fun poll(): HubEvent? = events.poll()

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

    fun updatePlayerTile(x: Int, y: Int) {
        playerTile.set(TileCoord(x, y))
    }

    fun latestPlayerTile(): TileCoord = playerTile.get()

    companion object {
        const val SPAWN_TILE_X = 25
        const val SPAWN_TILE_Y = 20
    }
}

data class TileCoord(val x: Int, val y: Int)

sealed interface HubEvent {
    /** 玩家走入 [npcKey] 站点 1 格范围内。 */
    data class NpcInRange(val npcKey: String, val tileX: Int, val tileY: Int) : HubEvent
    /** 玩家离开所有 NPC 范围。 */
    data object NpcOutOfRange : HubEvent
    /** 玩家走到边缘门洞（地图切换占位，Android-R13+ 实现）。 */
    data class ExitTouched(val targetMap: String) : HubEvent
}
