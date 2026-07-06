package com.pythontopia.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 设计令牌 · 字体：标题加字重、标签略增字距；正文保持默认字距以利 CJK 阅读。
 * [pixelFontFamily] 为点阵字体接入点（`res/font/` 内 OFL 字体），当前回退 [FontFamily.SansSerif]。
 */
object PytTypography {

    val pixelFontFamily: FontFamily = FontFamily.SansSerif

    /** 等宽字体族，供代码编辑器与 traceback 等场景统一取用。 */
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
