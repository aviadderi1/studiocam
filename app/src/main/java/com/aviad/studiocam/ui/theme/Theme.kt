package com.aviad.studiocam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Black = Color(0xFF000000)
val PanelBg = Color(0xFF0D0D0D)
val CardBg = Color(0xFF1C1C1C)
val RecRed = Color(0xFFFF3B30)
val TextPrimary = Color(0xFFE8E8E8)
val TextSecondary = Color(0xFF666666)
val MeterGreen = Color(0xFF4CAF50)
val MeterAmber = Color(0xFFE6B84A)
val MeterRed = Color(0xFFE0743A)

private val StudioColorScheme = darkColorScheme(
    background = Black,
    surface = PanelBg,
    primary = RecRed,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun StudioCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        content = content
    )
}
