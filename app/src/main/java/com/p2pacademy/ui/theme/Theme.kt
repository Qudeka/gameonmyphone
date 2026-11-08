package com.p2pacademy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object P2PColors {
    val Background = Color(0xFF0B0F14)
    val Surface = Color(0xFF131A22)
    val SurfaceHigh = Color(0xFF1B2530)
    val Brand = Color(0xFF3DDC97)
    val Profit = Color(0xFF2ECC71)
    val Loss = Color(0xFFFF5C5C)
    val Warning = Color(0xFFF5C542)
    val TextMuted = Color(0xFF8A97A6)
}

private val Scheme = darkColorScheme(
    primary = P2PColors.Brand,
    onPrimary = Color(0xFF00241A),
    background = P2PColors.Background,
    onBackground = Color(0xFFE6EDF3),
    surface = P2PColors.Surface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = P2PColors.SurfaceHigh,
    onSurfaceVariant = P2PColors.TextMuted,
    error = P2PColors.Loss,
)

@Composable
fun P2PTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
