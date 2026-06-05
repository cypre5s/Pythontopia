package com.pythontopia.core.design

import androidx.compose.ui.graphics.Color

/**
 * Pythontopia 设计令牌 · 配色。
 *
 * 主题基调：星露谷式「黄昏木屋」暖色像素小镇——深胡桃木背景 + 黄铜描边 + 羊皮纸暖白文字 + 故事书系强调色。
 * 全局沿用**深色方案**（深木底 + 暖白字），以保证所有 feature 既有的「浅字深底」对比度不被破坏。
 *
 * 约定：
 *  - 既有令牌名一律保留（被各 feature 直接引用），仅微调取值以增强协调感，绝不改名。
 *  - 「羊皮纸 / 木质框架 / 次要强调」为新增追加令牌，供浅色菜单、木框面板与 Material 次要角色按需取用。
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

    // —— 新增 · 羊皮纸表面族（浅色菜单 / 提示气泡 / 浅色主题 / Material inverseSurface）——
    val parchment = Color(0xFFF3E2BD)
    val parchmentSunken = Color(0xFFE6D2A6)
    val onParchment = Color(0xFF3A2A16)
    val onParchmentMuted = Color(0xFF6E5630)

    // —— 新增 · 木质框架族（深浅木纹，供面板 / 边框分层）——
    val woodLight = Color(0xFF8C6336)
    val wood = Color(0xFF6B4A28)
    val woodDark = Color(0xFF38240F)

    // —— 新增 · 次要 / 第三强调（供 Material secondary / tertiary 角色，避免到处复用 primary 金）——
    val secondary = Color(0xFF8FB36B)
    val onSecondary = Color(0xFF1C140B)
    val tertiary = Color(0xFFCBA0B6)
    val onTertiary = Color(0xFF231019)
}
