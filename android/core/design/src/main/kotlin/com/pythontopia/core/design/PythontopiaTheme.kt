package com.pythontopia.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun PythontopiaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = PytColors.primary,
        onPrimary = PytColors.onPrimary,
        background = PytColors.bgPrimary,
        onBackground = PytColors.textPrimary,
        surface = PytColors.surface,
        onSurface = PytColors.onSurface,
        error = PytColors.danger,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PytTypography.default,
        content = content,
    )
}
