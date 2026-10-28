package com.ahu.ahutong.next.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.next.ui.theme.NextPalette

/** 标题栏遮罩下方的留白，对话页与各功能页保持一致。 */
internal val HeaderScrimBottomPadding = 28.dp

/** 内容顶部让位距离：刚好越过遮罩，不在第一屏盖住卡片。 */
internal val HeaderScrimContentTop = 112.dp

/** 标题栏渐隐遮罩：顶部实底保证标题可读，底部快速淡出，尽量少盖页内内容。 */
internal fun Modifier.headerScrim(): Modifier = background(
    Brush.verticalGradient(
        0.0f to NextPalette.Background.copy(alpha = 0.99f),
        0.52f to NextPalette.Background.copy(alpha = 0.97f),
        0.78f to NextPalette.Background.copy(alpha = 0.86f),
        0.92f to NextPalette.Background.copy(alpha = 0.42f),
        1.0f to Color.Transparent,
    ),
)
