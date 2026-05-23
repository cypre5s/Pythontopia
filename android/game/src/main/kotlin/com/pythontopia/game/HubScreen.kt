package com.pythontopia.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4

/**
 * Pythontopia hub LibGDX 屏幕：[PythontopiaGame] 启动后默认进入的场景。
 *
 * 与既有 [HubScene] 的差异（两者可共存）：
 * - 把"输入处理 / 玩家状态 / 颜色 token"拆出独立类（[InputController]/[PlayerActor]/[PixelTheme]），
 *   方便 headless 测试与 R-13 替换为 `SpriteBatch + TextureAtlas`。
 * - 摄像机用 `y-up`（更贴近标准 LibGDX 约定），渲染时直接写世界坐标，省掉手工镜像。
 * - 渲染分两阶段：世界（用世界投影矩阵）+ HUD overlay（虚拟摇杆用屏幕像素矩阵），
 *   避免摇杆随相机缩放变形。
 *
 * 渲染数据来自硬编码的 [HUB_BUILDINGS] / [HUB_NPCS]，与 [HubScene] 同源；R-13 后改为从
 * `assets/pixel/maps/town_hub.tmx` 读取（见 `docs/android/03-rendering-libgdx-vs-compose.md` §13）。
 */
class HubScreen(
    private val bridge: HubGameBridge,
    private val onPlayerMoved: ((tileX: Int, tileY: Int, direction: String) -> Unit)? = null,
) : ScreenAdapter() {

    private val camera = OrthographicCamera()
    private val hudMatrix = Matrix4()
    private val shapeRenderer = ShapeRenderer()
    private val player = PlayerActor()
    private val input = InputController(
        viewportWidthProvider = { Gdx.graphics?.width ?: 0 },
        viewportHeightProvider = { Gdx.graphics?.height ?: 0 },
    )
    private var lastEmittedNpcKey: String? = null

    override fun show() {
        camera.setToOrtho(false, WORLD_PIXEL_WIDTH.toFloat(), WORLD_PIXEL_HEIGHT.toFloat())
        camera.update()
        Gdx.input?.inputProcessor = input

        val seed = bridge.latestPlayerTile()
        if (seed.x != player.tileX || seed.y != player.tileY) {
            player.snapTo(seed.x, seed.y)
            bridge.updatePlayerTile(player.tileX, player.tileY)
        }
    }

    override fun hide() {
        if (Gdx.input?.inputProcessor === input) {
            Gdx.input.inputProcessor = null
        }
    }

    override fun resize(width: Int, height: Int) {
        val targetAspect = WORLD_PIXEL_WIDTH.toFloat() / WORLD_PIXEL_HEIGHT
        val actualAspect = width.toFloat() / height
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

        hudMatrix.setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        input.updateLayout()
    }

    override fun render(delta: Float) {
        player.tick(delta)
        handleInput()
        clear()
        drawWorld()
        drawHud()
    }

    override fun dispose() {
        shapeRenderer.dispose()
    }

    private fun handleInput() {
        val snapshot = InputController.KeyboardSnapshot.from(buildSet {
            for (k in MOVE_KEYS) {
                if (Gdx.input?.isKeyPressed(k) == true) add(k)
            }
        })
        val (dx, dy) = input.resolveStep(snapshot)
        if (dx != 0 || dy != 0) {
            val moved = player.tryStep(
                dx = dx,
                dy = dy,
                mapWidth = MAP_WIDTH,
                mapHeight = MAP_HEIGHT,
                blocked = ::isBlocked,
            )
            if (moved) {
                bridge.updatePlayerTile(player.tileX, player.tileY)
                onPlayerMoved?.invoke(player.tileX, player.tileY, player.facing.toServerString())
                checkProximity(force = false)
            }
        }

        while (true) {
            val edge = input.pollEdge() ?: break
            if (edge == InputController.EdgeEvent.INTERACT) {
                checkProximity(force = true)
            }
        }

        if (bridge.consumeInteractRequest()) {
            checkProximity(force = true)
        }
    }

    private fun clear() {
        Gdx.gl?.glClearColor(
            PixelTheme.clearBackground.r,
            PixelTheme.clearBackground.g,
            PixelTheme.clearBackground.b,
            1f,
        )
        Gdx.gl?.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }

    private fun drawWorld() {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        drawGround()
        drawPathways()
        drawBuildings()
        drawExits()
        drawNpcs()
        player.draw(shapeRenderer, TILE_SIZE)
        shapeRenderer.end()
    }

    private fun drawHud() {
        shapeRenderer.projectionMatrix = hudMatrix
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        input.drawJoystick(shapeRenderer)
        shapeRenderer.end()
    }

    private fun drawGround() {
        shapeRenderer.color = PixelTheme.ground
        shapeRenderer.rect(0f, 0f, WORLD_PIXEL_WIDTH.toFloat(), WORLD_PIXEL_HEIGHT.toFloat())
    }

    private fun drawPathways() {
        shapeRenderer.color = PixelTheme.path
        rectTile(MAP_WIDTH / 2, 0, 1, MAP_HEIGHT)
        rectTile(0, MAP_HEIGHT / 2, MAP_WIDTH, 1)
    }

    private fun drawBuildings() {
        for (b in HUB_BUILDINGS) {
            shapeRenderer.color = PixelTheme.buildingWall
            rectTile(b.x, b.y, b.w, b.h)
            shapeRenderer.color = PixelTheme.buildingRoof
            rectTile(b.x, b.y + b.h - 1, b.w, 1)
        }
    }

    private fun drawExits() {
        shapeRenderer.color = PixelTheme.mapExit
        rectTile(MAP_WIDTH / 2 - 2, MAP_HEIGHT - 1, 4, 1)
        rectTile(MAP_WIDTH / 2 - 2, 0, 4, 1)
        rectTile(MAP_WIDTH - 1, MAP_HEIGHT / 2 - 2, 1, 4)
        rectTile(0, MAP_HEIGHT / 2 - 2, 1, 4)
    }

    private fun drawNpcs() {
        shapeRenderer.color = PixelTheme.npcBody
        for (n in HUB_NPCS) {
            rectTile(n.x, n.y, 1, 1)
        }
    }

    private fun rectTile(tx: Int, ty: Int, w: Int, h: Int) {
        shapeRenderer.rect(
            (tx * TILE_SIZE).toFloat(),
            (ty * TILE_SIZE).toFloat(),
            (w * TILE_SIZE).toFloat(),
            (h * TILE_SIZE).toFloat(),
        )
    }

    private fun isBlocked(x: Int, y: Int): Boolean = HUB_BUILDINGS.any { b ->
        x in b.x until (b.x + b.w) && y in b.y until (b.y + b.h)
    }

    private fun checkProximity(force: Boolean) {
        val nearby = HUB_NPCS.firstOrNull { player.isAdjacentTo(it.x, it.y) }
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

    data class Building(val x: Int, val y: Int, val w: Int, val h: Int)
    data class Npc(val key: String, val x: Int, val y: Int)

    companion object {
        const val TILE_SIZE: Int = 32
        const val MAP_WIDTH: Int = 50
        const val MAP_HEIGHT: Int = 40
        const val WORLD_PIXEL_WIDTH: Int = MAP_WIDTH * TILE_SIZE
        const val WORLD_PIXEL_HEIGHT: Int = MAP_HEIGHT * TILE_SIZE

        private val MOVE_KEYS = intArrayOf(
            com.badlogic.gdx.Input.Keys.W,
            com.badlogic.gdx.Input.Keys.A,
            com.badlogic.gdx.Input.Keys.S,
            com.badlogic.gdx.Input.Keys.D,
            com.badlogic.gdx.Input.Keys.UP,
            com.badlogic.gdx.Input.Keys.LEFT,
            com.badlogic.gdx.Input.Keys.DOWN,
            com.badlogic.gdx.Input.Keys.RIGHT,
        )

        // 与 frontend/public/assets/maps/town_hub.json 的 building 对象同源（y 轴 y-up 翻转）：
        // frontend 用 y-down（pixel y=0 在上），本 Screen 用 y-up（tile y=0 在下），坐标 = 40 - y_down - h。
        // R-13 接入 TmxMapLoader 后由 tmx 解析取代，本表删除。
        // 严格避让主路：x=25 竖向主路、y=20 横向主路 不被任何 building 覆盖，
        // 否则玩家无法从 spawn 走到 mayor_py。
        val HUB_BUILDINGS: List<Building> = listOf(
            Building(23, 31, 5, 5), // 镇长大厅 mayor_hall（北侧主建筑，挡住玩家继续北上）
            Building(8, 4, 4, 4),   // 民居·东 house_a（南侧左）
            Building(21, 4, 4, 4),  // 民居·中 house_b（南侧中，覆盖 21-24 避开 x=25）
            Building(36, 4, 4, 4),  // 民居·西 house_c（南侧右）
            Building(4, 14, 4, 4),  // 杂货铺 shop_west（左中，避开 y=20 横路）
            Building(42, 14, 4, 4), // 工具铺 shop_east（右中，避开 y=20 横路）
        )

        // demo 仅启用 mayor_py；y-up 坐标：站在 mayor_hall (y=31-35) 正南的主路 (25, 30)。
        // 玩家从 spawn (25, 20) 向北走 10 格即可与之相邻并触发 NpcInRange。
        val HUB_NPCS: List<Npc> = listOf(
            Npc("mayor_py", 25, 30),
        )
    }
}
