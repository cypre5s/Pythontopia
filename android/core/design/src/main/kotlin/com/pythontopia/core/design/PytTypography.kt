package com.pythontopia.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Pythontopia 设计令牌 · 字体。
 *
 * 像素小镇的「故事书 UI」观感主要靠：标题更扎实的字重 + 标签略宽的字距。正文（含大量中文）保持默认字距，
 * 避免 CJK 逐字拉开影响可读性。
 *
 * [pixelFontFamily] 是接入真实点阵字体的**唯一切换点**：把审计通过的像素字体（如 Fusion Pixel / 缝合像素，
 * OFL-1.1）放入 `core/design` 的 `res/font/` 后，将此处替换为 `FontFamily(Font(R.font.xxx))` 即可全局生效；
 * 当前为零资产可编译而回退到 [FontFamily.SansSerif]。
 */
object PytTypography {

    /** 像素字体族——全局唯一切换点（详见类注释）。 */
    val pixelFontFamily: FontFamily = FontFamily.SansSerif

    /** 等宽字体族——代码编辑器 / traceback 等场景统一取用，避免各 feature 直接散用 [FontFamily.Monospace]。 */
    val monoFontFamily: FontFamily = FontFamily.Monospace

    private val base = Typography()

    private fun TextStyle.pixel(
        weight: FontWeight? = null,
        tracking: TextUnit = TextUnit.Unspecified,
    ): TextStyle = copy(
        fontFamily = pixelFontFamily,
        fontWeight = weight ?: fontWeight,
        letterSpacing = if (tracking != TextUnit.Unspecified) tracking else letterSpacing,
    )

    val default: Typography = Typography(
        displayLarge = base.displayLarge.pixel(FontWeight.Bold),
        displayMedium = base.displayMedium.pixel(FontWeight.Bold),
        displaySmall = base.displaySmall.pixel(FontWeight.Bold),
        headlineLarge = base.headlineLarge.pixel(FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.pixel(FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.pixel(FontWeight.SemiBold),
        titleLarge = base.titleLarge.pixel(FontWeight.SemiBold),
        titleMedium = base.titleMedium.pixel(FontWeight.SemiBold),
        titleSmall = base.titleSmall.pixel(FontWeight.Medium),
        bodyLarge = base.bodyLarge.pixel(),
        bodyMedium = base.bodyMedium.pixel(),
        bodySmall = base.bodySmall.pixel(),
        labelLarge = base.labelLarge.pixel(FontWeight.SemiBold, 0.5.sp),
        labelMedium = base.labelMedium.pixel(FontWeight.Medium, 0.5.sp),
        labelSmall = base.labelSmall.pixel(FontWeight.Medium, 0.5.sp),
    )
}
