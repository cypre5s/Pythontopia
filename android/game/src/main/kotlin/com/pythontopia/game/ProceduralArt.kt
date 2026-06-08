package com.pythontopia.game

import com.badlogic.gdx.graphics.Color

/** 一个角色的程序化配色（皮肤 / 上衣 / 头发 / 裤子）。 */
data class CharPalette(
    val skin: Color,
    val shirt: Color,
    val hair: Color,
    val pants: Color,
)

/**
 * 缺 Kenney/LPC 素材时的程序化配色（CC0）；帧号约定 `frame = gid - 1`（firstgid=1）。
 * 纯函数供 JVM 单测；Pixmap→Texture 绘制在 [GameAssets] 运行时完成。
 */
internal object ProceduralArt {

    val PLAYER_PALETTE = CharPalette(
        skin = rgb(0xF2C28C),
        shirt = rgb(0x3F77B5),
        hair = rgb(0x3A2A1A),
        pants = rgb(0x2E2A33),
    )

    fun rgb(hex: Int): Color = Color((hex shl 8) or 0xFF)

    /** Kenney tiny-town 帧 → 语义配色。未列出的帧按草地处理（地图未用到）。 */
    fun townTileColor(frame: Int): Color = when (frame) {
        1 -> rgb(0x6CB45A) // grassAlt
        2 -> rgb(0x7BBE57) // grassFlower
        3 -> rgb(0x4E9942) // grassTall
        4 -> rgb(0x3E8B3A) // bushSmall
        5 -> rgb(0x9A9488) // rockSmall
        6 -> rgb(0x837D72) // rockMedium
        7 -> rgb(0x9B6B3A) // logPile
        8 -> rgb(0xC0552F) // mushroom
        12 -> rgb(0xD9C18B) // sandPath
        13, 14, 15 -> rgb(0xC0533B) // 屋顶
        16, 17, 18 -> rgb(0xD8B98C) // 上层墙
        19, 20, 21 -> rgb(0xCBA877) // 中层墙
        22, 24 -> rgb(0xCBA877) // 底层墙
        23 -> rgb(0x6B4A2B) // 门
        25 -> rgb(0xD86F9C) // flowerPink
        26 -> rgb(0x5A78C0) // flowerBlue
        27 -> rgb(0x8A5A2E) // barrel
        28 -> rgb(0xA9763C) // crate
        29 -> rgb(0x7A7A86) // well
        48, 50 -> rgb(0x4C86C0) // waterEdge
        49 -> rgb(0x3F77B5) // water
        61 -> rgb(0x2E5C97) // waterDeep
        68, 80 -> rgb(0x8A5A2E) // 木栅栏
        69 -> rgb(0x7A4F28) // 栅栏柱
        84 -> rgb(0x6B4A2B) // treeStump
        85 -> rgb(0x2F7A3E) // treePine
        86 -> rgb(0x8A5A2E) // signPost
        87 -> rgb(0xE0C341) // lamp
        88 -> rgb(0x9B6B3A) // benchH
        else -> rgb(0x5BA64B) // grass（含 frame 0）
    }

    /** Kenney tiny-dungeon 帧 → 语义配色。未列出按石地板处理。 */
    fun dungeonTileColor(frame: Int): Color = when (frame) {
        9, 10, 11 -> rgb(0x141014) // 黑色虚空（矿洞入口）
        12 -> rgb(0x787470) // floorAlt
        21, 22, 23 -> rgb(0x4A3A2C) // 棕黑墙
        24 -> rgb(0x66625E) // floorAlt2
        33, 34 -> rgb(0x5A5A5E) // 灰墙
        45 -> rgb(0x8A8270) // 立柱
        95 -> rgb(0x3E78C0) // 蓝色矿石
        109 -> rgb(0xC79A3C) // 宝箱
        else -> rgb(0x6E6A66) // 石地板（含 frame 0）
    }

    /** 由 NPC 精灵文件名稳定派生上衣色（同名同色，异名多半异色）。 */
    fun characterPalette(seed: String): CharPalette {
        val hue = ((seed.hashCode() and 0x7FFFFFFF) % 360).toFloat()
        val shirt = Color().fromHsv(hue, 0.55f, 0.78f)
        shirt.a = 1f
        return CharPalette(
            skin = rgb(0xF2C28C),
            shirt = shirt,
            hair = rgb(0x3A2A1A),
            pants = rgb(0x2E2A33),
        )
    }
}
