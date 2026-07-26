package com.gituploader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkOnPrimary,
    onSecondary = DarkOnSecondary,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    error = DarkError,
)

@Composable
fun GitBatchUploaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
