package com.majiang.counter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    secondary = Color(0xFFA5D6A7),
    tertiary = Color(0xFFFFCC80),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
)

@Composable
fun MajiangTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
