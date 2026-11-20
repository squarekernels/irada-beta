package com.ctxaware.irada.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF4B32F4)
private val LightColors = lightColorScheme(
    primary = Purple, onPrimary = Color.White,
    background = Color.White, onBackground = Color(0xFF11121A),
    surface = Color.White, onSurface = Color(0xFF11121A),
    surfaceVariant = Color(0xFFF6F4FF), onSurfaceVariant = Color(0xFF5C5B6D),
    outline = Color(0xFF777184), error = Color(0xFFB4233B)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4B5FD), onPrimary = Color(0xFF11121A),
    background = Color(0xFF11121A), onBackground = Color(0xFFF5F3FF),
    surface = Color(0xFF11121A), onSurface = Color(0xFFF5F3FF),
    surfaceVariant = Color(0xFF20212F), onSurfaceVariant = Color(0xFFBCB8CE),
    outline = Color(0xFF817A95), error = Color(0xFFFF9DAE)
)

@Composable
fun IradaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}

// Brand purple buttons retain readable white labels in both appearances.
val ActionPurple = Purple
