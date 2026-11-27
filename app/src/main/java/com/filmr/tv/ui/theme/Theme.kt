package com.filmr.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FilmrGold = Color(0xFFE5B869)
val FilmrGoldBright = Color(0xFFF6D38C)
val NuvioBgDark = Color(0xFF0A0B0E)
val NuvioSurface = Color(0xFF14161D)
val NuvioCard = Color(0xFF1B1E28)
val NuvioBorder = Color(0x33FFFFFF)
val NuvioFocusBorder = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF5F6FA)
val TextSecondary = Color(0xFFA8B0C2)

@Composable
fun FilmrNuvioTheme(
    accentColor: Color = FilmrGold,
    pureBlack: Boolean = true,
    content: @Composable () -> Unit
) {
    val bg = if (pureBlack) Color(0xFF07080B) else NuvioBgDark
    val scheme = darkColorScheme(
        primary = accentColor,
        onPrimary = Color(0xFF111111),
        secondary = Color(0xFF8EA8FF),
        background = bg,
        surface = NuvioSurface,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        surfaceVariant = NuvioCard,
        onSurfaceVariant = TextSecondary
    )
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
