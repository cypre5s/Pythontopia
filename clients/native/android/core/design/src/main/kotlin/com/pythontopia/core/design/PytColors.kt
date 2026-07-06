package com.pythontopia.core.design

import androidx.compose.ui.graphics.Color

/**
 * 设计令牌 · 配色：「黄昏木屋」暖色像素小镇——深木底、黄铜描边、羊皮纸暖白字。
 * 默认深色方案，保证浅字深底对比度。
 */
object PytColors {

    // —— 背景：由深到浅的一条暖木色阶 ——
    val bgSunken = Color(0xFF140D06)
    val bgPrimary = Color(0xFF1C140B)
    val bgSoft = Color(0xFF241A0F)
    val bgElevated = Color(0xFF2E2213)

    // —— 描边：黄铜 / 金色阶 ——
    val borderStrong = Color(0xFFCBAA6E)
    val border = Color(0xFF8C6E33)
    val borderSoft = Color(0xFF5C4626)

    // —— 文字：羊皮纸暖白 → 金 → 暗金 ——
    val textPrimary = Color(0xFFF6ECD2)
    val textMuted = Color(0xFFCBAA6E)
    val textHint = Color(0xFFA98C55)

    // —— 故事书系强调色（与暖木底协调，保持像素小镇观感）——
    val success = Color(0xFF74B34E)
    val warning = Color(0xFFE6A93C)
    val danger = Color(0xFFD45F46)
    val info = Color(0xFF5FA8C4)

    // —— 主色（黄铜金）——
    val primary = Color(0xFFCBAA6E)
    val onPrimary = Color(0xFF1C140B)

    val surface = bgElevated
    val onSurface = textPrimary

    // —— 羊皮纸表面族（浅色菜单 / 提示气泡 / Material inverseSurface）——
    val parchment = Color(0xFFF3E2BD)
    val parchmentSunken = Color(0xFFE6D2A6)
    val onParchment = Color(0xFF3A2A16)
    val onParchmentMuted = Color(0xFF6E5630)

    // —— 木质框架族（面板 / 边框分层）——
    val woodLight = Color(0xFF8C6336)
    val wood = Color(0xFF6B4A28)
    val woodDark = Color(0xFF38240F)

    // —— 次要 / 第三强调（Material secondary / tertiary）——
    val secondary = Color(0xFF8FB36B)
    val onSecondary = Color(0xFF1C140B)
    val tertiary = Color(0xFFCBA0B6)
    val onTertiary = Color(0xFF231019)
}
