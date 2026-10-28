package com.ahu.ahutong.next.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private data class PaletteTokens(
    val background: Color,
    val backgroundBottom: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val surfaceBorder: Color,
    val surfaceBorderStrong: Color,
    val surfaceDisabled: Color,
    val ink: Color,
    val inkSecondary: Color,
    val accent: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val line: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerSoft: Color,
    val glow: Color,
    val pendingSurface: Color,
)

private val LightTokens = PaletteTokens(
    background = Color(0xFFFAF9F6),
    backgroundBottom = Color(0xFFF3F1EB),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF2F1ED),
    surfaceBorder = Color(0xCCFFFFFF),
    surfaceBorderStrong = Color(0xE6FFFFFF),
    surfaceDisabled = Color(0xFFE2E0DA),
    ink = Color(0xFF17191D),
    inkSecondary = Color(0xFF71767F),
    accent = Color(0xFFF07B46),
    accentDeep = Color(0xFFD9662F),
    accentSoft = Color(0xFFFFF0E8),
    line = Color(0x1417191D),
    success = Color(0xFF2E9E6B),
    warning = Color(0xFFB7791F),
    danger = Color(0xFFD94A5A),
    dangerSoft = Color(0xFFFFF1F2),
    glow = Color(0x18F07B46),
    pendingSurface = Color(0xFFF3F7FF),
)

private val DarkTokens = PaletteTokens(
    background = Color(0xFF121110),
    backgroundBottom = Color(0xFF0C0B0A),
    surface = Color(0xFF1C1B18),
    surfaceSoft = Color(0xFF272520),
    surfaceBorder = Color(0x1FFFFFFF),
    surfaceBorderStrong = Color(0x33FFFFFF),
    surfaceDisabled = Color(0xFF2C2A26),
    ink = Color(0xFFF4F1EA),
    inkSecondary = Color(0xFFA09A90),
    accent = Color(0xFFF5865A),
    accentDeep = Color(0xFFFFB392),
    accentSoft = Color(0xFF3A2318),
    line = Color(0x1FFFFFFF),
    success = Color(0xFF57C795),
    warning = Color(0xFFE0A64F),
    danger = Color(0xFFFF7D88),
    dangerSoft = Color(0xFF3A1F23),
    glow = Color(0x26F07B46),
    pendingSurface = Color(0xFF1B2430),
)

/**
 * 全局调色板。颜色存放在快照状态里，切换明暗时已组合的界面会自动重绘，
 * 因此业务代码仍然按 [NextPalette.Background] 这样的方式直接取值。
 */
object NextPalette {
    private var tokens by mutableStateOf(LightTokens)
    private var appliedDark = false

    var isDark by mutableStateOf(false)
        private set

    val Background: Color get() = tokens.background
    val BackgroundBottom: Color get() = tokens.backgroundBottom
    val Surface: Color get() = tokens.surface
    val SurfaceSoft: Color get() = tokens.surfaceSoft
    val SurfaceBorder: Color get() = tokens.surfaceBorder
    val SurfaceBorderStrong: Color get() = tokens.surfaceBorderStrong
    val SurfaceDisabled: Color get() = tokens.surfaceDisabled
    val Ink: Color get() = tokens.ink
    val InkSecondary: Color get() = tokens.inkSecondary
    val Accent: Color get() = tokens.accent
    val AccentDeep: Color get() = tokens.accentDeep
    val AccentSoft: Color get() = tokens.accentSoft
    val Line: Color get() = tokens.line
    val Success: Color get() = tokens.success
    val Warning: Color get() = tokens.warning
    val Danger: Color get() = tokens.danger
    val DangerSoft: Color get() = tokens.dangerSoft
    val Glow: Color get() = tokens.glow
    val PendingSurface: Color get() = tokens.pendingSurface

    internal fun use(dark: Boolean) {
        if (appliedDark == dark) return
        appliedDark = dark
        isDark = dark
        tokens = if (dark) DarkTokens else LightTokens
    }
}

/** 深色下把偏暗的装饰色提亮，保证在近黑背景上仍看得清。 */
fun Color.forDarkSurface(amount: Float = 0.35f): Color {
    if (!NextPalette.isDark) return this
    return Color(
        red = red + (1f - red) * amount,
        green = green + (1f - green) * amount,
        blue = blue + (1f - blue) * amount,
        alpha = alpha,
    )
}

private val NextLightColors = lightColorScheme(
    primary = LightTokens.accent,
    onPrimary = Color.White,
    primaryContainer = LightTokens.accentSoft,
    onPrimaryContainer = LightTokens.accentDeep,
    secondary = LightTokens.inkSecondary,
    onSecondary = Color.White,
    background = LightTokens.background,
    onBackground = LightTokens.ink,
    surface = LightTokens.surface,
    onSurface = LightTokens.ink,
    surfaceVariant = LightTokens.surfaceSoft,
    onSurfaceVariant = LightTokens.inkSecondary,
    outline = Color(0xFFDDDAD2),
    error = LightTokens.danger,
    onError = Color.White,
)

private val NextDarkColors = darkColorScheme(
    primary = DarkTokens.accent,
    onPrimary = Color(0xFF2A1206),
    primaryContainer = DarkTokens.accentSoft,
    onPrimaryContainer = DarkTokens.accentDeep,
    secondary = DarkTokens.inkSecondary,
    onSecondary = Color(0xFF1A1208),
    background = DarkTokens.background,
    onBackground = DarkTokens.ink,
    surface = DarkTokens.surface,
    onSurface = DarkTokens.ink,
    surfaceVariant = DarkTokens.surfaceSoft,
    onSurfaceVariant = DarkTokens.inkSecondary,
    outline = Color(0xFF4A453E),
    error = DarkTokens.danger,
    onError = Color(0xFF2A0F12),
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
fun NextTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    NextPalette.use(darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) NextDarkColors else NextLightColors,
        shapes = NextShapes,
        typography = NextTypography,
        content = content,
    )
}
