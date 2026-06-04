package com.pythontopia.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

private fun darkPythontopiaColorScheme() = darkColorScheme(
    primary = PytColors.primary,
    onPrimary = PytColors.onPrimary,
    background = PytColors.bgPrimary,
    onBackground = PytColors.textPrimary,
    surface = PytColors.surface,
    onSurface = PytColors.onSurface,
    error = PytColors.danger,
)

private fun lightPythontopiaColorScheme() = lightColorScheme(
    primary = PytColors.onPrimary,
    onPrimary = PytColors.textPrimary,
    background = PytColors.textPrimary,
    onBackground = PytColors.bgPrimary,
    surface = PytColors.textPrimary,
    onSurface = PytColors.bgPrimary,
    error = PytColors.danger,
)
