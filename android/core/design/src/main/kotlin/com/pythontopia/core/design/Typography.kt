package com.pythontopia.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

object PytTypography {
    private val base = Typography()

    // 后续接入 Fusion Pixel 字体
    private fun TextStyle.withPixelFamily(): TextStyle = copy(fontFamily = FontFamily.SansSerif)

    val default: Typography = Typography(
        displayLarge = base.displayLarge.withPixelFamily(),
        displayMedium = base.displayMedium.withPixelFamily(),
        displaySmall = base.displaySmall.withPixelFamily(),
        headlineLarge = base.headlineLarge.withPixelFamily(),
        headlineMedium = base.headlineMedium.withPixelFamily(),
        headlineSmall = base.headlineSmall.withPixelFamily(),
        titleLarge = base.titleLarge.withPixelFamily(),
        titleMedium = base.titleMedium.withPixelFamily(),
        titleSmall = base.titleSmall.withPixelFamily(),
        bodyLarge = base.bodyLarge.withPixelFamily(),
        bodyMedium = base.bodyMedium.withPixelFamily(),
        bodySmall = base.bodySmall.withPixelFamily(),
        labelLarge = base.labelLarge.withPixelFamily(),
        labelMedium = base.labelMedium.withPixelFamily(),
        labelSmall = base.labelSmall.withPixelFamily(),
    )
}
