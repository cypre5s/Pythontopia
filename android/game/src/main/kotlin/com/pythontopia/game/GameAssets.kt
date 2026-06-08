package com.pythontopia.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import com.pythontopia.game.PlayerActor.Direction
import kotlin.math.sqrt
import com.badlogic.gdx.utils.Array as GdxArray

/** GL 线程创建的 GPU 纹理与动画切片；须在 [WorldRenderScreen.show] 创建、[dispose] 释放。NPC key 映射对齐 web BootScene npc-composites。 */
class GameAssets : Disposable {

    private val disposables = mutableListOf<Disposable>()

    // 真素材优先；缺失则程序化生成（CC0，无需二进制资产），保证 5 张地图永不因缺图崩溃。
    val townTiles: Texture =
        loadTextureOrNull("tilesets/town.png") ?: buildTilesetTexture { ProceduralArt.townTileColor(it) }
    val dungeonTiles: Texture =
        loadTextureOrNull("tilesets/dungeon.png") ?: buildTilesetTexture { ProceduralArt.dungeonTileColor(it) }

    private val townRegions: Array<TextureRegion> = sliceTileset(townTiles)
    private val dungeonRegions: Array<TextureRegion> = sliceTileset(dungeonTiles)

    // 玩家：优先真 LPC walk9 精灵（sprites/player.png）；退回旧 ULPC 整表；再退程序化。
    private val playerTexture: Texture? = loadTextureOrNull("sprites/player.png")
    val player: CharacterAnimSet =
        playerTexture?.let { buildWalk9AnimSet(it) }
            ?: loadTextureOrNull("sprites/player_sara2.png")?.let { buildUlpcAnimSet(it) }
            ?: buildProceduralAnimSet(ProceduralArt.PLAYER_PALETTE)

    /** 程序化生成的软椭圆阴影（径向 alpha 衰减），让角色「踩」在地上。 */
    val shadow: TextureRegion = buildShadowRegion()

    /** 1×1 纯白像素，用于 SpriteBatch 内画纯色块（HUD 面板/进度条等）。 */
    val white: TextureRegion = buildWhiteRegion()

    private val npcTextures = mutableMapOf<String, Texture>()
    private val npcAnimSets = mutableMapOf<String, CharacterAnimSet>()

    /** 取某图集第 [index] 块瓦片（index = gid - firstgid，0 起）。越界返回 null。 */
    fun tileRegion(usesDungeon: Boolean, index: Int): TextureRegion? {
        if (index < 0) return null
        val regions = if (usesDungeon) dungeonRegions else townRegions
        return regions.getOrNull(index)
    }

    /** 取（或惰性加载）某 NPC key 的动画集；缺精灵 PNG 时退回程序化角色（按文件名派生配色）。 */
    fun npcAnimSet(npcKey: String): CharacterAnimSet {
        val file = NPC_SPRITE_FILE[npcKey] ?: DEFAULT_NPC_SPRITE
        return npcAnimSets.getOrPut(file) {
            val texture = loadTextureOrNull("sprites/npc/$file.png")
            if (texture != null) {
                npcTextures[file] = texture
                buildWalk9AnimSet(texture)
            } else {
                buildProceduralAnimSet(ProceduralArt.characterPalette(file))
            }
        }
    }

    /** 真素材存在则加载；缺失/损坏返回 null（调用方回退程序化生成），永不崩。 */
    @Suppress("TooGenericExceptionCaught")
    private fun loadTextureOrNull(path: String): Texture? = try {
        val file = Gdx.files?.internal(path)
        if (file == null || !file.exists()) {
            null
        } else {
            val texture = Texture(file)
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            disposables += texture
            texture
        }
    } catch (t: RuntimeException) {
        Gdx.app?.error("GameAssets", "texture load failed for $path: ${t.message}")
        null
    }

    /** 程序化 132 帧瓦片集（12×11，每帧 16px 语义配色 + 1px 暗边），缺真素材时渲染地图。 */
    private fun buildTilesetTexture(colorOf: (Int) -> Color): Texture {
        val cols = 12
        val rows = 11
        val pm = Pixmap(cols * TILE_PX, rows * TILE_PX, Pixmap.Format.RGBA8888)
        pm.setColor(0f, 0f, 0f, 0f)
        pm.fill()
        for (frame in 0 until cols * rows) {
            val ox = (frame % cols) * TILE_PX
            val oy = (frame / cols) * TILE_PX
            val base = colorOf(frame)
            pm.setColor(base)
            pm.fillRectangle(ox, oy, TILE_PX, TILE_PX)
            pm.setColor(base.r * 0.82f, base.g * 0.82f, base.b * 0.82f, 1f)
            pm.fillRectangle(ox, oy + TILE_PX - 1, TILE_PX, 1)
            pm.fillRectangle(ox + TILE_PX - 1, oy, 1, TILE_PX)
        }
        val texture = Texture(pm)
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        pm.dispose()
        disposables += texture
        return texture
    }

    /** 程序化角色精灵表：3 帧（站 / 走A / 走B）× 4 向打包成 1 张纹理后切片。 */
    private fun buildProceduralAnimSet(palette: CharPalette): CharacterAnimSet {
        val cols = 3
        val rows = Direction.entries.size
        val pm = Pixmap(PROC_FRAME * cols, PROC_FRAME * rows, Pixmap.Format.RGBA8888)
        pm.setColor(0f, 0f, 0f, 0f)
        pm.fill()
        for (dir in Direction.entries) {
            val row = procRow(dir)
            for (step in 0 until cols) drawCharacter(pm, step * PROC_FRAME, row * PROC_FRAME, dir, step, palette)
        }
        val texture = Texture(pm)
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        pm.dispose()
        disposables += texture

        val walk = HashMap<Direction, Animation<TextureRegion>>()
        val idle = HashMap<Direction, Animation<TextureRegion>>()
        for (dir in Direction.entries) {
            val row = procRow(dir)
            val walkFrames = GdxArray<TextureRegion>(2)
            walkFrames.add(TextureRegion(texture, PROC_FRAME, row * PROC_FRAME, PROC_FRAME, PROC_FRAME))
            walkFrames.add(TextureRegion(texture, 2 * PROC_FRAME, row * PROC_FRAME, PROC_FRAME, PROC_FRAME))
            walk[dir] = Animation(WALK_FRAME_DURATION, walkFrames, Animation.PlayMode.LOOP)
            val idleFrames = GdxArray<TextureRegion>(1)
            idleFrames.add(TextureRegion(texture, 0, row * PROC_FRAME, PROC_FRAME, PROC_FRAME))
            idle[dir] = Animation(IDLE_FRAME_DURATION, idleFrames, Animation.PlayMode.LOOP)
        }
        return CharacterAnimSet(walk, idle)
    }

    /** 在精灵表的某个 [PROC_FRAME] 单元里画一个简易像素小人（脚朝下、头朝上）。 */
    private fun drawCharacter(pm: Pixmap, ox: Int, oy: Int, dir: Direction, step: Int, p: CharPalette) {
        pm.setColor(p.pants)
        val leftLong = step != 2
        val rightLong = step != 1
        pm.fillRectangle(ox + 12, oy + 25, 4, if (leftLong) 6 else 4)
        pm.fillRectangle(ox + 17, oy + 25, 4, if (rightLong) 6 else 4)
        pm.setColor(p.shirt)
        pm.fillRectangle(ox + 9, oy + 15, 14, 11)
        pm.fillRectangle(ox + 6, oy + 16, 3, 8)
        pm.fillRectangle(ox + 23, oy + 16, 3, 8)
        pm.setColor(p.hair)
        pm.fillCircle(ox + 16, oy + 10, 6)
        pm.setColor(p.skin)
        val faceCenterY = if (dir == Direction.UP) oy + 12 else oy + 11
        pm.fillCircle(ox + 16, faceCenterY, if (dir == Direction.UP) 4 else 5)
        pm.setColor(EYE_COLOR)
        when (dir) {
            Direction.DOWN -> {
                pm.fillRectangle(ox + 13, oy + 11, 2, 2)
                pm.fillRectangle(ox + 18, oy + 11, 2, 2)
            }
            Direction.LEFT -> pm.fillRectangle(ox + 12, oy + 11, 2, 2)
            Direction.RIGHT -> pm.fillRectangle(ox + 19, oy + 11, 2, 2)
            Direction.UP -> Unit
        }
    }

    private fun sliceTileset(texture: Texture): Array<TextureRegion> {
        val cols = texture.width / TILE_PX
        val rows = texture.height / TILE_PX
        return Array(cols * rows) { i ->
            val col = i % cols
            val row = i / cols
            TextureRegion(texture, col * TILE_PX, row * TILE_PX, TILE_PX, TILE_PX)
        }
    }

    /** ULPC「Expanded」13×42 表：walk bank 第 8 行起 9 帧、idle bank 第 22 行起 2 帧。 */
    private fun buildUlpcAnimSet(texture: Texture): CharacterAnimSet {
        val walk = HashMap<Direction, Animation<TextureRegion>>()
        val idle = HashMap<Direction, Animation<TextureRegion>>()
        for (dir in Direction.entries) {
            val rowOffset = dirRowOffset(dir)
            val walkFrames = sliceRow(texture, row = WALK_FIRST_ROW + rowOffset, startCol = 1, count = 8)
            walk[dir] = Animation(WALK_FRAME_DURATION, walkFrames, Animation.PlayMode.LOOP)
            val idleFrames = sliceRow(texture, row = IDLE_FIRST_ROW + rowOffset, startCol = 0, count = 2)
            idle[dir] = Animation(IDLE_FRAME_DURATION, idleFrames, Animation.PlayMode.LOOP)
        }
        return CharacterAnimSet(walk, idle)
    }

    /** walk9 NPC 表：每方向 1 行（UP/LEFT/DOWN/RIGHT=0/1/2/3），col0 站立、col1..8 行走。 */
    private fun buildWalk9AnimSet(texture: Texture): CharacterAnimSet {
        val walk = HashMap<Direction, Animation<TextureRegion>>()
        val idle = HashMap<Direction, Animation<TextureRegion>>()
        for (dir in Direction.entries) {
            val row = walk9Row(dir)
            val walkFrames = sliceRow(texture, row = row, startCol = 1, count = 8)
            walk[dir] = Animation(WALK_FRAME_DURATION, walkFrames, Animation.PlayMode.LOOP)
            val standing = sliceRow(texture, row = row, startCol = 0, count = 1)
            idle[dir] = Animation(IDLE_FRAME_DURATION, standing, Animation.PlayMode.LOOP)
        }
        return CharacterAnimSet(walk, idle)
    }

    private fun sliceRow(texture: Texture, row: Int, startCol: Int, count: Int): GdxArray<TextureRegion> {
        val arr = GdxArray<TextureRegion>(count)
        for (i in 0 until count) {
            arr.add(TextureRegion(texture, (startCol + i) * FRAME_PX, row * FRAME_PX, FRAME_PX, FRAME_PX))
        }
        return arr
    }

    private fun buildShadowRegion(): TextureRegion {
        val size = 32
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val c = (size - 1) / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - c) / c
                val dy = (y - c) / c
                val d = sqrt(dx * dx + dy * dy)
                val a = (1f - d).coerceIn(0f, 1f)
                pm.setColor(0f, 0f, 0f, a * a * 0.45f)
                pm.drawPixel(x, y)
            }
        }
        val texture = Texture(pm)
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pm.dispose()
        disposables += texture
        return TextureRegion(texture)
    }

    private fun buildWhiteRegion(): TextureRegion {
        val pm = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pm.setColor(1f, 1f, 1f, 1f)
        pm.drawPixel(0, 0)
        val texture = Texture(pm)
        pm.dispose()
        disposables += texture
        return TextureRegion(texture)
    }

    override fun dispose() {
        disposables.forEach { it.dispose() }
        disposables.clear()
        npcTextures.clear()
        npcAnimSets.clear()
    }

    companion object {
        const val TILE_PX = 16
        const val FRAME_PX = 64

        /** 程序化角色单帧边长（像素）；绘制坐标均基于该尺寸。 */
        private const val PROC_FRAME = 32
        private val EYE_COLOR = ProceduralArt.rgb(0x20181A)

        /** 4 向在程序化精灵表中的行序（与 walk9 行序一致：UP/LEFT/DOWN/RIGHT）。 */
        private fun procRow(dir: Direction): Int = when (dir) {
            Direction.UP -> 0
            Direction.LEFT -> 1
            Direction.DOWN -> 2
            Direction.RIGHT -> 3
        }

        // ULPC bank 行偏移（DIR_ROW_ORDER = UP, LEFT, DOWN, RIGHT），对齐 lpcAnimations.ts。
        private const val WALK_FIRST_ROW = 8
        private const val IDLE_FIRST_ROW = 22
        private const val WALK_FRAME_DURATION = 0.10f
        private const val IDLE_FRAME_DURATION = 0.25f

        private const val DEFAULT_NPC_SPRITE = "npc_student"

        private fun dirRowOffset(dir: Direction): Int = when (dir) {
            Direction.UP -> 0
            Direction.LEFT -> 1
            Direction.DOWN -> 2
            Direction.RIGHT -> 3
        }

        private fun walk9Row(dir: Direction): Int = when (dir) {
            Direction.UP -> 0
            Direction.LEFT -> 1
            Direction.DOWN -> 2
            Direction.RIGHT -> 3
        }

        /** 对话 NPC key → npc-composites 精灵文件名（无扩展名）。对齐 web 端贴图清单。 */
        /** R-14：12 主线 NPC 精灵映射（与 Web town_hub 对齐）。 */
        private val NPC_SPRITE_FILE: Map<String, String> = mapOf(
            "mayor_py" to "npc_mayor",
            "librarian_doc" to "npc_librarian",
            "coach_q" to "npc_coach",
            "sage_review" to "npc_sage",
            "professor" to "npc_professor",
            "student_a" to "npc_student",
            "class_president" to "npc_student",
            "captain_def" to "npc_sailor",
            "mine_keeper" to "npc_miner",
            "foreman_bug" to "npc_guard",
            "workshop_master" to "npc_workshop",
            "arena_guard" to "npc_guard",
        )
    }
}

/** 一个角色的 4 向行走 + 待机动画集合。 */
class CharacterAnimSet(
    private val walk: Map<Direction, Animation<TextureRegion>>,
    private val idle: Map<Direction, Animation<TextureRegion>>,
) {
    fun frame(dir: Direction, moving: Boolean, stateTime: Float): TextureRegion {
        val bank = if (moving) walk else idle
        val anim = bank[dir] ?: bank[Direction.DOWN] ?: error("CharacterAnimSet missing DOWN bank")
        return anim.getKeyFrame(stateTime)
    }
}
