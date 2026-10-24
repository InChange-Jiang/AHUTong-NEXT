package com.ahu.ahutong.next.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NextColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF081120),
    primaryContainer = Color(0xFF1D3557),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Color(0xFF8FD8C8),
    onSecondary = Color(0xFF062019),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFE7ECF5),
    surface = Color(0xFF141821),
    onSurface = Color(0xFFE7ECF5),
    surfaceVariant = Color(0xFF1C2230),
    onSurfaceVariant = Color(0xFFA5B0C4),
    outline = Color(0xFF2B3547),
    error = Color(0xFFFF8A9B),
    onError = Color(0xFF2A0710),
)

private val NextShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NextTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NextColors,
        shapes = NextShapes,
        typography = Typography(),
        content = content,
    )
}
