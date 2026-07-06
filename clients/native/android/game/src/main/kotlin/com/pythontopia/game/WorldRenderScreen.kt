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

/** LibGDX 主渲染屏：Kenney 瓦片 + LPC/ULPC 精灵，镜头跟随玩家；对外事件契约与 [HubScreen] 相同（[HubGameBridge]）。 */
@Suppress("TooManyFunctions") // 地图/输入/镜头/绘制拆分为私有函数。
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

    // 天气粒子：以视口比例 (0..1) 存储，规避镜头移动/尺寸变化导致的越界。
    private val weatherRng = java.util.Random()
    private var weatherX: FloatArray? = null
    private var weatherY: FloatArray? = null

    override fun show() {
        batch = SpriteBatch()
        shape = ShapeRenderer()
        assets = GameAssets()
        input = InputController(
            viewportWidthProvider = { Gdx.graphics?.width ?: 0 },
            viewportHeightProvider = { Gdx.graphics?.height ?: 0 },
        ).also { Gdx.input?.inputProcessor = it }

        // 始终从 town_hub 进入；存档 mapKey 非 hub 时回退默认出生点。
        val seed = bridge.latestPlayerTile()
        val onHub = seed.mapKey == WorldMaps.townHub.key
        loadMap(
            WorldMaps.townHub,
            spawnX = if (onHub) seed.x else WorldMaps.townHub.spawnX,
            spawnY = if (onHub) seed.y else WorldMaps.townHub.spawnY,
        )
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
        drawEnvironmentTint(batch, assets)
        drawWeather(batch, assets, safeDelta)
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
        consumeTeleport()
    }

    /** 小地图点选瞬移：落到最近可走格，立即更新镜头与 NPC 邻近态。 */
    private fun consumeTeleport() {
        val req = bridge.consumeTeleportRequest() ?: return
        val player = player ?: return
        if (player.moving) return
        val dest = nudgeToWalkable(req.x, req.y)
        if (isSolid(dest.first, dest.second)) return
        player.snapTo(dest.first, dest.second)
        followCamera()
        publishPlayerPosition()
        checkProximity(force = true)
    }

    private fun tryStartStep(dx: Int, dy: Int) {
        val player = player ?: return
        val dir = WorldCharacter.directionOf(dx, dy, player.facing)
        val nextX = player.tileX + dx
        val nextY = player.tileY + dy

        // 仅允许切回 town_hub；外围图出口忽略（防御性约束，防止数据回退后误切图）。
        val exit = currentMap.exits.firstOrNull { it.area.contains(nextX, nextY) }
        if (exit != null && exit.targetMap == DEFAULT_WORLD_MAP_KEY) {
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
        // 逻辑事件已在 beginStep 上报；此处可挂脚步音效等扩展。
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
            // 地面层关混合省 fill-rate；上层需 alpha 再开混合。
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

    /** 日夜光照：按 timeOfDay 在世界上叠一层视口大小的半透明色块（NOON/未知无叠加）。 */
    private fun drawEnvironmentTint(batch: SpriteBatch, assets: GameAssets) {
        val tint = envTint(bridge.environment().timeOfDay) ?: return
        val halfW = camera.viewportWidth / 2f
        val halfH = camera.viewportHeight / 2f
        batch.setColor(tint[0], tint[1], tint[2], tint[3])
        batch.draw(
            assets.white,
            camera.position.x - halfW,
            camera.position.y - halfH,
            camera.viewportWidth,
            camera.viewportHeight,
        )
        batch.setColor(1f, 1f, 1f, 1f)
    }

    /** 天气粒子：雨（细长蓝线，快落）/ 雪（白点，慢落）；晴/阴无粒子。 */
    private fun drawWeather(batch: SpriteBatch, assets: GameAssets, delta: Float) {
        val w = bridge.environment().weather?.uppercase()
        val isRain = w == "RAIN" || w == "RAINY" || w == "STORM"
        val isSnow = w == "SNOW" || w == "SNOWY"
        if (!isRain && !isSnow) return
        val vw = camera.viewportWidth
        val vh = camera.viewportHeight
        val left = camera.position.x - vw / 2f
        val bottom = camera.position.y - vh / 2f
        val n = WEATHER_PARTICLES
        var ax = weatherX
        var ay = weatherY
        if (ax == null || ay == null || ax.size != n) {
            ax = FloatArray(n) { weatherRng.nextFloat() }
            ay = FloatArray(n) { weatherRng.nextFloat() }
            weatherX = ax
            weatherY = ay
        }
        val speedFrac = (if (isRain) 1.4f else 0.35f) * delta
        if (isRain) batch.setColor(0.6f, 0.72f, 1f, 0.5f) else batch.setColor(1f, 1f, 1f, 0.85f)
        for (i in 0 until n) {
            ay[i] -= speedFrac
            if (ay[i] < 0f) {
                ay[i] += 1f
                ax[i] = weatherRng.nextFloat()
            }
            val px = left + ax[i] * vw
            val py = bottom + ay[i] * vh
            if (isRain) batch.draw(assets.white, px, py, 1.5f, 10f) else batch.draw(assets.white, px, py, 3.5f, 3.5f)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun envTint(timeOfDay: String?): FloatArray? = when (timeOfDay?.uppercase()) {
        "MORNING" -> floatArrayOf(1f, 0.85f, 0.6f, 0.10f)
        "AFTERNOON" -> floatArrayOf(1f, 0.9f, 0.7f, 0.08f)
        "EVENING" -> floatArrayOf(1f, 0.55f, 0.3f, 0.28f)
        "NIGHT" -> floatArrayOf(0.15f, 0.2f, 0.45f, 0.5f)
        else -> null
    }

    private fun drawCharacters(batch: SpriteBatch, assets: GameAssets) {
        // 屏外 NPC 剔除；玩家恒绘制。
        visibleChars.clear()
        for (n in npcs) {
            if (isOnScreen(n.character)) visibleChars += n.character
        }
        player?.let { visibleChars += it }

        batch.setColor(1f, 1f, 1f, 1f)
        // 阴影同纹理，先批量画阴影再画本体（减少 flush）。
        for (c in visibleChars) c.drawShadow(batch, assets)
        // feetY 小者后画，保证 y-up 前后遮挡正确。
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
        const val WEATHER_PARTICLES = 70

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
