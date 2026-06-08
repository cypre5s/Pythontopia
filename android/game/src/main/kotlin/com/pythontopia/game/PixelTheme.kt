package com.pythontopia.game

import com.badlogic.gdx.graphics.Color

/** LibGDX 渲染色系，与前端 tokens.css 及 core:design.PytColors 色值对齐；对象为单例，禁止 mutate。 */
object PixelTheme {

    // 主色 4 档（金棕系）
    val primary50: Color = rgb(0xFE, 0xF7, 0xE3)
    val primary100: Color = rgb(0xFD, 0xF3, 0xD0)
    val primary500: Color = rgb(0xC9, 0xA8, 0x6B)
    val primary900: Color = rgb(0x4D, 0x36, 0x13)

    // 辅色
    val accentGold: Color = rgb(0xD4, 0xAF, 0x37)
    val accentPurple: Color = rgb(0x9B, 0x59, 0xB6)
    val accentBlue: Color = rgb(0x5B, 0xC0, 0xDE)
    val accentPink: Color = rgb(0xFF, 0xB7, 0xC5)

    // 语义色
    val success: Color = rgb(0x6B, 0xBF, 0x59)
    val warning: Color = rgb(0xC2, 0x87, 0x1F)
    val danger: Color = rgb(0xD9, 0x53, 0x4F)
    val info: Color = rgb(0x2A, 0x6F, 0x97)
    val neutral: Color = rgb(0x8B, 0x6B, 0x47)

    // 灰阶 6 档（暖灰）
    val gray50: Color = rgb(0xFF, 0xF7, 0xE6)
    val gray100: Color = rgb(0xF3, 0xE4, 0xBE)
    val gray300: Color = rgb(0xD8, 0xC2, 0x93)
    val gray500: Color = rgb(0xA0, 0x86, 0x53)
    val gray700: Color = rgb(0x6E, 0x4F, 0x1D)
    val gray900: Color = rgb(0x2B, 0x1D, 0x0A)

    /** 玩家像素体（蓝衣） */
    val playerBody: Color = rgb(0x4F, 0x9F, 0xD6)

    /** 玩家朝向指示器（高对比金，避免与 NPC 红衣冲突） */
    val playerFacing: Color = accentGold

    /** NPC 默认体色（红衣） */
    val npcBody: Color = rgb(0xE8, 0x4D, 0x4D)

    /** 草地（HUB 主背景，比 primary50 略偏绿） */
    val ground: Color = rgb(0x66, 0x95, 0x4C)

    /** 路径（沙土黄，对应建筑入口前的踏板） */
    val path: Color = rgb(0xD1, 0xBC, 0x73)

    /** 建筑屋身（暖棕，与 primary900 同色相但更浅，提供 4px 描边对比） */
    val buildingWall: Color = rgb(0x8B, 0x57, 0x2E)

    /** 建筑屋顶（更深棕，模拟北光阴影） */
    val buildingRoof: Color = rgb(0x6B, 0x3F, 0x1E)

    /** 边缘门洞（高亮金，提示玩家可以走出地图） */
    val mapExit: Color = accentGold

    /** 渲染清屏背景色（夜深沉，让 letterbox 区域与游戏区分） */
    val clearBackground: Color = rgb(0x14, 0x0D, 0x08)

    private fun rgb(r: Int, g: Int, b: Int): Color =
        Color(r / 255f, g / 255f, b / 255f, 1f)
}
