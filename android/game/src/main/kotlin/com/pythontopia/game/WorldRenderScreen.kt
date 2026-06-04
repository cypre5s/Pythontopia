package com.pythontopia.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.pythontopia.game.PlayerActor.Direction
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * 原生像素世界主屏：Kenney 瓦片地图 + 官方 LPC/ULPC 角色精灵，镜头跟随玩家。
 *
 * 取代旧 [HubScreen] 的 `ShapeRenderer` 色块渲染：
 * - 真实瓦片：解析 [TiledMapVisual]（town_hub 等 5 图）按层渲染 + 视锥剔除；
 * - 真实角色：主角 ULPC 行走/待机动画 + 平滑插值移动，16 个 NPC 用 LPC 合成图；
 * - 镜头：跟随玩家 + 边界 clamp + 拉近缩放，告别"小镇缩成一团、玩家是个点"。
 *
 * 对外契约与 [HubScreen] 完全一致（同一 [HubGameBridge] 事件），因此 `:feature:hub`
 * 的 Compose HUD / 对话面板无需改动。碰撞优先取自瓦片 `buildings` 层（与可见建筑一致），
 * 缺图时回退 [WorldMapData] 矩形；NPC 锚点与出口仍用 [WorldMaps]（被 20-NPC 测试约束）。
 */
@Suppress("TooManyFunctions") // 渲染屏编排器：地图/输入/镜头/绘制各拆成小而单一职责的私有函数。
class WorldRenderScreen(
    private val bridge: HubGameBridge,
    private val onPlayerMoved: ((tileX: Int, tileY: Int, direction: String) -> Unit)? = null,
) : ScreenAdapter() {

    private var batch: SpriteBatch? = null
    private var shape: ShapeRenderer? = null
    private var assets: GameAssets? = null
    private val camera = OrthographicCamera()
    private val hudCamera = OrthographicCamera()
    private var input: InputController? = null

    private var currentMap: WorldMapDefinition = WorldMaps.townHub
    private var visual: TiledMapVisual? = null
    private var player: WorldCharacter? = null
    private val npcs = ArrayList<NpcInstance>()
    private val visibleChars = ArrayList<WorldCharacter>()
    private var lastEmittedNpcKey: String? = null

    override fun show() {
        batch = SpriteBatch()
        shape = ShapeRenderer()
        assets = GameAssets()
        input = InputController(
            viewportWidthProvider = { Gdx.graphics?.width ?: 0 },
            viewportHeightProvider = { Gdx.graphics?.height ?: 0 },
        ).also { Gdx.input?.inputProcessor = it }

        val seed = bridge.latestPlayerTile()
        loadMap(WorldMaps.byKey(seed.mapKey), spawnX = seed.x, spawnY = seed.y)
    }

    override fun hide() {
        if (Gdx.input?.inputProcessor === input) Gdx.input.inputProcessor = null
    }

    override fun resize(width: Int, height: Int) {
        configureCamera(width, height)
        hudCamera.setToOrtho(true, width.toFloat(), height.toFloat())
        hudCamera.update()
        input?.updateLayout()
    }

    override fun render(delta: Float) {
        val safeDelta = min(delta, MAX_DELTA)
        handleInput(safeDelta)
        followCamera()

        Gdx.gl?.glClearColor(
            PixelTheme.clearBackground.r,
            PixelTheme.clearBackground.g,
            PixelTheme.clearBackground.b,
            1f,
        )
        Gdx.gl?.glClear(GL20.GL_COLOR_BUFFER_BIT)

        val batch = batch ?: return
        val assets = assets ?: return
        batch.projectionMatrix = camera.combined
        batch.begin()
        drawWorld(batch, assets)
        drawCharacters(batch, assets)
        batch.end()

        drawHud()
    }

    override fun dispose() {
        batch?.dispose()
        shape?.dispose()
        assets?.dispose()
        batch = null
        shape = null
        assets = null
    }

    // --- world / map ---

    private fun loadMap(map: WorldMapDefinition, spawnX: Int = map.spawnX, spawnY: Int = map.spawnY) {
        currentMap = map
        visual = TiledMapVisual.fromInternal(map.key, "${map.key}.json")
        val assets = assets ?: return
        npcs.clear()
        for (n in map.npcs) {
            npcs += NpcInstance(n.key, WorldCharacter(assets.npcAnimSet(n.key), n.x, n.y, Direction.DOWN))
        }
        val start = nudgeToWalkable(spawnX, spawnY)
        player = WorldCharacter(assets.player, start.first, start.second, Direction.DOWN)
        bridge.updatePlayerTile(start.first, start.second, map.key)
        lastEmittedNpcKey = null
        configureCamera(Gdx.graphics?.width ?: mapPixelW(), Gdx.graphics?.height ?: mapPixelH())
        hudCamera.setToOrtho(true, (Gdx.graphics?.width ?: 1).toFloat(), (Gdx.graphics?.height ?: 1).toFloat())
        hudCamera.update()
        input?.updateLayout()
        checkProximity(force = true)
    }

    private fun switchMap(targetKey: String) {
        val target = WorldMaps.byKey(targetKey)
        loadMap(target)
        bridge.emit(HubEvent.NpcOutOfRange)
        bridge.emit(HubEvent.ExitTouched(target.key))
        publishPlayerPosition()
    }

    private fun mapWidth(): Int = visual?.width ?: currentMap.width
    private fun mapHeight(): Int = visual?.height ?: currentMap.height
    private fun mapPixelW(): Int = mapWidth() * TiledMapVisual.TILE
    private fun mapPixelH(): Int = mapHeight() * TiledMapVisual.TILE

    private fun inBounds(x: Int, y: Int): Boolean = x in 0 until mapWidth() && y in 0 until mapHeight()

    private fun isSolid(x: Int, y: Int): Boolean {
        if (!inBounds(x, y)) return true
        return visual?.isSolid(x, y) ?: currentMap.buildings.any { it.contains(x, y) }
    }

    private fun nudgeToWalkable(x: Int, y: Int): Pair<Int, Int> {
        if (!isSolid(x, y)) return x to y
        for (r in 1..MAX_NUDGE_RADIUS) {
            val found = ringSearch(x, y, r)
            if (found != null) return found
        }
        return x to y
    }

    private fun ringSearch(cx: Int, cy: Int, r: Int): Pair<Int, Int>? {
        for (dy in -r..r) for (dx in -r..r) {
            if (!isSolid(cx + dx, cy + dy)) return (cx + dx) to (cy + dy)
        }
        return null
    }

    // --- input / movement ---

    private fun handleInput(delta: Float) {
        val player = player ?: return
        val input = input ?: return

        val reached = player.update(delta, STEP_DURATION)
        if (reached) onStepComplete()

        val gdxInput = Gdx.input
        val snapshot = InputController.KeyboardSnapshot(
            up = gdxInput?.isKeyPressed(Input.Keys.W) == true || gdxInput?.isKeyPressed(Input.Keys.UP) == true,
            down = gdxInput?.isKeyPressed(Input.Keys.S) == true || gdxInput?.isKeyPressed(Input.Keys.DOWN) == true,
            left = gdxInput?.isKeyPressed(Input.Keys.A) == true || gdxInput?.isKeyPressed(Input.Keys.LEFT) == true,
            right = gdxInput?.isKeyPressed(Input.Keys.D) == true || gdxInput?.isKeyPressed(Input.Keys.RIGHT) == true,
        )
        val (dx, dy) = input.resolveStep(snapshot)

        if (!player.moving) {
            if (dx != 0 || dy != 0) tryStartStep(dx, dy) else player.stop()
        }

        while (true) {
            val edge = input.pollEdge() ?: break
            if (edge == InputController.EdgeEvent.INTERACT) checkProximity(force = true)
        }
        if (bridge.consumeInteractRequest()) checkProximity(force = true)
    }

    private fun tryStartStep(dx: Int, dy: Int) {
        val player = player ?: return
        val dir = WorldCharacter.directionOf(dx, dy, player.facing)
        val nextX = player.tileX + dx
        val nextY = player.tileY + dy

        val exit = currentMap.exits.firstOrNull { it.area.contains(nextX, nextY) }
        if (exit != null) {
            switchMap(exit.targetMap)
            return
        }
        if (isSolid(nextX, nextY)) {
            player.face(dir)
            return
        }
        player.beginStep(nextX, nextY, dir)
        publishPlayerPosition()
        checkProximity(force = false)
    }

    private fun onStepComplete() {
        // 视觉抵达；逻辑事件已在 beginStep 上报，这里仅保留扩展点（脚步音效等）。
    }

    private fun publishPlayerPosition() {
        val player = player ?: return
        val dir = player.facing.toServerString()
        bridge.updatePlayerTile(player.tileX, player.tileY, currentMap.key)
        bridge.emit(HubEvent.PlayerMoved(currentMap.key, player.tileX, player.tileY, dir))
        onPlayerMoved?.invoke(player.tileX, player.tileY, dir)
    }

    private fun checkProximity(force: Boolean) {
        val player = player ?: return
        val nearby = npcs.firstOrNull { isAdjacent(it.character, player) }
        if (nearby == null) {
            if (lastEmittedNpcKey != null) {
                lastEmittedNpcKey = null
                bridge.emit(HubEvent.NpcOutOfRange)
            }
            return
        }
        faceToward(nearby.character, player)
        if (force || nearby.key != lastEmittedNpcKey) {
            lastEmittedNpcKey = nearby.key
            bridge.emit(HubEvent.NpcInRange(nearby.key, nearby.character.tileX, nearby.character.tileY))
        }
    }

    // --- camera ---

    private fun configureCamera(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val minScreen = min(w, h).toFloat()
        val unitsAcrossMin = TILES_ACROSS * TiledMapVisual.TILE
        val pxPerUnit = minScreen / unitsAcrossMin
        camera.viewportWidth = w / pxPerUnit
        camera.viewportHeight = h / pxPerUnit
        followCamera()
    }

    private fun followCamera() {
        val player = player ?: return
        val halfW = camera.viewportWidth / 2f
        val halfH = camera.viewportHeight / 2f
        val mapW = mapPixelW().toFloat()
        val mapH = mapPixelH().toFloat()
        camera.position.x = if (mapW <= camera.viewportWidth) mapW / 2f else player.feetX.coerceIn(halfW, mapW - halfW)
        camera.position.y = if (mapH <= camera.viewportHeight) mapH / 2f else player.feetY.coerceIn(halfH, mapH - halfH)
        camera.update()
    }

    // --- draw ---

    private fun drawWorld(batch: SpriteBatch, assets: GameAssets) {
        val visual = visual
        val halfW = camera.viewportWidth / 2f
        val halfH = camera.viewportHeight / 2f
        val tile = TiledMapVisual.TILE
        val minX = floor((camera.position.x - halfW) / tile).toInt() - 1
        val maxX = ceil((camera.position.x + halfW) / tile).toInt() + 1
        val minY = floor((camera.position.y - halfH) / tile).toInt() - 1
        val maxY = ceil((camera.position.y + halfH) / tile).toInt() + 1
        if (visual != null) {
            // 地面层不透明 → 关混合省 fill-rate；建筑/装饰/角色层需 alpha → 开混合。
            batch.disableBlending()
            visual.render(batch, assets, minX, minY, maxX, maxY, fromLayer = 0, toLayer = 1)
            batch.enableBlending()
            if (visual.layerCount > 1) {
                visual.render(batch, assets, minX, minY, maxX, maxY, fromLayer = 1, toLayer = visual.layerCount)
            }
        } else {
            drawProceduralFallback(batch, assets)
        }
    }

    private fun drawProceduralFallback(batch: SpriteBatch, assets: GameAssets) {
        val tile = TiledMapVisual.TILE.toFloat()
        val ground = currentMap.groundColor
        batch.setColor(ground.r, ground.g, ground.b, 1f)
        batch.draw(assets.white, 0f, 0f, mapPixelW().toFloat(), mapPixelH().toFloat())
        val wall = PixelTheme.buildingWall
        batch.setColor(wall.r, wall.g, wall.b, 1f)
        for (b in currentMap.buildings) {
            batch.draw(assets.white, b.x * tile, b.y * tile, b.w * tile, b.h * tile)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawCharacters(batch: SpriteBatch, assets: GameAssets) {
        // 视口剔除：屏外 NPC 不绘制（省 draw call + 纹理绑定）；玩家恒在视口内。
        visibleChars.clear()
        for (n in npcs) {
            if (isOnScreen(n.character)) visibleChars += n.character
        }
        player?.let { visibleChars += it }

        batch.setColor(1f, 1f, 1f, 1f)
        // 阴影同纹理 → 先一次性批量画完（1 次 flush），再画本体。
        for (c in visibleChars) c.drawShadow(batch, assets)
        // 南（feetY 小）后画 → 盖在北边角色之上，形成正确的前后遮挡。
        visibleChars.sortByDescending { it.feetY }
        for (c in visibleChars) c.drawBody(batch)
    }

    private fun isOnScreen(c: WorldCharacter): Boolean {
        val marginX = camera.viewportWidth / 2f + TiledMapVisual.TILE * 2
        val marginY = camera.viewportHeight / 2f + TiledMapVisual.TILE * 2
        return kotlin.math.abs(c.feetX - camera.position.x) <= marginX &&
            kotlin.math.abs(c.feetY - camera.position.y) <= marginY
    }

    private fun drawHud() {
        val shape = shape ?: return
        val input = input ?: return
        shape.projectionMatrix = hudCamera.combined
        Gdx.gl?.glEnable(GL20.GL_BLEND)
        shape.begin(ShapeRenderer.ShapeType.Filled)
        input.drawJoystick(shape)
        shape.end()
    }

    private data class NpcInstance(val key: String, val character: WorldCharacter)

    private companion object {
        const val STEP_DURATION = 0.16f
        const val MAX_DELTA = 0.05f
        const val TILES_ACROSS = 13f
        const val MAX_NUDGE_RADIUS = 6

        fun isAdjacent(a: WorldCharacter, b: WorldCharacter): Boolean =
            kotlin.math.abs(a.tileX - b.tileX) <= 1 && kotlin.math.abs(a.tileY - b.tileY) <= 1

        fun faceToward(npc: WorldCharacter, player: WorldCharacter) {
            val dx = player.tileX - npc.tileX
            val dy = player.tileY - npc.tileY
            if (dx == 0 && dy == 0) return
            npc.face(WorldCharacter.directionOf(dx, dy, npc.facing))
        }
    }
}
