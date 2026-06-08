package com.pythontopia.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** Pythontopia Material 3 主题入口；[darkTheme] 默认 true（深色「黄昏木屋」）。 */
@Composable
fun PythontopiaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkPythontopiaColorScheme() else lightPythontopiaColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PytTypography.default,
        content = content,
    )
}

/** 深色「黄昏木屋」方案（默认）：深木底 + 暖白字，把更多 Material 角色映射到设计令牌以增强协调感。 */
private fun darkPythontopiaColorScheme() = darkColorScheme(
    primary = PytColors.primary,
    onPrimary = PytColors.onPrimary,
    primaryContainer = PytColors.wood,
    onPrimaryContainer = PytColors.textPrimary,
    secondary = PytColors.secondary,
    onSecondary = PytColors.onSecondary,
    secondaryContainer = PytColors.woodDark,
    onSecondaryContainer = PytColors.textPrimary,
    tertiary = PytColors.tertiary,
    onTertiary = PytColors.onTertiary,
    background = PytColors.bgPrimary,
    onBackground = PytColors.textPrimary,
    surface = PytColors.surface,
    onSurface = PytColors.onSurface,
    surfaceVariant = PytColors.bgSoft,
    onSurfaceVariant = PytColors.textMuted,
    outline = PytColors.border,
    outlineVariant = PytColors.borderSoft,
    error = PytColors.danger,
    onError = PytColors.onPrimary,
    inverseSurface = PytColors.parchment,
    inverseOnSurface = PytColors.onParchment,
)

/** 浅色「羊皮纸」方案：暖米纸底 + 深木字。 */
private fun lightPythontopiaColorScheme() = lightColorScheme(
    primary = PytColors.wood,
    onPrimary = PytColors.parchment,
    primaryContainer = PytColors.parchmentSunken,
    onPrimaryContainer = PytColors.woodDark,
    secondary = PytColors.secondary,
    onSecondary = PytColors.onSecondary,
    tertiary = PytColors.tertiary,
    onTertiary = PytColors.onTertiary,
    background = PytColors.parchment,
    onBackground = PytColors.onParchment,
    surface = PytColors.parchment,
    onSurface = PytColors.onParchment,
    surfaceVariant = PytColors.parchmentSunken,
    onSurfaceVariant = PytColors.onParchmentMuted,
    outline = PytColors.border,
    outlineVariant = PytColors.borderSoft,
    error = PytColors.danger,
    onError = PytColors.parchment,
)
