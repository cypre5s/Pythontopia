package com.pythontopia.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4

/** LibGDX 小镇屏（ShapeRenderer 兜底渲染）：单中央小镇 town_hub，12 个 NPC 锚点为交互目标。 */
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

    private var currentMap: WorldMapDefinition = WorldMaps.townHub
    private var lastEmittedNpcKey: String? = null

    override fun show() {
        Gdx.input?.inputProcessor = input
        val seed = bridge.latestPlayerTile()
        currentMap = WorldMaps.byKey(seed.mapKey)
        player.snapTo(
            seed.x.coerceIn(0, currentMap.width - 1),
            seed.y.coerceIn(0, currentMap.height - 1),
        )
        bridge.updatePlayerTile(player.tileX, player.tileY, currentMap.key)
        configureCamera(Gdx.graphics?.width ?: currentMap.pixelWidth, Gdx.graphics?.height ?: currentMap.pixelHeight)
        checkProximity(force = true)
    }

    override fun hide() {
        if (Gdx.input?.inputProcessor === input) {
            Gdx.input.inputProcessor = null
        }
    }

    override fun resize(width: Int, height: Int) {
        configureCamera(width, height)
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

    private fun configureCamera(width: Int, height: Int) {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val targetAspect = currentMap.pixelWidth.toFloat() / currentMap.pixelHeight
        val actualAspect = safeWidth.toFloat() / safeHeight
        val viewportWidth: Float
        val viewportHeight: Float
        if (actualAspect >= targetAspect) {
            viewportHeight = currentMap.pixelHeight.toFloat()
            viewportWidth = viewportHeight * actualAspect
        } else {
            viewportWidth = currentMap.pixelWidth.toFloat()
            viewportHeight = viewportWidth / actualAspect
        }
        camera.viewportWidth = viewportWidth
        camera.viewportHeight = viewportHeight
        camera.position.set(currentMap.pixelWidth / 2f, currentMap.pixelHeight / 2f, 0f)
        camera.update()

        hudMatrix.setToOrtho2D(0f, 0f, safeWidth.toFloat(), safeHeight.toFloat())
        input.updateLayout()
    }

    private fun handleInput() {
        val gdxInput = Gdx.input
        val snapshot = InputController.KeyboardSnapshot(
            up = gdxInput?.isKeyPressed(com.badlogic.gdx.Input.Keys.W) == true ||
                gdxInput?.isKeyPressed(com.badlogic.gdx.Input.Keys.UP) == true,
            down = gdxInput?.isKeyPressed(com.badlogic.gdx.Input.Keys.S) == true ||
                gdxInput?.isKeyPressed(com.badlogic.gdx.Input.Keys.DOWN) == true,
            left = gdxInput?.isKeyPressed(com.badlogic.gdx.Input.Keys.A) == true ||
                gdxInput?.isKeyPressed(com.badlogic.gdx.Input.Keys.LEFT) == true,
            right = gdxInput?.isKeyPressed(com.badlogic.gdx.Input.Keys.D) == true ||
                gdxInput?.isKeyPressed(com.badlogic.gdx.Input.Keys.RIGHT) == true,
        )
        val (dx, dy) = input.resolveStep(snapshot)
        if (dx != 0 || dy != 0) {
            tryMove(dx, dy)
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

    private fun tryMove(dx: Int, dy: Int) {
        val nextX = (player.tileX + dx).coerceIn(0, currentMap.width - 1)
        val nextY = (player.tileY + dy).coerceIn(0, currentMap.height - 1)
        val exit = currentMap.exits.firstOrNull { it.area.contains(nextX, nextY) }
        if (exit != null) {
            switchMap(exit.targetMap)
            return
        }

        val moved = player.tryStep(
            dx = dx,
            dy = dy,
            mapWidth = currentMap.width,
            mapHeight = currentMap.height,
            blocked = ::isBlocked,
        )
        if (moved) {
            publishPlayerPosition()
            checkProximity(force = false)
        }
    }

    private fun switchMap(targetMapKey: String) {
        val target = WorldMaps.byKey(targetMapKey)
        currentMap = target
        player.snapTo(target.spawnX, target.spawnY)
        lastEmittedNpcKey = null
        configureCamera(Gdx.graphics?.width ?: target.pixelWidth, Gdx.graphics?.height ?: target.pixelHeight)
        bridge.emit(HubEvent.NpcOutOfRange)
        bridge.emit(HubEvent.ExitTouched(target.key))
        publishPlayerPosition()
        checkProximity(force = true)
    }

    private fun publishPlayerPosition() {
        val direction = player.facing.toServerString()
        bridge.updatePlayerTile(player.tileX, player.tileY, currentMap.key)
        bridge.emit(HubEvent.PlayerMoved(currentMap.key, player.tileX, player.tileY, direction))
        onPlayerMoved?.invoke(player.tileX, player.tileY, direction)
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
        shapeRenderer.color = currentMap.groundColor
        shapeRenderer.rect(0f, 0f, currentMap.pixelWidth.toFloat(), currentMap.pixelHeight.toFloat())
    }

    private fun drawPathways() {
        shapeRenderer.color = currentMap.pathColor
        rectTile(currentMap.width / 2, 0, 1, currentMap.height)
        rectTile(0, currentMap.height / 2, currentMap.width, 1)
    }

    private fun drawBuildings() {
        for (b in currentMap.buildings) {
            shapeRenderer.color = PixelTheme.buildingWall
            rectTile(b.x, b.y, b.w, b.h)
            shapeRenderer.color = PixelTheme.buildingRoof
            rectTile(b.x, b.y + b.h - 1, b.w, 1)
        }
    }

    private fun drawExits() {
        shapeRenderer.color = PixelTheme.mapExit
        for (exit in currentMap.exits) {
            rectTile(exit.area.x, exit.area.y, exit.area.w, exit.area.h)
        }
    }

    private fun drawNpcs() {
        for (n in currentMap.npcs) {
            shapeRenderer.color = npcColor(n.key)
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

    private fun isBlocked(x: Int, y: Int): Boolean =
        currentMap.buildings.any { it.contains(x, y) }

    private fun checkProximity(force: Boolean) {
        val nearby = currentMap.npcs.firstOrNull { player.isAdjacentTo(it.x, it.y) }
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

    private fun npcColor(key: String) = when (key) {
        "coach_q", "trainer", "arena_guard" -> PixelTheme.warning
        "librarian_doc", "sage_review", "professor", "tutor_ai" -> PixelTheme.accentPurple
        "foreman_bug", "miner", "mine_keeper" -> PixelTheme.gray300
        "sailor", "captain_def", "class_president" -> PixelTheme.info
        else -> PixelTheme.npcBody
    }

    companion object {
        const val TILE_SIZE: Int = ANDROID_TILE_SIZE
    }
}
