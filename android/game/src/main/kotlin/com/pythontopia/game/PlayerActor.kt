package com.pythontopia.game

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.abs

/**
 * 玩家角色：tile 坐标 + 4 向朝向 + 简单步进冷却 + 自绘像素体。
 *
 * 不持有任何 GL handle（[ShapeRenderer] 由调用方传入并由调用方负责 begin/end），
 * 因此可以在 headless 环境单测全部逻辑分支（移动、阻挡、朝向、邻近判定）。
 *
 * 设计取舍：
 * - tile 单位最小步长 = 1 格，每步固定 [PlayerActor.STEP_INTERVAL] 秒冷却；
 *   匹配 web 端 `frontend/src/game/scenes/WorldScene.ts` 的 grid-based 移动。
 * - 朝向用 4 向枚举（与后端 `/api/world/player` 的 direction 字段一致），8 向降级
 *   在 [InputController] 层处理后传入。
 * - 当玩家与 NPC 曼哈顿距离 ≤ 1 时视为「相邻」，与 [HubScreen] 的对话触发距离一致。
 */
class PlayerActor(
    spawnTileX: Int = HubGameBridge.SPAWN_TILE_X,
    spawnTileY: Int = HubGameBridge.SPAWN_TILE_Y,
) {

    var tileX: Int = spawnTileX
        private set

    var tileY: Int = spawnTileY
        private set

    var facing: Direction = Direction.DOWN
        private set

    private var stepCooldown: Float = 0f

    /**
     * 推进冷却：每帧 [HubScreen.render] 调用。冷却结束后才允许下一次 [tryStep]。
     */
    fun tick(delta: Float) {
        if (stepCooldown > 0f) {
            stepCooldown -= delta
            if (stepCooldown < 0f) stepCooldown = 0f
        }
    }

    /**
     * 尝试朝 [dx]/[dy] 走一格。返回 true 表示移动成功。
     *
     * - 在冷却中或目标格被 [blocked] 阻挡时不动；
     * - dx/dy 非 0 但不能移动时仍更新 [facing]（玩家"想"看哪边），符合星露谷"卡墙转身"手感。
     */
    fun tryStep(dx: Int, dy: Int, mapWidth: Int, mapHeight: Int, blocked: (Int, Int) -> Boolean): Boolean {
        if (dx == 0 && dy == 0) return false
        facing = directionOf(dx, dy)
        if (stepCooldown > 0f) return false

        val nextX = (tileX + dx).coerceIn(0, mapWidth - 1)
        val nextY = (tileY + dy).coerceIn(0, mapHeight - 1)
        if (nextX == tileX && nextY == tileY) return false
        if (blocked(nextX, nextY)) return false

        tileX = nextX
        tileY = nextY
        stepCooldown = STEP_INTERVAL
        return true
    }

    /**
     * 强制回到指定 tile（如 bootstrap 时拉服务器存档位置）。重置冷却让玩家立即可动。
     */
    fun snapTo(x: Int, y: Int) {
        tileX = x
        tileY = y
        stepCooldown = 0f
    }

    /**
     * 与另一个 tile 的曼哈顿距离 ≤ 1（含本格）。
     */
    fun isAdjacentTo(otherX: Int, otherY: Int): Boolean =
        abs(otherX - tileX) <= 1 && abs(otherY - tileY) <= 1

    /**
     * 用 [ShapeRenderer] 画一个 1×1 tile 的玩家像素体 + 朝向指示器。
     * 渲染线程调用；调用方负责 `shape.begin(Filled)` / `shape.end()`。
     */
    fun draw(shape: ShapeRenderer, tileSize: Int) {
        val px = (tileX * tileSize).toFloat()
        val py = (tileY * tileSize).toFloat()
        val size = tileSize.toFloat()

        shape.color = PixelTheme.playerBody
        shape.rect(px, py, size, size)

        shape.color = PixelTheme.playerFacing
        val pip = tileSize / 4f
        when (facing) {
            Direction.UP -> shape.rect(px + pip, py + size - pip, pip * 2, pip)
            Direction.DOWN -> shape.rect(px + pip, py, pip * 2, pip)
            Direction.LEFT -> shape.rect(px, py + pip, pip, pip * 2)
            Direction.RIGHT -> shape.rect(px + size - pip, py + pip, pip, pip * 2)
        }
    }

    private fun directionOf(dx: Int, dy: Int): Direction = when {
        abs(dx) >= abs(dy) -> if (dx > 0) Direction.RIGHT else Direction.LEFT
        else -> if (dy > 0) Direction.UP else Direction.DOWN
    }

    /** 4 向（与后端 PlayerStateDto.direction 同空间）。LibGDX 摄像机 `y-up`，DOWN = 屏幕下方。 */
    enum class Direction {
        UP, DOWN, LEFT, RIGHT;

        fun toServerString(): String = when (this) {
            UP -> "UP"
            DOWN -> "DOWN"
            LEFT -> "LEFT"
            RIGHT -> "RIGHT"
        }
    }

    companion object {
        /** 每格步进的冷却（秒）；与 web 端 grid 移动节奏对齐。 */
        const val STEP_INTERVAL: Float = 0.14f
    }
}
