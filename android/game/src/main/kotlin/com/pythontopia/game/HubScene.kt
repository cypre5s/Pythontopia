package com.pythontopia.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Hub 场景：程序化绘制 50×40 网格（草地 + 6 个建筑 + 4 个门洞 + 1 个 NPC 点）。
 *
 * 渲染策略：
 * - 使用 [ShapeRenderer] 画矩形（草地/路/建筑/边界/NPC/玩家），不依赖外部 PNG，
 *   保证在缺少素材打包的环境下也能跑起 demo。
 * - 真正的瓦片素材接入留给 Android-R13+：那时换成 [SpriteBatch] + [TextureAtlas]。
 *
 * 输入处理：
 * - 键盘 WASD / 方向键：每按一次走一格（按住每 120ms 走一格）。
 * - 屏幕触摸：在屏幕中心方向 D-Pad 风格落点位置走对应方向（[touchToDirection]）。
 *
 * NPC 交互检测：
 * - 当玩家与某个 NPC 的曼哈顿距离 ≤ 1 时，往 bridge 写一个 [HubEvent.NpcInRange]。
 * - 若 bridge 的 `consumeInteractRequest()` 返回 true（Compose 侧点了交互按钮），就再发一次。
 */
class HubScene(
    private val bridge: HubGameBridge,
) : ScreenAdapter() {

    private val camera = OrthographicCamera()
    private val shapeRenderer = ShapeRenderer()

    private var playerTileX: Int = HubGameBridge.SPAWN_TILE_X
    private var playerTileY: Int = HubGameBridge.SPAWN_TILE_Y
    private var stepCooldown: Float = 0f
    private var lastEmittedNpcKey: String? = null

    override fun show() {
        camera.setToOrtho(true, WORLD_PIXEL_WIDTH.toFloat(), WORLD_PIXEL_HEIGHT.toFloat())
        camera.update()
    }

    override fun resize(width: Int, height: Int) {
        val targetAspect = WORLD_PIXEL_WIDTH.toFloat() / WORLD_PIXEL_HEIGHT
        val actualAspect = width.toFloat() / height
        // 简单 letterbox：保持纵横比，由 GL 视口 fit。
        val viewportWidth: Float
        val viewportHeight: Float
        if (actualAspect >= targetAspect) {
            viewportHeight = WORLD_PIXEL_HEIGHT.toFloat()
            viewportWidth = viewportHeight * actualAspect
        } else {
            viewportWidth = WORLD_PIXEL_WIDTH.toFloat()
            viewportHeight = viewportWidth / actualAspect
        }
        camera.viewportWidth = viewportWidth
        camera.viewportHeight = viewportHeight
        camera.position.set(WORLD_PIXEL_WIDTH / 2f, WORLD_PIXEL_HEIGHT / 2f, 0f)
        camera.update()
    }

    override fun render(delta: Float) {
        handleInput(delta)
        Gdx.gl.glClearColor(BG_R, BG_G, BG_B, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        drawGround()
        drawPathways()
        drawBuildings()
        drawExits()
        drawNpcs()
        drawPlayer()
        shapeRenderer.end()
    }

    override fun dispose() {
        shapeRenderer.dispose()
    }

    private fun handleInput(delta: Float) {
        stepCooldown -= delta
        if (stepCooldown > 0f) return

        var dx = 0
        var dy = 0
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) dy -= 1
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) dy += 1
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) dx -= 1
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx += 1

        if (dx == 0 && dy == 0 && Gdx.input.isTouched) {
            val (touchDx, touchDy) = touchToDirection(Gdx.input.x, Gdx.input.y)
            dx = touchDx
            dy = touchDy
        }

        if (dx != 0 || dy != 0) {
            val newX = (playerTileX + dx).coerceIn(0, MAP_WIDTH - 1)
            val newY = (playerTileY + dy).coerceIn(0, MAP_HEIGHT - 1)
            if (!isBlocked(newX, newY)) {
                playerTileX = newX
                playerTileY = newY
                bridge.updatePlayerTile(playerTileX, playerTileY)
                stepCooldown = STEP_INTERVAL
                checkProximity()
            }
        }

        if (bridge.consumeInteractRequest()) {
            checkProximity(force = true)
        }
    }

    private fun touchToDirection(rawX: Int, rawY: Int): Pair<Int, Int> {
        val sx = Gdx.graphics.width.toFloat()
        val sy = Gdx.graphics.height.toFloat()
        val nx = rawX / sx
        val ny = rawY / sy
        val cx = 0.5f
        val cy = 0.5f
        val ddx = nx - cx
        val ddy = ny - cy
        val deadzone = 0.08f
        if (abs(ddx) < deadzone && abs(ddy) < deadzone) return 0 to 0
        return if (abs(ddx) > abs(ddy)) {
            (if (ddx > 0) 1 else -1) to 0
        } else {
            0 to (if (ddy > 0) 1 else -1)
        }
    }

    private fun isBlocked(x: Int, y: Int): Boolean = BUILDINGS.any { b ->
        x in b.x until (b.x + b.w) && y in b.y until (b.y + b.h)
    }

    private fun checkProximity(force: Boolean = false) {
        val nearby = NPCS.firstOrNull { npc ->
            abs(npc.x - playerTileX) <= 1 && abs(npc.y - playerTileY) <= 1
        }
        if (nearby == null) {
            if (lastEmittedNpcKey != null) {
                lastEmittedNpcKey = null
                bridge.emit(HubEvent.NpcOutOfRange)
            }
            return
        }
        if (force || nearby.key != lastEmittedNpcKey) {
            lastEmittedNpcKey = nearby.key
            bridge.emit(HubEvent.NpcInRange(nearby.key, nearby.x, nearby.y))
        }
    }

    private fun drawGround() {
        shapeRenderer.color = Color(0.40f, 0.58f, 0.30f, 1f)
        shapeRenderer.rect(0f, 0f, WORLD_PIXEL_WIDTH.toFloat(), WORLD_PIXEL_HEIGHT.toFloat())
    }

    private fun drawPathways() {
        shapeRenderer.color = Color(0.82f, 0.74f, 0.45f, 1f)
        // 中央竖向主路 (tile x = 25)
        rectTile(25, 0, 1, MAP_HEIGHT)
        // 中央横向主路 (tile y = 20)
        rectTile(0, 20, MAP_WIDTH, 1)
    }

    private fun drawBuildings() {
        for (b in BUILDINGS) {
            val (cr, cg, cb) = b.color
            shapeRenderer.color = Color(cr, cg, cb, 1f)
            rectTile(b.x, b.y, b.w, b.h)
            // 屋顶高光
            shapeRenderer.color = Color(min(cr + 0.10f, 1f), min(cg + 0.10f, 1f), min(cb + 0.10f, 1f), 1f)
            rectTile(b.x, b.y, b.w, max(b.h / 2, 1))
        }
    }

    private fun drawExits() {
        shapeRenderer.color = Color(0.96f, 0.86f, 0.30f, 1f)
        // 北门 (顶部)
        rectTile(MAP_WIDTH / 2 - 2, 0, 4, 1)
        // 南门 (底部)
        rectTile(MAP_WIDTH / 2 - 2, MAP_HEIGHT - 1, 4, 1)
        // 东门 (右)
        rectTile(MAP_WIDTH - 1, MAP_HEIGHT / 2 - 2, 1, 4)
        // 西门 (左)
        rectTile(0, MAP_HEIGHT / 2 - 2, 1, 4)
    }

    private fun drawNpcs() {
        for (n in NPCS) {
            shapeRenderer.color = Color(0.95f, 0.36f, 0.36f, 1f)
            rectTile(n.x, n.y, 1, 1)
        }
    }

    private fun drawPlayer() {
        shapeRenderer.color = Color(0.20f, 0.55f, 0.90f, 1f)
        rectTile(playerTileX, playerTileY, 1, 1)
    }

    private fun rectTile(tileX: Int, tileY: Int, tilesW: Int, tilesH: Int) {
        val px = tileX * TILE_SIZE
        val py = tileY * TILE_SIZE
        shapeRenderer.rect(px.toFloat(), py.toFloat(), (tilesW * TILE_SIZE).toFloat(), (tilesH * TILE_SIZE).toFloat())
    }

    private data class Building(val x: Int, val y: Int, val w: Int, val h: Int, val color: Triple<Float, Float, Float>)
    private data class Npc(val key: String, val x: Int, val y: Int)

    companion object {
        const val TILE_SIZE = 16
        const val MAP_WIDTH = 50
        const val MAP_HEIGHT = 40
        const val WORLD_PIXEL_WIDTH = MAP_WIDTH * TILE_SIZE
        const val WORLD_PIXEL_HEIGHT = MAP_HEIGHT * TILE_SIZE
        const val STEP_INTERVAL = 0.12f

        private const val BG_R = 0.06f
        private const val BG_G = 0.05f
        private const val BG_B = 0.04f

        // 6 个建筑，参考 frontend town_hub.json 的 building 对象大致位置（按 tile 坐标缩放）。
        private val BUILDINGS = listOf(
            // 镇长大厅（mayor_hall）位置 tile x=23..27 y=4..8
            Building(23, 4, 5, 5, Triple(0.55f, 0.30f, 0.20f)),
            // 民居·东 (house_a): tile 8..12, 28..32
            Building(8, 28, 4, 4, Triple(0.65f, 0.40f, 0.25f)),
            // 民居·中 (house_b): tile 22..26, 28..32
            Building(22, 28, 4, 4, Triple(0.65f, 0.40f, 0.25f)),
            // 民居·西 (house_c): tile 36..40, 28..32
            Building(36, 28, 4, 4, Triple(0.65f, 0.40f, 0.25f)),
            // 杂货铺 (shop_west): tile 4..8, 14..18
            Building(4, 14, 4, 4, Triple(0.30f, 0.45f, 0.65f)),
            // 工具铺 (shop_east): tile 42..46, 14..18
            Building(42, 14, 4, 4, Triple(0.30f, 0.45f, 0.65f)),
        )

        // demo 仅启用 mayor_py（其它 NPC 留给 Android-R13+ 扩展）。
        // 坐标参考 frontend town_hub.json id=12 的 npc_mayor_py（x=400/16=25, y=160/16=10）。
        private val NPCS = listOf(
            Npc("mayor_py", 25, 10),
        )
    }
}
