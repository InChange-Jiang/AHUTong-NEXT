package com.ahu.ahutong.next.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NextPalette {
    val Background = Color(0xFFFAF9F6)
    val BackgroundBottom = Color(0xFFF3F1EB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSoft = Color(0xFFF2F1ED)
    val Ink = Color(0xFF17191D)
    val InkSecondary = Color(0xFF71767F)
    val Accent = Color(0xFFF07B46)
    val AccentDeep = Color(0xFFD9662F)
    val AccentSoft = Color(0xFFFFF0E8)
    val Line = Color(0x1417191D)
    val Success = Color(0xFF2E9E6B)
    val Warning = Color(0xFFB7791F)
    val Danger = Color(0xFFD94A5A)
    val DangerSoft = Color(0xFFFFF1F2)
    val Glow = Color(0x18F07B46)
}

private val NextColors = lightColorScheme(
    primary = NextPalette.Accent,
    onPrimary = Color.White,
    primaryContainer = NextPalette.AccentSoft,
    onPrimaryContainer = NextPalette.AccentDeep,
    secondary = NextPalette.InkSecondary,
    onSecondary = Color.White,
    background = NextPalette.Background,
    onBackground = NextPalette.Ink,
    surface = NextPalette.Surface,
    onSurface = NextPalette.Ink,
    surfaceVariant = NextPalette.SurfaceSoft,
    onSurfaceVariant = NextPalette.InkSecondary,
    outline = Color(0xFFDDDAD2),
    error = NextPalette.Danger,
    onError = Color.White,
)

private val NextTypography = Typography().copy(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.2.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 38.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

private val NextShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun NextTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NextColors,
        shapes = NextShapes,
        typography = NextTypography,
        content = content,
    )
}
