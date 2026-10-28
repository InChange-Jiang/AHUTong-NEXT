package com.ahu.ahutong.next.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 轻量提示：白底圆角胶囊，居中偏下。新消息直接替换旧消息，不排队。
 * 对应旧版“已是最新版本”那种提示样式，不使用系统 Toast。
 */
val LocalNextToast = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun NextToast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    var lastMessage by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) lastMessage = message
    }
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
    ) {
        // 外圈留白给阴影：Surface/AnimatedVisibility 的边界会裁掉超出自身的绘制。
        Box(
            modifier = Modifier
                .graphicsLayer { clip = false }
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { clip = false }
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(50),
                        clip = false,
                        ambientColor = Color(0x33000000),
                        spotColor = Color(0x33000000),
                    )
                    .background(Color.White, RoundedCornerShape(50))
                    .border(1.dp, Color(0x14000000), RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 11.dp),
            ) {
                Text(
                    text = lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1F1F1F),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
