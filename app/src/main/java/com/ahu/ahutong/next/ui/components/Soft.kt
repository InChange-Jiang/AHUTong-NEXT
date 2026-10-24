package com.ahu.ahutong.next.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.next.ui.theme.NextPalette

@Composable
fun SoftBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NextPalette.Background, NextPalette.BackgroundBottom),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NextPalette.Glow, Color.Transparent),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.9f,
                        ),
                        radius = size.width * 0.9f,
                        center = Offset(size.width / 2f, 0f),
                    )
                },
        )
        content()
    }
}

@Composable
fun SoftSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    color: Color = NextPalette.Surface,
    elevation: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color(0x1417191D),
            spotColor = Color(0x1A17191D),
        ),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
        content = content,
    )
}

@Composable
fun Modifier.softClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "softPress",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

@Composable
fun SoftIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SoftSurface(
        modifier = modifier
            .size(46.dp)
            .softClickable(onClick),
        shape = CircleShape,
        elevation = 8.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun SoftPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = RoundedCornerShape(50),
                clip = false,
                ambientColor = Color(0x332F6BFF),
                spotColor = Color(0x332F6BFF),
            )
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(Color(0xFF4A82FF), NextPalette.Accent))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFFE2E0DA), Color(0xFFE2E0DA)))
                },
            )
            .softClickable(onClick, enabled)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color.White else NextPalette.InkSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun SoftSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftSurface(
        modifier = modifier.softClickable(onClick),
        shape = RoundedCornerShape(50),
        color = NextPalette.SurfaceSoft,
        elevation = 2.dp,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = NextPalette.Ink,
        )
    }
}

@Composable
fun SoftChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    selected: Boolean = false,
) {
    SoftSurface(
        modifier = modifier.softClickable(onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) NextPalette.AccentSoft else NextPalette.Surface,
        elevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = if (selected) NextPalette.Accent else NextPalette.InkSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) NextPalette.AccentDeep else NextPalette.Ink,
            )
        }
    }
}

@Composable
fun AhtorusMark(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = size * 0.2f,
                shape = CircleShape,
                clip = false,
                ambientColor = Color(0x332F6BFF),
                spotColor = Color(0x332F6BFF),
            )
            .border(
                width = size * 0.24f,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFB7D6FF), Color(0xFF4A82FF), Color(0xFF1F55E8)),
                ),
                shape = CircleShape,
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = size * 0.13f, top = size * 0.08f)
                .size(width = size * 0.28f, height = size * 0.13f)
                .background(Color.White.copy(alpha = 0.62f), CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = size * 0.14f, bottom = size * 0.10f)
                .size(size * 0.11f)
                .background(Color.White.copy(alpha = 0.18f), CircleShape),
        )
    }
}

@Composable
fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = NextPalette.InkSecondary,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            shape = RoundedCornerShape(18.dp),
            visualTransformation = visualTransformation,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NextPalette.SurfaceSoft,
                unfocusedContainerColor = NextPalette.SurfaceSoft,
                focusedBorderColor = NextPalette.Accent.copy(alpha = 0.55f),
                unfocusedBorderColor = Color.Transparent,
                cursorColor = NextPalette.Accent,
                focusedTextColor = NextPalette.Ink,
                unfocusedTextColor = NextPalette.Ink,
            ),
        )
    }
}
