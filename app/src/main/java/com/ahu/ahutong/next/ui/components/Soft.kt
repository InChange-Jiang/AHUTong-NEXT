package com.ahu.ahutong.next.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.next.R
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
        modifier = modifier,
        shape = shape,
        color = color,
        shadowElevation = elevation,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
        content = content,
    )
}

@Composable
fun SoftIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(46.dp),
        shape = CircleShape,
        color = NextPalette.Surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
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
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = NextPalette.Accent,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFE2E0DA),
            disabledContentColor = NextPalette.InkSecondary,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 2.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SoftSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = NextPalette.SurfaceSoft,
            contentColor = NextPalette.Ink,
        ),
        elevation = ButtonDefaults.filledTonalButtonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
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
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) NextPalette.AccentSoft else NextPalette.Surface,
        shadowElevation = if (selected) 2.dp else 8.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
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
fun AgentAvatar(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = size * 0.18f,
                shape = CircleShape,
                clip = false,
                ambientColor = Color(0x33F07B46),
                spotColor = Color(0x33F07B46),
            )
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
    ) {
        Image(
            painter = painterResource(R.drawable.agent_ahtorus),
            contentDescription = "安筱桐",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
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
