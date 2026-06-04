package com.pythontopia.game

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.pythontopia.game.PlayerActor.Direction

/**
 * 像素世界里的一个可渲染角色（玩家或 NPC）。
 *
 * 与旧 [PlayerActor]（纯逻辑、瞬时格子跳变）的差异：本类把渲染像素位置 [feetX]/[feetY]
 * 在两格之间**平滑插值**（对齐 web 端 `sprite.ts` 的 140ms tween 手感），并按
 * 朝向 + 是否移动选择 walk/idle 帧，外加一枚软阴影。坐标全程 y-up（与本项目统一）。
 *
 * 逻辑格子（[tileX]/[tileY]）在一步**开始**时即更新（用于碰撞 / 邻近判定的提前量），
 * 像素位置随后追赶；这样 NPC 邻近触发与 web 的"踏入格子即触发"一致。
 */
class WorldCharacter(
    private val anim: CharacterAnimSet,
    startTileX: Int,
    startTileY: Int,
    startFacing: Direction = Direction.DOWN,
) {
    var tileX: Int = startTileX
        private set
    var tileY: Int = startTileY
        private set
    var facing: Direction = startFacing
        private set
    var moving: Boolean = false
        private set

    private var feetXPx: Float = centerX(startTileX)
    private var feetYPx: Float = centerY(startTileY)
    private var fromX: Float = feetXPx
    private var fromY: Float = feetYPx
    private var toX: Float = feetXPx
    private var toY: Float = feetYPx
    private var progress: Float = 1f
    private var stateTime: Float = 0f

    val feetX: Float get() = feetXPx
    val feetY: Float get() = feetYPx

    fun snapTo(tx: Int, ty: Int) {
        tileX = tx
        tileY = ty
        feetXPx = centerX(tx)
        feetYPx = centerY(ty)
        fromX = feetXPx
        fromY = feetYPx
        toX = feetXPx
        toY = feetYPx
        progress = 1f
        moving = false
    }

    /** 开始走向相邻格 [tx],[ty]（朝向 [dir]）。逻辑格子立即更新。 */
    fun beginStep(tx: Int, ty: Int, dir: Direction) {
        facing = dir
        tileX = tx
        tileY = ty
        fromX = feetXPx
        fromY = feetYPx
        toX = centerX(tx)
        toY = centerY(ty)
        progress = 0f
        moving = true
    }

    /** 只转身不移动（卡墙时）。 */
    fun face(dir: Direction) {
        facing = dir
    }

    fun stop() {
        moving = false
    }

    /**
     * 推进插值。[stepDuration] 为跨一格所需秒数。返回 true 表示这一帧抵达目标格。
     */
    fun update(delta: Float, stepDuration: Float): Boolean {
        stateTime += delta
        if (!moving) return false
        progress += delta / stepDuration
        if (progress >= 1f) {
            progress = 1f
            feetXPx = toX
            feetYPx = toY
            return true
        }
        feetXPx = fromX + (toX - fromX) * progress
        feetYPx = fromY + (toY - fromY) * progress
        return false
    }

    /** 阴影绘制（与其它角色阴影共用同一张纹理，调用方一次性批量绘制以减少 flush）。 */
    fun drawShadow(batch: SpriteBatch, assets: GameAssets) {
        val shadowW = TILE * 0.85f
        val shadowH = TILE * 0.42f
        batch.draw(assets.shadow, feetXPx - shadowW / 2f, feetYPx - shadowH * 0.55f, shadowW, shadowH)
    }

    /** 角色本体绘制（按 y 排序后逐个画，保证前后遮挡正确）。 */
    fun drawBody(batch: SpriteBatch) {
        val region = anim.frame(facing, moving, stateTime)
        batch.draw(
            region,
            feetXPx - DRAW_SIZE / 2f,
            feetYPx - DRAW_SIZE * FOOT_RATIO,
            DRAW_SIZE,
            DRAW_SIZE,
        )
    }

    companion object {
        private val TILE = TiledMapVisual.TILE.toFloat()

        /** 64px LPC 帧在世界里的绘制边长（约 2 格高，与 web 0.7 缩放视觉一致）。 */
        private const val DRAW_SIZE = 30f

        /** 脚底锚点在绘制框中的「自底向上」比例：LPC 脚约在帧底 ~10%，留一点贴地。 */
        private const val FOOT_RATIO = 0.12f

        private fun centerX(tx: Int): Float = tx * TILE + TILE / 2f
        private fun centerY(ty: Int): Float = ty * TILE + TILE / 2f

        fun directionOf(dx: Int, dy: Int, fallback: Direction): Direction = when {
            dx == 0 && dy == 0 -> fallback
            kotlin.math.abs(dx) >= kotlin.math.abs(dy) -> if (dx > 0) Direction.RIGHT else Direction.LEFT
            else -> if (dy > 0) Direction.UP else Direction.DOWN
        }
    }
}
